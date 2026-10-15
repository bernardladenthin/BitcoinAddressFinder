#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0
#
# local-jmh.sh <name> "<extraBuildOptions or ->" <jmh args...>
# Runs one JMH benchmark on this machine's GPU (free) with the Temurin JDK, optional extra kernel
# build options (no spaces inside one option: -DKEYS_BATCH_INV=8, -cl-nv-maxrregcount=96), and
# writes results/local-<gpu>/jmh-<name>.{log,json}. Needs `vastbench.sh bundle` (or the same mvn
# call) to have populated target/classes, target/test-classes and target/bench-lib.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME_TEMURIN:?set JAVA_HOME_TEMURIN to a Temurin JDK 21 (an Oracle JDK 21.0.9 cannot run NullAway JSpecify mode)}"
name=$1; extra=$2; shift 2
GPU=$(nvidia-smi --query-gpu=name --format=csv,noheader | head -1 | tr -cd 'A-Za-z0-9' | tr 'A-Z' 'a-z')
OUT="$ROOT/perf-investigation/results/local-$GPU"; mkdir -p "$OUT"
cd "$ROOT"
SEP=";"; [ "$(uname -o 2>/dev/null)" = "Msys" ] || SEP=":"
CP="target/test-classes${SEP}target/classes${SEP}$(ls target/bench-lib/*.jar | tr '\n' "$SEP")"
OPENS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
PROP=""; [ "$extra" = "-" ] || PROP="-Dnet.ladenthin.bitcoinaddressfinder.extraBuildOptions=$extra"
"$JAVA_HOME/bin/java" $OPENS -cp "$CP" org.openjdk.jmh.Main "$@" -jvmArgsAppend "-Xmx20g $OPENS $PROP" \
  -rf json -rff "$OUT/jmh-$name.json" > "$OUT/jmh-$name.log" 2>&1
echo "== $name (extra: $extra)"
grep -E "^GpuFuse8|^GridSize|\[profiling\]|Kernel resource usage" "$OUT/jmh-$name.log" | sed 's/.*OpenCLContext - //' | sort | uniq -c
