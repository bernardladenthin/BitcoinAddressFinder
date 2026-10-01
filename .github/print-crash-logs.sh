#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

# Cross-repo shared script -- kept BYTE-IDENTICAL in java-llama.cpp, BitcoinAddressFinder,
# srcmorph and streambuffer (listed in each repo's .github/shared-files.sha256). The "Print crash
# logs" step of workspace/policies/ci-test-diagnostics.md section 3.1: echoes the JVM crash logs
# of a failed test job into the job log, because an uploaded artifact is unreadable from anywhere
# that cannot fetch from Azure Blob Storage (a phone, a restricted network, an agent sandbox).
#
# Usage: print-crash-logs.sh [<module-dir>...]   (default: the current directory)
#   For each module directory: <dir>/hs_err_pid*.log (first 200 lines -- the diagnostic core is at
#   the top, the tail is thread dumps and the memory map) and
#   <dir>/target/surefire-reports/*.dumpstream|*.dump (whole; they are small).
#
# Always exits 0: it runs under `if: failure()` and must never replace the job's real failure.
shopt -s nullglob
[ "$#" -gt 0 ] || set -- .
found=0
for dir in "$@"; do
    for f in "$dir"/hs_err_pid*.log; do
        found=1
        echo "===== $f (first 200 lines; full file in the uploaded artifact) ====="
        sed -n '1,200p' "$f"
    done
    for f in "$dir"/target/surefire-reports/*.dumpstream "$dir"/target/surefire-reports/*.dump; do
        found=1
        echo "===== $f ====="
        cat "$f"
    done
done
if [ "$found" = 0 ]; then
    # Worded defensively on purpose (policy section 3): the step fires on EVERY job failure, and
    # an ordinary assertion failure writes no crash log. Never assert an abort from a missing file.
    echo "No hs_err_pid*.log and no surefire dump/dumpstream was written (looked in: $*)."
    echo
    echo "For an ordinary test failure that is EXPECTED, not a finding: this step runs on"
    echo "any job failure, and an assertion failure, a timeout or a compile error writes no"
    echo "crash log. Read the surefire output above for the real cause."
    echo
    echo "It points at a JVM-level abort only if the log ALSO shows a fork ending abnormally"
    echo "-- 'The forked VM terminated without properly saying goodbye', or an exit with no"
    echo "test results. In that case the abort bypassed the JVM error handler (a native"
    echo "exit()/terminate() rather than a raised signal), which is why no file was written."
fi
exit 0
