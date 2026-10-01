# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Every `run:` script of the workflows and composite actions that runs in bash, parsed with
`bash -n` -- so a broken script fails the `shared-files` job in the first minutes of a run instead
of the job that executes it, possibly hours later or only on a release path.

The case this exists for: a cleanup lost the trailing backslash of six continued lines, leaving a
line that starts with `||` or `-e` -- in a step only a publish run or a native build reaches. Neither
actionlint (it parses scripts only when shellcheck is installed) nor a review saw it; `bash -n` sees
every one of them.

Which scripts are bash is decided the way the runner decides it: the step's `shell:`, else the job's
and then the workflow's `defaults.run.shell`, else the runner's default -- PowerShell on a Windows
runner, bash everywhere else. A runner chosen by an expression (a matrix, a workflow input) counts
as Windows only when the step's `if:` says so; a step without `shell:` on such a job runs on every
OS of the matrix and has to be valid bash anyway. Scripts in `sh`, `bash -el {0}` or a bash given by
path are bash; pwsh, powershell, cmd and python are skipped.

Like workflow.py this reads the two-space layout the workflows are written in, not general YAML.
The run value is taken as YAML defines it: a literal block (`|`) line for line, a folded block
(`>`) and a plain scalar folded into one line, a single-quoted scalar unquoted. A double-quoted
scalar is skipped (its escapes are not worth a YAML parser). Every `${{ ... }}` expression becomes
a word, which is what the runner substitutes before bash sees the script.
"""

import glob
import os
import re
import shutil
import subprocess
import sys

RUN = re.compile(r"^(?P<lead>\s*(?:- )?)run:(?:\s+(?P<value>.*?))?\s*$")
BLOCK_HEADER = re.compile(r"^(?P<style>[|>])[-+]?[1-9]?\s*(#.*)?$")
EXPRESSION = re.compile(r"\$\{\{.*?\}\}")
COMMENT = re.compile(r"\s+#.*$")
BASH_LINE = re.compile(r"line (\d+)")
BASH_SHELLS = ("bash", "sh")
SKIPPED_SHELLS = ("pwsh", "powershell", "cmd", "python")
WINDOWS_IF = re.compile(r"runner\.os\s*==\s*'Windows'")
PATTERNS = (".github/workflows/*.yml", ".github/workflows/*.yaml", ".github/actions/*/action.yml",
            ".github/actions/*/action.yaml")


class Script:
    """One run script: the file line of its `run:` key, the shell it runs in, and its text."""

    def __init__(self, line, shell, text, literal=False):
        self.line = line
        self.shell = shell
        self.text = text
        self.literal = literal


def _indent(line):
    return len(line) - len(line.lstrip())


def _significant(line):
    stripped = line.strip()
    return bool(stripped) and not stripped.startswith("#")


def _scalar(value):
    value = COMMENT.sub("", value or "").strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
        value = value[1:-1]
    return value


def _value(lines, indent, keys):
    """The scalar at the mapping path `keys` in `lines`, its first key at `indent`, else None."""
    for number, line in enumerate(lines):
        if _indent(line) != indent or not line.strip().startswith(keys[0] + ":"):
            continue
        rest = line.strip()[len(keys[0]) + 1:]
        if len(keys) == 1:
            return _scalar(rest)
        block = []
        for following in lines[number + 1:]:
            if _significant(following) and _indent(following) <= indent:
                break
            block.append(following)
        return _value(block, indent + 2, keys[1:])
    return None


def _job_lines(lines, index):
    """The lines of the workflow job containing line `index` (none in a composite action)."""
    start = None
    for number in range(index, -1, -1):
        if re.match(r"^  [A-Za-z0-9_-]+:\s*(#.*)?$", lines[number]):
            start = number
            break
        if lines[number] and not lines[number][0].isspace() and _significant(lines[number]):
            return []
    if start is None:
        return []
    end = len(lines)
    for number in range(index + 1, len(lines)):
        if _significant(lines[number]) and _indent(lines[number]) <= 2:
            end = number
            break
    return lines[start:end]


def _step_start(lines, index, key_indent):
    """The line the step of the `run:` key at line `index` starts at; None when the key is not a
    step's (`defaults: run:` is a mapping, not a script)."""
    if lines[index].lstrip().startswith("- "):
        return index
    for number in range(index - 1, -1, -1):
        line = lines[number]
        if not _significant(line):
            continue
        if _indent(line) == key_indent - 2 and line.lstrip().startswith("- "):
            return number
        if _indent(line) < key_indent:
            return None
    return None


def _step_lines(lines, index, key_indent):
    """The lines of the step the `run:` key at line `index` belongs to."""
    dash = key_indent - 2
    start = _step_start(lines, index, key_indent)
    end = len(lines)
    for number in range(index + 1, len(lines)):
        if _significant(lines[number]) and _indent(lines[number]) <= dash:
            end = number
            break
    # the step's own keys, its first one written after the dash
    return [" " * key_indent + line.lstrip()[2:] if number == start and line.lstrip().startswith("- ")
            else line for number, line in enumerate(lines[start:end], start)]


def _shell_name(shell):
    first = shell.split()[0] if shell.split() else ""
    name = re.split(r"[\\/]", first)[-1].lower()
    return name[:-4] if name.endswith(".exe") else name


def shell_of(lines, index, key_indent):
    """The shell the step whose `run:` key is at line `index` runs in, as the runner decides it."""
    step = _step_lines(lines, index, key_indent)
    shell = _value(step, key_indent, ["shell"])
    job = _job_lines(lines, index)
    if not shell and job:
        shell = _value(job, 4, ["defaults", "run", "shell"])
    if not shell:
        shell = _value(lines, 0, ["defaults", "run", "shell"])
    if shell:
        return _shell_name(shell)
    runs_on = (_value(job, 4, ["runs-on"]) or "") if job else ""
    condition = _value(step, key_indent, ["if"]) or ""
    if "windows" in EXPRESSION.sub("", runs_on).lower() or WINDOWS_IF.search(condition):
        return "pwsh"
    return "bash"


def _fold(lines):
    """YAML folding of a block's lines (indentation removed): lines of equal indentation join with
    a space, an empty line is a line break, more-indented lines keep their breaks."""
    out = ""
    previous = None
    for line in lines:
        if not line.strip():
            out += "\n"
            previous = "empty"
            continue
        more = line[0].isspace()
        if previous == "text" and not more:
            out += " " + line
        else:
            if previous is not None and previous != "empty":
                out += "\n"
            out += line
        previous = "more" if more else "text"
    return out


def scripts(text):
    """Every run script of a workflow or composite action, in file order."""
    lines = text.splitlines()
    found = []
    number = 0
    while number < len(lines):
        match = RUN.match(lines[number])
        if not match or _step_start(lines, number, len(match.group("lead"))) is None:
            number += 1
            continue
        key_indent = len(match.group("lead"))
        value = match.group("value") or ""
        body_end = number + 1
        while body_end < len(lines) and (not lines[body_end].strip() or _indent(lines[body_end]) > key_indent):
            body_end += 1
        while body_end > number + 1 and not lines[body_end - 1].strip():
            body_end -= 1
        body = lines[number + 1:body_end]
        header = BLOCK_HEADER.match(value)
        if header:
            significant = [line for line in body if line.strip()]
            indent = min((_indent(line) for line in significant), default=0)
            body = [line[indent:] for line in body]
            script = "\n".join(body) if header.group("style") == "|" else _fold(body)
        elif value.startswith('"'):
            script = None
        elif value.startswith("'"):
            script = " ".join([value] + [line.strip() for line in body]).strip()
            script = script[1:-1].replace("''", "'") if script.endswith("'") else None
        else:
            script = COMMENT.sub("", " ".join([value] + [line.strip() for line in body]))
        if script is not None:
            found.append(Script(number + 1, shell_of(lines, number, key_indent), script,
                                literal=bool(header) and header.group("style") == "|"))
        number = max(body_end, number + 1)
    return found


def bash_n(script, bash="bash"):
    """bash -n over the script: None when it parses, else bash's message."""
    result = subprocess.run([bash, "-n"], input=EXPRESSION.sub("X", script), capture_output=True,
                            text=True, check=False)
    return None if result.returncode == 0 else result.stderr.strip() or f"exit {result.returncode}"


def check(path, text, parser=bash_n):
    """`path:line: message` per bash script that does not parse; the count of scripts checked."""
    errors = []
    checked = 0
    for script in scripts(text):
        if script.shell in SKIPPED_SHELLS:
            continue
        if script.shell not in BASH_SHELLS:
            errors.append(f"{path}:{script.line}: unknown shell '{script.shell}' -- add it to runscripts.py")
            continue
        checked += 1
        message = parser(script.text)
        if message:
            # bash counts a literal block's lines from 1, the first one being the line after `run: |`;
            # a folded script has no line of the file to point at but its `run:`
            match = BASH_LINE.search(message)
            line = script.line + int(match.group(1)) if match and script.literal else script.line
            errors.append(f"{path}:{line}: {message}")
    return errors, checked


def files(root):
    return sorted({path for pattern in PATTERNS for path in glob.glob(os.path.join(root, pattern))})


def main(argv, root, out=sys.stdout, err=sys.stderr):
    """check-run-scripts.py -- bash -n over every bash run script of the workflows and actions"""
    if argv[1:]:
        print(main.__doc__, file=err)
        return 2
    bash = shutil.which("bash")
    if not bash:
        print("::error::bash not found", file=err)
        return 2
    errors = []
    checked = 0
    paths = files(root)
    for path in paths:
        with open(path, encoding="utf-8") as f:
            found, count = check(os.path.relpath(path, root), f.read(), lambda s: bash_n(s, bash))
        errors += found
        checked += count
    for error in errors:
        print(f"::error::{error}", file=err)
    print(f"{checked} bash run scripts in {len(paths)} files, {len(errors)} that do not parse", file=out)
    return 1 if errors or not paths else 0
