#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Preserve legal files from resolved runtime JARs without filename collisions.

Input is maven-dependency-plugin:build-classpath output on this operating system.
This performs no license approvals, downloads, source rewriting or file filtering
by license type. Archives without legal files remain visible in inventory.json.
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


def collect(classpath, output):
    marker = output / ".generated-by-collect-third-party-licenses"
    if output.is_symlink():
        raise ValueError("Output must not be a symlink")
    if output.exists():
        if not marker.is_file():
            raise ValueError("Refusing to replace an output directory without our marker")
        shutil.rmtree(output)
    output.mkdir(parents=True)
    marker.write_text("Generated runtime dependency license inventory.\n", encoding="utf-8")
    entries = []
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
        entries.append({"artifact": source.name, "sha256": digest,
                        "directory": directory, "legal_files": files})
    (output / "inventory.json").write_text(
        json.dumps({"format": 1, "scope": "runtime", "artifacts": entries}, indent=2) + "\n",
        encoding="utf-8")
    missing = sum(not entry["legal_files"] for entry in entries)
    print(f"Runtime legal inventory: {len(entries)} JARs, {missing} without embedded legal files")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    collect(args.classpath, args.output)


if __name__ == "__main__":
    main()
