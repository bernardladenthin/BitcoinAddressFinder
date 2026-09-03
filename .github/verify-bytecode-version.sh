#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

# Cross-repo shared script — kept BYTE-IDENTICAL in java-llama.cpp, srcmorph,
# BitcoinAddressFinder and streambuffer (sync any edit to all four, and to the checksum table in
# workspace/crossrepostatus.md). Fails when a built jar contains a class file newer than the Java
# release the artifact claims to support.
#
# Why: `maven.compiler.release` governs only the code WE compile. A dependency compiled for a newer
# Java lands in the jar untouched, and nothing in a normal build objects. The failure surfaces at a
# consumer's JVM as UnsupportedClassVersionError, which is the worst possible place to find it.
# This has happened twice here: checker-qual 4.x (Java 11 bytecode, and its annotations are
# @Retention(RUNTIME), so anything reflecting over an annotated element loads them), and
# logback-classic from 1.4.0 on, whose LogbackServiceProvider SLF4J's ServiceLoader loads at
# startup — a guaranteed crash rather than a latent one.
#
# Scan the BUILT ARTIFACT, not a resolved classpath: an uber jar is what a user actually runs, and
# `dependency:build-classpath` answers a different question (and answers it with an empty file when
# it fails, which reads as a pass).
#
# Usage: verify-bytecode-version.sh --max-major <N> [--allow <pattern>]... <path>...
#   --max-major <N>   highest class-file major version a consumer JVM may be asked to load.
#                     52 = Java 8, 55 = Java 11, 61 = Java 17, 65 = Java 21.
#                     Pass it from the pipeline so the value lives next to the release it belongs
#                     to, instead of being duplicated here per repo.
#   --allow <pattern> repeatable, optional. Glob matched against "<jar-basename>:<entry-path>", so
#                     it can waive a whole jar (`--allow 'foo-*.jar:*'`) or a single entry
#                     (`--allow '*:com/example/Legacy.class'`). Use sparingly and say why in the
#                     workflow: every entry here is a hole in the guarantee, and a hole nobody
#                     revisits is how a gate stops gating.
#   <path>...         jars, and/or directories searched recursively for *.jar.
#
# ALWAYS skipped, not configurable — a classpath JVM never loads these, so a high version in them
# is not a defect and waiving them per-repo would only invite blanket exceptions:
#   * module-info.class (any directory)  — read only in module mode, and Java 8 has none
#   * META-INF/versions/**               — multi-release overlays, invisible below their own release
#
# Exit codes: 0 clean · 1 violations found · 2 nothing to scan / bad usage. 2 matters as much as 1:
# a run that scanned no jars must never be reported as a pass.

set -euo pipefail

MAX_MAJOR=""
ALLOW=()
PATHS=()

fail_usage() {
    echo "::error::$*" >&2
    echo "usage: verify-bytecode-version.sh --max-major <N> [--allow <pattern>]... <path>..." >&2
    exit 2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --max-major) [ $# -ge 2 ] || fail_usage "--max-major needs a value"; MAX_MAJOR="$2"; shift 2 ;;
        --allow)     [ $# -ge 2 ] || fail_usage "--allow needs a value";     ALLOW+=("$2");   shift 2 ;;
        --) shift; while [ $# -gt 0 ]; do PATHS+=("$1"); shift; done ;;
        -*) fail_usage "unknown option '$1'" ;;
        *)  PATHS+=("$1"); shift ;;
    esac
done

[ -n "$MAX_MAJOR" ] || fail_usage "--max-major is required"
case "$MAX_MAJOR" in ''|*[!0-9]*) fail_usage "--max-major must be a number, got '$MAX_MAJOR'" ;; esac
[ "${#PATHS[@]}" -gt 0 ] || fail_usage "at least one jar or directory is required"

for p in "${PATHS[@]}"; do
    [ -e "$p" ] || fail_usage "path '$p' does not exist"
done

command -v python3 >/dev/null 2>&1 || fail_usage "python3 is required to read class-file headers"

# The scan itself: one pass per jar, reading the 8-byte class-file header of every entry. Kept in
# python because the alternative (unzip -p per entry) spawns a process per class — thousands for a
# fat jar — and because a zip reader must not be reimplemented in shell.
python3 - "$MAX_MAJOR" "${#ALLOW[@]}" "${ALLOW[@]}" "${PATHS[@]}" <<'PYTHON'
import fnmatch, os, sys, zipfile

max_major = int(sys.argv[1])
n_allow = int(sys.argv[2])
allow = sys.argv[3:3 + n_allow]
paths = sys.argv[3 + n_allow:]

jars = []
for p in paths:
    if os.path.isdir(p):
        for root, _dirs, files in os.walk(p):
            jars.extend(os.path.join(root, f) for f in files if f.endswith(".jar"))
    elif p.endswith(".jar"):
        jars.append(p)
jars = sorted(set(jars))

# An empty scan is a broken measurement, never a pass. A glob that matched nothing, a download step
# that silently produced no artifact, a renamed output directory: all of them yield "0 violations"
# from a scanner that just shrugs, and that is indistinguishable from a clean run.
if not jars:
    print(f"::error::no jars found under: {', '.join(paths)} -- refusing to report a pass", file=sys.stderr)
    sys.exit(2)

def skipped_always(entry):
    # A plain classpath JVM never loads either of these, at any Java level.
    return (entry == "module-info.class"
            or entry.endswith("/module-info.class")
            or entry.startswith("META-INF/versions/"))

violations = []
waived = 0
scanned = 0

for jar in jars:
    base = os.path.basename(jar)
    try:
        zf = zipfile.ZipFile(jar)
    except Exception as exc:                                   # noqa: BLE001 - report, do not crash
        print(f"::error::cannot read '{jar}': {exc}", file=sys.stderr)
        sys.exit(2)
    with zf:
        for entry in zf.namelist():
            if not entry.endswith(".class") or skipped_always(entry):
                continue
            try:
                with zf.open(entry) as handle:
                    head = handle.read(8)
            except Exception:                                  # noqa: BLE001 - unreadable entry
                continue
            if len(head) < 8 or head[:4] != b"\xca\xfe\xba\xbe":
                continue
            scanned += 1
            major = int.from_bytes(head[6:8], "big")
            if major <= max_major:
                continue
            key = f"{base}:{entry}"
            if any(fnmatch.fnmatch(key, pattern) for pattern in allow):
                waived += 1
                continue
            violations.append((base, entry, major))

# One line per offending jar, naming an example entry: a full listing of a fat jar's thousands of
# classes buries the answer, and the jar is the unit somebody acts on.
by_jar = {}
for base, entry, major in violations:
    prev = by_jar.get(base)
    if prev is None or major > prev[1]:
        by_jar[base] = (entry, major)

for base in sorted(by_jar):
    entry, major = by_jar[base]
    print(f"::error::{base}: class-file major {major} (Java {major - 44}) exceeds the "
          f"allowed {max_major} (Java {max_major - 44}) -- e.g. {entry}")

print(f"scanned {scanned} class file(s) in {len(jars)} jar(s); "
      f"{len(by_jar)} jar(s) over major {max_major}"
      + (f"; {waived} entr(y/ies) waived by --allow" if waived else ""))

sys.exit(1 if violations else 0)
PYTHON
