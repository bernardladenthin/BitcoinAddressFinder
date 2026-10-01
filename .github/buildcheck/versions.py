# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""The Maven dependencies and plugins java-llama.cpp, BitcoinAddressFinder, srcmorph and streambuffer
have in common, compared across the repositories: a warning per coordinate whose version differs.

The four repositories pin the same build tooling (JUnit, Error Prone, NullAway, Spotless, SpotBugs,
JaCoCo, the Maven plugins, ...), and Dependabot bumps each repository on its own, so they drift one
pull request at a time. This check makes the drift visible in every repository's `shared-files`
job. It compares what the POMs actually USE -- every `groupId:artifactId` of a plugin, dependency or
annotation-processor path with its version, plus the formatter version Spotless is configured with,
`${property}` references resolved -- not property
names, which differ between repositories and include values that must differ (the Java release).

Warnings, never failures: a bump lands in four pull requests, and the first one merged would
otherwise turn the other three repositories red. The repository's own artifacts
(`net.ladenthin:*`) are left out: a sibling depends on the last RELEASE of java-llama.cpp while
java-llama.cpp itself is at the next snapshot.
"""

import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

from .sharedfiles import OWNER, REPOS, current_repo

NS = "{http://maven.apache.org/POM/4.0.0}"
POMS = {
    "java-llama.cpp": ("pom.xml", "llama/pom.xml", "llama-langchain4j/pom.xml", "llama-kotlin/pom.xml",
                       "llama-platform/pom.xml", "llama-atmosphere-agent/pom.xml"),
    "BitcoinAddressFinder": ("pom.xml",),
    "srcmorph": ("pom.xml", "srcmorph/pom.xml", "srcmorph-cli/pom.xml", "srcmorph-maven-plugin/pom.xml"),
    "streambuffer": ("pom.xml",),
}
OWN_GROUP = "net.ladenthin"
DEFAULT_PLUGIN_GROUP = "org.apache.maven.plugins"
VERSIONED = ("plugin", "dependency", "path", "annotationProcessorPath")
# Versions a plugin's <configuration> names without coordinates: Spotless takes its formatter as
# <palantirJavaFormat><version>. The versions plugin cannot see those, which is how palantir fell
# behind unnoticed more than once (workspace crossrepostatus, "Tool versions").
CONFIGURED = {"palantirJavaFormat": "com.palantir.javaformat:palantir-java-format"}
RAW_URL = "https://raw.githubusercontent.com/{owner}/{repo}/HEAD/{path}"
PROPERTY = re.compile(r"\$\{([^}]+)\}")


def coordinates(pom_texts):
    """groupId:artifactId -> set of versions used across the POMs of one repository. Properties
    are looked up across all of them (a child module uses its parent's), up to a few levels deep;
    a version that does not resolve to a literal is left out."""
    roots = [ET.fromstring(text) for text in pom_texts]
    props = {}
    for root in roots:
        for element in root.findall(NS + "properties/*"):
            props[element.tag[len(NS):]] = (element.text or "").strip()

    def resolve(value):
        for _ in range(5):
            match = PROPERTY.fullmatch(value)
            if not match or match.group(1) not in props:
                break
            value = props[match.group(1)]
        return None if "${" in value else value

    found = {}
    for root in roots:
        for element in root.iter():
            tag = element.tag[len(NS):]
            if tag in CONFIGURED:
                version = resolve((element.findtext(NS + "version") or "").strip())
                if version:
                    found.setdefault(CONFIGURED[tag], set()).add(version)
                continue
            if tag not in VERSIONED:
                continue
            artifact, version = element.findtext(NS + "artifactId"), element.findtext(NS + "version")
            if not artifact or not version:
                continue
            group = (element.findtext(NS + "groupId") or DEFAULT_PLUGIN_GROUP).strip()
            version = resolve(version.strip())
            if version and group != OWN_GROUP:
                found.setdefault(f"{group}:{artifact.strip()}", set()).add(version)
    return found


def compare(own, others):
    """Warnings for every coordinate another repository uses in a different version."""
    warnings = []
    for repo, theirs in sorted(others.items()):
        for coordinate in sorted(set(own) & set(theirs)):
            if own[coordinate] != theirs[coordinate]:
                warnings.append(f"{coordinate} is {', '.join(sorted(own[coordinate]))} here and "
                                f"{', '.join(sorted(theirs[coordinate]))} in {repo}: bump it in every repository")
    return warnings


def fetch(repo, path):
    with urllib.request.urlopen(RAW_URL.format(owner=OWNER, repo=repo, path=path), timeout=20) as response:
        return response.read().decode("utf-8")


def read_local(root, repo):
    texts = []
    for path in POMS[repo]:
        with open(f"{root}/{path}", encoding="utf-8") as f:
            texts.append(f.read())
    return texts


def main(argv, root, fetcher=fetch, out=sys.stdout, err=sys.stderr):
    """check-versions.py -- compare with the siblings' default branches (warnings only)"""
    if argv[1:]:
        print(main.__doc__, file=err)
        return 2
    repo = current_repo(root)
    if repo not in POMS:
        print(f"::error::{repo} is not one of {', '.join(REPOS)}", file=err)
        return 2
    own = coordinates(read_local(root, repo))
    others = {}
    for sibling in REPOS:
        if sibling == repo:
            continue
        texts = []
        for path in POMS[sibling]:
            try:
                texts.append(fetcher(sibling, path))
            except Exception as e:  # noqa: BLE001 -- a sibling that cannot be read is a warning, never a red
                print(f"::warning::could not read {sibling}'s {path}: {e}", file=err)
        if texts:
            others[sibling] = coordinates(texts)
    warnings = compare(own, others)
    for warning in warnings:
        print(f"::warning::{warning}", file=err)
    shared = set(own).intersection(*others.values()) if others else set()
    print(f"{len(own)} versioned coordinates here, {len(shared)} used by every repository read, "
          f"{len(warnings)} version differences (compared with {', '.join(sorted(others)) or 'no other repository'})",
          file=out)
    return 0
