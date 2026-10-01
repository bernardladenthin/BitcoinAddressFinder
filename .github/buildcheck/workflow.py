# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""The part of a GitHub Actions workflow the checks need: its jobs, their `needs`, the artifacts
they upload and the text of their steps.

Deliberately not a YAML parser. The runners' Python has no PyYAML guaranteed, and the checks
need only the job graph, which the workflow writes in two fixed shapes: jobs at two spaces of
indentation below `jobs:`, and `needs:` at four, either inline (`needs: a` / `needs: [a, b]`) or
as a block list (`- a` at six). A workflow written any other way is reported by `parse`, never
misread silently: every job must be found, and a `needs:` that matches neither shape fails.
"""

import re

JOB = re.compile(r"^  ([A-Za-z0-9_-]+):\s*(#.*)?$")
NEEDS = re.compile(r"^    needs:\s*(.*?)\s*(#.*)?$")
NEEDS_ITEM = re.compile(r"^      - ([A-Za-z0-9_-]+)\s*(#.*)?$")
STEP = re.compile(r"^      - ")


class Job:
    """One job: its id, the jobs it needs, and its lines (the job header included)."""

    def __init__(self, name):
        self.name = name
        self.needs = []
        self.lines = []

    @property
    def text(self):
        return "\n".join(self.lines)

    def steps(self):
        """The job's steps, each as a list of lines (a step starts at `      - `)."""
        out = []
        for line in self.lines:
            if STEP.match(line):
                out.append([line])
            elif out:
                out[-1].append(line)
        return out

    def uploads(self):
        """The names of the artifacts the job uploads (upload-artifact's `with: name:`)."""
        names = []
        for step in self.steps():
            if not any("actions/upload-artifact@" in line for line in step):
                continue
            with_indent = None
            for line in step:
                stripped = line.lstrip()
                indent = len(line) - len(stripped)
                if stripped.startswith("with:"):
                    with_indent = indent
                elif with_indent is not None and indent > with_indent and stripped.startswith("name:"):
                    names.append(stripped[len("name:"):].strip().strip("'\""))
                    break
                elif with_indent is not None and indent <= with_indent:
                    with_indent = None
        return names


def parse(text):
    """Jobs by id, in file order. Raises ValueError on a `needs:` in a shape it does not read."""
    jobs = {}
    in_jobs = False
    current = None
    block_needs = False
    for number, line in enumerate(text.splitlines(), 1):
        if line and not line[0].isspace() and not line.startswith("#"):
            in_jobs = line.rstrip() == "jobs:"
            current = None
            continue
        if not in_jobs:
            continue
        match = JOB.match(line)
        if match:
            current = jobs[match.group(1)] = Job(match.group(1))
            current.lines.append(line)
            block_needs = False
            continue
        if current is None:
            continue
        current.lines.append(line)
        match = NEEDS.match(line)
        if match:
            value = match.group(1)
            if value == "":
                block_needs = True
            elif value.startswith("[") and value.endswith("]"):
                current.needs += [n.strip() for n in value[1:-1].split(",") if n.strip()]
            elif re.fullmatch(r"[A-Za-z0-9_-]+", value):
                current.needs.append(value)
            else:
                raise ValueError(f"line {number}: cannot read needs of job {current.name}: {value!r}")
            continue
        if block_needs:
            item = NEEDS_ITEM.match(line)
            if item:
                current.needs.append(item.group(1))
            elif line.strip() and not line.lstrip().startswith("#"):
                block_needs = False
    for job in jobs.values():
        for need in job.needs:
            if need not in jobs:
                raise ValueError(f"job {job.name} needs {need}, which is no job of this workflow")
    return jobs


def closure(jobs, name):
    """Every job `name` waits for, directly or through another job (itself excluded)."""
    seen = set()
    todo = list(jobs[name].needs)
    while todo:
        need = todo.pop()
        if need not in seen:
            seen.add(need)
            todo += jobs[need].needs
    return seen
