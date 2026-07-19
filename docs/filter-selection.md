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

## 4. GPU probe (one session, RTX 3070 Laptop, 4 194 304 probes per op)

| Filter | 1e7 | 1e8 | per probe @1e8 |
|---|--:|--:|--:|
| `FUSE8` | 1.784 ± 0.008 | 3.011 ± 0.002 | 0.718 ns |
| `FUSE16` | 2.437 ± 0.001 | 3.088 ± 0.018 | 0.736 ns |
| `BLOCKED_BLOOM` 11/6 | **0.902 ± 0.029** | **1.459 ± 0.017** | **0.348 ns** |

The coalescing advantage is confirmed cross-vendor — 1.6–2.3× on NVIDIA, and on RDNA3 it *grows*
with size, reaching 2.53× at 1e9. The CPU's short-circuit advantage does **not** carry over: a warp
runs in lockstep and waits for its slowest lane, so an early exit saves nothing.

`FUSE16` costs only **+2.6 %** over `FUSE8` at 1e8, against 37 % at 1e7. The gap closes with size
because once both filters read from VRAM the access *count* is unchanged — only the payload per
access doubles.

---

## 5. Build cost (storage-free, first JIT-carrying shot discarded)

| Backend | 1e7 | 1e8 |
|---|--:|--:|
| `BINARY_FUSE_8` | 3 793 ± 762 ms | 43 752 ± 8 796 ms |
| `BINARY_FUSE_16` | 3 798 ± 646 ms | 43 628 ± 7 930 ms |
| `BLOCKED_BLOOM` 11/6 | **669 ± 170 ms** | **14 560 ± 599 ms** |
| `BLOCKED_BLOOM` 17/7 | 980 ± 48 ms | 14 959 ± 107 ms |
| `BLOCKED_BLOOM` 26/9 | 1 215 ± 132 ms | 15 335 ± 384 ms |

**Blocked bloom builds 3.0× faster at 1e8** and streams the bit array in one pass. Binary Fuse peels
through auxiliary arrays at ~29 B/entry — roughly 40 GB of transient heap at the 1.377 B tier,
against 1.89 GB for the finished blocked-bloom filter.

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
the early microbenchmark, and the `TuneConfiguration` command has since measured it against two real
databases in their actual page-cache state:

| Machine | Database | Measured verification cost |
|---|---|--:|
| Ryzen 9800X3D / RX 7900 XTX | (operator's LMDB) | **195.56 µs** |
| Ryzen 5800H / RTX 3070 | 5.5 GB LMDB, cold | **385.65 µs** |

Both land near the **cold** end, i.e. 50–95× my original warm estimate of 4.1 µs — a real scan
against real storage pays far more per false positive than the warm figure suggests, which is
exactly why the tuner reports this term as ESTIMATED unless it measures your database directly. At
385.65 µs a false positive costs ~5 000× a probe. At 1e9 entries, filters at equal footprint:

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

### Measured end-to-end, and what it corrects

Running the full pipeline against the 5.5 GB LMDB on the RTX 3070, winner grid, only the GPU filter
type varied (`TuneConfiguration` with `sweepFilterTypes`):

| GPU filter | Net throughput | Survivors reaching the consumer |
|---|--:|--:|
| `FUSE_8` | 231.4 M/s | **3 615 569 /s** |
| `FUSE_16` | 233.4 M/s | **14 141 /s** |

Two separate facts, and the second corrects an earlier overclaim of mine:

- **Consumer load: 256× lower** — matching the 242× false-positive-rate ratio almost exactly. The
  cascade does what the arithmetic said. This is the measured payoff.
- **Net throughput: unchanged** (231 ≈ 233 M/s, within noise). On this rig the **GPU is the
  bottleneck**, not the consumer: even Fuse-8's 3.6 M survivors/s did not saturate the (default
  8-thread) consumer against a warm-cached database, so the better filter did not raise the ceiling.
  An earlier version of this document claimed a flat "20× throughput" for the cascade; that was the
  cost-per-candidate model mistaken for pipeline throughput, and the measurement refutes it.

So `FUSE_16` on the device is best understood as **free insurance**: identical throughput, 256× less
pressure on the serial verification path. It converts to an actual throughput *win* only when the
consumer saturates — slower or genuinely cold storage (where the 385.65 µs cold cost bites), several
GPUs feeding one consumer, or a heavier lookup backend. The cost model quantifies that regime: at the
measured 385.65 µs, FUSE_8 totals 1 571 ns per candidate against FUSE_16's 87 ns, an 18× gap that
appears the moment verification, not key generation, is the limiting stage.

The lesson is the general one from this whole investigation: net throughput is `min(stage rates)`,
not the sum of per-candidate costs. A stage improvement only moves the ceiling if that stage is the
one holding it down.

---

## 8. Device allocation limit — check it before choosing

The filter is a **single** OpenCL allocation, bounded by `CL_DEVICE_MAX_MEM_ALLOC_SIZE` rather than
by total VRAM. That bound is vendor policy, not hardware:

| Device | Total VRAM | Max single allocation | Share |
|---|--:|--:|--:|
| RTX 3070 Laptop | 8 191 MB | 2 047 MB | 25 % — the OpenCL spec minimum |
| RX 7900 XTX | 24 560 MB | 20 876 MB | 85 % |

At 2.25 B/entry the NVIDIA limit admits ~909 M entries. So the 1.377 B-entry Full DB needs `FUSE_8`
on that card and fits `FUSE_16` comfortably on the AMD one. Run the `OpenCLInfo` command to read
your device's limit before choosing.

---

## 9. Recommended configuration

**GPU runs — `gpuFilterType = FUSE_16` with `addressLookupBackend = BINARY_FUSE_8`**
→ [`examples/config_Find_GPU_Fuse16Cascade.json`](../examples/config_Find_GPU_Fuse16Cascade.json)

Measured net throughput is **unchanged** versus Fuse-8 on the device (231 vs 233 M/s, within noise —
the isolated probe benchmark's +2.6 % disappears once key generation, not the probe, sets the rate),
while the work reaching the consumer drops **256×** (14 141/s against 3 615 569/s). It is free
insurance: same speed, a serial verification path that stays far from saturation, and a real
throughput win the moment storage is the bottleneck. The two stages are different filters, so they
compound (§7).

Where the device allocation limit cannot hold Fuse-16, swap them
→ [`examples/config_Find_GPU_LowVram.json`](../examples/config_Find_GPU_LowVram.json). Fuse-8 fits
every limit measured so far, including the Full DB tier on an 8 GB NVIDIA card.

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
