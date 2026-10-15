#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0
#
# Sourced by every perf-investigation experiment on the rented host. Everything goes to /root/out.
set -u
OUT=/root/out; mkdir -p "$OUT"
cd /root

echo "=== common: $(date -u +%FT%TZ)"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq > /dev/null 2>&1 && apt-get install -y -qq ocl-icd-libopencl1 clinfo > /dev/null 2>&1
mkdir -p /etc/OpenCL/vendors
[ -s /etc/OpenCL/vendors/nvidia.icd ] || echo libnvidia-opencl.so.1 > /etc/OpenCL/vendors/nvidia.icd
clinfo -l > "$OUT/clinfo.txt" 2>&1
grep -q "Platform #0" "$OUT/clinfo.txt" || { echo "!!! ABORT: no OpenCL platform on this host"; cat "$OUT/clinfo.txt"; exit 3; }

mkdir -p /root/baf && tar xzf /root/bundle.tgz -C /root/baf || { echo "!!! ABORT: bundle unpack failed (broken host filesystem?)"; exit 3; }
mkdir -p /root/baf/bench-lib
if [ -f /root/baf/deps.urls ]; then   # public dependency jars straight from Maven Central
  (cd /root/baf/bench-lib && xargs -P 8 -n 1 curl -sSfLO < /root/baf/deps.urls) || echo "!!! dependency download failed"
  echo "dependencies: $(ls /root/baf/bench-lib | wc -l) of $(wc -l < /root/baf/deps.urls) jars"
fi
CP="/root/baf/test-classes:/root/baf/classes:$(ls /root/baf/bench-lib/*.jar | tr '\n' ':')"
OPENS=(--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED
       --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED
       --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED)
RAM_GB=$(free -g | awk '/^Mem:/ {print $2}')
HEAP="${HEAP:--Xmx$(( RAM_GB * 6 / 10 > 16 ? 16 : RAM_GB * 6 / 10 ))g}"

# Host facts, and the provider-side configuration a renter cannot see otherwise (H1).
{
  nproc; free -g; uname -r
  nvidia-smi --query-gpu=name,driver_version,vbios_version,pcie.link.gen.max,pcie.link.width.max,power.limit,power.default_limit,power.max_limit,enforced.power.limit,clocks.max.sm,clocks.max.mem,clocks.applications.graphics,clocks.default_applications.graphics,persistence_mode,compute_mode --format=csv
} > "$OUT/host.txt" 2>&1
nvidia-smi -q -d PERFORMANCE,CLOCK,POWER,TEMPERATURE > "$OUT/smi-q-idle.txt" 2>&1

# A provider-side power cap below the card's default makes every number on this host meaningless
# (2026-10-15: an RTX 3090 at 200 W of 370 W ran at ~1 GHz). Abort early unless explicitly allowed.
read -r PL PDEF < <(nvidia-smi --query-gpu=enforced.power.limit,power.default_limit --format=csv,noheader,nounits | head -1 | tr -d ' ' | tr ',' ' ')
if [ -n "${PL:-}" ] && [ -n "${PDEF:-}" ] && awk "BEGIN{exit !($PL + 1 < $PDEF)}"; then
  echo "!!! POWER CAPPED: enforced ${PL} W < default ${PDEF} W" | tee "$OUT/POWER_CAPPED"
  [ "${ALLOW_CAPPED:-0}" = 1 ] || { echo "!!! ABORT (set ALLOW_CAPPED=1 to measure anyway)"; exit 3; }
fi

# Sample every 2 s for the whole run; throttle reasons are the H1 evidence.
REASON=clocks_event_reasons.active
nvidia-smi --query-gpu=$REASON --format=csv > /dev/null 2>&1 || REASON=clocks_throttle_reasons.active
nvidia-smi --query-gpu=timestamp,clocks.sm,clocks.mem,power.draw,temperature.gpu,utilization.gpu,utilization.memory,$REASON \
  --format=csv -l 2 > "$OUT/smi.csv" 2>&1 &
SMI_PID=$!
trap 'kill $SMI_PID 2>/dev/null; nvidia-smi -q -d PERFORMANCE,CLOCK,POWER > "$OUT/smi-q-end.txt" 2>&1' EXIT

# jmh <name> <jmh args...>   -- one JMH run, human log + json result
jmh() {
  local name=$1; shift
  echo "=== jmh $name: $*"
  java "${OPENS[@]}" -cp "$CP" org.openjdk.jmh.Main "$@" \
    -jvmArgsAppend "$HEAP ${OPENS[*]}" -rf json -rff "$OUT/jmh-$name.json" > "$OUT/jmh-$name.log" 2>&1
  grep -E "^Benchmark|^GpuFuse8|^GridSize|^FieldMul|^InvMod|\[profiling\]|Kernel resource usage|ERROR|Exception" \
    "$OUT/jmh-$name.log" | grep -v "^\s*at " | head -60
}

# baf <name> <config.json>   -- one BitcoinAddressFinder CLI run (e.g. TuneConfiguration)
baf() {
  local name=$1 cfg=$2
  echo "=== baf $name"
  java "${OPENS[@]}" $HEAP -Dnet.ladenthin.bitcoinaddressfinder.dumpProgramBinaryDir=$OUT/ptx ${BAF_PROPS:-} -cp "$CP" net.ladenthin.bitcoinaddressfinder.cli.Main "$cfg" > "$OUT/baf-$name.log" 2>&1
  grep -E "Winner|candidates/s|^\s+[0-9]{2}\s+[0-9]+\s|Kernel resource usage|ptxas|registers|spill|Statistics|ERROR|Exception" \
    "$OUT/baf-$name.log" | grep -v "cli.Main" | head -80
}
