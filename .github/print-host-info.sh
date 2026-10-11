#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

# Prints the runner's CPU, for reading a native build or test log: which instruction sets the
# compiler saw (GGML_NATIVE, the x86 variants) and how many cores the job had. Informational only
# -- it never fails the step. One script for every OS (the step runs `shell: bash`, which is Git
# Bash on Windows).
case "$(uname -s)" in
    Linux)
        echo "=== CPU information (lscpu) ==="
        lscpu || true
        echo
        echo "=== CPU details (/proc/cpuinfo) ==="
        cat /proc/cpuinfo || true
        ;;
    Darwin)
        echo "=== CPU information (sysctl) ==="
        sysctl hw.model hw.cachelinesize hw.cpufrequency hw.cachesize hw.physicalcpu hw.logicalcpu \
            hw.packages hw.memsize hw.ncpu 2>/dev/null || true
        echo
        echo "=== Processor details (system_profiler) ==="
        system_profiler SPHardwareDataType || true
        ;;
    MINGW* | MSYS* | CYGWIN*)
        echo "=== CPU information (Get-CimInstance Win32_Processor) ==="
        powershell.exe -NoProfile -Command 'Get-CimInstance Win32_Processor | Select-Object * | Format-List' || true
        echo "=== Processor (systeminfo) ==="
        systeminfo | grep -i processor || true
        ;;
    *)
        echo "no CPU report for $(uname -s)"
        ;;
esac
exit 0
