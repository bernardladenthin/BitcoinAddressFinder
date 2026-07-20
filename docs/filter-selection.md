# Filter selection — the complete measured basis

*Measured 2026-07-19 on two machines. Reproduce with
[`measurements/bench_filters.sh`](measurements/bench_filters.sh).*

Every statement below is one measurement with one varied parameter, taken **without LMDB behind the
filter**, so the numbers describe the filter rather than the storage. Backends are only ever
compared **within one JMH session**: identical configurations measured in separate sessions differed
by up to 34 % on this hardware and silently reversed two conclusions before that rule was enforced
in the script.

Machines: `ryzen75800h-63g-win11` (16 MB L3, RTX 3070 Laptop 8 GB) and `ryzen79800x3d8core-61g-win11`
(96 MB L3, RX 7900 XTX 24 GB).

---

## 1. Size and accuracy — exact and deterministic

Footprints come from each backend's `sizeInBytes()`, not from heap deltas (those ran ~25 % high).
FPR at 1e8 entries, 5 M probes, all configurations from one source.

| Configuration | B/entry | FPR | Bits used / spent | Efficiency |
|---|--:|--:|--:|--:|
| `BINARY_FUSE_8` | 1.126 | 0.3874 % | 8.01 / 9.01 | **0.89** |
| `BLOCKED_BLOOM` 9/5 | 1.125 | 1.6283 % | 5.94 / 9.00 | 0.66 |
| `BLOCKED_BLOOM` 11/6 | 1.375 | 0.7563 % | 7.05 / 11.00 | 0.64 |
| `BLOCKED_BLOOM` 17/7 | 2.125 | 0.1555 % | 9.33 / 17.00 | 0.55 |
| `BLOCKED_BLOOM` 18/7 | 2.250 | 0.1340 % | 9.54 / 18.00 | 0.53 |
| `BINARY_FUSE_16` | 2.252 | 0.0016 % | 15.90 / 18.02 | **0.88** |
| `BLOCKED_BLOOM` 26/9 | 3.250 | 0.0592 % | 10.72 / 26.00 | 0.41 |

"Bits used" is `log2(1/FPR)` — how much discrimination each configuration actually buys for the bits
it spends. Two facts follow directly:

- **`BINARY_FUSE_16` dominates `BLOCKED_BLOOM` 26/9 outright** — less memory (2.252 vs 3.250 B/entry)
  *and* 37× better FPR. No criterion favours the dense blocked-bloom variant.
- **Blocked bloom's efficiency decays with density** (0.66 → 0.53 → 0.41) while fuse holds ~0.88
  at both widths. This is structural, not tuning: all `k` bits must land in one 512-bit block, so
  the fuller the block the more entries collide inside it. Blocked bloom is only competitive at
  *low* density.

> ### Reproducing the FPR across machines
> It is **bit-identical for identical parameters** — same entry count, same probe count, same seeds.
> Verified by re-running (0.007531 twice) and by both machines producing exactly 0.007531 for
> `BLOCKED_BLOOM` 11/6 at 1e7 entries with 2 M probes.
>
> It is **not** identical across different probe counts, because it is an estimate over a finite
> sample. At p ≈ 0.0075 with 2 M probes the standard error is `sqrt(p(1-p)/n)` = 6.1e-5. Values from
> 1e7/2M and 1e8/5M runs differ by less than one standard error and agree perfectly well.
>
> An earlier instruction in this project told the second machine to expect digit-identical agreement
> *regardless of parameters* and to treat deviation as a setup fault. That was wrong and cost real
> time chasing a non-existent bug. The correct criterion: identical parameters → bit-identical;
> differing probe counts → within ±2 standard errors.

---

## 2. CPU probe latency (ns/op, one session)

Non-member probes. Misses dominate a key scan, and a miss short-circuits, so this measures the
filter's own work rather than a delegate's.

| Backend | 1e7 | 1e8 | 1e9 |
|---|--:|--:|--:|
| `BINARY_FUSE_8` | 31.2 ± 6.0 | 61.5 ± 1.4 | 80.4 ± 2.3 |
| `BINARY_FUSE_16` | 36.5 ± 2.8 | 64.5 ± 2.5 | 80.8 ± 2.3 |
| `BLOCKED_BLOOM` 11/6 | 35.8 ± 0.8 | **46.8 ± 2.2** | **59.0 ± 2.8** |
| `TRUNCATED_LONG_64` | 337.3 ± 13.4 | 635.5 ± 30.6 | 1061.7 ± 15.7 |

`TRUNCATED_LONG_64` is Pareto-dominated by an order of magnitude at seven times the memory. It
remains in the table only as the reference point for exact in-RAM storage.

---

## 3. Equal-memory comparison — the confound removed

Every table above compares at equal *entry count*, which means **unequal memory**: at 1e9 entries
Fuse-8 occupies 1.13 GB while blocked bloom at bpe 26 occupies 3.25 GB. Two things vary at once
there, so part of any latency difference is simply cache behaviour. Here the footprint is held
constant and only the filter type varies.

| Budget | Backend | 1e8 | 1e9 |
|---|---|--:|--:|
| **1.13 B/entry** | `BINARY_FUSE_8` | 59.3 ± 1.4 | 76.7 ± 0.8 |
| | `BLOCKED_BLOOM` 9/5 | **45.3 ± 3.8** | **63.4 ± 2.5** |
| **2.25 B/entry** | `BINARY_FUSE_16` | 65.8 ± 0.7 | 80.4 ± 1.5 |
| | `BLOCKED_BLOOM` 18/7 | **40.7 ± 1.9** | **54.4 ± 6.7** |

**Blocked bloom probes faster at every budget — 17 to 38 %.** All `k` bits sit in one 512-bit block,
i.e. one coalesced transaction, where a fuse lookup makes three scattered reads.

---

## 4. GPU probe (one session, RTX 3070 Laptop, 4 194 304 probes per op, ms/op)

| Filter | 1e7 | 1e8 | 1e9 |
|---|--:|--:|--:|
| `FUSE8` | 1.784 ± 0.008 | 3.011 ± 0.002 | 3.144 ± 0.037 |
| `FUSE16` | 2.437 ± 0.001 | 3.088 ± 0.018 | — (no GPU arm run) |
| `BLOCKED_BLOOM` 11/6 | **0.902 ± 0.029** | **1.459 ± 0.017** | 1.520 ± 0.051 |
| `BLOCKED_BLOOM` 17/7 | — | — | 1.396 ± 0.022 |
| `BLOCKED_BLOOM` 26/9 | — | — | **1.350 ± 0.029** |

The coalescing advantage is confirmed cross-vendor and **grows with size**: blocked bloom's lead over
Fuse-8 reaches **2.33×** at 1e9 on NVIDIA (1.7× at 1e7, 2.2× at 1e8), and 2.53× on RDNA3. The CPU's
short-circuit advantage does **not** carry over: a warp runs in lockstep and waits for its slowest
lane, so an early exit saves nothing — which is also why on the GPU the *denser* blocked bloom is
*faster* (26/9 at 1.350 ms beats 11/6 at 1.520 ms), the opposite of the CPU ordering.

`FUSE16` costs only **+2.6 %** over `FUSE8` at 1e8, against 37 % at 1e7. The gap closes with size
because once both filters read from VRAM the access *count* is unchanged — only the payload per
access doubles.

---

## 5. Build cost (storage-free, first JIT-carrying shot discarded)

| Backend | 1e7 | 1e8 | 1e9 |
|---|--:|--:|--:|
| `BINARY_FUSE_8` | 3 793 ± 762 ms | 43 752 ± 8 796 ms | 610 948 ± 93 998 ms |
| `BINARY_FUSE_16` | 3 798 ± 646 ms | 43 628 ± 7 930 ms | 621 422 ± 109 399 ms |
| `BLOCKED_BLOOM` 11/6 | **669 ± 170 ms** | **14 560 ± 599 ms** | **159 398 ± 9 193 ms** |
| `BLOCKED_BLOOM` 17/7 | 980 ± 48 ms | 14 959 ± 107 ms | 168 215 ± 30 410 ms |
| `BLOCKED_BLOOM` 26/9 | 1 215 ± 132 ms | 15 335 ± 384 ms | 192 482 ± 14 483 ms |

**Blocked bloom builds 3.0× faster at 1e8 and 3.8× at 1e9** — the advantage grows with scale, because
the multi-pass fuse peeling degrades faster than the single streaming pass. Binary Fuse peels through
auxiliary arrays at ~29 B/entry — roughly 40 GB of transient heap at the 1.377 B tier, against 1.89 GB
for the finished blocked-bloom filter. (The 1e9 fuse builds need ~48 GB of heap; an 8 GB run OOMs
mid-peel, which is how the earlier "fuse cannot build at Full DB" claim arose — the machine, not the
algorithm.)

Two secondary readings: Fuse-8 and Fuse-16 build in the **same** time, so the cost is the peeling
rather than the fingerprint width — Fuse-16 is free to build relative to Fuse-8 and costs only
memory. And blocked bloom's density barely affects build time (669 → 1 215 ms), since more bits per
entry means only more writes within the same single pass.

> Measured with `-wi 0`, these same numbers carried error bars **larger than their means**
> (4282 ± 8010 ms), because the first single-shot carried the entire JIT compilation while the rest
> did not — a bimodal mixture of two different programs. One discarded shot cut the spread 6–74×.
> A dispersion exceeding its mean is not an honest wide error bar; it is an unusable one.

---

## 6. The cost model — why probe latency is the term that barely counts

```
total = probe + false-positive-rate × verification
```

Verification is the LMDB read a false positive forces. It spans **4.1 µs warm to 292.7 µs cold** on
the early microbenchmark, and the `TuneConfiguration` command has since measured it against real
databases in their actual page-cache state:

| Machine | Database state | Measured verification cost |
|---|---|--:|
| Ryzen 9800X3D / RX 7900 XTX | operator's LMDB | **195.56 µs** |
| Ryzen 5800H / RTX 3070 | 5.5 GB LMDB, **cold** (first touch) | **385.65 µs** |
| Ryzen 5800H / RTX 3070 | same 5.5 GB LMDB, **warm** (after a sustained scan) | **~11 µs** |

The last two rows are the **same database** — a cold first-touch lookup costs 385 µs; once a sustained
scan has pulled the working set into the page cache, the same lookup costs ~11 µs. A **35× spread on
one database**, driven entirely by cache state. This is why the tuner reports the term as ESTIMATED
unless it measures *your* database in *its* current state, and why the filter recommendation flips
with it: expensive verification (cold, or a large database that never fully warms) rewards the filter
with the lowest false-positive rate, while cheap verification (warm, small database) makes the choice
nearly throughput-neutral. At the cold 385.65 µs a false positive costs ~5 000× a probe. At 1e9
entries, filters at equal footprint:

| Filter | Memory | Probe | FPR | Total warm | Total cold |
|---|--:|--:|--:|--:|--:|
| `BINARY_FUSE_8` | 1.13 B/e | 76.7 ns | 0.387 % | **92.6 ns** | **1 211 ns** |
| `BLOCKED_BLOOM` 9/5 | 1.13 B/e | 63.4 ns | 1.628 % | 130.2 ns | 4 829 ns |
| `BINARY_FUSE_16` | 2.25 B/e | 80.4 ns | 0.0016 % | 80.5 ns | **85.1 ns** |
| `BLOCKED_BLOOM` 18/7 | 2.25 B/e | 54.4 ns | 0.134 % | **59.9 ns** | 447 ns |

Crossover points follow from solving the two cost equations:

- **1.13 B budget:** Fuse-8 wins once verification exceeds **1.07 µs** — i.e. always, since the
  cheapest ever measured is 4.1 µs.
- **2.25 B budget:** crossover at **19.6 µs**. Blocked bloom wins against a warm database, Fuse-16
  wins against a cold one by 5.3×.

**Blocked bloom wins every probe column in this document and loses every total.** That is the single
most important result here, and it inverted the recommendation twice before the equal-memory
comparison isolated the effect.

---

## 7. The two-stage cascade

The GPU pre-filter (`gpuFilterType`) and the consumer backend (`addressLookupBackend`) are
**independent layers, not competing choices**. Both filter families have one-sided error — a member
is never missed — so cascading them is always safe. Because they hash independently, the compound
false-positive rate is the **product** of the two:

| GPU stage | CPU stage | Reaching LMDB per 1e6 candidates |
|---|---|--:|
| `FUSE_8` | `BINARY_FUSE_8` | **3 874** — second stage rejects nothing |
| `FUSE_8` | `BINARY_FUSE_16` | 0.06 |
| `FUSE_16` | `BINARY_FUSE_8` | **0.0006** |

The first row is the trap. With `addressLookupBackend = BINARY_FUSE_8` the consumer **reuses the
very filter instance it uploaded to the device**. The filter is deterministic, so every GPU survivor
passes the CPU stage as well and the second filter is a no-op. This is pinned by
`FilterCascadeTest#sameFilterTwice_rejectsNothing`, and it is why the recommended pairing
deliberately uses two *different* filters.

### Measured end-to-end, against a real database, at varying consumer capacity

This is the measurement the whole recommendation rested on and, until it was run, was only derived.
Real `Find` runs against the 5.5 GB LMDB (`LMDB_ONLY`, `batchSizeInBits=22`, `keysPerWorkItem=256`),
candidate rate read from the producer's batch counter, sweeping the consumer thread count. Two
independent passes (A and D), reproducing to within 2 % — raw data in
[`cascade_under_load.csv`](measurements/cascade_under_load.csv):

| Consumer threads | `FUSE_8` (A / D) | `FUSE_16` (A / D) | Fuse-16 speed-up |
|--:|--:|--:|--:|
| 8 | 188.6 / 187.2 M/s | 227.8 / 228.1 M/s | **1.21×** |
| 4 | 105.7 / 105.5 M/s | 228.2 / 228.5 M/s | **2.16×** |
| 2 | 55.1 / 54.2 M/s | 228.3 / 228.2 M/s | **4.14×** |
| 1 | 31.7 / 31.1 M/s | 228.3 / 228.6 M/s | **7.24×** |

Two facts, and the second **corrects an earlier claim in this very document**:

- **`FUSE_16` is flat at ~228 M/s regardless of thread count**, because it hands the consumer only
  ~14 k survivors/s — the consumer never becomes the limiting stage, and the pipeline runs at the
  GPU's rate. `FUSE_8` instead scales *linearly* with consumer threads, because its ~3.6 M
  survivors/s make the single-verify path the bottleneck. The `Producer blocked (queue full)` counter
  confirms it: thousands of stalls per window with `FUSE_8`, **zero** with `FUSE_16`.

- **`FUSE_16` raises net throughput — by 1.21× at 8 threads up to 7.24× at 1 thread.** An earlier
  version of this section reported the cascade as throughput-*neutral*, from a `TuneConfiguration`
  `sweepFilterTypes` run that measured 231 vs 233 M/s. That run was misleading: the tuner's consumer
  uses a trivial always-absent lookup (`NEVER_PRESENT`), never touching LMDB, so its consumer can
  never saturate and the filter choice looks free. A **real** consumer doing real LMDB verification
  saturates readily, and then the pre-filter's false-positive rate sets the ceiling. The lesson: do
  not measure the cascade with a stubbed consumer.

So the benefit is not fixed — it is `min(GPU rate, consumer rate)`, and `FUSE_16` raises the consumer
rate 256× (matching the FPR ratio), removing it as the bottleneck. How much that helps depends on how
close the consumer already was: abundant threads + warm cache → ~1.2×; a single thread, or cold
storage (the 385 µs cold cost), or several GPUs feeding one consumer → several-fold to an order of
magnitude. On this rig even 8 warm threads left 21 % on the table with `FUSE_8`.

The general lesson from the whole investigation: net throughput is `min(stage rates)`, not the sum of
per-candidate costs. A stage improvement only moves the ceiling if that stage is the one holding it
down — and the GPU pre-filter's job is precisely to keep the single consumer from being that stage.

---

## 8. Device allocation limit — a reported floor, not a hard cap

The filter is a **single** OpenCL allocation. `CL_DEVICE_MAX_MEM_ALLOC_SIZE` is often quoted as its
ceiling, and the two vendors report very different values:

| Device | Total VRAM | Reported max single allocation | Share |
|---|--:|--:|--:|
| RTX 3070 Laptop | 8 191 MB | 2 047 MB | 25 % — the OpenCL spec *minimum* |
| RX 7900 XTX | 24 560 MB | 20 876 MB | 85 % |

**The reported value is the spec's guaranteed minimum, not a hard limit — at least on NVIDIA.**
An earlier version of this document treated the 2 047 MB as a ceiling and concluded the Full DB tier
"needs `FUSE_8`" on the 3070. That was **wrong**: the RTX 3070 (driver 581.83) allocated and probed a
**single 3 099 MiB buffer** — blocked bloom at 26 bits/entry over 1 e9 entries — with no error (§4).
So NVIDIA under-reports the cap by ~4× and honours allocations up to roughly available VRAM; `FUSE_16`
at the 1.377 B tier (3.14 GB) fits comfortably on the 3070's 8 GB, contrary to the old claim.

The honest caveat: the OpenCL spec only *guarantees* allocations up to the reported value; anything
larger is implementation-defined, and a different driver or device may enforce it strictly. So the
robust test is whether the allocation actually succeeds, not the reported number — the upload path
fails loudly rather than silently if it does not. Run `OpenCLInfo` to see the reported value, but
treat it as a conservative floor.

---

## 9. Recommended configuration

**GPU runs — `gpuFilterType = FUSE_16` with `addressLookupBackend = BINARY_FUSE_8`**
→ [`examples/config_Find_GPU_Fuse16Cascade.json`](../examples/config_Find_GPU_Fuse16Cascade.json)

Measured against a real database, `FUSE_16` raises net throughput by **1.21× (8 warm consumer
threads) up to 7.24× (1 thread)** while handing the consumer **256× less work** (§7). The gain grows
as the consumer gets tighter — fewer threads, colder storage, or several GPUs per consumer — and is
never negative, so it is the right default whenever a GPU filter is used and the filter fits in VRAM.
The two stages are different filters, so they compound.

`FUSE_16` fits the Full DB tier (3.14 GB) even on an 8 GB NVIDIA card — the reported 2 047 MB
allocation cap is a spec floor, not a real limit (§8). Keep
[`examples/config_Find_GPU_LowVram.json`](../examples/config_Find_GPU_LowVram.json) (`FUSE_8` on the
device) only as a fallback for genuinely small cards or drivers that enforce the cap strictly; on the
hardware measured here it is not needed.

**CPU-only runs — `BINARY_FUSE_16`.** 14× lower total cost than Fuse-8 against a cold database
(85 ns vs 1 211 ns), and no GPU filter is built, so the second filter costs nothing.

**Rebuild-heavy or heap-constrained — `BLOCKED_BLOOM` at low density** (bpe 9–11). 3× faster to
build in a single streaming pass, with none of the ~29 B/entry peeling arrays. At bpe ≥ 17
`BINARY_FUSE_16` dominates it on both memory and accuracy.

**Never `TRUNCATED_LONG_64`** for this purpose — an order of magnitude slower at seven times the
memory.

---

## 10. Grid tuning is machine-specific — measure it, don't copy it

The filter choice above is portable: false-positive rates are deterministic and the cost model holds
across hardware. The **grid** parameters (`batchSizeInBits`, `keysPerWorkItem`) are not — they are a
property of the specific GPU, and copying another machine's values leaves throughput on the table.
`TuneConfiguration` measures them on yours. Arm tables: [`tune_arms.csv`](measurements/tune_arms.csv)
(RTX 3070 grid + the cascade run) and the full 25-arm RDNA3 sweep in
[`tuner_ryzen9800x3d_gfx1100.csv`](measurements/tuner_ryzen9800x3d_gfx1100.csv). The two machines
disagree on the optimum:

| Machine | Winner | Peak throughput | Note |
|---|---|--:|---|
| RTX 3070 Laptop | `bits=22`, `kpwi=256` | 229–234 M/s | `kpwi=256` is optimal at `bits=22` and the **worst** of all at `bits=18` (5.8× spread) |
| RX 7900 XTX | `bits=22`, `kpwi=64` | 130.2 M/s | `kpwi=256` is past-peak (110.1 M); spread widens to 24.9× (worst arm `19/1` at 5.24 M/s) |

(Absolute rates are not vendor-comparable here — `noInlineHelpers=auto` gave the AMD run its
out-of-lined kernel — but the *location* of the optimum is what transfers, and that is what differs.)

The `batchSizeInBits` agrees but `keysPerWorkItem` does not, and the two parameters interact — the
right `keysPerWorkItem` depends on `batchSizeInBits`, so sweeping one axis alone finds a false
optimum. This is the whole reason the tuner exists rather than a documented constant. Note the
device's own suggested-config heuristic pointed at `bits=21` on the 3070, which the sweep beat with
`bits=22`; treat the heuristic as a starting point, not an answer.
