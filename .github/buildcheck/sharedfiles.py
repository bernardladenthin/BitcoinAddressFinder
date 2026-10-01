# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""The files kept byte-identical across java-llama.cpp, BitcoinAddressFinder, srcmorph and
streambuffer, checked by every repository's own `shared-files` job.

Each repository lists the shared files it carries, with their SHA-256, in
.github/shared-files.sha256 -- `sha256sum` format. An entry can also name ONE JOB of a workflow,
`.github/workflows/publish.yml#startgate`: jobs such as `startgate` or `check-tag` are kept identical
inside four otherwise different workflows, and a job entry hashes just that job's text (its header
and body, without the comment lines between it and the next job, which belong to the next one).
An entry ending in `?repo` (`SUPPORT.md?repo`, `.github/workflows/publish.yml#code-style?repo`)
stands for a file or job that is identical up to the repository's own name: it is hashed with every
occurrence of that name replaced by `{repo}`, so a support page or a Sonar project key that names its
repository can be shared too. `sha256sum -c` reads the plain file entries only. The job
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

from . import workflow

REPOS = ("java-llama.cpp", "BitcoinAddressFinder", "srcmorph", "streambuffer")
OWNER = "bernardladenthin"
MANIFEST = ".github/shared-files.sha256"
RAW_URL = "https://raw.githubusercontent.com/{owner}/{repo}/HEAD/" + MANIFEST
LINE = re.compile(r"^([0-9a-f]{64}) [ *](.+)$")
REPO_SUFFIX = "?repo"
REPO_PLACEHOLDER = b"{repo}"


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


def content(root, entry):
    """The bytes an entry stands for: a file, or for `<workflow>#<job>` that one job's text; for an
    entry ending in `?repo` with the repository's name replaced by `{repo}`. None when the file or
    the job does not exist."""
    if entry.endswith(REPO_SUFFIX):
        data = content(root, entry[:-len(REPO_SUFFIX)])
        return None if data is None else data.replace(current_repo(root).encode("utf-8"), REPO_PLACEHOLDER)
    path, _, job = entry.partition("#")
    full = os.path.join(root, path)
    if not os.path.isfile(full):
        return None
    with open(full, "rb") as f:
        data = f.read()
    if not job:
        return data
    jobs = workflow.parse(data.decode("utf-8"))
    if job not in jobs:
        return None
    lines = jobs[job].lines
    while lines and (not lines[-1].strip() or lines[-1].startswith("  #")):
        lines = lines[:-1]
    return ("\n".join(lines) + "\n").encode("utf-8")


def sha256(root, entry):
    data = content(root, entry)
    return None if data is None else hashlib.sha256(data).hexdigest()


def verify(root, entries):
    """Failures: every listed file (or job) exists and still has its listed hash."""
    failures = []
    for path, digest in entries.items():
        actual = sha256(root, path)
        if actual is None:
            failures.append(f"{path} is listed in {MANIFEST} but does not exist")
        elif actual != digest:
            failures.append(f"{path} differs from its line in {MANIFEST}: a shared file was changed in "
                            f"this repository alone -- change every copy, then update every manifest")
    return failures


def rewrite(text, root):
    """The manifest with every hash recomputed from the files; comments and order kept."""
    out = []
    for line in text.splitlines():
        match = LINE.match(line)
        if not match:
            out.append(line)
            continue
        digest = sha256(root, match.group(2))
        if digest is None:
            raise ValueError(f"{match.group(2)} is listed in {MANIFEST} but does not exist")
        out.append(f"{digest}  {match.group(2)}")
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
