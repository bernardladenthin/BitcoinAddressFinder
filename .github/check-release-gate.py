#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Fail when a job of publish.yml gates neither publish job without a written reason.

See buildcheck/releasegate.py; the reasons are in .github/release-gate-exemptions.txt.
Usage (from anywhere): check-release-gate.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from buildcheck import releasegate, workflow  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    with open(os.path.join(ROOT, ".github", "workflows", "publish.yml"), encoding="utf-8") as f:
        jobs = workflow.parse(f.read())
    with open(os.path.join(ROOT, releasegate.EXEMPTIONS_FILE), encoding="utf-8") as f:
        exemptions = releasegate.read_exemptions(f.read())
    failures = releasegate.check(jobs, exemptions)
    for failure in failures:
        print(f"::error::{failure}", file=sys.stderr)
    print(f"{len(jobs)} jobs, {len(exemptions)} exempt, {len(failures)} violations")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
