# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""The files kept byte-identical across java-llama.cpp, BitcoinAddressFinder, srcmorph and
streambuffer, checked by every repository's own `shared-files` job.

Each repository lists the shared files it carries, with their SHA-256, in
.github/shared-files.sha256 -- `sha256sum` format, so `sha256sum -c` reads it as well. The job
  * fails when a listed file is missing or its content no longer matches its line: a shared file
    was edited here alone. Edit it in every repository that lists it, then update each manifest
    (`check-shared-files.py --write` rewrites the hashes of this repository's lines);
  * warns when another repository's manifest (its default branch) lists the same file with a
    different hash: one side of a sync is not merged yet. A warning and not a failure, because a
    sync is one change per repository and lands in four steps.
A file is matched across repositories by its path, or, when the other side has no such path, by
its file name if that is unique there (lombok.config lives at llama/lombok.config in the
java-llama.cpp reactor and at the root elsewhere).
"""

import hashlib
import os
import re
import sys
import urllib.request

REPOS = ("java-llama.cpp", "BitcoinAddressFinder", "srcmorph", "streambuffer")
OWNER = "bernardladenthin"
MANIFEST = ".github/shared-files.sha256"
RAW_URL = "https://raw.githubusercontent.com/{owner}/{repo}/HEAD/" + MANIFEST
LINE = re.compile(r"^([0-9a-f]{64}) [ *](.+)$")


def parse(text):
    """path -> sha256 of a manifest. Comment and blank lines are skipped; anything else that is
    not a `sha256sum` line is an error, and so is a path listed twice."""
    entries = {}
    for number, line in enumerate(text.splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        match = LINE.match(line)
        if not match:
            raise ValueError(f"{MANIFEST} line {number}: not `<sha256>  <path>`: {line!r}")
        if match.group(2) in entries:
            raise ValueError(f"{MANIFEST} line {number}: {match.group(2)} is listed twice")
        entries[match.group(2)] = match.group(1)
    return entries


def sha256(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def verify(root, entries):
    """Failures: every listed file exists and still has its listed hash."""
    failures = []
    for path, digest in entries.items():
        full = os.path.join(root, path)
        if not os.path.isfile(full):
            failures.append(f"{path} is listed in {MANIFEST} but does not exist")
        elif sha256(full) != digest:
            failures.append(f"{path} differs from its line in {MANIFEST}: a shared file was changed in "
                            f"this repository alone -- change every copy, then update every manifest")
    return failures


def rewrite(text, root):
    """The manifest with every hash recomputed from the files; comments and order kept."""
    out = []
    for line in text.splitlines():
        match = LINE.match(line)
        out.append(f"{sha256(os.path.join(root, match.group(2)))}  {match.group(2)}" if match else line)
    return "\n".join(out) + "\n"


def counterpart(path, other):
    """The path `other` (another manifest) lists for our `path`: the same path, else a unique
    file of the same name, else None."""
    if path in other:
        return path
    same_name = [p for p in other if os.path.basename(p) == os.path.basename(path)]
    return same_name[0] if len(same_name) == 1 else None


def compare(own, others):
    """Warnings for every shared file another repository lists with a different hash."""
    warnings = []
    for repo, entries in sorted(others.items()):
        for path, digest in own.items():
            theirs = counterpart(path, entries)
            if theirs is not None and entries[theirs] != digest:
                warnings.append(f"{path} differs from {repo}'s {theirs}: finish or redo the sync")
    return warnings


def current_repo(root):
    name = os.environ.get("GITHUB_REPOSITORY", "").split("/")[-1]
    return name or os.path.basename(os.path.abspath(root))


def fetch(repo):
    with urllib.request.urlopen(RAW_URL.format(owner=OWNER, repo=repo), timeout=20) as response:
        return response.read().decode("utf-8")


def main(argv, root, fetcher=fetch, out=sys.stdout, err=sys.stderr):
    """check-shared-files.py [--write | --offline]"""
    path = os.path.join(root, MANIFEST)
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if argv[1:] == ["--write"]:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(rewrite(text, root))
        print(f"rewrote the hashes of {MANIFEST}", file=out)
        return 0
    if argv[1:] not in ([], ["--offline"]):
        print(main.__doc__, file=err)
        return 2
    own = parse(text)
    failures = verify(root, own)
    for failure in failures:
        print(f"::error::{failure}", file=err)
    print(f"{len(own)} shared files, {len(failures)} changed here alone", file=out)
    if argv[1:] == ["--offline"]:
        return 1 if failures else 0
    others = {}
    for repo in REPOS:
        if repo == current_repo(root):
            continue
        try:
            others[repo] = parse(fetcher(repo))
        except Exception as e:  # noqa: BLE001 -- a sibling that cannot be read is a warning, never a red
            print(f"::warning::could not read {repo}'s {MANIFEST}: {e}", file=err)
    for warning in compare(own, others):
        print(f"::warning::{warning}", file=err)
    print(f"compared with {', '.join(sorted(others)) or 'no other repository'}", file=out)
    return 1 if failures else 0
