# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Shared fixtures of the buildcheck tests."""

import os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))


def workflow_text(jobs):
    """A workflow with the given jobs, each (name, needs, body lines)."""
    lines = ["name: t", "on:", "  push:", "jobs:"]
    for name, needs, body in jobs:
        lines.append(f"  {name}:")
        if needs is not None:
            lines.append(f"    needs: {needs}")
        lines.append("    runs-on: ubuntu-latest")
        lines.append("    steps:")
        lines += body
    return "\n".join(lines) + "\n"


def upload(artifact, path="x/"):
    return ["      - uses: actions/upload-artifact@v7", "        with:", f"          name: {artifact}",
            f"          path: {path}"]
