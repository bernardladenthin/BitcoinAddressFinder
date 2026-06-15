# ECC GPU Performance Optimization — secp256k1 OpenCL Kernel

> **Status:** Investigation + implementation design. **No code changed yet.**
> **Audience:** A future implementation session (with access to a real OpenCL GPU,
> e.g. an NVIDIA RTX 3070) that will execute the stages below and benchmark them.
> **Scope:** `src/main/resources/inc_ecc_secp256k1custom.cl` (the project kernel) and the
> hashcat baseline `src/main/resources/copyfromhashcat/inc_ecc_secp256k1.cl`, plus the thin
> Java host glue (`opencl/OpenCLContext.java`, `opencl/OpenClTask.java`).

This document is **self-contained**: it captures the current design, a rigorous cost analysis,
the full set of optimization candidates that were considered (with the reasoning for accepting
or rejecting each), and a concrete, code-anchored implementation plan for the two that matter.

---

## 1. TL;DR

The GPU key-generation kernel is **EC-arithmetic-bound, not hash-bound** (proof in §4). The two
techniques that make the fastest open-source key searchers (BitCrack, VanitySearch) fast are
only *partially* applied here:

| Lever | What it replaces | Field-mul effect | Projected overall |
|---|---|---|---|
| **L1 — Fixed-base comb** for the one-time `P0 = k0·G` | general-purpose wNAF (small ±1,3,5,7·G table) | ~2600 → ~700 field-muls | **~1.48×** |
| **L2 — Affine batched-add walk** for `P0+G, P0+2G, …` | Jacobian add + batched Z-inversion | ~18 → ~5 field-muls / key | **~1.14×** |
| **L1 + L2 combined** | — | ~58 → ~17 field-muls / key (**~3.4× on EC**) | **~1.85× (target)** |

These projections are **logically derived** (§4–§5), not measured. They must be validated on
real GPU hardware with the existing JMH benchmarks (§9). The current documented peak is
**~19.8 M keys/s** (RTX 3070 Laptop, `keysPerWorkItem=64`, `batchSizeInBits=20`,
`README.md:272`).

**Recommended order:** Stage 0 (cheap flags) → Stage 1 (L2, contained, lower risk) → Stage 2
(L1, bigger lever). Each stage is independently shippable and independently benchmarkable.

---

## 2. How key generation works today (code map)

`__kernel generateKeysKernel_grid(...)` — `inc_ecc_secp256k1custom.cl:683`.

A Find-mode batch covers `2^batchSizeInBits` consecutive private keys. The CPU aligns a
candidate down to a `2^batchSizeInBits` boundary (`secretBase`) and submits it once; the kernel
launches `2^batchSizeInBits / keysPerWorkItem` work-items. Each work-item `g` produces
`K = keysPerWorkItem` consecutive keys for scalars `secretBase | (g*K + m)`, `m = 0..K-1`
(`README.md:205-258`). The combine is an **OR** (`baseK0 | base_offset`,
`inc_ecc_secp256k1custom.cl:804`), valid because `secretBase`'s low bits are cleared.

Per work-item:

1. **First key:** `P0 = k0·G` via `point_mul_xy` (`copyfromhashcat/inc_ecc_secp256k1.cl:1859`),
   a left-to-right **wNAF (window 4)** over a precomputed `±1,3,5,7·G` table
   (`g_precomputed`, a `__constant secp256k1_t`). ~256 `point_double` + ~51 `point_add`
   + 1 `inv_mod` + affine convert ≈ **~2600 field-muls**.
2. **Walk (keys 1..K-1):** a running point `Q` is advanced `+G` per step with the mixed
   Jacobian–affine `point_add` (`inc_ecc_secp256k1.cl:1240`, **11 `mul_mod`**), kept in
   Jacobian (X,Y,Z), then converted to affine `KEYS_BATCH_INV=8` at a time with Montgomery's
   simultaneous inversion (`inc_ecc_secp256k1custom.cl:808-849`). Per walked key
   ≈ 11 (add) + ~7 (conversion machinery) = **~18 `mul_mod`** + 1 `inv_mod`/8 keys.
3. **Per key:** two hash160 chains (SHA-256 → RIPEMD-160 of the **uncompressed** and the
   **compressed** SEC pubkey), then a 108-byte output entry
   (`inc_ecc_secp256k1custom.cl:851-931`). Compact mode applies a GPU Binary Fuse 8 filter and
   claims an output slot with `atomic_add` (OpenCL 2.0+).

**Field layer** (`copyfromhashcat/inc_ecc_secp256k1.cl`): 8×`u32` limbs; schoolbook `mul_mod`
(`:593`) + fast reduction for `p = 2²⁵⁶ − 2³² − 977`; `add_mod` (`:235`); `sub_mod` (`:214`);
`inv_mod` (`:798`) is a **binary extended-GCD** (~256 data-dependent iterations) and already
**guards `a == 0`** (`:806-807`).

**Kernel build:** `clBuildProgram(program, 0, null, null, null, null)` — **no options string**
(`OpenCLContext.java:227`). Sources are concatenated from `inc_defines.h`, the `copyfromhashcat/`
headers + hash + ecc files, then `inc_ecc_secp256k1custom.cl`.

---

## 3. Why this is the hot path

`keysPerWorkItem` was added precisely because key generation dominates runtime: raising it from
1 to 64 yields **8.4×** throughput on the RTX 3070 (`README.md:266-273`) by amortizing the
expensive `k·G` over a cheap `+G` walk. The address hashing + LMDB lookup runs on the CPU
consumer in parallel; the GPU producer's job is purely EC + hashing. Within the kernel, EC point
arithmetic — not the SHA/RIPEMD hashing — is the bottleneck (proof next).

---

## 4. Cost decomposition + EC-bound proof

### Field-multiplies per work-item at the sweet spot (`K = keysPerWorkItem = 64`)

| Component | Field-muls | Share of EC |
|---|---|---|
| One-time scalar mult `P0` (wNAF) | ~2600 | **~70%** |
| Walk: 63 × 18 (Jacobian add + convert) | ~1134 | ~30% |
| **Total** | **~3734 (≈58/key)** | 100% |

**Key insight:** at the current sweet spot the *one-time* scalar multiplication dominates EC
cost, because the walk has already amortized it 64-fold. Therefore **optimizing the walk alone
is bounded**; the biggest lever is making `P0` cheap.

### Proof the kernel is EC-bound (not hash-bound)

`README.md:285` documents that introducing **batched inversion** (removing 7 of every 8
`inv_mod`) alone improved throughput by **+37%** (≈14.4 → 19.8 M keys/s). A modular inversion is
pure EC work. If hashing were the majority of runtime, removing a *fraction* of EC work could
not move the total by 37%. Working backwards: the eliminated inversions were ≈ `1 − 1/1.37` ≈
27% of total runtime, so all-inversions-per-key were ≈ 31% of runtime before batching. Hence:

- **EC arithmetic ≈ 60–75% of runtime**, hashing ≈ ~30%, inversion now ~4% (already batched).

All throughput projections below use **EC ≈ 65%** as the central estimate.

---

## 5. Optimization candidates — logical evaluation

### ★ L1 — Fixed-base comb / window for `P0 = k0·G` (biggest lever)

`G` is a **fixed** point, so a precomputed *fixed-base* table evaluates `k·G` with almost no
doublings. A 4-bit windowed fixed-base table (64 window positions × 16 digits ≈ 1024 points,
≈ 64 KB) computes `k·G` in **~64 additions, ~0 doublings ≈ ~700 field-muls** vs the current
~2600 — **~3.7× on the scalar mult**. The current wNAF only precomputes `±1,3,5,7·G` (a tiny
table) and is really a *general-base* method that still pays ~256 doublings.

- **Caveat:** the per-window table read is indexed by secret bits → data-dependent (not a warp
  broadcast). Mitigated by the small, cache-resident table; high occupancy hides the latency.
  This is exactly what BitCrack does. Net win expected; confirm by benchmark.
- **Effort:** moderate–high (host-generated table upload + new comb routine). **Risk:** moderate
  (same math as wNAF ⇒ directly testable against the bitcoinj reference).
- **L1 alone ≈ 1.48×** (EC 58 → 29 field-muls/key ⇒ `1/(0.35 + 0.65/2.0) ≈ 1.48`).

### ★ L2 — Affine batched-addition walk (replace the Jacobian walk)

Generate `P_m = P0 + m·G` directly in **affine**. The `m·G` are fixed constants; anchoring every
point at the *same* `P0` makes the denominators `dx_m = x_{mG} − x_{P0}` mutually independent ⇒
one Montgomery inversion covers a whole sub-batch. Per key ≈ **5 `mul_mod` + 5 `sub_mod`**
(slope formula + Montgomery overhead) + 1 `inv_mod`/sub-batch — vs the current ~18 `mul_mod`. No
Jacobian, no per-point Z-conversion. (`sub_mod` is far cheaper than `mul_mod` — a 256-bit
subtract + conditional add of `p`, no limb products — so the 5 subs do not erode the win.)

- **Effort:** moderate (contained rewrite of the walk loop). **Risk:** moderate (gated by exact
  byte-compare parity tests, §8).
- **L2 alone ≈ 1.14×** (the one-time `P0` bounds it: EC 58 → 47 field-muls/key).

### ★ L1 + L2 combined — RECOMMENDED TARGET

`700` (comb) + `63 × 6` (affine walk) ≈ **1078 field-muls/work-item ≈ 17/key vs 58/key = ~3.4×
on EC**. With EC ≈ 65% of runtime ⇒ **~1.85× overall** (`1/(0.35 + 0.65/3.4)`), range ~1.5–2×
depending on the real hash fraction and the re-tuned occupancy / `keysPerWorkItem` sweet spot.
Both levers are independent and stackable; this is the BitCrack/VanitySearch design.

### Stage-0 cheap experiments (low effort, do first, independent)

- **Build flags:** `clBuildProgram` currently passes `null` options (`OpenCLContext.java:227`).
  Try `-cl-mad-enable` and an explicit `-cl-std=CL2.0`; add `#pragma unroll` to the fixed-count
  field loops (`mul_mod` / reduction in `inc_ecc_secp256k1.cl`). Near-zero risk, minutes to test,
  low/uncertain reward. **Do not** use `-cl-fast-relaxed-math` — it only affects floats; this
  kernel is integer-only.
- **Re-sweep `keysPerWorkItem`** with `GridSizeSweepBenchmark` after every change — the sweet
  spot shifts once per-key cost drops.

### Bounded / conditional / rejected (with reasons)

- **Fermat inversion** (`a^(p−2)` via an addition chain) instead of binary-GCD: warp-uniform
  (no divergence) vs GCD's data-dependent iteration count, which on a GPU forces a warp to run
  to the slowest lane. **But** inversion is only ~4% of runtime post-batching ⇒ headroom ≤ ~4%.
  Worth a cheap A/B, not a headline.
- **±P symmetry** (one addition yields `P` and `−P = (x, −y)`): **random-search mode only**. For
  sequential range scanning the `−P` keys are `n − k`, which fall *outside* the scanned range ⇒
  useless. Conditional, low priority.
- **Optional single-hash (compressed-only) mode:** skip one of the two hash160 chains
  (~+15% if hashing ~30%) — but it is a **coverage/semantics change** (would miss uncompressed
  P2PKH hits). Opt-in only, clearly documented. Out of scope for the core EC work.
- **GLV endomorphism:** halves doublings in a *general* scalar mult, but a fixed-base comb (L1)
  already eliminates almost all doublings for the fixed `G` ⇒ subsumed. Reject (could stack for
  marginal gain; not worth the complexity).
- **Karatsuba 256-bit multiply / Montgomery field form:** schoolbook + special-prime reduction
  is already near-optimal on GPU; Karatsuba's extra adds/branches usually lose at this width.
  Reject.
- **Move hashing to CPU:** defeats the compact-output Binary Fuse 8 design (the whole point is to
  *not* ship full pubkeys over PCIe). Reject.

---

## 6. Stage 0 — quick wins (do first)

1. In `OpenCLContext.init()` change `clBuildProgram(program, 0, null, null, null, null)`
   (`:227`) to pass an options string, e.g. `"-cl-std=CL2.0 -cl-mad-enable"`. Keep it a single
   constant so it is easy to A/B and revert.
2. Add `#pragma unroll` to the fixed-trip-count loops in `mul_mod` / the reduction in
   `copyfromhashcat/inc_ecc_secp256k1.cl` (the 8-limb loops). The custom file already uses
   `#pragma unroll` for small copies (`inc_ecc_secp256k1custom.cl:677`).
3. Record a clean **baseline** with `GridSizeSweepBenchmark` (§9), sweeping `keysPerWorkItem`
   `1,2,4,8,16,32,64`. This is the reference all later stages compare against.
4. (Optional) A/B a constant-time **Fermat** `inv_mod` against the binary-GCD one.

These are reversible and carry essentially no correctness risk, but the reward is uncertain —
treat them as measurement/setup, not the main event.

---

## 7. Stage 1 — Affine batched-addition walk (single-anchor)

**Idea:** every key is `P_m = P0 + m·G`. Anchor *all* points at the same `P0` (no running-point
state machine, no error accumulation), reading `m·G` from a host-uploaded table. Replace the
Jacobian walk + Z-inversion with an affine slope formula whose denominators are batch-inverted.

### 7.1 Host: generate the `i·G` table (`OpenCLContext.java`)

bitcoinj (`bitcoinj-core` 0.17.1) is already used for key derivation in `PublicKeyBytes` and
`KeyUtility`. `ECKey.fromPrivate(d)` returns `d·G`, so:

```java
// i*G as an uncompressed SEC key: 0x04 || X(32 BE) || Y(32 BE)
byte[] sec = ECKey.fromPrivate(BigInteger.valueOf(i), /*compressed=*/false).getPubKey();
byte[] xBe = Arrays.copyOfRange(sec, 1, 33);   // X, 32-byte big-endian
byte[] yBe = Arrays.copyOfRange(sec, 33, 65);  // Y, 32-byte big-endian
```

Build `byte[(K-1) * 64]` where entry `i-1` (`i = 1..K-1`) holds `X` then `Y`, **each reversed to
device word order**. Reuse the project's existing converter — the same one
`OpenClTask.setSrcPrivateKeyChunk` uses on the private key
(`EndiannessConverter(ByteOrder.BIG_ENDIAN, OpenClKernelConstants.GPU_NATIVE_WORD_ORDER, ...)`,
`OpenClTask.java:317-319`). The on-device convention is 8×`u32` little-endian words = the full
byte-reversal of the 32-byte big-endian coordinate (verified against `g_precomputed`:
`SECP256K1_G_PRE_COMPUTED_00 = 0x16f81798` is the *low* limb of `x(G)=0x79be667e…16f81798`,
`inc_ecc_secp256k1.h`).

The table is independent of the private key, so build it **once** in `OpenCLContext.init()` after
`keysPerWorkItem` is known. When `K == 1`, allocate a 1-byte placeholder (the walk never reads
it) — mirror the empty-filter placeholder in `allocateFilterBuffers`.

### 7.2 Host: allocate, bind, release the buffer

Copy the exact idiom already used for the Binary Fuse 8 filter:

- Allocate a `cl_mem igTableMem` with `CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR` from the byte
  array (see `OpenCLContext.allocateFilterBuffers`, the `fuse8FingerprintsMem` field, and its
  release path). Store it in a new `@ToString.Exclude private @Nullable cl_mem igTableMem;`
  field and free it in `close()` / re-init.
- Thread it through `OpenCLContext.createKeys(...)` → `OpenClTask.executeKernel(...)` (new param,
  mirror `fuse8FpMem`) and bind it as a **new trailing kernel arg (index 6)** via
  `clSetKernelArg` (the current args are 0–5 at `OpenClTask.java:380-385`).

### 7.3 Kernel: add the argument

In `generateKeysKernel_grid` (`inc_ecc_secp256k1custom.cl:683-689`) add a trailing parameter:

```c
__global const u32 *iG_table   // (K-1) points, each [x(8 words)][y(8 words)], device word order
```

### 7.4 Kernel: replace the walk (`inc_ecc_secp256k1custom.cl:798-849`)

Keep `point_mul_xy(q_x, q_y, k_littleEndian_local, &g_precomputed)` for `P0` (lines ~804-805).
Treat `(x0, y0) = (q_x, q_y)` as a **constant anchor** for the whole work-item. Emit `m = 0`
(`P0`) directly into `x_littleEndian_local` / `y_littleEndian_local` and run the existing tail.
Then process `m = 1 .. K-1` in `KEYS_BATCH_INV`-sized sub-batches:

```c
// (x0,y0) = P0 (affine, constant for this work-item). gx_m, gy_m read from iG_table[m-1].
// ---- Pass A: build dx_m and Montgomery prefix products ----
acc = 1
for each m in sub-batch:
    sub_mod(dx[j],  gx_m, x0)       // dx_m = x_{mG} - x0        (1 sub)
    copy   (prefix[j], acc)         // product of dx before this one
    mul_mod(acc, acc, dx[j])        // acc = dx_0 * ... * dx_j   (1 mul)
inv_mod(acc)                        // ONE inverse per sub-batch

// ---- Pass B: recover each 1/dx_m and apply the slope formula (reverse order) ----
for each m in sub-batch, reversed:
    mul_mod(inv_dx, acc, prefix[j]) // 1/dx_m                    (1 mul)
    mul_mod(acc, acc, dx[j])        // strip dx_m from acc       (1 mul)
    sub_mod(num, gy_m, y0)          // y_{mG} - y0               (1 sub)
    mul_mod(lambda, num, inv_dx)    // λ = (y_{mG}-y0)/dx_m      (1 mul)
    mul_mod(lam2, lambda, lambda)   // λ^2                       (1 mul = square)
    sub_mod(x_i, lam2, gx_m)        // λ^2 - x_{mG}
    sub_mod(x_i, x_i, x0)           // x_m = λ^2 - x_{mG} - x0   (2 sub)
    sub_mod(t, x0, x_i)             // x0 - x_m
    mul_mod(yt, lambda, t)          // λ(x0 - x_m)               (1 mul)
    sub_mod(y_i, yt, y0)            // y_m = λ(x0 - x_m) - y0    (1 sub)
    // x_i -> x_littleEndian_local, y_i -> y_littleEndian_local, then run the
    // EXISTING endianness/SEC/SHA256/RIPEMD160/filter/emit tail (lines 851-931) verbatim,
    // with loop_index = base_offset + m.
```

Read `gx_m` / `gy_m` from `iG_table` at word offsets `(m-1)*16` and `(m-1)*16 + 8` with a copy
like the existing `copy_global_u32_array_private_u32` (`inc_ecc_secp256k1custom.cl:345`).

**Reuse existing primitives** (`copyfromhashcat/inc_ecc_secp256k1.cl`): `mul_mod` (`:593`),
`sub_mod` (`:214`), `add_mod` (`:235`), `inv_mod` (`:798`). Squaring is `mul_mod(r, a, a)` (as in
`point_add` and `point_add_xy`).

### 7.5 Scratch / register notes

- Drop `batch_x` / `batch_y` / `batch_z` (the Jacobian snapshots,
  `inc_ecc_secp256k1custom.cl:808-810`). Each affine `(x_m, y_m)` is produced *and consumed by
  the hashing tail in the same loop iteration*, so it never needs to persist.
- Add `dx[KEYS_BATCH_INV][8]` + `prefix[KEYS_BATCH_INV][8]`. Net per-work-item scratch
  **1024 → 512 bytes** ⇒ occupancy *improves*; a larger `KEYS_BATCH_INV` (`:667`) may now fit.
- The remaining temporaries (`acc, inv_dx, lambda, lam2, num, t, yt, x_i, y_i`) are short-lived
  registers, not arrays.

### 7.6 Degenerate case (`dx_m == 0`)

`dx_m = 0` iff `m·G == ±P0`, i.e. `(baseK0 | base_offset) ≡ ±m (mod n)` — astronomically rare and
impossible for the supported aligned sequential / random ranges (the same practical assumption
the current code documents at `inc_ecc_secp256k1custom.cl:792-796`). No extra per-point branch is
needed: if any `dx_m == 0`, then `acc` becomes 0 in Pass A and the existing `inv_mod` zero-guard
(`:806-807`) returns without hanging; the recovered inverses are then garbage and the filter
rejects the resulting hashes — **strictly no worse than today**.

### 7.7 Do **not** touch

`point_add`, `point_add_xy`, `point_mul_xy`, the hashcat `g_precomputed` table, the filter code,
the 108-byte output layout (`OpenClKernelConstants` + kernel lines 851-931), or the `OpenClTask`
readback logic — all remain valid.

---

## 8. Stage 2 — Fixed-base comb for `P0` (bigger lever)

Reuse Stage 1's host-upload plumbing for a **larger fixed-base table** and replace the wNAF
`point_mul_xy` call **for the P0 anchor only**.

- **Table:** 4-bit windows over 64 positions: for `pos = 0..63`, `digit = 0..15`, store
  `digit · 2^(4·pos) · G` (1024 points ≈ 64 KB). Build host-side with the same
  `ECKey.fromPrivate(BigInteger.valueOf(digit).shiftLeft(4*pos))...`-style derivation (or
  `digit * (2^(4*pos))` as a `BigInteger` scalar), reversed to device word order. Upload via the
  same `cl_mem` mechanism as §7.2 (a second read-only buffer, new kernel arg).
- **Routine:** `k·G = Σ_pos T[pos][window_pos(k)]`, where `window_pos` extracts the 4-bit digit
  of `k` at bit `4·pos`. This is ~64 mixed `point_add`s (reuse `point_add`), ~0 doublings.
  Accumulate in Jacobian and convert once to affine (reuse the `point_add_xy` tail /
  `inv_mod` + 4 `mul_mod`). The Stage 1 slope-formula walk is unchanged and consumes the
  resulting affine `P0`.
- **Validate first:** before wiring into the kernel, assert the comb result equals
  `point_mul_xy` for a spread of scalars (a focused C++/OpenCL or host cross-check), because this
  changes *how* `P0` is computed. The §8/§9 parity tests then gate the end-to-end result.
- **Trade-off to watch:** the `T[pos][window]` read is data-dependent (secret-keyed) ⇒ not a warp
  broadcast. The 64 KB table is cache-resident; rely on occupancy to hide latency. If divergence
  proves costly on the target device, fall back to a smaller window (e.g. 8 positions of 8 bits
  is worse for table size; 2-bit windows reduce table but add positions) — benchmark to choose.

After Stage 2, **re-sweep `keysPerWorkItem`**: with a cheap `P0` the amortization pressure drops,
so the optimum may move (possibly toward smaller `K` for better occupancy, or stay if occupancy
is already saturated). Let `GridSizeSweepBenchmark` decide.

---

## 9. Verification

### 9.1 Correctness — runs under pocl, no discrete GPU required

The output (X, Y, both hash160s, slot ordering) must stay **byte-for-byte identical**; only the
path that computes the points changes. Gate on these tests:

- **`ProbeAddressesOpenCLTest#createKeys_acrossKeysPerWorkItem_allResultsMatchReference`** — the
  primary gate. Parameterized over `keysPerWorkItem ∈ {1,2,4,8,16}`; it byte-compares GPU `X`
  and `Y` **and** the hash160 against `ECKey.fromPrivate(secretBase | i, …)` for *every*
  work-item, and its Javadoc explicitly targets "any kernel change that batches the affine
  conversion." If this passes for all `keysPerWorkItem`, the EC rewrite is correct.
- **`OpenCLCompactOutputIntegrationTest`** — derives every candidate on the CPU, seeds a real
  Binary Fuse 8 filter, runs the kernel in compact mode, and asserts the emitted hit-set exactly
  matches the CPU oracle (gates the hashing/filter/emit tail + the new buffer plumbing).
- **`OpenCLContextTest`** — init/upload/close lifecycle + device guards. The new `cl_mem` must be
  allocated in `init()` and released in `close()` without leaking.
- **`Fuse8GpuHashParityTest`** — pure-Java filter-hash contract (unchanged by this work; listed
  because the kernel filter tail must keep matching it).
- **`ProducerOpenCLTest`** — pipeline-level produce→consume.

Run:

```bash
mvn test -Dtest='ProbeAddressesOpenCLTest,OpenCLCompactOutputIntegrationTest,OpenCLContextTest,Fuse8GpuHashParityTest,ProducerOpenCLTest'
```

GPU tests are `@OpenCLTest` and **self-skip** when no OpenCL device is present (they begin with
`OpenCLPlatformAssume…assumeOpenClLibrary…`). In CI they run on the **pocl** `test-opencl` job
(a conformant OpenCL 3.0 CPU ICD). Locally, install pocl or use the real GPU.

Also add a **pure-Java unit test** for the host table generator: assert `iG_table[i-1]` decodes
back to `ECKey.fromPrivate(BigInteger.valueOf(i), false).getPubKey()` X/Y (no GPU needed) — same
philosophy as `Fuse8GpuHashParityTest`.

### 9.2 Throughput — needs the real GPU (RTX 3070)

A/B the **old kernel binary vs the new** with identical params. Both benchmarks drive
`OpenCLContext.createKeys(...)` inside the timed `@Benchmark`.

- **`GridSizeSweepBenchmark`** — the right tool for the EC-walk speedup. **Widen the sweep** past
  the default `{1,2}` corner, because the affine-walk gain grows with `keysPerWorkItem`:

  ```bash
  mvn test-compile exec:java -Dexec.args="GridSizeSweepBenchmark \
    -p batchSizeInBits=20 -p keysPerWorkItem=1,2,4,8,16,32,64 -f 1 -wi 1 -w 30 -i 1 -r 200"
  ```

  (If the `exec:java` JMH fork throws `ClassNotFoundException: …ForkedMain`, use the direct
  `java -cp` recipe in `CLAUDE.md` → "Running JMH benchmarks locally".) Candidates/s =
  ops/s × `2^batchSizeInBits`. Compare each `keysPerWorkItem` before/after.

- **`GpuFuse8FilterBenchmark`** — regression guard for the filter/transfer path
  (`keysPerWorkItem = 1`, so it does *not* exercise the walk). Enable `-p profiling=true` to read
  device-side kernel vs readback nanos and confirm the gain is in **kernel compute**, not
  transfer.

- **Re-tune** `keysPerWorkItem` (and possibly the compile-time `KEYS_BATCH_INV`, `:667`, since
  scratch is now smaller) after each stage; the optimum **rises** once per-key cost drops.

Use `{"command":"OpenCLInfo"}` to confirm a device and pick `platformIndex` / `deviceIndex`.
For GPU benchmarks prefer one long measurement iteration over many short samples (`-i 1 -r 200`,
plus a short `-wi 1 -w 30` to reach steady clocks) — kernel compilation (the one-time cost) runs
in `@Setup`, outside the timed region.

---

## 10. Risks & caveats

- **This is the cryptographic hot path.** Correctness is paramount; every stage is gated by the
  §9.1 pocl parity tests *before* any throughput claim. Never report a speedup from a build whose
  parity tests have not passed.
- **Throughput numbers here are projections, not measurements** (no GPU was available when this
  was written). Validate on real hardware; the staging exists so each lever is measured in
  isolation.
- **Data-dependent table reads** (L1 comb, and the `i·G` reads in L2) interact with occupancy.
  The `keysPerWorkItem` / `KEYS_BATCH_INV` re-sweep is part of the deliverable, not an
  afterthought.
- **Determinism / reproducibility:** the `i·G` and comb tables are deterministic functions of the
  curve; generating them via the same bitcoinj path the CPU reference uses keeps a single source
  of truth and makes the host-side unit test trivial.

---

## 11. File checklist

| File | Change |
|---|---|
| `src/main/resources/inc_ecc_secp256k1custom.cl` | New trailing kernel arg(s); rewrite walk `:798-849` (Stage 1); swap `P0` to comb (Stage 2). Tail `:851-931` unchanged. |
| `src/main/resources/copyfromhashcat/inc_ecc_secp256k1.cl` | (Stage 0 only) optional `#pragma unroll`; **reused** primitives `mul_mod`/`sub_mod`/`add_mod`/`inv_mod`/`point_add` — do not modify their logic. |
| `src/main/java/.../opencl/OpenCLContext.java` | Build-flags string (`:227`); generate + allocate + release `i·G` table (and comb table); thread through `createKeys`. Mirror `allocateFilterBuffers` / `fuse8FingerprintsMem`. |
| `src/main/java/.../opencl/OpenClTask.java` | New `cl_mem` param in `executeKernel`; bind new `clSetKernelArg` index(es) after arg 5 (`:380-385`). Reuse `EndiannessConverter` (`:317-319`). |
| `src/test/java/.../ProbeAddressesOpenCLTest.java` | Primary correctness gate (no change expected; must stay green across `keysPerWorkItem`). |
| `src/test/java/.../opencl/…`, `…/OpenCLCompactOutputIntegrationTest.java` | Must stay green; add a pure-Java `i·G`-table decode test. |

---

## 12. Background — why these two techniques

BitCrack and VanitySearch, the fastest open-source secp256k1 key searchers, both: (1) precompute
a **fixed-base table for `G`** so the initial `k·G` is cheap (L1), and (2) generate consecutive
points with **affine batched addition** using Montgomery's simultaneous inversion (L2), often
with a `±P` symmetry refinement (not applicable to sequential range scanning here). BAF currently
has a *general-base* wNAF (small table) for (1) and a *Jacobian* walk for (2) — leaving exactly
the headroom this document quantifies.
