#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: Apache-2.0
#
# vastbench.sh -- run one BitcoinAddressFinder experiment on a rented Vast.ai GPU, bring the
# results home, and destroy the instance. Built for an investigation loop: every call is
# self-contained and leaves nothing running.
#
#   vastbench.sh bundle                                  build this checkout into target/vastbench/bundle.tgz
#   vastbench.sh run <label> "<offer query>" <exp.sh> [max_minutes]
#   vastbench.sh sweep                                   destroy every baf-perf-* instance, delete the throwaway key
#   vastbench.sh status                                  credit, budget left, instances
#
# Budget: VB_BUDGET_USD (default 14) counted from the credit stored in target/vastbench/start_credit
# on first use. A run whose worst case (price x max_minutes) would cross the budget is refused.
# Safety: the instance is destroyed by an EXIT trap, whatever happens in between.
set -euo pipefail
export MSYS_NO_PATHCONV=1 PYTHONIOENCODING=utf-8

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HERE="$ROOT/perf-investigation"
WORK="$ROOT/target/vastbench"
VAST="${VASTAI:-vastai}"
BUDGET="${VB_BUDGET_USD:-14}"
IMAGE="${VB_IMAGE:-eclipse-temurin:21-jdk}"
KEY="$WORK/id_ed25519"
KH="$WORK/known_hosts"
mkdir -p "$WORK"

log() { echo "[vastbench $(date +%T)] $*" >&2; }
py() { python -c "$1"; }

credit() { "$VAST" show user --raw | py "import json,sys; print(json.load(sys.stdin)['credit'])"; }

budget_left() {
  [ -f "$WORK/start_credit" ] || credit > "$WORK/start_credit"
  local start now
  start=$(tr -d '\r\n' < "$WORK/start_credit"); now=$(credit | tr -d '\r\n')
  py "print(round($BUDGET - ($start - $now), 3))"
}

ensure_key() {
  if [ ! -f "$KEY" ]; then
    ssh-keygen -q -t ed25519 -N "" -C "vastbench-throwaway" -f "$KEY"
  fi
  local pub; pub=$(cat "$KEY.pub")
  if ! "$VAST" show ssh-keys --raw | grep -q -F "$(echo "$pub" | cut -d' ' -f2)"; then
    "$VAST" create ssh-key "$pub" > /dev/null
    log "throwaway SSH key registered"
  fi
}

delete_key() {
  [ -f "$KEY.pub" ] || return 0
  local blob; blob=$(cut -d' ' -f2 "$KEY.pub")
  for id in $("$VAST" show ssh-keys --raw | py "
import json,sys
d=json.load(sys.stdin); d=d if isinstance(d,list) else d.get('ssh_keys',[])
print(' '.join(str(k['id']) for k in d if '$blob' in (k.get('public_key') or k.get('ssh_key') or '')))"); do
    "$VAST" delete ssh-key "$id" > /dev/null && log "throwaway SSH key $id deleted"
  done
}

instances() {
  "$VAST" show instances --raw | py "
import json,sys
d=json.load(sys.stdin) or []
for i in d: print(i['id'], i.get('label') or '-', i.get('actual_status'), round(i.get('dph_total') or 0, 3))"
}

destroy() {
  local id=$1
  for _ in 1 2 3; do
    "$VAST" destroy instance "$id" -y > /dev/null 2>&1 || true
    sleep 3
    if ! instances | grep -q "^$id "; then log "instance $id destroyed"; return 0; fi
  done
  log "!!! instance $id still listed after destroy -- run 'vastbench.sh sweep' NOW"; return 1
}

# mark_bad <offer-id> <offers-json>: put the offer's machine on the bad-host list
mark_bad() {
  echo "$2" | py "
import json,sys
for o in json.load(sys.stdin):
    if str(o['id'])=='$1': print(o.get('machine_id',''))" >> "$WORK/bad_hosts"
}

cmd_bundle() {
  # Only this branch's own classes travel from here (a few MB): the dependency jars are public and
  # the host downloads them from Maven Central itself -- uploading 55 MB from a home line to a far
  # host took 20 minutes of paid time on 2026-10-15.
  log "building (test-compile) and listing test-scope dependencies ..."
  ( cd "$ROOT" && env -u MSYS_NO_PATHCONV mvn -q -B test-compile dependency:list -DincludeScope=test       -DoutputFile=target/vastbench/deps.txt -DoutputAbsoluteArtifactFilename=false )
  py "
import re
urls=[]
for line in open(r'$(cygpath -m "$WORK")/deps.txt',encoding='utf-8'):
    m=re.match(r'\s+([^:\s]+):([^:\s]+):jar:(?:([^:\s]+):)?([^:\s]+):(compile|runtime|test|provided)',line)
    if not m: continue
    g,a,c,v,_=m.groups()
    if c and re.match(r'natives-(windows|macos|freebsd|linux-arm)',c): continue
    urls.append(f\"https://repo1.maven.org/maven2/{g.replace('.','/')}/{a}/{v}/{a}-{v}{'-'+c if c else ''}.jar\")
open(r'$(cygpath -m "$WORK")/deps.urls','w',newline='\n').write('\n'.join(urls)+'\n')
print(len(urls),'dependency urls')"
  cp "$WORK/deps.urls" "$ROOT/target/deps.urls"
  tar czf "$WORK/bundle.tgz" -C "$ROOT/target" --exclude=test-classes/testRoundtrip classes test-classes deps.urls
  ( cd "$ROOT" && git rev-parse --short HEAD ) > "$WORK/bundle.rev"
  ( cd "$ROOT" && git status --porcelain ) | grep -q . && echo "+dirty" >> "$WORK/bundle.rev" || true
  log "bundle: $(du -h "$WORK/bundle.tgz" | cut -f1), rev $(tr '
' ' ' < "$WORK/bundle.rev")"
}

cmd_run() {
  local label=$1 query=$2 exp=$3 maxmin=${4:-40}
  [ -f "$WORK/bundle.tgz" ] || { log "no bundle -- run 'vastbench.sh bundle' first"; exit 2; }
  [ -f "$exp" ] || { log "no such experiment: $exp"; exit 2; }
  local left; left=$(budget_left)
  log "budget left: $left USD (of $BUDGET)"
  ensure_key

  local id="" offer="" dph="" url="" t0
  local offers=""
  for pass in 1 2; do   # pass 2 re-searches: offers go stale within minutes
  offers=$("$VAST" search offers "$query num_gpus=1 rentable=true" -o dph_total --raw --limit 8)
  for row in $(echo "$offers" | py "
import json,sys
bad=set(open(r'$(cygpath -m "$WORK")/bad_hosts').read().split()) if __import__('os').path.exists(r'$(cygpath -m "$WORK")/bad_hosts') else set()
for o in json.load(sys.stdin):
    if str(o.get('machine_id')) not in bad: print(f\"{o['id']}:{o['dph_total']:.4f}\")"); do
    offer=${row%%:*}; dph=${row##*:}
    if ! py "import sys; sys.exit(0 if $dph * $maxmin / 60 <= $left else 1)"; then
      log "offer $offer at $dph USD/h x $maxmin min would exceed the budget -- refused"; exit 3
    fi
    local r; r=$("$VAST" create instance "$offer" --image "$IMAGE" --disk 30 --label "baf-perf-$label"         --ssh --direct --cancel-unavail --raw 2>&1 || true)
    id=$(echo "$r" | py "
import json,sys
try: print(json.load(sys.stdin).get('new_contract',''))
except Exception: print('')")
    if [ -z "$id" ]; then log "offer $offer not available"; continue; fi
    trap "destroy $id; trap - EXIT" EXIT INT TERM
    log "instance $id (offer $offer, $dph USD/h, label baf-perf-$label) -- waiting for it to start"
    t0=$(date +%s); url=""
    while :; do
      local st; st=$("$VAST" show instance "$id" --raw 2>/dev/null | py "
import json,sys
try: d=json.load(sys.stdin); print(d.get('actual_status'))
except Exception: print('unknown')")
      if [ "$st" = "running" ]; then url=$("$VAST" ssh-url "$id" 2>/dev/null | tr -d '
' | grep '^ssh://' || true); fi
      [ -n "$url" ] && break
      if [ $(( $(date +%s) - t0 )) -gt "${VB_START_TIMEOUT:-480}" ]; then
        log "instance $id not running after ${VB_START_TIMEOUT:-480} s (status $st) -- destroying, next offer"
        destroy "$id"; trap - EXIT; mark_bad "$offer" "$offers"; id=""; break
      fi
      sleep 15
    done
    if [ -n "$url" ]; then
      local hp0=${url#ssh://root@}
      local t2; t2=$(date +%s)
      until ssh -a -i "$KEY" -p "${hp0##*:}" -o IdentitiesOnly=yes -o UserKnownHostsFile="$KH"               -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 "root@${hp0%%:*}" true 2>/dev/null; do
        if [ $(( $(date +%s) - t2 )) -gt "${VB_SSH_TIMEOUT:-300}" ]; then
          log "instance $id running but ssh not answering after ${VB_SSH_TIMEOUT:-300} s -- destroying, next offer"
          destroy "$id"; trap - EXIT; mark_bad "$offer" "$offers"; id=""; url=""; break
        fi
        sleep 10
      done
    fi
    [ -n "$url" ] && break
  done
  [ -n "$url" ] && break
  log "offer list exhausted -- searching again"
  done
  [ -n "$url" ] || { log "no offer could be started for: $query"; exit 4; }

  # results/ is committed: it gets the offer's hardware facts only. Offer, instance, host and machine
  # ids stay in $WORK (ignored by git); the machine id is what the bad-host list needs.
  local out; out="$HERE/results/$(date +%Y-%m-%d_%H%M)-$label"; mkdir -p "$out"
  local mid; mid=$(echo "$offers" | py "
import json,sys
o=[x for x in json.load(sys.stdin) if str(x['id'])=='$offer'][0]
json.dump({k:o.get(k) for k in ('gpu_name','dph_total','cpu_name','cpu_cores_effective','cpu_ram','gpu_ram',
  'driver_version','cuda_max_good','pcie_bw','pci_gen','reliability2','geolocation','inet_down')},
  open(r'$(cygpath -m "$out")/offer.json','w'), indent=1)
print(o.get('machine_id',''))")
  cp "$WORK/bundle.rev" "$out/bundle.rev"
  echo "$(basename "$out") instance=$id offer=$offer machine=$mid" >> "$WORK/runs.txt"

  local hp=${url#ssh://root@}; local host=${hp%%:*} port=${hp##*:}
  local SSH=(ssh -a -i "$KEY" -p "$port" -o IdentitiesOnly=yes -o UserKnownHostsFile="$KH" \
             -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 -o ServerAliveInterval=30 "root@$host")
  local SCP=(scp -q -i "$KEY" -P "$port" -o IdentitiesOnly=yes -o UserKnownHostsFile="$KH" \
             -o StrictHostKeyChecking=accept-new)
  log "ssh up after $(( $(date +%s) - t0 )) s ($host:$port); uploading"
  "${SSH[@]}" "mkdir -p /root/out /root/baf"
  "${SCP[@]}" "$WORK/bundle.tgz" "$HERE/remote/common.sh" "root@$host:/root/"
  "${SCP[@]}" "$exp" "root@$host:/root/exp.sh"
  "${SSH[@]}" "nohup bash -c 'timeout ${maxmin}m bash /root/exp.sh > /root/out/run.log 2>&1; echo \$? > /root/out/EXIT' > /dev/null 2>&1 &"
  log "experiment $(basename "$exp") started (hard limit $maxmin min)"

  local t1; t1=$(date +%s)
  while ! "${SSH[@]}" "test -f /root/out/EXIT" 2>/dev/null; do
    if [ $(( $(date +%s) - t1 )) -gt $(( maxmin * 60 + 120 )) ]; then log "deadline passed without EXIT"; break; fi
    sleep 30
  done
  "${SCP[@]}" -r "root@$host:/root/out/." "$out/" || log "fetch failed"
  log "results in $out (exit $(cat "$out/EXIT" 2>/dev/null || echo ?), run.log $(wc -l < "$out/run.log" 2>/dev/null || echo 0) lines)"
  destroy "$id"; trap - EXIT
  log "budget left now: $(budget_left) USD"
  if grep -q '^!!! ABORT' "$out/run.log" 2>/dev/null; then
    echo "$mid" >> "$WORK/bad_hosts"
    log "host $mid aborted the precheck ($(grep -m1 '^!!!' "$out/run.log")) -- excluded"
    if [ "${VB_RETRY_DEPTH:-0}" -lt 3 ]; then
      log "retrying on the next offer"
      VB_RETRY_DEPTH=$(( ${VB_RETRY_DEPTH:-0} + 1 )) cmd_run "$label" "$query" "$exp" "$maxmin"; return
    fi
  fi
  echo "$out"
}

cmd_sweep() {
  local n=0
  while read -r id label _; do
    [ -n "$id" ] || continue
    case "$label" in baf-perf-*) destroy "$id" || true; n=$((n+1));; esac
  done < <(instances)
  delete_key
  log "swept $n instance(s). Remaining instances:"; instances >&2 || true
  log "volumes: $("$VAST" show volumes --raw 2>/dev/null | py "import json,sys; d=json.load(sys.stdin); print(len(d if isinstance(d,list) else d.get('volumes',[]) or []))" 2>/dev/null || echo '?')"
  log "credit $(credit), budget left $(budget_left) USD"
}

cmd_status() { log "credit $(credit), budget left $(budget_left) USD"; instances; }

case "${1:-}" in
  bundle) shift; cmd_bundle "$@";;
  run) shift; cmd_run "$@";;
  sweep) shift; cmd_sweep "$@";;
  status) shift; cmd_status "$@";;
  *) sed -n '5,17p' "$0"; exit 1;;
esac
