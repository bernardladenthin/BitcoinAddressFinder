# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Freeze signed publication bytes once; independent consumers never rebuild them."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

from .releaseassets import configuration, projects, signature_gaps, _classifiers, _format, _inside

PORTAL = "https://central.sonatype.com/api/v1/publisher/upload"
SNAPSHOTS = "https://central.sonatype.com/repository/maven-snapshots/"
DEPLOY_PLUGIN = "3.1.4"


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def safe_path(root, name):
    p = PurePosixPath(name)
    if not name or p.is_absolute() or ".." in p.parts or "\\" in name or ":" in name:
        raise ValueError("unsafe publication path: " + name)
    target = root / name
    if target.is_symlink() or any(parent.is_symlink() for parent in target.parents):
        raise ValueError("publication symlink: " + name)
    return target


def expected_central(root, config, modules, *, only_central=True):
    names = set()
    for _, artifact, version, packaging, reactor in modules:
        base = f"{artifact}-{version}"
        names.add(base + ".pom")
        if packaging != "pom":
            names.update(base + suffix + ".jar" for suffix in ("", "-sources", "-javadoc"))
        if reactor and config.get("maven", {}).get("cyclonedx", False):
            names.add(base + "-cyclonedx.json")
    for rule in config.get("files", []):
        if only_central and not rule.get("central", False):
            continue
        for classifier in _classifiers(root, rule.get("classifiers", [""])):
            matches = list(root.glob(_format(rule["source"], modules[0][2], classifier)))
            if len(matches) != 1:
                raise ValueError("missing/ambiguous Central input: " + rule["source"])
            names.add(_format(rule["name"], modules[0][2], classifier) if "name" in rule else matches[0].name)
    return names


def prepare(root, assets, destination):
    """Gate both publishers on exact membership, valid signatures and frozen checksums."""
    root, assets, destination = Path(root).resolve(), Path(assets).resolve(), Path(destination).resolve()
    if destination.exists():
        raise ValueError("publication destination must not exist")
    missing, errors = signature_gaps(assets)
    if missing:
        raise ValueError("incomplete publication: " + "; ".join(errors))
    config = configuration(root)
    publication = config.get("publication", {})
    modules = projects(root, config.get("maven", {}).get("standalone", []))
    version = modules[0][2]
    channel = "snapshot" if version.endswith("-SNAPSHOT") else "release"
    staging = "central-deferred" if channel == "snapshot" else "central-staging"
    roots = publication.get("central_roots", ["target/{staging}"])
    central = {}
    for item in roots:
        folder = _inside(root, item.format(staging=staging))
        if not folder.is_dir():
            raise ValueError("missing Central staging: " + str(folder))
        for path in folder.rglob("*"):
            if not path.is_file():
                continue
            relative = path.relative_to(folder).as_posix()
            safe_path(folder, relative)
            if not re.search(r"\.(?:pom|jar|aar|json)(?:\.asc)?$", path.name):
                continue  # Maven metadata/checksums are regenerated, never mistaken for payload.
            parts = PurePosixPath(relative).parts
            if len(parts) < 4 or parts[-2] != version:
                raise ValueError("unexpected Central coordinates: " + relative)
            if relative in central and digest(central[relative]) != digest(path):
                raise ValueError("conflicting Central input: " + relative)
            central[relative] = path
    expected = expected_central(root, config, modules)
    actual = {path.name for path in central.values() if not path.name.endswith(".asc")}
    if actual != expected or len(central) != 2 * len(expected):
        raise ValueError("Central membership mismatch; missing=" + str(sorted(expected - actual))
                         + "; unexpected=" + str(sorted(actual - expected)))
    github_expected = expected_central(root, config, modules, only_central=False)
    github_actual = {p.name for p in assets.iterdir() if not p.name.endswith(".asc")}
    if github_actual != github_expected:
        raise ValueError("GitHub membership does not match the release contract")
    # Require the Central pair to match GitHub; verify signatures on the frozen copy.
    for path in sorted(assets.iterdir()):
        if path.is_symlink() or not path.is_file():
            raise ValueError("non-flat publication input: " + str(path))
    for name, path in central.items():
        if not (assets / path.name).is_file() or digest(assets / path.name) != digest(path):
            raise ValueError("Central/GitHub bytes differ: " + name)
    # Build a temporary package, then promote it locally only after all checks succeed.
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="publication-", dir=destination.parent) as tmp:
        package = Path(tmp) / "package"
        (package / "github").mkdir(parents=True)
        for path in assets.iterdir():
            shutil.copyfile(path, package / "github" / path.name)
        for relative, path in central.items():
            target = safe_path(package / "central", relative)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, target)
        for path in sorted((package / "github").iterdir()):
            if not path.name.endswith(".asc"):
                subprocess.run(["gpg", "--batch", "--verify", str(path) + ".asc", str(path)], check=True)
        for path in (package / "central").rglob("*"):
            if path.is_file() and digest(path) != digest(package / "github" / path.name):
                raise ValueError("frozen Central/GitHub files differ: " + path.name)
        manifest = {"schema": 1, "version": version, "channel": channel,
                    "commit": os.environ.get("GITHUB_SHA", ""), "files": {}}
        for path in sorted(package.rglob("*")):
            if path.is_file():
                manifest["files"][path.relative_to(package).as_posix()] = {
                    "sha256": digest(path), "size": path.stat().st_size}
        (package / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        shutil.move(str(package), destination)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write("complete=true\n")
    print(f"Publication ready: {version}; {len(expected)} Central artifacts; all signatures verified")


def verify(package):
    """Transport integrity check; never regenerate or re-sign publication files."""
    package = Path(package).resolve()
    manifest = json.loads((package / "manifest.json").read_text())
    if manifest.get("schema") != 1 or manifest.get("channel") not in ("snapshot", "release"):
        raise ValueError("unsupported publication manifest")
    if os.environ.get("GITHUB_SHA") and manifest.get("commit") != os.environ["GITHUB_SHA"]:
        raise ValueError("publication belongs to a different commit")
    actual = {p.relative_to(package).as_posix() for p in package.rglob("*") if p.is_file()}
    if actual != set(manifest["files"]) | {"manifest.json"}:
        raise ValueError("publication file membership changed")
    for relative, info in manifest["files"].items():
        path = safe_path(package, relative)
        if not path.is_file() or path.stat().st_size != info["size"] or digest(path) != info["sha256"]:
            raise ValueError("publication bytes changed: " + relative)
    return manifest


def snapshot_pom(package, destination, url=SNAPSHOTS):
    """Generate transfer-only deploy-file executions, including all signature extensions.

    Maven handles timestamped snapshot names and metadata in one request per component.
    There is no project build, dependency resolution, compilation or signing phase.
    """
    manifest = verify(package)
    if manifest["channel"] != "snapshot":
        raise ValueError("snapshot transfer requires a SNAPSHOT version")
    root = ET.Element("project", xmlns="http://maven.apache.org/POM/4.0.0")
    def child(parent, name, text):
        node = ET.SubElement(parent, name)
        node.text = str(text)
        return node
    for name, value in [("modelVersion", "4.0.0"), ("groupId", "local.publication"),
                        ("artifactId", "snapshot-transfer"), ("version", "1"), ("packaging", "pom")]:
        child(root, name, value)
    plugin = ET.SubElement(ET.SubElement(ET.SubElement(root, "build"), "plugins"), "plugin")
    child(plugin, "groupId", "org.apache.maven.plugins")
    child(plugin, "artifactId", "maven-deploy-plugin")
    child(plugin, "version", DEPLOY_PLUGIN)
    executions = ET.SubElement(plugin, "executions")
    central = Path(package).resolve() / "central"
    for i, pom in enumerate(sorted(central.rglob("*.pom"))):
        parts = pom.relative_to(central).parts
        group, artifact, version = ".".join(parts[:-3]), parts[-3], parts[-2]
        xml = ET.parse(pom).getroot()
        packaging = xml.findtext("{*}packaging", "jar")
        extension = "jar" if packaging == "maven-plugin" else packaging
        base = f"{artifact}-{version}"
        # A POM-only component has a single unclassified signature. Deploy-file
        # treats an empty classifiers parameter as absent, so use that signature
        # as the primary artifact and let pomFile attach its signed POM alongside.
        transfer_packaging = "pom.asc" if packaging == "pom" else packaging
        main = pom.parent / (f"{base}.pom.asc" if packaging == "pom" else f"{base}.{extension}")
        if not main.is_file():
            raise ValueError("missing primary Central artifact: " + str(main))
        execution = ET.SubElement(executions, "execution")
        child(execution, "id", f"transfer-{i}")
        child(execution, "phase", "validate")
        child(ET.SubElement(execution, "goals"), "goal", "deploy-file")
        config = ET.SubElement(execution, "configuration")
        for name, value in [("repositoryId", "central"), ("url", url), ("file", main),
                            ("pomFile", pom), ("groupId", group), ("artifactId", artifact),
                            ("version", version), ("packaging", transfer_packaging), ("generatePom", "false")]:
            child(config, name, value)
        attached = []
        for path in sorted(pom.parent.iterdir()):
            if path in (pom, main):
                continue
            suffix = path.name[len(base):] if path.name.startswith(base) else ""
            match = re.fullmatch(r"(?:-(.+))?\.(pom|jar|aar|json)(\.asc)?", suffix)
            if not match:
                raise ValueError("invalid attached artifact: " + path.name)
            classifier = match[1] or ""
            kind = match[2] + (match[3] or "")
            if "," in str(path):
                raise ValueError("comma in Maven transfer path")
            attached.append((str(path), classifier, kind))
        if attached:
            for name, column in [("files", 0), ("classifiers", 1), ("types", 2)]:
                child(config, name, ",".join(item[column] for item in attached))
    ET.ElementTree(root).write(destination, encoding="utf-8", xml_declaration=True)


def upload(package):
    """Transfer frozen files, accepting Portal upload without validation polling."""
    package = Path(package).resolve()
    manifest = verify(package)
    if manifest["channel"] == "snapshot":
        with tempfile.TemporaryDirectory(prefix="snapshot-transfer-") as tmp:
            pom = Path(tmp) / "pom.xml"
            snapshot_pom(package, pom)
            subprocess.run(["mvn", "-B", "--no-transfer-progress", "-Dmaven.repository.credentialScope=id",
                            "-f", str(pom), "validate"], check=True)
        return
    with tempfile.TemporaryDirectory(prefix="central-transfer-") as tmp:
        bundle = Path(tmp) / "central-bundle.zip"
        with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as archive:
            for relative in manifest["files"]:
                if not relative.startswith("central/"):
                    continue
                path = package / relative
                name = relative[len("central/"):]
                hashes = {a: hashlib.new(a) for a in ("md5", "sha1", "sha256", "sha512")}
                with path.open("rb") as source, archive.open(name, "w", force_zip64=True) as target:
                    for block in iter(lambda: source.read(1024 * 1024), b""):
                        target.write(block)
                        for h in hashes.values():
                            h.update(block)
                if not name.endswith(".asc"):
                    for algorithm, h in hashes.items():
                        archive.writestr(name + "." + algorithm, h.hexdigest())
        token = base64.b64encode((os.environ["MAVEN_USERNAME"] + ":" +
                                 os.environ["MAVEN_PASSWORD"]).encode()).decode()
        config = Path(tmp) / "curl.conf"
        config.touch(mode=0o600)
        config.write_text('header = "Authorization: Bearer ' + token + '"\n')
        response = subprocess.run(["curl", "--fail-with-body", "--silent", "--show-error",
                                   "--connect-timeout", "30", "--max-time", "1800", "--config", str(config),
                                   "-F", "bundle=@" + str(bundle), PORTAL + "?publishingType=AUTOMATIC"],
                                  text=True, capture_output=True)
        if response.returncode:
            raise RuntimeError("Central upload failed: " + response.stderr + response.stdout)
        deployment = response.stdout.strip().strip('"')
        if not re.fullmatch(r"[A-Za-z0-9_-]+", deployment):
            raise RuntimeError("Central returned no valid deployment receipt")
        print("Central accepted deployment: " + deployment)
        print("Validation and publication continue asynchronously at Central; no polling.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare_parser = commands.add_parser("prepare")
    prepare_parser.add_argument("assets", type=Path)
    prepare_parser.add_argument("package", type=Path)
    verify_parser = commands.add_parser("verify")
    verify_parser.add_argument("package", type=Path)
    verify_parser.add_argument("--export-github", type=Path)
    upload_parser = commands.add_parser("upload")
    upload_parser.add_argument("package", type=Path)
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(Path(__file__).resolve().parents[2], args.assets, args.package)
    elif args.command == "verify":
        verify(args.package)
        if args.export_github:
            if args.export_github.exists():
                raise ValueError("GitHub export destination must not exist")
            shutil.copytree(args.package / "github", args.export_github)
    else:
        upload(args.package)
