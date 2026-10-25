# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Collect signed Maven release assets and explicitly configured extra files.

No repository names or native/Android layouts live here. See the workspace policy
policies/release-asset-scripts.md for the JSON contract and staging options.
"""

import csv
import json
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path


def _keys(value, allowed, context):
    if not isinstance(value, dict) or set(value) - set(allowed):
        raise ValueError(f"invalid {context}; allowed keys: {', '.join(allowed)}")


def _inside(root, relative):
    if not isinstance(relative, str) or not relative or Path(relative).is_absolute():
        raise ValueError(f"expected a repository-relative path: {relative!r}")
    path = (root / relative).resolve()
    if not path.is_relative_to(root):
        raise ValueError(f"path escapes repository: {relative}")
    return path


def configuration(root, path=None):
    """Read the explicit contract; unknown keys fail instead of silently dropping assets.

    The contract is part of the repository: a relative --config is relative to the repository root,
    and a path outside the repository is refused.
    """
    root = Path(root).resolve()
    path = (root / (path if path is not None else ".github/release-assets.json")).resolve()
    if not path.is_relative_to(root):
        raise ValueError(f"release asset configuration outside the repository: {path}")
    config = json.loads(path.read_text())
    _keys(config, ("schema", "maven", "files", "publication"), "release asset configuration")
    if type(config.get("schema")) is not int or config["schema"] != 1:
        raise ValueError("release asset configuration requires schema: 1")
    publication = config.get("publication", {})
    _keys(publication, ("central_roots",), "publication configuration")
    if "central_roots" in publication and (not isinstance(publication["central_roots"], list)
            or not publication["central_roots"]
            or any(not isinstance(path, str) or not path for path in publication["central_roots"])):
        raise ValueError("publication.central_roots must be a nonempty list of paths")
    maven = config.get("maven", {})
    _keys(maven, ("standalone", "cyclonedx"), "maven configuration")
    if not isinstance(maven.get("standalone", []), list):
        raise ValueError("maven.standalone must be a list of module paths")
    if type(maven.get("cyclonedx", False)) is not bool:
        raise ValueError("maven.cyclonedx must be a boolean")
    if not isinstance(config.get("files", []), list):
        raise ValueError("files must be a list")
    for rule in config.get("files", []):
        if isinstance(rule, dict) and "central" in rule and type(rule["central"]) is not bool:
            raise ValueError("file rule central must be a boolean")
    return config


def projects(root, standalone=()):
    """Discover the reactor recursively, then optional separately published Maven projects."""
    root = Path(root).resolve()
    seen, result = set(), []

    def visit(folder, reactor=True):
        folder = folder.resolve()
        if not folder.is_relative_to(root) or folder in seen:
            raise ValueError(f"invalid or repeated Maven module: {folder}")
        seen.add(folder)
        pom = ET.parse(folder / "pom.xml").getroot()
        artifact = pom.findtext("{*}artifactId")
        version = pom.findtext("{*}version") or pom.findtext("{*}parent/{*}version")
        packaging = pom.findtext("{*}packaging", "jar")
        if (not artifact or not version or not re.fullmatch(r"[\w.-]+", artifact)
                or not re.fullmatch(r"[\w.+-]+", version)
                or packaging not in ("pom", "jar", "maven-plugin")):
            raise ValueError(f"unsupported release coordinates in {folder / 'pom.xml'}")
        if result and version != result[0][2]:
            raise ValueError(f"release version mismatch in {folder / 'pom.xml'}: {version}")
        result.append((folder, artifact, version, packaging, reactor))
        for module in pom.findall("{*}modules/{*}module"):
            visit(_inside(root, str(folder.relative_to(root) / (module.text or "").strip())), reactor)

    visit(root)
    for module in standalone:
        visit(_inside(root, module), reactor=False)
    return result


def _classifiers(root, spec):
    if isinstance(spec, dict):
        _keys(spec, ("csv", "column"), "classifier CSV")
        if not isinstance(spec.get("column"), str) or not spec["column"]:
            raise ValueError("classifier CSV requires a column")
        with _inside(root, spec.get("csv")).open() as stream:
            rows = csv.DictReader(line for line in stream if line.strip() and not line.lstrip().startswith("#"))
            if spec["column"] not in (rows.fieldnames or []):
                raise ValueError(f"missing classifier CSV column: {spec['column']}")
            spec = [row[spec["column"]] for row in rows]
    if (not isinstance(spec, list) or not spec or any(not isinstance(c, str)
            or not re.fullmatch(r"[\w.-]*", c) for c in spec) or len(set(spec)) != len(spec)):
        raise ValueError("classifiers must be a nonempty list of unique classifier names")
    return spec


def _format(template, version, classifier):
    if not isinstance(template, str) or not template:
        raise ValueError("file source/name must be a nonempty string")
    try:
        return template.format(version=version, classifier=classifier)
    except (KeyError, ValueError, IndexError, AttributeError) as error:
        raise ValueError(f"invalid release filename template: {template}") from error


def file_classifiers(root, index, config_path=None):
    """Let packaging loops consume the same classifier list the collector checks."""
    if index < 0:
        raise ValueError("file rule index must not be negative")
    config = configuration(root, config_path)
    try:
        item = config.get("files", [])[index]
    except IndexError as error:
        raise ValueError(f"no file rule at index {index}") from error
    _keys(item, ("source", "name", "classifiers", "central"), "file rule")
    return _classifiers(Path(root).resolve(), item.get("classifiers", [""]))


def collect(root, destination, config_path=None, *, maven_only=False, maven_assets=None):
    """Copy available outputs, returning all missing files/signatures. Never hide partial output.

    maven_only stages deploy-signed files before later packaging overwrites target/.
    maven_assets reads those staged files by their final names during the final collection.
    """
    root, destination = Path(root).resolve(), Path(destination).resolve()
    config = configuration(root, config_path)
    modules = projects(root, config.get("maven", {}).get("standalone", []))
    version, expected, errors = modules[0][2], [], []
    for folder, artifact, _, packaging, reactor in modules:
        base = f"{artifact}-{version}"
        outputs = [(folder / "target" / f"{base}.pom", f"{base}.pom")]
        if packaging != "pom":
            for classifier in ("", "-sources", "-javadoc"):
                name = f"{base}{classifier}.jar"
                outputs.append((folder / "target" / name, name))
        if reactor and config.get("maven", {}).get("cyclonedx", False):
            outputs.append((folder / "target/bom.json", f"{base}-cyclonedx.json"))
        for source, name in outputs:
            expected.append((Path(maven_assets) / name if maven_assets is not None else source, name))

    for item in [] if maven_only else config.get("files", []):
        _keys(item, ("source", "name", "classifiers", "central"), "file rule")
        for classifier in _classifiers(root, item.get("classifiers", [""])):
            pattern = _format(item.get("source"), version, classifier)
            _inside(root, pattern)
            matches = sorted(root.glob(pattern))
            if len(matches) != 1:
                errors.append(f"expected one release output matching {pattern}, found {len(matches)}")
                continue
            source = matches[0]
            _inside(root, str(source.relative_to(root)))
            name = _format(item["name"], version, classifier) if "name" in item else source.name
            expected.append((source, name))

    names = set()
    for source, name in expected:
        if (Path(name).name != name or name in (".", "..") or name.endswith(".asc")
                or name in names or name + ".asc" in names):
            raise ValueError(f"invalid or duplicate release asset filename: {name}")
        names.update((name, name + ".asc"))
        if destination == source.parent.resolve():
            raise ValueError("asset destination overlaps release inputs")
    destination.mkdir(parents=True, exist_ok=True)
    if any(destination.iterdir()):
        raise ValueError(f"asset destination must be empty: {destination}")
    for source, name in expected:
        for suffix in ("", ".asc"):
            path = Path(str(source) + suffix).resolve()
            if not path.is_file() or path.stat().st_size == 0:
                shown = path.relative_to(root) if path.is_relative_to(root) else path
                errors.append(f"missing or empty release output: {shown.as_posix()}")
                continue
            shutil.copyfile(path, destination / (name + suffix))
    return errors


def signature_gaps(directory):
    """Presence/size check for every file type; signing itself verifies cryptographic validity."""
    directory = Path(directory)
    files = sorted(directory.iterdir()) if directory.is_dir() else []
    artifacts = [path for path in files if not path.name.endswith(".asc")]
    if not artifacts:
        return -1, [f"no artifacts in {directory} -- the collection step produced nothing"]
    errors = []
    for path in artifacts:
        signature = Path(str(path) + ".asc")
        if not path.is_file() or path.stat().st_size == 0:
            errors.append(f"missing or empty artifact: {path.name}")
        elif not signature.is_file() or signature.stat().st_size == 0:
            errors.append(f"unsigned: {path.name} has no nonempty detached .asc")
    for path in files:
        if path.name.endswith(".asc") and not path.with_suffix("").is_file():
            errors.append(f"orphan signature: {path.name}")
    return len(errors), errors
