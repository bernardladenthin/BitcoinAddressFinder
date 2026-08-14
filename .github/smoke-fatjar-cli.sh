#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

# Cross-repo shared script — kept BYTE-IDENTICAL in BitcoinAddressFinder and srcmorph (sync any
# edit to both, and to the checksum table in workspace/crossrepostatus.md). Smoke-tests a runnable
# fat jar (jar-with-dependencies) by actually launching it: `java -jar <jar> <args>` must exit 0
# and print an expected success marker.
#
# Why: the fat jar is a GitHub-Release asset (workspace/policies/fat-jar-release-assets.md), and
# the convention is that no release asset is attached that CI has not run. An uber jar can be built
# and GPG-signed perfectly while being unrunnable — a missing Main-Class, a shade-mangled or
# duplicated resource, an absent SLF4J binding, a native library that will not load. None of that
# is visible to `mvn package` or to the unit tests, which run off target/classes and never touch
# the assembled artifact. Signing an artifact proves who built it, not that it works.
#
# The exit code alone is a weak assertion (a JVM that starts and does nothing also exits 0), so a
# success marker from the program's own output is required too.
#
# Usage: smoke-fatjar-cli.sh <jar-dir> <jar-glob> <work-dir> <success-marker> [args...]
#   <jar-dir>         directory to search for the jar (searched recursively, so a downloaded
#                     multi-module artifact that preserved its <module>/target/ layout works)
#   <jar-glob>        filename glob; must match EXACTLY ONE jar (an ambiguous match is an error,
#                     not a "pick the first" — that is how the wrong artifact gets tested)
#   <work-dir>        working directory for the run; example configs use relative paths, so this
#                     is what makes the smoke reproduce the documented invocation
#   <success-marker>  extended regex that must appear in the program's output
#   [args...]         passed to the program after `-jar <jar>`
#
# Output goes to smoke-out.log / smoke-err.log in the CALLER's working directory (uploaded by the
# CI job on failure). Deliberately a plain `java -jar` with no extra JVM flags: the contract under
# test is that the published artifact runs as-is.

set -euo pipefail

JAR_DIR="${1:?usage: smoke-fatjar-cli.sh <jar-dir> <jar-glob> <work-dir> <success-marker> [args...]}"
JAR_GLOB="${2:?usage: smoke-fatjar-cli.sh <jar-dir> <jar-glob> <work-dir> <success-marker> [args...]}"
WORK_DIR="${3:?usage: smoke-fatjar-cli.sh <jar-dir> <jar-glob> <work-dir> <success-marker> [args...]}"
MARKER="${4:?usage: smoke-fatjar-cli.sh <jar-dir> <jar-glob> <work-dir> <success-marker> [args...]}"
shift 4

# Generous ceiling: this bounds a hang (a CLI waiting on stdin, a server that never returns), it is
# not a performance budget. A healthy run of either repo's smoke config finishes in seconds.
TIMEOUT_SECONDS=600

OUT_LOG="$(pwd)/smoke-out.log"
ERR_LOG="$(pwd)/smoke-err.log"

fail() {
    echo "::error::$*" >&2
    [ -s "$OUT_LOG" ] && { echo "--- smoke-out.log (tail) ---" >&2; tail -50 "$OUT_LOG" >&2; }
    [ -s "$ERR_LOG" ] && { echo "--- smoke-err.log (tail) ---" >&2; tail -50 "$ERR_LOG" >&2; }
    exit 1
}

[ -d "$JAR_DIR" ] || fail "jar directory '$JAR_DIR' does not exist"
[ -d "$WORK_DIR" ] || fail "working directory '$WORK_DIR' does not exist"

jars=()
while IFS= read -r j; do jars+=("$j"); done < <(find "$JAR_DIR" -type f -name "$JAR_GLOB" | sort)
[ "${#jars[@]}" -eq 1 ] \
    || fail "expected exactly 1 jar matching '$JAR_GLOB' under '$JAR_DIR', got ${#jars[@]}: ${jars[*]:-none}"
JAR="$(cd "$(dirname "${jars[0]}")" && pwd)/$(basename "${jars[0]}")"

echo "smoke jar : $JAR"
echo "work dir  : $WORK_DIR"
echo "arguments : $*"

set +e
(cd "$WORK_DIR" && timeout "$TIMEOUT_SECONDS" java -jar "$JAR" "$@") > "$OUT_LOG" 2> "$ERR_LOG"
STATUS=$?
set -e

[ "$STATUS" -ne 124 ] || fail "the fat jar did not terminate within ${TIMEOUT_SECONDS}s"
[ "$STATUS" -eq 0 ] || fail "the fat jar exited with status $STATUS (expected 0)"

grep -hqE "$MARKER" "$OUT_LOG" "$ERR_LOG" \
    || fail "success marker '$MARKER' not found in the output — the jar started but did not complete its run"

echo "smoke test PASSED"
