# Performance & Tuning — BitcoinAddressFinder GPU Key Generation

This is the deep, technical reference for getting maximum throughput out of the secp256k1 OpenCL
key-generation kernel: how the kernel works, which parameters matter, how to choose them per device,
the full optimization history with measured results, and how to benchmark correctly.

For the high-level, user-facing overview of the same knobs (`batchSizeInBits`, `keysPerWorkItem`,
the address-lookup backends), see the **README**. This document is for choosing *optimal* settings
and understanding *why* — it goes well beyond the defaults.

---

## 1. TL;DR — how to go fast

1. **Use a discrete GPU via OpenCL** for key generation; the CPU consumer checks addresses in
   parallel.
2. **Raise `keysPerWorkItem`.** The default is `1`, which is the *slowest* setting (a full `k·G`
   scalar multiplication per key). The optimum is **device-dependent**; on an NVIDIA RTX 3070 Laptop
   it is `128` at `batchSizeInBits=20` — ≈**20× faster than `1`** in compact mode (≈138 M candidate
   keys/s on the GPU-filter fast path; ≈43 M in full-transfer mode). Sweep it on your hardware (§4).
3. **Match `batchSizeInBits` to the device** (e.g. `18` for a typical GPU, `20`–`21` for a high-end
   one) and ensure `batchSizeInBits` is divisible by `keysPerWorkItem`.
4. **Benchmark with `GridSizeSweepBenchmark`** and read §6 first — laptop GPUs throttle, and naive
   A/B comparisons are misleading.

The kernel implements the same two techniques the fastest open-source key searchers (BitCrack,
VanitySearch) use: a **fixed-base comb** for the one-time `k·G`, and an **affine batched-addition
walk** for the consecutive keys. The optimization history that got here is in §5.

---

## 2. How GPU key generation works

`__kernel generateKeysKernel_grid(...)` — `src/main/resources/inc_ecc_secp256k1custom.cl`.

A Find-mode batch covers `2^batchSizeInBits` consecutive private keys. The CPU aligns a candidate
down to a `2^batchSizeInBits` boundary (`secretBase`) and submits it once; the kernel launches
`2^batchSizeInBits / keysPerWorkItem` work-items. Each work-item produces `K = keysPerWorkItem`
consecutive keys for scalars `secretBase | (g·K + m)`, `m = 0 … K-1`. The combine is an **OR** (valid
because `secretBase`'s low bits are cleared).

Per work-item:

1. **Anchor `P₀ = k₀·G`** — one fixed-base **comb** scalar multiplication (§5, Stage 2). `G` is a
   fixed point, so `k·G` is read from a precomputed table with ~0 doublings.
2. **Walk (keys 1 … K-1)** — every key is `Pₘ = P₀ + m·G`, computed directly in **affine** from the
   same anchor `P₀` (§5, Stage 1). The fixed multiples `m·G` come from a host-uploaded `i·G` table,
   and a single **Montgomery simultaneous inversion** covers a whole `KEYS_BATCH_INV`-sized
   sub-batch.
3. **Per key** — two hash160 chains (SHA-256 → RIPEMD-160 of the uncompressed and the compressed SEC
   public key), then a 108-byte output entry. In compact mode a GPU Binary Fuse 8 filter decides
   which entries are emitted (claimed with `atomic_add`, OpenCL 2.0+).

**Field layer** (`src/main/resources/copyfromhashcat/inc_ecc_secp256k1.cl`): 8×`u32` limbs;
schoolbook `mul_mod` + fast reduction for `p = 2²⁵⁶ − 2³² − 977`; `add_mod`; `sub_mod`; `inv_mod`
defaults to the **safegcd** path (§5, Stage 4 — a fixed-iteration libsecp256k1 `modinv32` port), with
the original binary extended-GCD (~256 data-dependent iterations, guards `a == 0`) kept behind
`-D USE_LEGACY_BINARY_GCD_INV_MOD` / `useSafeGcdInverse=false`.

### Why this is the hot path

Key generation dominates GPU runtime; address hashing + LMDB lookup run on the CPU consumer in
parallel. Within the kernel, **EC point arithmetic is the largest cost, but the two hash160 chains
are not far behind**: the stage-attribution suite (§6) measures **EC ≈ 57%, hashing ≈ 43%** on the
current kernel (RTX 3070, `keysPerWorkItem=128`). (An earlier back-of-envelope estimate put hashing
at ~30%; the direct measurement corrected it upward — re-run the suite on your device, the split is
device-dependent.)

---

## 3. The parameters that matter

### `keysPerWorkItem` (the big lever)

How many consecutive keys each work-item generates. `1` ⇒ one full `k·G` per key (slow). Higher ⇒
the expensive anchor `P₀ = k₀·G` is amortized over `K-1` cheap affine-addition steps, until too few
work-items remain to keep the GPU's compute units busy — so there is a **per-device sweet spot**.

- Must be a power of two; `batchSizeInBits` must be divisible by it.
- Default `1` is **not** optimal for scanning.
- On an RTX 3070 Laptop the optimum is `128` at `batchSizeInBits=20` (§4 table; it rose from 64 to 128
  once Stage 2 made `P₀` cheap). Weaker/older GPUs peak lower; sweep to find it.
- Config field: `producerOpenCL.keysPerWorkItem` (`CProducerOpenCL.java`).

### `batchSizeInBits`

Log₂ of the per-launch work size; each launch produces `2^batchSizeInBits` candidate keys.

| `batchSizeInBits` | Keys per batch | Use case |
|---|---:|---|
| `0` | 1 | sequential / secrets-file mode (no batching) |
| `14` | 16,384 | per-CPU-producer batch |
| `18` | 262,144 | typical OpenCL device |
| `20`–`21` | 1M–2M | high-end OpenCL device |

Upper bound: `PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY` (so per-batch result arrays stay within
Java's 32-bit array-length limit). Larger batches improve GPU occupancy and amortize launch overhead,
but cost more VRAM for the result buffer and more host readback per launch.

### `KEYS_BATCH_INV` (compile-time)

Sub-batch size for Montgomery's simultaneous inversion in the affine walk: `KEYS_BATCH_INV` points
share **one** `inv_mod` (plus a few multiplies per point) instead of one inverse each. It is a
`#define` in `inc_ecc_secp256k1custom.cl` (default **`16`**). Larger values amortize the inverse over
more keys but use more private scratch. It is **not** a runtime argument — it sizes fixed-length
private arrays, so changing it means editing the kernel (or prepending a `#define` to the program
source before `clBuildProgram`) and re-running. Re-sweep `keysPerWorkItem` after changing it.

**Measured (RTX 3070, kpwi=128 compact, order-controlled).** Larger batch is genuinely faster — the
extra inverse amortization beats the extra spill (`kernelMaxWorkGroupSize` stays 256 regardless, so
occupancy is *not* the limiter here; only spill grows):

| `KEYS_BATCH_INV` | 4 | **8 (old default)** | **16 (default)** | 32 | 64 |
|---|--:|--:|--:|--:|--:|
| ops/s (kpwi=128) | ~136 | ~147 | ~155 | ~161 | ~165 |
| private-mem spill (bytes/work-item) | 384 | 640 | 1152 | 2176 | 4224 |

The default was raised **8 → 16** (≈ +5%, modest spill, and it matches the example configs' `kpwi=16`).
`32`/`64` add a further ≈ +4% / +6% **only when `keysPerWorkItem` is large** — they are worth setting
for a high-kpwi deployment but waste scratch when kpwi is small (the arrays are always sized to
`KEYS_BATCH_INV`), so they are left as an opt-in tune rather than the default.

### Address-lookup backend (`addressLookupBackend`) and the GPU filter

Independent of the EC knobs above but performance-relevant: the `LMDB_ONLY` default keeps LMDB open
and exact; the in-RAM filters (`BLOOM`, `HASHSET`, `TRUNCATED_LONG_64`, `BINARY_FUSE_8/16`) trade RAM
for lookup speed; `producerOpenCL.enableGpuFilter` runs a Binary Fuse 8 pre-filter on the GPU so only
candidate hits are transferred over PCIe. See the README for the user-facing comparison; the GPU
filter's measured transfer saving (~1.28× at grid 19 on an RTX 3070) is benchmarked by
`GpuFuse8FilterBenchmark`.

---

## 4. Benchmarked tuning — `keysPerWorkItem` sweep

NVIDIA RTX 3070 Laptop GPU, OpenCL 3.0 CUDA, `batchSizeInBits = 20`, **current kernel (all stages,
safegcd default)**. Fresh single-session re-sweep after Stage 4; candidates/s = JMH ops/s ×
`2^batchSizeInBits`. Two modes shown — full transfer (`GridSizeSweepBenchmark`, every result read
back) and compact (`GpuFuse8FilterBenchmark -p gpuFilter=true`, only filter hits read back, i.e. the
real GPU-filter fast path):

| `keysPerWorkItem` | 1 | 8 | 16 | 32 | 64 | **128** | 256 |
|---|--:|--:|--:|--:|--:|--:|--:|
| M keys/s — full transfer | 6.4 | 26 | 30 | 34 | 41 | **43** | 36 |
| M keys/s — compact (fast path) | 7.0 | 47 | 69 | 96 | 124 | **138** | 93 |
| vs. `=1` (compact) | 1.0× | 6.7× | 9.8× | 13.7× | 17.7× | **19.8× (peak)** | 13.3× |

Notes:

- The default `keysPerWorkItem = 1` pays a full scalar multiplication per key and is far from
  optimal — up to ~20× off the peak in compact mode.
- **The sweet spot is `keysPerWorkItem = 128` (at `batchSizeInBits = 20`) and did not move after
  Stage 4** — confirmed independently in *both* modes (rise to 128, fall at 256). safegcd made every
  point faster but the peak is set by the work-item count vs. the GPU's compute units, not by the
  inverse cost.
- **Compact ≫ full transfer** because the fast path skips the ~113 MB readback; this is why the
  numbers here are much higher than the pre-Stage-3/4 editions of this table (those were full transfer
  in an unknown thermal window — per §6, treat absolute numbers across sessions as non-comparable; the
  robust, reproducible result is the *shape and the peak location*).
- Beyond the sweet spot, throughput drops because too few work-items remain to fill all compute units
  (`2^20 / 128 = 8192` work-items still fills this 40-SM GPU; `2^20 / 256 = 4096` under-fills it).
- The sweet spot is **device-dependent** — sweep on your own hardware with the §6 recipe. The peak
  also depends on `batchSizeInBits` via the work-item count `2^batchSizeInBits / keysPerWorkItem` — at
  the smaller `batchSizeInBits = 18` the work-item-count analog of this peak is `keysPerWorkItem = 32`
  (also 8192 work-items), and the example configs use `16` as a safe cross-device default. The curve
  is flatter than pre-comb, so even a moderate value (16–32) captures most of the gain on a wide range
  of GPUs.

Use `{"command":"OpenCLInfo"}` to confirm a device is present and pick `platformIndex` /
`deviceIndex` before benchmarking.

---

## 5. Optimization history (measured)

The kernel was optimized in stages; each stage is independently shippable, gated **byte-for-byte**
against the bitcoinj reference *before* any throughput claim (§7), and benchmarked with the
thermal-aware methodology in §6. All throughput in `M keys/s` (= JMH ops/s × `2^20 / 1e6`) on the
RTX 3070 Laptop, `GridSizeSweepBenchmark`, `batchSizeInBits = 20`.

### Reference baseline (original wNAF + Jacobian kernel)

| `keysPerWorkItem` | 1 | 2 | 4 | 8 | 16 | 32 | 64 |
|---|--:|--:|--:|--:|--:|--:|--:|
| M keys/s | 2.51 | 4.51 | 6.82 | 10.92 | 14.17 | 16.00 | **18.54** |

The pre-optimization design computed `P₀` with a **wNAF** (window-4, `±1,3,5,7·G` table,
~256 doublings ≈ 2600 field-muls) and walked consecutive keys with a **Jacobian** mixed addition
(~11 `mul_mod` each) plus batched Montgomery inversion to convert back to affine. The wNAF `P₀`
dominated EC cost once the walk amortized it.

### Stage 0 — kernel build flags + `#pragma unroll` (no measurable gain; kept as hygiene)

`clBuildProgram` passes `-cl-std=CL2.0 -cl-mad-enable` (constant `CL_BUILD_OPTIONS` in
`OpenCLContext.java`), and `#pragma unroll` was added to the fixed 8-limb `mul_mod` / fast-reduction
loops in `copyfromhashcat/inc_ecc_secp256k1.cl`.

Parity: ✅ 5/5 byte-identical. Throughput: **no reliable gain** — every arm's JMH error bar overlaps
the baseline (e.g. kpwi=64: 18.4 ± 1.4 vs 17.7 ± 1.8 ops/s). Expected for an integer-only kernel:
`-cl-mad-enable` affects only floating-point math, the NVIDIA PTX compiler already unrolls these
small fixed-trip loops, and `-cl-std=CL2.0` only pins the OpenCL-2.0 semantics compact mode's
`atomic_add` already required. Kept because harmless, verified byte-identical, and it makes the
2.0 requirement explicit — but it is setup/hygiene, not a speed-up.

### Stage 1 — single-anchor affine batched-addition walk (+~10% at the sweet spot)

Replaces the per-key **Jacobian** walk with a **single-anchor affine** walk. Every key is
`Pₘ = P₀ + m·G`, computed directly in affine from the *same* anchor `P₀`, reading the fixed `m·G`
from a host-uploaded `i·G` table (`iG_table`, built once in `OpenCLContext.init()`). Anchoring all
points at one `P₀` makes the slope denominators `dx_m = x_{mG} − x₀` mutually independent, so a single
Montgomery simultaneous inversion still covers a sub-batch — but each key now costs ~6 `mul_mod` +
~6 `sub_mod` (the affine slope formula) instead of an ~11-multiply Jacobian add plus a per-point
`X/Z²,Y/Z³` conversion. No Jacobian state, less private scratch.

Correctness: ✅ byte-identical — `ProbeAddressesOpenCLTest` 5/5, full `@OpenCLTest` gate 77/0-fail,
plus a pure-Java `OpenCLContextIGTableTest` that pins the `i·G` table byte layout without a GPU.

Fair back-to-back A/B (baseline vs Stage 1), M keys/s:

| `keysPerWorkItem` | 1 | 4 | 16 | 32 | **64 (sweet spot)** |
|---|--:|--:|--:|--:|--:|
| Baseline | 2.47 | 7.54 | 13.41 | 16.74 | 18.07 |
| Stage 1 | 1.96 | 6.16 | 13.32 | 16.08 | **19.83** |
| Δ | −21% | −18% | ~0% | ~−4% | **+9.8%** |

The walk rewrite only pays off where walk steps dominate the work-item: at kpwi=64, 63 of every 64
keys are cheap affine steps, so Stage 1 is **+9.8%** (error bars non-overlapping). At low
`keysPerWorkItem` there is little walk to speed up and the fixed per-sub-batch `inv_mod` + anchor
(`m=0`) overhead makes it slower — but production scans at the sweet spot.

### Stage 2 — fixed-base comb for the `P₀` anchor (+~11% at the sweet spot, up to 2× at low `keysPerWorkItem`)

Replaces the **wNAF** scalar multiplication for `P₀ = k₀·G` with a **fixed-base comb**. The scalar is
split into 64 four-bit windows, `k·G = Σ_pos comb_table[pos][digit_pos(k)]` (~64 mixed point-adds,
~0 doublings, vs the wNAF's ~256 doublings ≈ 2600 field-muls → ~700). The table
(`64 positions × 16 digits = 1024 affine points ≈ 64 KB`) is built once in `OpenCLContext.init()`
from the same bitcoinj curve the CPU reference uses (scalars reduced mod the group order `n`),
uploaded as a read-only buffer, and consumed by `point_mul_xy_comb` in the kernel. The Stage 1
affine walk is unchanged.

Correctness: ✅ byte-identical — full `@OpenCLTest` gate 86/0-fail plus a pure-Java
`OpenCLContextCombTableTest` that checks every table entry **and** reconstructs `k·G` by summing the
window points for 32 random scalars (validating the comb decomposition without a GPU).

Stage 1 → Stage 2, M keys/s (¹ = matched high-precision pair, 6 samples, same thermal window, error
bars disjoint at kpwi=64; other columns are the fair 3-sample sweep):

| `keysPerWorkItem` | 1 | 8 | 16 | 32 | **64 (sweet spot)** |
|---|--:|--:|--:|--:|--:|
| Stage 1 | 1.89 | 9.26 | 12.63¹ | 16.34¹ | 17.37¹ |
| Stage 2 | 4.01 | 15.23 | 16.70¹ | 18.06¹ | **19.25¹** |
| Δ | **+112%** | **+64%** | +32% | +10.5% | **+10.8%** |

The comb's win is largest where `P₀` is **not** amortized: at kpwi=1 (a fresh `k·G` per key) it is
~**2×**, +64% at kpwi=8. At the kpwi=64 sweet spot `P₀` is only 1/64 of the work — already cheap
after Stage 1 — so the remaining ceiling is the affine walk + the two hash160 chains, and the comb
still adds a clean **+10.8%**. The optimum stays at the high end (≥64) but the curve is far flatter.

### Stage 2b — signed-digit (±P) comb halving (table −50%; throughput within measurement noise)

A refinement of the Stage 2 comb: recode each 4-bit window into a **signed** digit `b ∈ {−8..+7}`
(carry-propagated low→high) instead of an unsigned `0..15`. On this curve `−P = (x, p − y)` is free,
so a negative digit reuses the magnitude-`|b|` table entry with `y` negated. The table therefore
stores only **magnitudes 1..8 per position (8 points)** instead of digits 0..15 (16) — **half the
table, 64 KB → ~32.5 KB**. A signed recode of a 256-bit scalar can carry out of the top window, so
the comb runs to **65 positions** (the extra position only ever uses magnitude 1 = `2²⁵⁶·G`).

Correctness: ✅ byte-identical — `OpenCLPrecomputeKernelTest` validates every `(pos, mag)` entry
incl. the new carry-out position 64, and `ProbeAddressesOpenCLTest` (43/0-fail) proves end-to-end key
derivation is unchanged.

**Throughput: no measurable change on the RTX 3070 Laptop — and that is the honest finding, not a
hedge.** The comb computes only the `P₀` anchor (once per work-item), so at the high-`keysPerWorkItem`
operating point it is amortized to a negligible fraction and any effect is expected to be sub-1%. The
attempt to measure it ran straight into the thermal-noise wall (§6): two back-to-back runs of the
**identical unsigned baseline** scored **73.1 then 109.9 ops/s at kpwi=128** (a +50% swing) and
**10.93 then 8.95 ops/s at kpwi=1** (−18%). The signed-comb numbers (90.2 / 9.22 ops/s) fall *inside*
that baseline's own run-to-run envelope, i.e. the change is statistically indistinguishable from
noise on this machine. It was kept regardless: correctness is proven, it is **never a large loss**,
and the **halved table is a concrete, throughput-independent win** (less VRAM, less memory traffic per
`point_add`, and the freed budget could fund a denser comb later). The kernel-side cost is balanced —
the same ~60 `point_add`s as before, plus ~30 cheap `sub_mod` negations and one extra position, against
reading half as much table.

### Cumulative result

Stage 1 (+9.8%) × Stage 2 (+10.8%) ≈ **~+21% at the sweet spot** over the original wNAF + Jacobian
kernel, and a **multiple** of that at low `keysPerWorkItem`. This is the BitCrack/VanitySearch design:
fixed-base table for `k·G` + affine batched-addition walk. Stage 2b halves the comb table at
throughput parity (within noise); **Stage 4 (safegcd `inv_mod`) then adds ≈ +45% kernel throughput**
by removing warp divergence in the modular inverse; Stage 3 separately adds host-side buffer reuse
(+~18% end-to-end in compact mode). The largest single kernel-side win of the whole effort turned out
to be Stage 4 — the modular inverse, not the point arithmetic.

### Stage 3 — result-buffer reuse (host-side I/O; +~18% in compact mode, no change in full transfer)

Stages 0–2 are all *kernel* (compute) work. Stage 3 attacks the **host overhead per launch**:
end-to-end profiling showed compact mode reaching only ~36 M keys/s against a ~118 M keys/s raw
kernel, i.e. ~20 ms/launch spent outside the kernel — dominated by allocating and freeing the
**full per-batch result buffers** (the GPU `cl_mem` plus a >100 MB direct host `ByteBuffer`) on
*every* launch. Two steps, both **pure reuse — buffers stay full size, no right-sizing/overflow
handling** (ranges with many consecutive hits must never lose entries):

- **Step 1 — reuse the GPU output `cl_mem`.** Allocated once at the fixed batch size in the
  `OpenClTask` constructor, reused every launch (it is touched strictly synchronously — kernel write
  + readback, each `clFinish`-fenced, on the single producer thread). Measured **no** end-to-end
  change → the device-buffer alloc was *not* the bottleneck.
- **Step 2 — pool the host readback `ByteBuffer`.** This is the win. Each launch's host buffer is read
  **asynchronously** by the result-reader pool, so it cannot be a single shared buffer; instead
  `OpenClTask` keeps a thread-safe pool, `executeKernel` checks one out, and `OpenCLGridResult`
  (now `AutoCloseable`) returns it on `close()` after the reader consumes it. Up to
  `maxResultReaderThreads` buffers are in flight (the same peak as before) — isolation is preserved,
  only the `allocateDirect` + zeroing is eliminated. A caller that never closes simply GCs its buffer
  (no reuse, no leak), so reuse is an optimisation, not a correctness requirement.

Matched back-to-back A/B on the RTX 3070 Laptop (baseline = commit before the pool; `batchSizeInBits=19`
→ 524 288 candidates/launch, `keysPerWorkItem=128`, profiling off, `-f 1 -wi 1 -w 20 -i 3 -r 60`):

| mode | baseline (no pool) | with host-buffer pool | Δ |
|---|--:|--:|--:|
| **compact** (`gpuFilter=true`) | 60.57 ± 1.61 ops/s (≈31.8 M keys/s) | **71.77 ± 0.68 ops/s (≈37.6 M keys/s)** | **+18.5%** |
| full transfer (`gpuFilter=false`) | 9.71 ± 1.04 ops/s (≈5.09 M keys/s) | 9.78 ± 0.68 ops/s (≈5.13 M keys/s) | +0.8% (within noise) |

The win lands entirely in **compact mode**: there only the hits are transferred, so readback is tiny
and the fixed per-launch host allocation was a large fraction of wall-clock — removing it is +18.5%
(error bars disjoint, robust). **Full transfer** is PCIe-bound on the ~113 MB readback itself, which
dwarfs the allocation, so the pool neither helps nor hurts (error bars overlap). Crucially it is
**never slower**, so per the on/off-flag criterion ("flag only if not always faster") **no flag was
added** — reuse is unconditional.

### Stage 4 — safegcd modular inverse (≈ +45% kernel throughput; now the default)

Replaces the modular inverse `inv_mod` (used by every Jacobian→affine conversion: the comb's final
`inv_mod`, the affine walk's batched inverse, `point_to_affine`) with a faithful port of
libsecp256k1's **constant-time `modinv32`** (Bernstein–Yang "safegcd" divsteps; `inv_mod_safegcd` in
`inc_ecc_secp256k1.cl`, 9 signed-30-bit limbs so every product fits a 64-bit accumulator).

**Why it helps far more than expected.** The old `inv_mod` is a *binary* extended GCD whose iteration
count and inner branches **depend on the input value**. Under SIMT, the 32 lanes of a warp run in
lock-step, so a warp pays for its *slowest* lane every step — heavy **warp divergence**. safegcd does
a **fixed 20×30 = 600 divsteps for every input**, branch-uniform, so a warp finishes together. Even
though the inverse is only ~1 per 8 keys (batched) at high `keysPerWorkItem`, removing that divergence
moved the whole-kernel throughput a lot.

Reproduce the A/B in one JMH run (safegcd is a benchmark `@Param`, so no rebuild between arms):

```bash
# (after the classpath step in §6) — sweeps the inverse at the operating point
java <--add-opens flags from §6> -cp "target/test-classes;target/classes;$(cat target/cp-test.txt)" \
     org.openjdk.jmh.Main GpuFuse8FilterBenchmark \
     -p gpuFilter=true -p batchSizeInBits=19 -p keysPerWorkItem=128 \
     -p useSafeGcdInverse=true,false -f 1 -wi 1 -w 20 -i 3 -r 40
```

Because JMH iterates the params in order, prefer running each arm a couple of times (or interleaving)
and reading the relative delta per §6 — a single ON/OFF pair is thermally confounded. The numbers
below came from an explicit **ON–OFF–ON** sequence to defeat the thermal-ordering trap (compact mode,
`batchSizeInBits=19`, `-f 1 -wi 1 -w 20 -i 3 -r 40`):

| run (in order) | kpwi=1 | kpwi=128 |
|---|--:|--:|
| safegcd ON (1st) | 13.81 ops/s | 156.79 ops/s |
| binary-GCD OFF (2nd) | 10.88 ops/s | 108.08 ops/s |
| safegcd ON (3rd) | 15.22 ops/s | 155.39 ops/s |

The two ON runs bracket OFF and are **flat** (156.8 then 155.4 — the *last* run is not faster, so this
is not warmup drift), while OFF sits clearly below both. The effect is therefore real, not ordering:
**≈ +44% at kpwi=128** and **≈ +27–40% at kpwi=1**. This is the rare case where the measurement
*beat* the thermal noise floor because the effect itself is large.

safegcd is now the **default** `inv_mod` (per "if always faster, no flag"). The binary GCD is kept
behind the kernel define `-D USE_LEGACY_BINARY_GCD_INV_MOD` for A/B and as a fallback for any device
whose signed right-shift is not arithmetic (safegcd, like the reference, assumes sign-extending `>>`;
NVIDIA and pocl both comply). The define is exposed as a runtime config flag,
`CProducerOpenCL.useSafeGcdInverse` (default `true`); setting it `false` makes `OpenCLContext`
append the legacy define to the kernel build options — so the inverse can be switched per run from
the JSON config without editing code. Correctness is gated two ways: `OpenCLPrecomputeKernelTest`'s `test_inv_mod_safegcd`
cross-checks safegcd vs. the binary GCD **and** `x·x⁻¹ ≡ 1 (mod p)` over 4096 random inputs, and the
full `ProbeAddressesOpenCLTest` (43/0-fail) derives byte-identical keys with safegcd as the live
inverse.

#### Isolated inverse microbenchmark (256-bit vs 160-bit operands)

The whole-kernel +45% mixes the inverse with everything else. `InvModBenchmark` isolates just
`inv_mod` (`bench_inv_mod` kernel: each work-item does 256 inverses over a 2¹⁸ grid, so warp
divergence is realistic), at two operand widths. One op = `2¹⁸ × 256 ≈ 67 M` inverses:

| operand width | safegcd | binary GCD | safegcd advantage |
|---|--:|--:|--:|
| **256-bit** (production) | 3.82 ops/s ≈ **256 M inv/s** | 0.40 ops/s ≈ 27 M inv/s | **9.5×** |
| 160-bit | 3.79 ops/s ≈ 254 M inv/s | 0.56 ops/s ≈ 37 M inv/s | **6.8×** |

Reading the table:

- **safegcd is flat across width** (3.82 vs 3.79) — it does a fixed 600 divsteps regardless of the
  operand, so its cost does not depend on the input. The binary GCD is **input-dependent**: it is
  ~38% faster at 160-bit than 256-bit (fewer bits to shift out) — which is exactly what makes it
  diverge across warp lanes.
- **safegcd wins at both widths** — 9.5× at 256-bit, still 6.8× at 160-bit. There is no operand size
  in range where the legacy inverse is competitive on this GPU.
- **256-bit is the production case.** `inv_mod` is only ever applied to field coordinates (X/Y/Z mod
  `p`), which are pseudo-random in `[0, p)` — i.e. full ≈256-bit — *no matter how small the
  private-key range being scanned is* (even a 1-bit private key yields a 256-bit public-key
  coordinate). So scanning a "160-bit range" does **not** put the inverse in the 160-bit column; the
  inverse always runs the 256-bit workload, where safegcd is 9.5× ahead in isolation (and that
  dilutes to the +45% whole-kernel figure because the inverse is ~1-per-8-keys of total work).

#### "Constant-time" here means *fast*, not slow

A note on the surprise (the original prediction was that an amortized ~1-inverse-per-8-keys change
would be lost in the noise — instead it was the biggest kernel win): the port is libsecp256k1's
**constant-time** `modinv32`, but it was chosen for **speed, not side-channel resistance** (this is a
key-search tool, not a wallet — there is no secret to leak). On a CPU the *variable-time* safegcd
(`modinv32_var`, with `ctz`-based jumps) is faster; on a **SIMT GPU the opposite holds** — any
data-dependent branching or variable trip-count serialises a whole 32-lane warp to its slowest lane.
The binary GCD's input-dependence is precisely why it is ~7–10× slower above. So "constant-time"
(branch-uniform, fixed trip-count) *is* the fast choice on the GPU; a variable-time inverse would
re-introduce the divergence we just removed and is expected to be slower here, not faster.

---

## 6. Benchmarking methodology (read before trusting any number)

### Thermal throttling is the #1 source of bogus comparisons

Laptop (and some desktop) GPUs throttle under sustained load. On the RTX 3070 Laptop the **same**
kernel measured **16.8 ops/s hot vs 18.9 ops/s cool** at kpwi=64 — an ~11–15% swing that **swamps**
the per-stage deltas being measured. Tight within-run JMH error bars do **not** capture this
between-run drift.

**Only a matched comparison is trustworthy:** measure baseline and candidate **back-to-back in the
same thermal window** (ideally consecutive runs, both with warmup), and compare the *relative* delta.
Absolute numbers from different sessions are not comparable. Large effects (the >50%
low-`keysPerWorkItem` gains) survive thermal noise; small ones (the ~10% operating-point gains) need
the matched-pair discipline and enough samples for disjoint error bars.

### JMH harness

`GridSizeSweepBenchmark` (`src/test/java/.../benchmark/`) drives `OpenCLContext.createKeys(...)`
inside the timed region. Kernel compilation (the one-time cost) runs in `@Setup`, outside timing.
For GPU benchmarks prefer **one long measurement iteration** over many short samples to reach steady
clocks; the staged A/Bs above used `-f 1 -wi 1 -w 20 -i 3 -r 20` (sweeps) and `-i 6` (high-precision
operating-point confirms).

### Running it locally

The README documents `mvn test-compile exec:java -Dexec.args="GridSizeSweepBenchmark …"`. **On
Windows that exec form was observed to fail** — the JMH JVM forks cannot find
`org.openjdk.jmh.runner.ForkedMain` (the `exec-maven-plugin` runs JMH in-process and the fork does
not inherit its classpath). The reliable recipe is to launch JMH directly so the fork inherits a real
`-cp`:

```bash
# 1. materialise the full test-scope classpath (includes jmh-core)
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp-test.txt -DincludeScope=test

# 2. run JMH directly; the --add-opens set must match pom.xml <argLine> (lmdbjava reflects into
#    sun.nio.ch). Use ';' as the classpath separator on Windows, ':' on POSIX.
java --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-opens=java.base/java.io=ALL-UNNAMED \
     --add-opens=java.base/java.nio=ALL-UNNAMED \
     --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
     --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
     -cp "target/test-classes;target/classes;$(cat target/cp-test.txt)" \
     org.openjdk.jmh.Main GridSizeSweepBenchmark \
     -p batchSizeInBits=20 -p keysPerWorkItem=1,2,4,8,16,32,64 -f 1 -wi 1 -w 20 -i 3 -r 20
```

Other benchmarks: `GpuFuse8FilterBenchmark` (filter/transfer path; `-p useSafeGcdInverse=true,false`
for the Stage 4 whole-kernel A/B; `-p profiling=true` to split device kernel vs readback nanos) and
`InvModBenchmark` (isolates just `inv_mod` over a full grid; `-p useSafeGcdInverse=true,false
-p inputBits=256,160` for the Stage 4 isolated/width A/B). GPU benchmarks self-skip when no
OpenCL 2.0+ device is present.

### Stage attribution — where kernel time goes (permanent, re-runnable)

The per-key kernel pipeline is **EC point generation → uncompressed hash160 → compressed hash160 →
filter/output**. To attribute time across these *without* per-instruction profiling, the kernel has
compile-time stage switches (`CProducerOpenCL.kernelProfileStage`, mapped to `-D PROFILE_*` defines
in `OpenCLContext.buildOptions`) that short-circuit the hashing. They are exposed as a
`GpuFuse8FilterBenchmark` `@Param`, so the full attribution is **one JMH run** — no code to write,
just run the suite and diff:

```bash
# (after the classpath step above) — compact mode keeps it compute-bound
java <--add-opens flags> -cp "target/test-classes;target/classes;$(cat target/cp-test.txt)" \
     org.openjdk.jmh.Main GpuFuse8FilterBenchmark \
     -p gpuFilter=true -p batchSizeInBits=20 -p keysPerWorkItem=128 \
     -p kernelProfileStage=FULL,ONE_HASH160,NO_HASH160 -f 1 -wi 1 -w 20 -i 3 -r 20
```

The three modes (each rebuilds the kernel in `@Setup`, outside timing):

| `kernelProfileStage` | kernel does | define |
|---|---|---|
| `NO_HASH160` | EC point generation only (hash160 slots filled from X) | `-D PROFILE_SKIP_HASH160` |
| `ONE_HASH160` | EC + one hash160 chain (compressed reuses uncompressed) | `-D PROFILE_SKIP_SECOND_HASH160` |
| `FULL` (default) | EC + both hash160 chains (the real kernel) | *(none)* |

**Interpreting it** — throughput is inverse time, so convert each mode to a per-op time `t = 1/ops`
(compact mode, so transfer is negligible and `t` is ~pure compute):

- EC arithmetic = `t(NO_HASH160)`
- one hash160 chain = `t(ONE_HASH160) − t(NO_HASH160)`
- the second hash160 chain = `t(FULL) − t(ONE_HASH160)`
- as a share of the kernel: divide each by `t(FULL)`.

**Caveats.** The non-`FULL` modes emit **incorrect** hash160s (they skip the real hashing) — this is
a *timing* harness, never a production mode; correctness lives in §7. Run the modes back-to-back and
read the relative split per the thermal rule above. `OpenCLContextTest.kernelProfileStage_buildsAndRuns`
gates that all three modes compile and launch, so the suite never silently rots.

**Measured (RTX 3070 Laptop, compact, `batchSizeInBits=20`, `keysPerWorkItem=128`, one back-to-back
sweep so the ratios are robust):**

| `kernelProfileStage` | ops/s | isolates |
|---|--:|---|
| `NO_HASH160` | 250.3 | EC point generation |
| `ONE_HASH160` | 177.5 | EC + one hash160 chain |
| `FULL` | 143.4 | EC + both hash160 chains |

Per-op-time attribution of the **full kernel**:

| stage | share |
|---|--:|
| EC point generation | **57%** |
| hash160 chain #1 (uncompressed) | 23.5% |
| hash160 chain #2 (compressed) | 19.2% |
| **both hash160 chains** | **43%** |

This **refines the old §2 estimate** ("EC ≈ 60–75%, hashing ≈ 30%"): after Stage 4 the split is closer
to **EC 57% / hashing 43%** — hashing is a larger slice than previously assumed, and the two chains
are comparable (the uncompressed SEC is 2 SHA-256 blocks vs 1 for compressed, but both share a
RIPEMD-160, so the gap is only ~4 pts). Direct consequences for what to optimize next:

- **Both hash160 chains (uncompressed and compressed) are mandatory and must always be computed** —
  dropping either is explicitly out of scope (the tool must find both legacy/uncompressed and
  compressed addresses). The 43% hashing cost is therefore a target for *faster* hashing
  (e.g. SHA-256/RIPEMD-160 micro-optimisation, sharing work between the two chains), **not** for
  skipping a chain. The `NO_HASH160`/`ONE_HASH160` modes above are **diagnostic only** — they exist
  to measure the split, never to run in production.
- **EC is ~57%** and is dominated by the field multiply (carry/add-bound, per the `sqr_mod` result in
  §8) ⇒ the EC lever is a **reduced-radix field** (shorter carry chains), not fewer multiplies.

### Occupancy / register pressure

`OpenCLContext` logs the built kernel's standard `clGetKernelWorkGroupInfo` stats once at init (see
`logKernelResourceUsage`) — grep the init log for `Kernel resource usage:`. On the RTX 3070:

```
Kernel resource usage: kernelMaxWorkGroupSize=256 workGroupSizeMultiple=32 privateMemBytes=640 localMemBytes=…
```

Reading it: the device's `CL_DEVICE_MAX_WORK_GROUP_SIZE` is 1024, but the kernel's max work-group
size is **256** — the kernel is **resource- (register-) limited**. On Ampere (65 536 registers per
SM-block) a 256-thread cap implies ≈ **255 registers/thread (the hardware ceiling)**, with
`privateMemBytes > 0` indicating **register spilling** to device-local memory. That puts achieved
occupancy at roughly **8 of 48 warps/SM (~17 %)** — the kernel is **register-bound, not
memory-bandwidth-bound**. This single fact explains several results: `sqr_mod` couldn't help (the
field path is carry-bound), and `KEYS_BATCH_INV` does not change `kernelMaxWorkGroupSize` (the
ceiling comes from the inlined EC + safegcd + hash core, not the batch arrays — only spill scales).

The practical lever this points to is **reducing register pressure** to lift occupancy — which today
means the same big project as the compute lever (a reduced-radix field uses fewer/cheaper temporaries;
splitting the megalithic kernel would also cut per-stage register peaks). Micro-tweaks won't move a
kernel pinned at the 255-register ceiling.

**Verbose build log (`producerOpenCL.logGpuDiagnostics`).** Off by default. When set, the kernel is
built with `-cl-nv-verbose` and the full `clGetProgramBuildInfo` build log is logged (on NVIDIA this
*can* surface ptxas register/spill stats — but is **driver-dependent and was empty on the RTX 3070's
581.x driver**, which is why the always-on `clGetKernelWorkGroupInfo` line above is the primary
occupancy signal). The device-info dump and the resource-usage line are logged regardless of this flag.

### Suggested starting config (auto-derived from the device)

The default `keysPerWorkItem = 1` is the slow trap (§3). To make the right ballpark obvious without a
manual sweep, `OpenCLContext` logs a **heuristic starting-point config** once at init, derived from the
device's reported `CL_DEVICE_MAX_COMPUTE_UNITS` and `CL_DEVICE_MAX_MEM_ALLOC_SIZE` (pure logic in
`OpenClConfigSuggestion`, unit-tested without a GPU):

```
Suggested starting config for this device (40 compute units): batchSizeInBits=21 keysPerWorkItem=256 (heuristic starting point - sweep to confirm, ...)
```

- **`batchSizeInBits`** — the largest batch whose full-transfer result buffer (`2^bits × 108 B`) fits
  ¼ of max-alloc, clamped to `[14, 21]` (below the hard `BIT_COUNT_FOR_MAX_CHUNKS_ARRAY = 24` cap).
- **`keysPerWorkItem`** — chosen to keep ≈ 200 work-items per compute unit (calibrated from the
  RTX 3070's ~8192-work-item peak over 40 CUs), rounded down to a power of two, clamped `[1, 256]`.

It is **a coarse starting point, not an optimum** — it deliberately can't capture the thermal/occupancy
subtleties, so the message tells the user to **sweep `keysPerWorkItem`** (§4) to confirm. The value is
that it replaces the kpwi=1 trap with a device-appropriate ballpark (e.g. "use ~256 / bsib 21 on this
40-CU GPU"), which is usually within a sweep step of the real peak.

---

## 7. Correctness gating

Every kernel change is gated **before** any throughput is reported. These run under pocl (CI's
`test-opencl` job) or a real GPU; `@OpenCLTest` classes self-skip when no device is present.

```bash
mvn test -Dtest='ProbeAddressesOpenCLTest,OpenCLCompactOutputIntegrationTest,OpenCLContextTest,Fuse8GpuHashParityTest,ProducerOpenCLTest,OpenCLPrecomputeKernelTest'
```

- **`ProbeAddressesOpenCLTest#createKeys_acrossKeysPerWorkItem_allResultsMatchReference`** — the
  primary gate. Byte-compares GPU X/Y **and** both hash160s against `ECKey.fromPrivate(secretBase | i,
  …)` for every work-item, across `keysPerWorkItem ∈ {1,2,4,8,16}`. This is what proves Stages 0–4 end
  to end: it runs on the live kernel, so the comb, the affine walk, and the (default) safegcd inverse
  all have to produce byte-identical keys.
- **`OpenCLCompactOutputIntegrationTest`** — compact-mode hit-set vs a CPU oracle (filter + emit +
  buffer plumbing); covers Stage 3's reuse of the result buffers.
- **`OpenCLContextTest`** — init/upload/close lifecycle (device buffers allocate in `init()` and
  release in `close()` without leaking) **and** `buildOptions()` (the `useSafeGcdInverse` →
  `-D USE_LEGACY_BINARY_GCD_INV_MOD` wiring; no GPU needed).
- **`OpenCLPrecomputeKernelTest`** — runs the on-device precompute / validation kernels and compares
  against bitcoinj: every `i·G` table entry equals `m·G`; every signed-digit comb entry (Stage 2b)
  equals `(mag·2^(4·pos))·G` incl. the carry-out position 64; and `invModSafegcd_…` (Stage 4)
  cross-checks safegcd vs. the binary GCD **and** `x·x⁻¹ ≡ 1 (mod p)` over 4096 random inputs (built
  with `useSafeGcdInverse=false` so both inverses are present and genuinely compared).
- **`Fuse8GpuHashParityTest`** — the pure-Java filter-hash contract the kernel filter must match.

**Never report a speedup from a build whose parity tests have not passed.** This is the cryptographic
hot path; correctness is paramount.

### Reproducibility map (every stage → how to re-measure → how it's gated)

| Stage | Optimization | Reproduce the throughput | Correctness gate |
|---|---|---|---|
| 0 | build flags + `#pragma unroll` | `GridSizeSweepBenchmark` (kpwi sweep) | `ProbeAddressesOpenCLTest` |
| 1 | affine batched-addition walk | `GridSizeSweepBenchmark` | `ProbeAddressesOpenCLTest` |
| 2 | fixed-base comb `P₀` | `GridSizeSweepBenchmark` | `OpenCLPrecomputeKernelTest` + `ProbeAddressesOpenCLTest` |
| 2b | signed-digit (±P) comb halving | within noise on this GPU — table size is the win, not throughput | `OpenCLPrecomputeKernelTest` |
| 3 | host result-buffer reuse | `GpuFuse8FilterBenchmark -p gpuFilter=true -p keysPerWorkItem=128` | `OpenCLCompactOutputIntegrationTest` + `ProbeAddressesOpenCLTest` |
| 4 | safegcd `inv_mod` (whole-kernel) | `GpuFuse8FilterBenchmark … -p useSafeGcdInverse=true,false` (one run, both arms) | `OpenCLPrecomputeKernelTest#invModSafegcd_…` + `ProbeAddressesOpenCLTest` |
| 4 | safegcd `inv_mod` (isolated, 256/160-bit) | `InvModBenchmark -p useSafeGcdInverse=true,false -p inputBits=256,160` | same as above |
| — | `keysPerWorkItem` tuning | `GridSizeSweepBenchmark` (§4) | — |
| — | stage attribution (EC vs hashing) | `GpuFuse8FilterBenchmark -p gpuFilter=true -p kernelProfileStage=FULL,ONE_HASH160,NO_HASH160` (§6) | `OpenCLContextTest#kernelProfileStage_buildsAndRuns` |
| — | occupancy / register pressure | grep init log for `Kernel resource usage:` (§6); `logGpuDiagnostics=true` for the verbose build log | — |
| — | suggested starting config | grep init log for `Suggested starting config` (§6); pure helper `OpenClConfigSuggestion` | `OpenClConfigSuggestionTest` |
| — | `KEYS_BATCH_INV` sweep | edit the `#define`, `GpuFuse8FilterBenchmark` (§3) | `ProbeAddressesOpenCLTest` |

**Honest caveat on A/B reproducibility:** only Stage 4 has a build-time toggle
(`useSafeGcdInverse`), so its A/B is a single JMH run. Stages 2b and 3 are unconditional (no flag, per
"if always faster, no flag" / "table size is the real win"), so to re-measure *their* deltas you must
benchmark the commit before the change vs. after (the staged commits on this branch are the A/B
points). All stages' **correctness**, though, is reproducible from the current build via the gate
command above.

---

## 8. Future work / not-yet-done levers

### Measured neutral — reverted

- **Stage `iG_table` into `__local` memory — neutral, reverted.** The affine walk reads the `m·G`
  table (≈ 8 KB at kpwi=128) from global memory per key, and every work-item reads the *same* table,
  so cooperatively copying it into per-work-group local memory once (with a barrier) looked like a way
  to cut global-memory latency. Implemented (extra `__local` kernel arg + cooperative load + barrier)
  and gated byte-identical (`ProbeAddressesOpenCLTest` 43/0). Matched **local–nolocal–local** A/B at
  kpwi=128 compact: **164.5 / 159.2 / 155.2 ops/s** — the two staged runs bracket the baseline, i.e.
  **no measurable change**. Expected from the occupancy finding (§6): the kernel is **register-bound,
  not memory-bound**, and the tiny broadcast-read table is already served well by L2. Since staging
  *adds* a barrier + complexity for zero gain (unlike the hashing rewrites, which simplified code), it
  was **reverted**. (`__constant` was considered instead but is capped at 64 KB, breaking for large
  kpwi.)

### Measured neutral (kept for code quality)

- **Direct `sha256_transform` / `ripemd160_transform` on pre-built blocks — throughput-neutral,
  kept.** The kernel builds fully-padded, block-aligned hash inputs, but used to run them through
  `sha256_init`+`sha256_update` / `ripemd160_init`+`ripemd160_update_swap`, whose streaming machinery
  (offset alignment, partial-block buffering, length tracking, full-ctx zeroing) is pure overhead for
  an already-aligned message. Replacing it with direct `*_transform` calls (custom-file helpers
  `sha256_hash_prebuilt_blocks` / `ripemd160_hash_prebuilt_block_swap`; vendored hash code untouched)
  is byte-identical (`ProbeAddressesOpenCLTest` 43/0). A matched **F1–base–F1** A/B at
  `keysPerWorkItem=128` compact gave **142.06 / 141.82 / 141.83 ops/s** — within ~0.2%, i.e. **no
  measurable change**. Kept anyway: it is simpler, idiomatic, and drops the per-key `ctx` structs
  (≈50 private words) lowering register pressure (a latent occupancy win on tighter configs). The
  lesson: the §6 "hashing ≈ 43%" cost is essentially **all** inside the vendored `sha256_transform` /
  `ripemd160_transform` (64 + 80 rounds) — the custom-file wrapper around them was negligible.

- **Build the SHA-256 input blocks directly from coordinate limbs (no `uchar` round-trip) —
  throughput-neutral, kept.** Follow-on to the above: the hash160 path used to go EC limbs → reverse
  to big-endian words → `get_sec_bytes` (write a `uchar` SEC array) → `pack_bytes_to_u32_words`
  (repack) → pad. Two self-contained functions now build the fully-padded SHA-256 block(s) directly
  from the little-endian coordinate limbs — no byte buffer, no pack/unpack, no per-word swap. The
  final form factors this into two general, composable primitives rather than per-format splices:
  `sha_block_put_be32(block, byte_off, val)` / `sha_block_put_byte(block, byte_off, b)` OR a value
  into the block at an arbitrary byte offset (straddle-aware). The SEC builders then just zero the
  block, "put" the prefix at offset 0, "put" each big-endian coordinate word at successive offsets
  (X, then Y for uncompressed), and "put" the 0x80 pad — compressed vs. uncompressed differ only by
  the number of appends. Because the offsets are compile-time constants in the unrolled callers, the
  put shift/branch folds to exactly the `(msword<<24)|(lsword>>8)` splice: the generality of a
  streaming append at the cost of a straight-line write. `sec_uncompressed_pubkey_to_sha256_blocks` /
  `sec_compressed_pubkey_to_sha256_block` are the clean "coordinates → hash input" entry points
  intended for **upstreaming to hashcat**. Removed ~10 now-dead helpers + the SEC `uchar` buffers.
  Byte-identical (`ProbeAddressesOpenCLTest` 43/0; an early version fed the byte-swapped big-endian
  array and the gate caught it immediately — the full tests localize wiring errors, so no dedicated
  test kernel was needed). Matched A/B at `keysPerWorkItem=128` compact showed **no measurable
  change** (both the direct-splice and the generic-put forms land within the ~6% run-to-run noise of
  the baseline), confirming the round-trip overhead was negligible. Kept for code cleanliness, the
  composable primitives, and the upstreamable methods.

The combined conclusion: from the custom file, the hashing path is **not** a throughput lever — the
cost lives in the vendored `sha256_transform` / `ripemd160_transform`. Faster hashing would require
changing those transforms (out of scope) or fewer of them (impossible without dropping a chain, which
is forbidden — both address types are mandatory).

### Measured and rejected

- **Dedicated `sqr_mod` (symmetric modular squaring) — ~5% *slower*, reverted.** A faithful symmetric
  squaring (each off-diagonal product `a[j]·a[k]` formed once and added twice, diagonals once) cuts
  the 32×32 limb multiplies per square ~44% (≈36 vs 64) and was wired into every EC squaring
  (`λ²` in the affine walk, the `z²` conversions, `point_double`/`point_add`/`point_to_affine`). It
  was gated byte-identical (a `test_sqr_mod` kernel proving `sqr_mod(a) == mul_mod(a,a)` over 4096
  inputs, plus `ProbeAddressesOpenCLTest` 43/0-fail). A matched **sqr–mul–sqr** A/B at the
  `keysPerWorkItem=128` compact operating point on the RTX 3070 gave **mul 135.5 ops/s vs sqr
  127.3 / 130.4** — i.e. squaring was consistently ~5% slower (the `mul` middle run sat above both
  `sqr` runs, and `sqr` ran first/cooler, so it is not thermal ordering). **Why:** this schoolbook
  field multiply is **carry/add-bound, not multiply-bound** on this GPU — `sqr_mod` keeps the same
  ~64-term carry-propagating 64-bit add chain and only removes multiplies, while adding loop
  branch/addressing overhead, so it loses. Reverted. Could be revisited on a *multiply-bound* device
  (or paired with a reduced-radix representation that shortens the add chain).

### Candidates for a future stage

Evaluated during the investigation (re-sweep `keysPerWorkItem` after any of these, since the per-key
cost balance shifts):

> **Not an option: dropping a hash160 chain.** Both the uncompressed and the compressed hash160 are
> mandatory in every mode — the tool must find both address types. The §6 finding that hashing is
> ~43% is a target for making *both* chains faster, never for computing only one. (The diagnostic
> `kernelProfileStage` modes that skip a chain are timing-only and must never be used in production.)

- **Faster hash160 (both chains kept).** Hashing is ~43% (§6), so SHA-256 / RIPEMD-160
  micro-optimisation, or sharing more work between the uncompressed and compressed chains (they share
  the X coordinate; the compressed SEC prefix transform is already reused via `REUSE_FOR_COMPRESSED`),
  is the lever here — without ever dropping a chain. Both chains always run.
- **Reduced-radix field representation** (e.g. 2^26 / 2^29 limbs) — the EC lever (EC ≈ 57% per §6),
  attacking the carry/add chain that made `sqr_mod` a wash (see "Measured and rejected"). Big rewrite
  of every field op; would also make `sqr_mod` pay off. Highest EC potential, highest effort/risk.
- **Dedicated sequential-only "addition-walk" kernel (160-bit, output-only)** — a brand-new
  standalone kernel (alongside `generateKeysKernel_grid`) for contiguous scanning: the host supplies
  a single anchor `P0`, and the kernel enumerates `P0, P0+G, P0+2G, …` by pure affine point addition
  (batched Montgomery inversion) — no per-key scalar multiplication, no comb, no wNAF. Restricted to
  the 160-bit (MSB-zero) range, compact/output-only (no full-transfer path), keeping both hash160
  chains. The whole keyspace is one arithmetic progression, so for sequential scans only the *first*
  point needs a scalar multiplication; everything after is one addition per key — the theoretical
  floor. Optionally persist each work-item's point across launches (advance by a constant
  `Δ = batchSize·G`) so the per-launch start cost approaches zero.
- **Persistent / warp-synchronous "megakernel" (different execution model, big rewrite).** The
  endpoint of the addition-walk idea: instead of one host launch per batch, launch *once* as many
  work-items as the GPU can keep resident (occupancy-maximal — tens of thousands on an RTX 3070, not
  a handful), each owning a disjoint keyspace stripe, holding its running point **in registers**, and
  looping internally: add `+G` → hash160 → Fuse8 → on the (astronomically rare) hit, atomically
  append to a global output ring buffer. The inner loop is branch-free and warp-uniform, so SIMT
  efficiency is ~ideal and steady-state memory traffic is ~zero ⇒ purely compute-bound. The kernel
  runs "indefinitely" until a host `volatile` stop flag; the host **drains the output buffer
  asynchronously** (double-buffer) while it keeps running. Gotchas: this needs a **completely
  different host orchestration** (long-lived launch, persistent per-thread state, async drain,
  back-pressure) — not the current stateless launch-per-batch model; beware the **display-GPU
  TDR/watchdog** (chunk into long-but-bounded launches, disable TDR, or use a non-display GPU); the
  only remaining divergence is the rare hit and the data-dependent `inv_mod` ⇒ pairs naturally with a
  branch-free (safegcd) inverse. This is the most "OpenCL-native" design but also the largest
  departure from the current architecture.
  - **GPU-only compute + thin (Rust-capable) host + resumable "scan map".** Because all crypto lives
    on the GPU, the host shrinks to a thin driver — seed the initial private keys, drain hits, and
    **checkpoint the frontier** — so this component could be reimplemented standalone (e.g. in Rust)
    over the OpenCL kernel. Periodically (and on stop) read back each thread's current offset (its
    stripe is contiguous, so the covered set is just the union of `[start_t, start_t + done_t)`),
    giving a compact, **`ddrescue`-style coverage/map file**: persist it to disk → resume after a
    stop/crash by re-seeding each thread at its last frontier, and keep a provable, gap-/overlap-free
    record of exactly which keyspace has been searched (your own searched-domain artifact). The
    frontier is a handful of integers per thread, so checkpointing is cheap and can read a
    host-mapped progress buffer the threads update, without stopping the kernel.
- **Fermat inversion** (`a^(p−2)` via an addition chain) instead of binary-GCD: warp-uniform (no
  data-dependent iteration count) vs GCD's divergence. But inversion is only ~4% of runtime
  post-batching ⇒ headroom ≤ ~4%. A cheap A/B, not a headline.
- **Larger / re-tuned comb window** (e.g. 5-bit windows) or moving the comb table to `__constant`
  memory (64 KB fits, but competes with `g_precomputed`). The current `T[pos][digit]` read is
  secret-keyed (data-dependent, not a warp broadcast) — cache-resident and occupancy-hidden, but
  benchmark alternatives per device.
- **Optional single-hash (compressed-only) mode:** skip one of the two hash160 chains (~+15% if
  hashing ~30% of runtime) — but it is a coverage/semantics change (would miss uncompressed P2PKH
  hits), so it must be opt-in and clearly documented.
- **±P symmetry** (one addition yields `P` and `−P`): random-search mode only — for sequential range
  scanning the `−P` keys fall outside the scanned range.
- **Rejected:** GLV endomorphism (subsumed by the fixed-base comb, which already removes almost all
  doublings); Karatsuba / Montgomery field form (schoolbook + special-prime reduction is already
  near-optimal at this width on GPU); moving hashing to the CPU (defeats the compact-output Binary
  Fuse 8 design).

---

## 9. File map

| File | Role |
|---|---|
| `src/main/resources/inc_ecc_secp256k1custom.cl` | Project kernel: comb (`point_mul_xy_comb`), affine walk, hashing/filter/emit tail, `KEYS_BATCH_INV` |
| `src/main/resources/copyfromhashcat/inc_ecc_secp256k1.cl` | Field + EC primitives (`mul_mod`, `sub_mod`, `add_mod`, `inv_mod`, `point_add`, wNAF `point_mul_xy`) — vendored from hashcat |
| `src/main/java/.../opencl/OpenCLContext.java` | Context lifecycle; builds/uploads the `i·G` and comb tables; `clBuildProgram` options |
| `src/main/java/.../opencl/OpenClTask.java` | One kernel invocation: buffer binding, launch, readback, optional device profiling |
| `src/test/java/.../benchmark/GridSizeSweepBenchmark.java` | `keysPerWorkItem` / `batchSizeInBits` throughput sweep |
| `src/test/java/.../benchmark/GpuFuse8FilterBenchmark.java` | Filter/transfer-path benchmark |
| `src/test/java/.../ProbeAddressesOpenCLTest.java` | Primary byte-identical parity gate |
| `src/test/java/.../opencl/OpenCLContext{IG,Comb}TableTest.java` | Pure-Java table-generator parity (no GPU) |
