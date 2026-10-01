# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Every job of publish.yml gates both publish jobs, unless the repository lists it in
.github/release-gate-exemptions.txt with a reason.

A job that nothing waits for can go red and a release still ships -- the natives-build jobs that
`package` once forgot to wait for, and the aarch64 fat jars that were signed and attached for
releases without any job launching them, were both of that shape. So "not gating" has to be a
decision written down here, not the default a new job gets by being forgotten in two `needs:` lists.

The check runs both ways: a job outside the gates and outside the exemptions fails, and so does an
exemption that names no job or a job that gates both publish jobs after all (a stale
exemption would hide the next job of that name).
"""

from . import workflow

GATES = ("publish-snapshot", "publish-release")

# Each repository lists its own exemptions here, one `<job>: <reason>` per line (# comments).
EXEMPTIONS_FILE = ".github/release-gate-exemptions.txt"


def read_exemptions(text):
    """The exemptions file: job -> reason. A line without a reason is an error -- the point of the
    file is that every job allowed to stay red says why."""
    exemptions = {}
    for number, line in enumerate(text.splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        job, _, reason = line.partition(":")
        if not reason.strip() or not job.strip():
            raise ValueError(f"line {number}: expected `<job>: <reason>`, got {line!r}")
        if job.strip() in exemptions:
            raise ValueError(f"line {number}: {job.strip()} is listed twice")
        exemptions[job.strip()] = reason.strip()
    return exemptions


def check(jobs, non_gating, gates=GATES):
    missing = [g for g in gates if g not in jobs]
    if missing:
        return [f"publish.yml has no job {g}" for g in missing]
    closures = [workflow.closure(jobs, g) for g in gates]
    failures = []
    for name in jobs:
        gated = [g for g, c in zip(gates, closures) if name in c]
        if name in non_gating:
            if len(gated) == len(gates):
                failures.append(f"release-gate-exemptions.txt lists {name}, which gates {', '.join(gates)} "
                                f"-- remove the stale exemption")
        elif len(gated) != len(gates):
            ungated = [g for g in gates if g not in gated]
            failures.append(f"publish.yml: {', '.join(ungated)} does not wait for {name} -- add it to the "
                            f"needs, or to release-gate-exemptions.txt with the reason it may stay red")
    failures += [f"release-gate-exemptions.txt lists {name}, which is no job of publish.yml"
                 for name in sorted(set(non_gating) - set(jobs))]
    return failures
