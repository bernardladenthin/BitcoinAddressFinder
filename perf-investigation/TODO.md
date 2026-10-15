<!--
SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>

SPDX-License-Identifier: Apache-2.0
-->

# GPU throughput investigation — work master

**Local branch `claude/perf-investigation` only. Never pushed.** Commits are local. This file is the
single source of truth for the investigation: every hypothesis, every experiment, every finding and
its evidence (raw output under [`results/`](results/)), and what to try next.

## Summary (2026-10-15, ~0.90 USD of the 14 USD budget spent — investigation closed)

**The RTX 4090 was never slow — the CPU of the rented host was the limit.** With the GPU pre-filter at
its default width `FUSE_8`, ~0.78 % of all candidates are false positives that cross to the host and
are parsed and checked there. A rented host absorbs ~7–25 M of them per second (depends on the CPU,
4 consumer threads), which caps the pipeline at ~0.5–0.7 G keys/s whatever the GPU. With `FUSE_16`
(~256× fewer survivors) the cap disappears:

| card | FUSE_8 (same host) | **FUSE_16** | gain | kernel-only (device-timed) |
|---|--:|--:|--:|--:|
| RTX 5090 | 654 M | **1 897 M** | +190 % | — |
| RTX 4090 | 594 M | **1 029 M** | +73 % | 1 057 M (F5) |
| RTX 3090 (full 350 W) | 436 M | **612 M** | +40 % | — |
| Tesla V100 | 294 M | **377 M** | +28 % | — |
| RTX 5060 Ti | 455 M | **501 M** | +10 % | — |
| RTX 3070 (desktop, full power) | 356 M | 357 M | ±0 | — |
| RTX 3070 Laptop (local) | 252 M | 261 M | +3 % | 325 M (F3) |
| GTX 1080 Ti | 48 M | 49 M | ±0 | — |

The faster the card, the larger the gain; below ~300 M keys/s a typical host keeps up with FUSE_8.
With FUSE_16 the pipeline reaches 95–97 % of the kernel's own speed (statistics verified against
device timestamps), so nothing else of size sits between kernel and counter.

**Ruled out:** provider throttling as the cause of the 4090 anomaly (default 450 W, no throttle reasons —
but *other* hosts do cap: 3 of the 4 rented 3090s that started ran at 200–280 W, and one 3070 at 100 W); filter size vs
L2 (1 M vs 141 M entries: no difference on Ampere or Ada); register spills (126–128 registers, no spill,
on sm_86/89/120); a different EC/hash mix on Ada; wrong statistics; kernel build options
(`KEYS_BATCH_INV`, `maxrregcount`: within ±1.3 %).

**What to change (maintainer decisions — nothing changed in production code):**
1. **The tuner sweeps under FUSE_8 and recommends FUSE_16** with the FUSE_8 winner arm (F8) — resolve
   the filter type before the sweep, or default `gpuFilterType` to FUSE_16 where the device's maximum
   allocation allows it (the field's Javadoc already says so). On a 4090 the emitted config loses ~18 %.
2. **The README's rented-GPU table (PR #388) is FUSE_8-capped and partly measured on power-capped hosts**:
   corrected values above (5090 1.90 G, 4090 1.03 G, 3090 612 M, V100 377 M, 5060 Ti 501 M, 3070 357 M).
   The "4090 not saturated" footnote and the `TODO.md` item it points to are answered by F5/F6.
3. `docs/performance.md`'s "≈255 registers with spilling" (read from `kernelMaxWorkGroupSize = 256`) is
   wrong: 126–128 registers, no spill, a 1.5 KB local-memory stack; the 256 cap persists even at 96
   registers (F2, F9).
4. Only for GPUs that cannot hold FUSE_16: a leaner host parse of FUSE_8 survivors would raise their cap.

**Tooling left on this branch:** `vastbench.sh` (rent → run → fetch → destroy, budget guard, precheck
for power caps / broken hosts, bad-host list, offer retry), `local-jmh.sh`, two unit-tested diagnostic
levers (`dumpProgramBinaryDir`, `extraBuildOptions`), and the offline `ptxas -v` recipe (F2).

## Goal

Find where BitcoinAddressFinder leaves GPU throughput on the table, step by step, starting from the
RTX 4090 anomaly of 2026-10-15 (~600 M keys/s at 117–142 W of 450 W on two hosts, while the RTX 5090
reached 1 593 M/s and the RTX 3090 565 M/s with the same v1.8.0 jar — see `TODO.md` and the README
benchmark table). Rule out the boring explanations first (provider throttling, wrong statistics),
then bisect the pipeline: host ↔ kernel, kernel stage ↔ stage, kernel ↔ memory.

## Rules

* **Budget: ≤ 14 USD (≈ 12 €) of Vast.ai credit in total** for this investigation, counted from the
  account credit at the start. `vastbench.sh` refuses a run that could cross that line (`VB_BUDGET_USD`, start credit stored in `target/vastbench/start_credit`).
* Every instance is destroyed by `vastbench.sh` itself (trap on exit), and every loop iteration ends
  with `vastbench.sh sweep`: 0 instances, 0 volumes, the throwaway SSH key deleted.
* Only public code or this branch's own build goes onto a host; the SSH key used is a throwaway pair
  in `target/vastbench/`, never `~/.ssh`.
* One experiment answers one question. Record the result here *before* starting the next one.

## Tooling

| file | what |
|---|---|
| [`vastbench.sh`](vastbench.sh) | `bundle` (build this branch → tarball), `run <label> "<offer query>" <experiment> [max_min]`, `sweep`, `status` |
| [`remote/common.sh`](remote/common.sh) | sourced by every experiment on the host: OpenCL ICD, bundle unpack, classpath, `nvidia-smi` sampling incl. throttle reasons, helpers `jmh` / `baf` |
| `remote/expNN-*.sh` | one script per experiment, writes everything into `/root/out` |
| `results/<date>-<label>/` | what came back: `run.log`, `smi.csv`, `smi-q.txt`, JMH json, offer json (host, offer and machine ids removed), the 4090's PTX (`ptx/*.ptx.gz`) |

## Hypotheses (ranked by evidence so far)

| # | hypothesis | why it is plausible | how it is tested | status |
|---|---|---|---|---|
| H1 | **Host/provider throttling** (power cap, locked clocks, `nvidia-smi -pl/-lgc`) | rented cards are configured by strangers; one 3070 was capped at 100 W | `nvidia-smi -q -d PERFORMANCE,CLOCK,POWER` + `clocks_event_reasons.active` sampled under load | **ruled out** (F5) |
| H2 | **Statistics are wrong** | flat curves can be a measurement artefact | code review (counter bumped at batch submission, ≤ 5 in-flight batches → < 1 % bias); then cross-check with device-side `enableProfiling` kernel time | **ruled out** (F9: tuner = 95–97 % of device kernel rate) |
| H3 | **GPU filter probe is DRAM-latency bound** on a 141 M-entry FUSE_8 (~150 MB > 72 MB L2) | low power at ~98 % "utilisation" = busy but stalled; probes are random byte loads | `GpuFuse8FilterBenchmark` compact, `filterEntries` 1 M (L2-resident) vs 141 M, same arm | **ruled out** (F1 Ampere, F5 Ada) |
| H4 | **Occupancy / register spill** — kernel at the 255-register ceiling, `privateMemBytes` 1 536, ~17 % occupancy on Ampere; Ada may hide the spill latency worse | documented in `docs/performance.md` §occupancy | resource-usage line + `-cl-nv-verbose` build log on 4090 vs 3090 vs 5090; `KEYS_BATCH_INV` A/B | **revised** (F2: 126 regs, no spill, 1.5 KB stack; F3: KBI 16 ≈ optimum on Ampere) |
| H5 | **Synchronous host loop** (5 × `clFinish` per batch, no overlap) | real but small at batch 24 (~28 ms kernels) | `enableProfiling`: device kernel ns vs wall time per launch | **small** (F6/F9: ≤ 3–5 % with FUSE_16) |
| H6 | **Stage mix differs on Ada** (EC vs hash160) | stage split only ever measured on a 3070 | `kernelProfileStage` FULL / ONE_HASH160 / NO_HASH160 on 4090 vs 3090 | **ruled out** (F5: 61/39 vs 57/43) |
| H7 | **CPU-side handling of FUSE_8 false positives caps throughput on rented hosts** | F3: host gap per key; tuner default is FUSE_8; 4090 CSV shows 8–10 M addresses/s reaching the consumer | tuner FUSE_8 vs FUSE_16, same arm, same host | **CONFIRMED** (F6: 4090 0.59 → 1.03 G; F7: 5090 → 1.90 G) |

## Experiment queue

1. **exp01-diag-4090** — one RTX 4090: throttle diagnostics (H1), resource usage + verbose build log
   (H4), compact JMH at 22/256 with `filterEntries` 1 M vs 141 M (H3) and `kernelProfileStage` FULL vs
   NO_HASH160 (H6), with `profiling=true` (H5). Then the same script on a 3090 and a 5090 for ratios.
2. decided by exp01's result.

## Findings log

*(newest last; each entry: date, experiment, instance, cost, result, conclusion, evidence path)*

### F0 — 2026-10-15, local RTX 3070 Laptop (free): a host gap of ~3.8 ms per launch

Harness check of exp01's JMH call, `GpuFuse8FilterBenchmark` compact, 22/256, 1 M-entry filter,
`profiling=true`: **59.3 launches/s = 16.87 ms per launch wall**, but the device reports
**kernel 12.56 ms + readback 0.51 ms = 13.07 ms**. ~3.8 ms per launch (~22 %) the GPU waits for the
host — at batch 22. That is H5, already visible on Ampere; whether it is fixed per launch (then it
shrinks to ~6 % at batch 24) or grows with the batch is the next question. The kernel alone runs at
2²² / 12.56 ms ≈ **334 M keys/s** on this card versus 249 M/s end-to-end.

Same session: the tuner with a 1 M-entry filter (L2-resident) reached **295 M/s at 24/256** on this
laptop, against the documented ~266 M/s with the 141 M-entry filter — a first hint for H3, to be
measured properly in exp01. `-cl-nv-verbose` on driver 581.83 prints no ptxas register/spill
statistics (only an inlining warning), so H4 needs another route.

### F1 — 2026-10-15, local RTX 3070 Laptop (free): the filter size does not matter on Ampere (H3)

`GpuFuse8FilterBenchmark` compact 22/256, `profiling=true`, 2 × 15 s:

| `filterEntries` | launches/s | kernel per launch |
|---|--:|--:|
| 1 048 576 (~1 MB, L2-resident) | 58.9 | 12.34–12.73 ms |
| 141 045 995 (~150 MB, production size) | 59.2 | 12.3–12.7 ms |

No difference within noise. On the 3070 the filter probe is not a bottleneck at all, so H3 can only
still matter if Ada behaves differently — the 4090 run of exp01 measures the same pair. (The
tuner's 295 vs 266 M/s in F0 compared different arms, not filter sizes.) Evidence:
`results/local-3070/jmh-H3-filtersize.{log,json}`.

### F2 — 2026-10-15, offline `ptxas` 13.4 (free): 126 registers, no spills, 1 536 B stack (H4)

The NVIDIA driver hands out the kernel as PTX (`dumpProgramBinaryDir`, new). Assembled offline with
`ptxas -v` (CUDA 13.4, from the `nvidia-cuda-nvcc` wheel) for each architecture:

| target | registers | spill stores / loads | stack frame |
|---|--:|--:|--:|
| sm_86 (RTX 3090 / 3070) | 126 | 0 / 0 | 1 536 B |
| sm_89 (RTX 4090) | 126 | 0 / 0 | 1 536 B |
| sm_120 (RTX 5090) | 126 | 0 / 0 | 1 536 B |

So `docs/performance.md`'s "≈255 registers with spilling" is not what the offline assembler produces:
there is **no spilling**, but a **1.5 KB per-thread stack** — the dynamically indexed private arrays
(`dx`/`prefix[KEYS_BATCH_INV][10]` and friends) live in *local memory*, i.e. L1/L2/DRAM, and every
sub-batch iteration walks them. Caveat: the driver JIT is a different ptxas build; the runtime's
`kernelMaxWorkGroupSize=256` (rather than 512) hints that the driver may use more than 128
registers. Architectures do not differ in code shape, so a 4090-only collapse must come from how
the hardware serves that local memory / occupancy, not from different code.

Levers added for the next experiments (commit `0f80632`, unit-tested): `extraBuildOptions` system
property (`-D KEYS_BATCH_INV=<n>`, `-cl-nv-maxrregcount=<n>`), `KEYS_BATCH_INV` now `#ifndef`-guarded.

### F3 — 2026-10-15, local RTX 3070 Laptop (free): `KEYS_BATCH_INV` is near its optimum; the host gap grows with the batch

`GpuFuse8FilterBenchmark` compact **24/512**, 1 M filter, `profiling=true`, `-DKEYS_BATCH_INV=<n>`:

| KEYS_BATCH_INV | launches/s | kernel | readback | wall per launch | host gap | stack (`privateMemBytes`) |
|--:|--:|--:|--:|--:|--:|--:|
| 4 | 12.51 | 65.0 ms | 1.9 ms | 79.9 ms | 13.0 ms | 576 B |
| 8 | 13.77 | 52.5 ms | 2.3 ms | 72.6 ms | 17.8 ms | 896 B |
| **16** (default) | **14.43** | 51.7 ms | 2.25 ms | 69.3 ms | 15.3 ms | 1 536 B |
| 32 | 14.11 | 48.6 ms | 2.1 ms | 70.9 ms | 20.2 ms | 2 816 B |

* Kernel-only, 32 beats 16 by 6 % (48.6 vs 51.7 ms) — but not end to end. The default is fine on Ampere.
  Kernel-only throughput at 16: 2²⁴ / 51.7 ms = **325 M keys/s**, end to end 242 M/s.
* **The host gap scales with the batch:** ~3.8 ms at batch 22 (F0) → ~15 ms at batch 24, i.e. it is
  *per key*, not per launch. In this benchmark the op includes `getPublicKeyBytes()`: every FUSE_8
  false positive (2 × 0.39 % ≈ 131 k per 2²⁴ launch) is parsed on the calling thread (BigInteger +
  4 `byte[]` + `PublicKeyBytes` each). That is the gap.
* In `Find` / the tuner that parse runs on 4 reader threads and the consumer checks the survivors —
  concurrently, but on the **CPU of the rented host**. The 2026-07 `cascade_under_load.csv` already
  shows the shape on this laptop: FUSE_8 with 1 consumer thread 31.7 M/s, 8 threads 188 M/s, FUSE_16
  228 M/s regardless of threads.

**New hypothesis H7: on rented hosts the CPU-side handling of FUSE_8 false positives caps the
throughput** (~4.7 M survivors/s at 600 M keys/s on the 4090). Every Vast run of 2026-10-15 used
the tuner default `gpuFilterType = FUSE_8`. Test: tuner FUSE_8 vs FUSE_16 on the same arm — locally
first (running), then on the 4090.

### F4 — 2026-10-15, local RTX 3070 Laptop (free): FUSE_16 +3 % here — the laptop's CPU keeps up

Tuner, one arm 24/512, 20 s + 5 s warm-up, 141 M-entry synthetic filter, 4 consumer threads
(Ryzen 7 5800H, 8 cores / 16 threads):

| `gpuFilterType` | candidates/s | addresses reaching the consumer/s |
|---|--:|--:|
| FUSE_8 | 252.4 M | 3 924 k |
| FUSE_16 | **260.9 M** (+3.3 %) | 16 k |

At ~250 M keys/s an 8-core laptop absorbs ~3.9 M survivors/s with little loss. A 4090 at its
expected ~1.3 G keys/s would push ~20 M survivors/s through FUSE_8 — and the 2026-10-15 4090 hosts
were measured at only 8–10 M addresses/s reaching the consumer. Whether that is the ceiling is
exactly what exp02 on the 4090 measures. Evidence: `results/local-nvidiageforcertx3070laptopgpu/tune-FUSE_*.log`.

### F5 — 2026-10-15, exp01 on an RTX 4090 (Vast, JP, EPYC 7542 16 cores, 0.41 USD/h, ~0.25 USD incl. a 20-min upload): **the 4090's kernel runs at ~1.06 G keys/s — the pipeline delivers half of it**

* **H1 ruled out.** Power limit 450 W = default = max, no application clocks, `clocks_event_reasons`
  `0x0` in every loaded sample, ~2 760 MHz / ~254 W average under load. The provider throttles nothing.
* **Tuner on this host (FUSE_8, 4 consumer threads):** 22/256 527 M, 22/512 512 M, 24/256 511 M,
  24/512 **582 M** candidates/s, with 8.0–9.0 M addresses/s reaching the consumer — the same ~600 M
  plateau as on both hosts of 2026-10-15.
* **Kernel time** (`GpuFuse8FilterBenchmark`, compact 22/256, `profiling=true`):

  | filter | stage | launches/s (wall) | kernel | readback | kernel-only keys/s |
  |---|---|--:|--:|--:|--:|
  | 1 M | FULL | 129.8 | 3.97 ms | 0.33 ms | **1 057 M** |
  | 141 M | FULL | 128.1 | 4.04 ms | 0.28 ms | 1 038 M |
  | 1 M | NO_HASH160 | 234.9 | 2.44 ms | 0.09 ms | — |
  | 141 M | NO_HASH160 | 240.6 | 2.33 ms | 0.13 ms | — |

  Wall per FULL launch 7.7 ms = 4.0 kernel + 0.3 readback + **3.4 ms host** (the per-launch parse of
  ~33 k FUSE_8 false positives — the same ~3.5 ms the 3070 needs for the same batch, F0).
* **H3 ruled out on Ada too** (1 M vs 141 M filter: within 2 %). **H6 ruled out**: EC 61 % / hash160
  39 % (3070: 57 / 43). **H4**: the 4090 driver's own PTX (`.version 8.7`, driver 570.144) assembles to
  128 registers, no spill, 1 536 B stack — same as Ampere; nothing Ada-specific in the code.

**Conclusion so far: the GPU is not the limit on a 4090 — the host side is.** The kernel alone does
~1.06 G keys/s (≈ what its compute predicts, ≈ 1.9× a 3090), the tuner sees ~0.55–0.58 G. On slower
cards the same host cost hides behind a longer kernel; on the 4090/5090 it does not. Next: exp02
(FUSE_16 removes ~99.6 % of the survivors) — if H7 is right the 4090 should jump towards ~1 G.

### F6 — 2026-10-15, exp02 on an RTX 4090 (Vast, CA, EPYC 7502 16 cores, 0.43 USD/h, ~0.08 USD): **H7 confirmed — FUSE_16 takes the 4090 from ~0.59 G to 1.03 G keys/s (+73 %)**

Tuner, same host, same arms, 141 M-entry synthetic filter (candidates/s; addresses reaching the consumer/s):

| arm | FUSE_16, 4 threads | FUSE_8, 4 threads | FUSE_8, 8 threads |
|---|--:|--:|--:|
| 22/256 | 844 M (51 k) | 594 M (9.27 M) | 696 M (10.86 M) |
| 22/512 | 535 M (33 k) ¹ | 508 M (7.91 M) | 508 M (7.93 M) |
| 24/256 | 953 M (58 k) | 545 M (8.48 M) | 566 M (8.82 M) |
| **24/512** | **1 029 M** (63 k) | 464 M (7.16 M) | 533 M (8.21 M) |

* With FUSE_16 the pipeline reaches **97 % of the kernel-only rate** measured in F5 (1 057 M at 22/256),
  so on this card nothing else of size is left between the kernel and the counter.
* FUSE_8 is capped by the host at **~7–11 M survivors/s** (4 → 8 consumer threads: +17 % at best), i.e.
  ~0.5–0.7 G keys/s whatever the GPU. The same cap explains the flat curves of 2026-10-15: it is the
  CPU of the rented host, not the card, and "busy but stalled at 120–140 W" is the GPU idling between
  launches while the producer waits for free result slots.
* ¹ 22/512 under FUSE_16 is low (535 M) — a single 15 s arm; not investigated further, the 24/x arms are
  consistent.

**Consequences (recommendations, nothing changed in production code yet):**
1. **`TuneConfiguration` measures with `gpuFilterType = FUSE_8` by default** (`CProducerOpenCL` default) —
   so every tuner number on a fast card understates what a FUSE_16 `Find` achieves. Either tune with the
   filter type the run will use, or default to FUSE_16 where `CL_DEVICE_MAX_MEM_ALLOC_SIZE` allows (the
   field's own Javadoc already says to prefer it).
2. **The README's rented-GPU table (PR #388) is FUSE_8-capped for the fast cards** — the 4090 row
   (596–666 M, "not saturated") is really ~1.03 G; the RTX 5090 row (1 593 M) may be capped too
   (24.8 M addresses/s reached its consumer). exp03 re-measures the 5090.
3. FUSE_8 users on fast GPUs pay for the host-side parse of every false positive (`OpenCLGridResult` →
   `BigInteger` + 4 `byte[]` + `PublicKeyBytes` per survivor). A leaner parse would raise the FUSE_8 cap
   — worth it only for GPUs whose VRAM cannot hold FUSE_16.

### F7 — 2026-10-15, exp03 on an RTX 5090 (Vast, US-WA, EPYC 9J14 96-core/48 eff., 0.74 USD/h, ~0.08 USD): **the 5090 was capped too — 1.90 G keys/s with FUSE_16**

| arm | FUSE_16, 4 threads | FUSE_8, 4 threads |
|---|--:|--:|
| 24/256 | 1 840 M (112 k) | 635 M (9.90 M) |
| **24/512** | **1 897 M** (116 k) | 654 M (10.22 M) |
| 24/1024 | 1 096 M (67 k) | 640 M (9.98 M) |

* ~518 W / ~2.86 GHz under FUSE_16 load: the card is saturated now (the README run saw ~275 W median).
* **+19 % over the README's 1 593 M** (that host happened to absorb 24.8 M survivors/s under FUSE_8) and
  **2.9× FUSE_8 on this very host** (~10 M survivors/s). So the FUSE_8 ceiling is a property of the host
  CPU and the 4 consumer threads, and it varies by 2.5× between rented hosts — which is why the
  2026-10-15 table looked "noisy" for the fast cards.
* 24/1024 drops to 1.1 G under FUSE_16 (fewer work-items than the card needs to fill 170 SMs).

**H7 now holds on Ampere (+3 %, laptop), Ada (+73 %) and Blackwell (+19…190 %).** The README's
rented-GPU table should be re-measured with FUSE_16 for every card (cost ≈ 0.08 USD per card).

### F8 — 2026-10-15, code review (free): the tuner sweeps under one filter type and recommends another

`TuneConfiguration` (`run`, ~line 410–440): every arm is measured with the template's
`gpuFilterType` — `FUSE_8` unless configured (`CProducerOpenCL` default). Only **after** the sweep does
`resolveFilterType` pick the width to recommend, from `total = probe + fpr × verificationCost`
(`deriveFilterType`, ~line 897). Two gaps:

1. **The cost model has no host-throughput term.** It weighs a false positive by one LMDB verification
   (4.1 µs warm, documented) but not by the CPU that has to receive, parse and queue every survivor —
   which F6/F7 show is the binding limit on fast cards (~7–25 M survivors/s depending on the host).
   With the documented warm cost it already prefers FUSE_16 (80.5 ns vs 92.6 ns per candidate) — for
   the wrong reason, and by a margin (13 %) far smaller than the real effect (+73 % / +190 %).
2. **The recommended arm is the FUSE_8 winner, emitted with FUSE_16.** On the 4090 the FUSE_8 sweep
   picks 22/256 (594 M); under FUSE_16 the best arm is 24/512 (1 029 M) and 22/256 gives only 844 M.
   The paste-ready config therefore leaves ~18 % on the table even after the type is switched.

**Options for the maintainer (not implemented — they change what the tuner recommends):**
* (a) resolve the filter type **before** the sweep and measure the arms with it (when `sweepFilterTypes`
  is off and the template's type is the default) — cheapest, fixes gap 2;
* (b) add a host term to the model: `survivors/s = candidates/s × 2 × fpr` against a measured or assumed
  host capacity — fixes gap 1, but needs a capacity measurement;
* (c) make `FUSE_16` the default of `CProducerOpenCL.gpuFilterType` where `CL_DEVICE_MAX_MEM_ALLOC_SIZE`
  allows (its own Javadoc already says "prefer it whenever … has room") — fixes both for most users.

Evidence: F4, F6, F7.

### F9 — 2026-10-15, exp04 on an RTX 4090 (Vast, US, EPYC 7542 16 eff., 0.37 USD/h, ~0.1 USD): no kernel headroom from build options

FUSE_16, 24/512, one tuner arm (20 s) per build-option variant, same host:

| variant | candidates/s | vs base | `privateMemBytes` |
|---|--:|--:|--:|
| base | 1 004 M | — | 1 536 |
| `-DKEYS_BATCH_INV=8` | 978 M | −2.6 % | 896 |
| `-DKEYS_BATCH_INV=32` | 1 019 M | +1.3 % | 2 816 |
| `-cl-nv-maxrregcount=96` | 920 M | −8.4 % | 1 632 |
| `-cl-nv-maxrregcount=168` | 1 011 M | +0.5 % | 1 536 |
| base (repeat) | 1 008 M | +0.4 % | 1 536 |

* Noise floor ±0.4 % (two base runs). Nothing worth changing: `KEYS_BATCH_INV=32` is +1.3 % on Ada (and
  kernel-only +6 % but end-to-end −2 % on the 3070, F3) — not a cross-device win.
* **`kernelMaxWorkGroupSize = 256` is not a register signal**: it stays 256 at `maxrregcount=96`, where
  registers would allow 512+. `docs/performance.md`'s "≈255 registers" reading of it is wrong (see F2).
* **Statistics are consistent**: the tuner's 1.00–1.03 G (F6, F9) sits at 95–97 % of the device-reported
  kernel-only rate (1.06 G, F5) — the counters are not inflating or hiding anything (H2 closed).

### F10 — 2026-10-15, exp05 round 1 (6 cards in parallel, ~0.3 USD): FUSE_16 on the table cards; two broken hosts

Tuner, 141 M synthetic filter, best FUSE_16 arm vs FUSE_8 at 24/512 on the same host:

| card (host) | FUSE_16 best | FUSE_8 24/512 | gain | README (FUSE_8, 2026-10-15) |
|---|--:|--:|--:|--:|
| RTX 5060 Ti (CH, Xeon E5-2667 v4, 8 eff.) | **501 M** (24/1024) | 455 M | +10 % | 481 M |
| GTX 1080 Ti (KR, i7-6700, 8 eff.) | 49 M (24/512) | 48 M | ±0 | 49 M |
| RTX 3090 (TH, EPYC 7502, 9 eff.) — **power-capped 200 W of 370 W** | 323 M | 295 M | +9 % | 565 M |
| Tesla V100 (US-TX) | — host filesystem broken (`tar: Cannot open: Function not implemented`) | | | 310 M |
| RTX 3070 (JP) | — instance `running`, sshd never answered (15 min) | | | 249 M |
| RTX 2070 SUPER | — no offer with ≥ 32 GB host RAM | | | 216 M |

* The FUSE_8 penalty grows with the card's speed: 0 % (1080 Ti, 49 M) → ~10 % (5060 Ti / capped 3090,
  ~300–500 M) → 73 % (4090) / 190 % (5090). Below ~300 M keys/s a typical host keeps up with FUSE_8.
* **H1 does happen — on some hosts.** The 3090 host set `power.limit` to 200 W (default 370 W): throttle
  reason `0x4` (SW power cap) in all 64 loaded samples, ~1.03 GHz. `remote/common.sh` now aborts a run
  when `enforced.power.limit < power.default_limit` (override `ALLOW_CAPPED=1`), and when the bundle
  cannot be unpacked; the heap follows the host RAM (60 %, ≤ 16 GB) so smaller hosts qualify.
* `vastbench.sh` now also replaces an instance whose sshd does not answer within 5 min.

### F11 — 2026-10-15, exp05 round 2 (~0.04 USD): the prechecks pay off; a full-power 3070 desktop

* **RTX 3070** (NO, EPYC 7B13, 32 eff., 0.15 USD/h, full power): FUSE_16 **357 M** (24/512), FUSE_8 356 M —
  no difference at this speed. The README's 249 M came from the 100 W-capped host of 2026-10-15
  (+43 % on an uncapped card).
* **RTX 3090** (US-CA): again power-capped (220 W of 390 W) — **aborted by the precheck after ~2 min**
  instead of producing a wrong number. 2 of 3 rented 3090s so far were capped.
* **Tesla V100**: the same broken host as round 1 (cheapest offer again) — aborted at unpack.
* **RTX 2070 SUPER**: no offer at all at the moment.
* `vastbench.sh` now keeps `target/vastbench/bad_hosts` (machine ids that failed a precheck), skips them
  in every search and retries on the next offer automatically (up to 3 times).

### F12 — 2026-10-15, exp05 round 3 (~0.04 USD): V100 +28 % with FUSE_16; no clean 3090 yet

* **Tesla V100** (US-CA, Xeon E5-2682 v4, 32 eff., 0.08 USD/h, full power): FUSE_16 **377 M** (24/1024),
  FUSE_8 294 M at 24/512 (+28 %); +22 % over the README's 310 M.
* **RTX 3090**: one host never left `created`, the next was `running` without sshd; after 20 minutes
  every other offer on the list was gone (`not available`). `vastbench.sh` now re-searches once when
  its offer list is exhausted. Note: the README's 3090 (565 M) ran at `power.limit` 280 W — *also* below
  the 350 W default — so no full-power 3090 has been measured yet.
* **RTX 2070 SUPER**: the single offer's sshd never answered; dropped for now.

### F13 — 2026-10-15, exp05 round 4 (~0.02 USD): a host without OpenCL

RTX 3090 (CZ, EPYC 7452): one host never left `created`, the next `exited`, the third started fine — but
had **no OpenCL platform** (`clinfo`: 0 platforms; every arm `Invalid platform index: 0`). The NVIDIA
container runtime on that host does not mount `libnvidia-opencl`. `remote/common.sh` now aborts when
`clinfo -l` lists no platform, which puts the machine on the bad-host list.

### F14 — 2026-10-15, exp05 round 5 (~0.05 USD): a full-power RTX 3090 — 612 M with FUSE_16 (+40 %)

RTX 3090 (CZ, EPYC 7552, 16 eff., 0.15 USD/h, `power.limit` 350 W = default): FUSE_16 **612 M** (24/1024),
560 M (22/256); FUSE_8 436 M at 24/512 (+40 %). The README's 565 M came from a host capped at 280 W.
Getting there took 9 rentals of RTX 3090 offers: 5 never left `created`/`exited`, 1 never answered ssh,
2 were power-capped (200 W, 220 W), 1 had no OpenCL — only this one was usable. `vastbench.sh` now puts
every such machine on the bad-host list (`target/vastbench/bad_hosts`).

### Evidence — which `results/` directory backs which finding

| directory | finding | what it holds |
|---|---|---|
| `local-nvidiageforcertx3070laptopgpu/` | F0, F3, F4 | JMH `KEYS_BATCH_INV` 4/8/16/32, tuner FUSE_8 vs FUSE_16 (local, free) |
| `local-3070/` | F1 | JMH filter size 1 M vs 141 M (local, free) |
| `2026-10-15_0115-exp01-4090` | (Vast lesson) | instance never left `created` — offer only, no measurement |
| `2026-10-15_0133-exp01-4090` | F5 | tuner FUSE_8, JMH kernel/readback timing, 4090 PTX |
| `2026-10-15_0209-exp02-4090` | F6 | tuner FUSE_16 / FUSE_8 4 threads / FUSE_8 8 threads |
| `2026-10-15_0222-exp03-5090` | F7 | tuner FUSE_16 vs FUSE_8 |
| `2026-10-15_0231-exp04-4090` | F9 | build-option variants (`KEYS_BATCH_INV`, `maxrregcount`) |
| `2026-10-15_0250-exp05-{gtx_1080_ti,rtx_5060_ti,rtx_3090}` | F10 | FUSE_16 sweep + FUSE_8 24/512; the 3090 is the 200 W-capped host |
| `2026-10-15_0250-exp05-{tesla_v100,rtx_3070}` | F10 | broken filesystem / sshd never answered — no numbers |
| `2026-10-15_0308-exp05b-rtx_3070` | F11 | full-power 3070 desktop |
| `2026-10-15_0308-exp05b-{tesla_v100,rtx_3090}` | F11 | broken host again / `POWER_CAPPED` 220 W — aborted by the precheck |
| `2026-10-15_0331-exp05c-tesla_v100` | F12 | V100 FUSE_16 vs FUSE_8 |
| `2026-10-15_0359-exp05d-rtx_3090` | F13 | host without OpenCL (0 platforms) — no numbers |
| `2026-10-15_0436-exp05e-rtx_3090` | F14 | full-power 3090 FUSE_16 vs FUSE_8 |

## Closing (2026-10-15 04:45)

All hypotheses resolved (table above); the open items are maintainer decisions (Summary, "What to
change"). Final sweep: 0 instances, 0 volumes, throwaway key deleted, **~0.90 USD spent** of the 14 USD
budget. Loop stopped.

Status: with FUSE_16 the 4090 runs at its kernel's speed. What is left is algorithmic kernel work (out
of scope for build flags) and the product questions of F8.
