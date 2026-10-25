#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Preserve legal files from resolved runtime JARs without filename collisions.

Input is maven-dependency-plugin:build-classpath output on this operating system.
This performs no license approvals, downloads, source rewriting or file filtering
by license type. Archives without legal files remain visible in inventory.json.

A JAR that carries no legal files of its own gets the texts a repository supplies for it
(--supplemental: a directory with notices.json naming, per notice directory, the JAR base
names it covers -- the file name without version, classifier and extension). With
--require-complete a JAR that has neither fails the run, so a new dependency without
legal files cannot reach a fat jar unnoticed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import zipfile


def legal_file(name):
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts or "\\" in name or any(":" in part for part in path.parts):
        raise ValueError("Unsafe archive entry: " + name)
    if name.endswith("/"):
        return False
    lower = path.name.lower()
    # A provider's LICENSE.class is executable code, not a license document.
    if path.suffix.lower() in {".class", ".java", ".kt", ".png", ".jpg", ".gif", ".jar"}:
        return False
    return (any(part.lower() in {"licenses", "licences"} for part in path.parts)
            or re.fullmatch(r"(?:licen[sc]e|notice|copying|copyright)(?:[-_.].*)?", lower) is not None)


def base_name(jar_name):
    """`lwjgl-opencl-3.3.6-natives-linux.jar` -> `lwjgl-opencl`: everything before the version."""
    match = re.match(r"(.+?)-\d", jar_name)
    return match.group(1) if match else jar_name[:-len(".jar")]


def supplemental_notices(directory):
    """Map JAR base name -> notice directory, from <directory>/notices.json."""
    if directory is None:
        return {}
    config = json.loads((directory / "notices.json").read_text(encoding="utf-8"))
    if set(config) - {"format", "notices"} or config.get("format") != 1:
        raise ValueError("notices.json needs {\"format\": 1, \"notices\": [...]}")
    result = {}
    for notice in config["notices"]:
        if set(notice) - {"directory", "artifacts", "license"} or not notice.get("artifacts"):
            raise ValueError("a notice needs directory, artifacts and license: " + json.dumps(notice))
        folder = directory / notice["directory"]
        if not folder.is_dir() or not any(path.is_file() for path in folder.rglob("*")):
            raise ValueError("notice directory missing or empty: " + str(folder))
        for artifact in notice["artifacts"]:
            if artifact in result:
                raise ValueError("JAR base name listed twice in notices.json: " + artifact)
            result[artifact] = folder
    return result


def collect(classpath, output, supplemental=None, require_complete=False):
    marker = output / ".generated-by-collect-third-party-licenses"
    if output.is_symlink():
        raise ValueError("Output must not be a symlink")
    if output.exists():
        if not marker.is_file():
            raise ValueError("Refusing to replace an output directory without our marker")
        shutil.rmtree(output)
    notices = supplemental_notices(supplemental)
    output.mkdir(parents=True)
    marker.write_text("Generated runtime dependency license inventory.\n", encoding="utf-8")
    entries = []
    used = set()
    for item in sorted(set(classpath.read_text(encoding="utf-8").strip().split(os.pathsep))):
        if not item:
            continue
        source = Path(item)
        if not source.is_file() or source.suffix.lower() != ".jar":
            raise ValueError("Classpath entry is not a readable JAR: " + item)
        with source.open("rb") as stream:
            hasher = hashlib.sha256()
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                hasher.update(block)
            digest = hasher.hexdigest()
        # The filename preserves classifiers; the digest also distinguishes equal
        # filenames from different groups or different bytes of a snapshot.
        directory = re.sub(r"[^a-zA-Z0-9._-]", "_", source.name) + "-" + digest[:16]
        files = []
        with zipfile.ZipFile(source) as archive:
            for info in sorted(archive.infolist(), key=lambda entry: entry.filename):
                if not legal_file(info.filename):
                    continue
                destination = output / directory / info.filename
                destination.parent.mkdir(parents=True, exist_ok=True)
                data = archive.read(info)
                if destination.exists() and destination.read_bytes() != data:
                    raise ValueError("Conflicting duplicate entry: " + info.filename)
                destination.write_bytes(data)
                files.append(info.filename)
        entry = {"artifact": source.name, "sha256": digest, "directory": directory, "legal_files": files}
        folder = notices.get(base_name(source.name))
        if not files and folder is not None:
            supplied = []
            for path in sorted(p for p in folder.rglob("*") if p.is_file()):
                relative = "supplemental/" + path.relative_to(folder).as_posix()
                destination = output / directory / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, destination)
                supplied.append(relative)
            entry["supplemental_files"] = supplied
            used.add(base_name(source.name))
        entries.append(entry)
    (output / "inventory.json").write_text(
        json.dumps({"format": 1, "scope": "runtime", "artifacts": entries}, indent=2) + "\n",
        encoding="utf-8")
    missing = [entry["artifact"] for entry in entries
               if not entry["legal_files"] and not entry.get("supplemental_files")]
    supplied = sum(bool(entry.get("supplemental_files")) for entry in entries)
    print(f"Runtime legal inventory: {len(entries)} JARs, {supplied} with supplemental notices, "
          f"{len(missing)} without any legal files")
    stale = sorted(set(notices) - used)
    for name in stale:
        print(f"Note: notices.json covers {name}, which no runtime JAR without its own legal files needs")
    if require_complete and missing:
        raise ValueError("JARs without legal files and without a supplemental notice: " + ", ".join(missing))
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--supplemental", type=Path, help="directory with notices.json and the notice texts")
    parser.add_argument("--require-complete", action="store_true",
                        help="fail when a JAR has neither legal files nor a supplemental notice")
    args = parser.parse_args()
    try:
        collect(args.classpath, args.output, args.supplemental, args.require_complete)
    except (ValueError, OSError) as error:
        print(f"::error::{error}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
