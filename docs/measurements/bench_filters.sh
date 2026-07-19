#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0
#
# Filter test bench: one command, every backend, comparable numbers.
#
# WHY THIS EXISTS
# Choosing a lookup backend is a trade between three quantities, and measuring them separately in
# separate runs produced contradictions repeatedly during development:
#
#   memory        exact, self-reported by each backend (never a heap estimate - see sizeInBytes())
#   lookup speed  JMH, storage-free, ALL backends in ONE session
#   accuracy      false-positive rate, deterministic and machine-independent
#
# The fourth quantity, what a false positive costs, is a property of the deployment rather than the
# filter (measured 4.1 us warm to 292.7 us cold), so it enters as a parameter when the results are
# scored rather than as something this bench measures.
#
# The single most important rule this encodes: backends are compared WITHIN one JMH session.
# Identical configurations measured in different sessions differed by up to 34 % on the same
# machine, which silently reversed two conclusions before this was noticed.
#
# NOT MEASURED ON PURPOSE
# Nothing here pushes the machine into swap. Deliberately sized so every structure fits comfortably
# in RAM: the low-memory edge case is a property of the host, not of the filters, and measuring it
# only produces numbers that do not reproduce.
#
# USAGE
#   bash docs/measurements/bench_filters.sh [entriesList] [outputDir]
#   bash docs/measurements/bench_filters.sh 1000000,10000000,100000000
#
# Requires: mvn -q test-compile and target/cp-test.txt (see README.md in this directory).
# Runtime is roughly 60-120 min depending on the largest entry count. The GPU section is skipped
# automatically on hosts without an OpenCL device.
set -u

ENTRIES="${1:-1000000,10000000,100000000}"
OUT="${2:-target/bench-filters}"
mkdir -p "$OUT"

CP="target/test-classes;target/classes;$(cat target/cp-test.txt)"
case "$(uname -s 2>/dev/null || echo Windows)" in
  Linux|Darwin) CP="target/test-classes:target/classes:$(cat target/cp-test.txt)" ;;
esac
OPENS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED \
--add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"

# Blocked Bloom is swept across densities because that is its actual design space; the fuse filters
# and the exact snapshots have no equivalent knob. k follows the measured optimum per density
# (5/6/7/7/8/9 at 8/11/14/17/21/26) - NOT a proportional rule, which was tried and refuted.
# The first three are the density sweep; 9:5 and 18:7 exist to match the fuse footprints exactly
# (block 4). Their FPR must be MEASURED, not interpolated - estimating 9:5 from the 11:6 value was
# 25 % off (guessed 1.3 %, measured 1.63 %), and an earlier interpolation rule for k was refuted.
BB_DENSITIES="11:6 17:7 26:9 9:5 18:7"

echo "=== 1/5  memory, build time and false-positive rate (storage-free) ==="
echo "backend,entries,bits_per_entry,k,build_seconds,exact_bytes,exact_bytes_per_entry,fpr,false_negatives" \
  > "$OUT/accuracy_and_size.csv"
for N in ${ENTRIES//,/ }; do
  for BE in BINARY_FUSE_8 BINARY_FUSE_16 TRUNCATED_LONG_64; do
    java -Xmx40g $OPENS -cp "$CP" \
      net.ladenthin.bitcoinaddressfinder.benchmark.FilterMeasurementMain \
      "prng:$N" "$BE" 2000000 > "$OUT/raw_${BE}_${N}.out" 2>&1
    grep -h RESULT "$OUT/raw_${BE}_${N}.out" >> "$OUT/accuracy_and_size.raw" || true
    echo "  $BE n=$N done"
  done
  for D in $BB_DENSITIES; do
    BPE="${D%%:*}"; K="${D##*:}"
    java -Xmx40g $OPENS -cp "$CP" \
      net.ladenthin.bitcoinaddressfinder.benchmark.FilterMeasurementMain \
      "prng:$N" BLOCKED_BLOOM 2000000 "$K" "$BPE" > "$OUT/raw_BB_${BPE}_${N}.out" 2>&1
    grep -h RESULT "$OUT/raw_BB_${BPE}_${N}.out" >> "$OUT/accuracy_and_size.raw" || true
    echo "  BLOCKED_BLOOM bpe=$BPE k=$K n=$N done"
  done
done

echo "=== 2/5  lookup latency - every backend in ONE JMH session ==="
# ONE java invocation, no loop. An earlier revision of this script looped over the densities with a
# JVM launch each, which put every Blocked-Bloom-vs-fuse comparison in a different session - exactly
# the error the header warns about. It showed up as the largest filter measuring FASTEST with the
# tightest error bars while the others carried +-20 ns bands: session noise, not filter behaviour.
# Densities therefore travel inside the backend name (see FilterLookupBenchmark#backend).
BB_PARAMS=""
for D in $BB_DENSITIES; do
  BPE="${D%%:*}"; K="${D##*:}"
  BB_PARAMS="$BB_PARAMS,BLOCKED_BLOOM:$BPE:$K"
done
java -Xmx40g $OPENS -cp "$CP" org.openjdk.jmh.Main FilterLookupBenchmark \
  -p "backend=BINARY_FUSE_8,BINARY_FUSE_16,TRUNCATED_LONG_64$BB_PARAMS" -p entries="$ENTRIES" \
  -f 1 -wi 2 -w 2 -i 5 -r 3 -jvmArgsAppend "-Xmx40g" > "$OUT/lookup.log" 2>&1
echo "  lookup done (all backends, one session)"

echo "=== 3/5  construction cost, storage-free - ONE session ==="
# -wi 1 is essential: with SingleShotTime the first shot carries the whole JIT compilation. Running
# without it produced error bars LARGER than the means (+-8010 ms on a 4282 ms measurement).
# Discarding that one shot cut the spread by a factor of 6 to 74.
java -Xmx40g $OPENS -cp "$CP" org.openjdk.jmh.Main FilterBuildBenchmark   -p "backend=BINARY_FUSE_8,BINARY_FUSE_16$BB_PARAMS" -p entries="$ENTRIES"   -f 1 -wi 1 -i 5 -jvmArgsAppend "-Xmx40g" > "$OUT/build.log" 2>&1
echo "  build done (all backends, one session)"

echo "=== 4/5  equal MEMORY budget - the comparison the recommendation rests on ==="
# Every other block compares backends at equal ENTRY COUNT, which means unequal memory: at 1e9
# entries fuse-8 occupies 1.13 GB while blocked bloom at bpe 26 occupies 3.25 GB. Two things vary
# at once there (filter type AND size), so part of any latency difference is just cache behaviour.
# Here the footprint is held constant and only the filter type varies. Blocked Bloom densities are
# chosen to match the fuse footprints: fuse-8 = 1.126 B/entry = 9.0 bits -> bpe 9, k 5;
# fuse-16 = 2.252 B/entry = 18.0 bits -> bpe 18, k 7.
java -Xmx40g $OPENS -cp "$CP" org.openjdk.jmh.Main FilterLookupBenchmark   -p "backend=BINARY_FUSE_8,BLOCKED_BLOOM:9:5,BINARY_FUSE_16,BLOCKED_BLOOM:18:7"   -p entries="$ENTRIES" -f 1 -wi 2 -w 2 -i 5 -r 3   -jvmArgsAppend "-Xmx40g" > "$OUT/budget.log" 2>&1
echo "  equal-memory budget done (one session)"

echo "=== 5/5  GPU probe - the axis where the two filter families differ most ==="
# Skipped automatically when no OpenCL device is present (the benchmark's assume throws and JMH
# reports ERROR rows). Blocked Bloom confines all probes to one 64-byte block, i.e. one coalesced
# transaction, where a fuse lookup makes three scattered reads. The GPU cannot hide those in cache
# the way a CPU does - a few dozen bytes of L2 per resident thread against megabytes of L3 per CPU
# thread - so this is where the families separate most sharply. Note the CPU's short-circuit
# advantage does NOT carry over: a warp runs in lockstep and waits for its slowest lane.
# One session, densities encoded in the filter name - same rule as blocks 2 and 3.
java -Xmx24g $OPENS -cp "$CP" org.openjdk.jmh.Main GpuFilterProbeBenchmark   -p "filter=FUSE8$BB_PARAMS" -p entries="$ENTRIES"   -f 1 -wi 1 -w 3 -i 3 -r 3 -jvmArgsAppend "-Xmx24g" > "$OUT/gpu.log" 2>&1
echo "  gpu done (all filters, one session)"

echo
echo "Raw output in $OUT."
echo "Next: append the rows to docs/measurements/*.csv with your machine_id, then run"
echo "  python docs/measurements/plot.py"
