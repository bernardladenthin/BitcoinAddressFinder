# TODO — BitcoinAddressFinder

Open work items for this repo. Cross-cutting tracking lives in
[`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md);
items here are BAF-specific or are this repo's slice of a
cross-cutting initiative.

## Open — BAF-specific

### Persistence backends (genuinely open work)

Implementation of `BLOOM` / `HASHSET` / `TRUNCATED_LONG_64` is DONE
(see "Done" history below). The remaining open items in the
pluggable-persistence design plan:

- **Add open-addressing primitive hash table backend** (fastutil `Long2LongOpenHashMap` or hand-rolled over `long[]`) — would offer O(1) average vs. the sorted array's O(log n) per lookup with similar cache profile. Deferred because `TRUNCATED_LONG_64` is already the fastest backend in practice and the marginal speedup would not change the recommended default.

- **Add standalone `BloomFilterPersistence`** (Bloom-only / probabilistic mode without a backing `AddressLookup`). The current `BloomFilterAccelerator` returns `requiresBackend()==true`; a pure-Bloom variant would return `false` and accept `getAmount` semantics of "unsupported / always `Coin.ZERO`". Not yet needed by any caller; ship only when someone asks for it.

### GPU acceleration (three connected designs)

- **Pre-compute the `HASHSET`-backend lookup hash on the GPU.** Targets the `HASHSET` backend (`AddressLookupBackend.HASHSET` → `persistence/inmemory/HashSetAddressPresence.java`), which today wraps each derived hash160 in a thread-local `ByteBuffer` (`ConsumerJava.java:367-371`) and then calls `Set<ByteBuffer>.contains(...)` (`HashSetAddressPresence.java:74-78`). The dominant cost inside `contains(...)` is recomputing `ByteBuffer.hashCode()` per candidate — for a 20-byte hash160 this is 20 multiply-adds (`h = 31*h + b`) plus the `HashMap` spread (`(h ^ (h >>> 16))`). The same arithmetic can be computed once on the GPU, returned alongside the hash160, and consumed CPU-side without re-hashing. Per README §"Lookup latency" the HASHSET path is ~85 ns/op; the JDK hash + spread is ~20–25 ns of that, so the headroom is ~25 % of the HASHSET lookup time per candidate.
  - **Extend the kernel output struct.** Today the kernel writes the layout described in `PublicKeyBytes.java:240-242` (X, Y, hash160 uncompressed, hash160 compressed = 104 B/work-item). Add a 4-byte `int hashCodeUncompressed` and a 4-byte `int hashCodeCompressed` field per work-item (112 B/work-item, +7.7 % per-candidate PCIe bandwidth). Reuse the existing `CHUNK_SIZE_*` offset machinery in `OpenCLGridResult.java:118-122` to lay the fields out without churn.
  - **Reproduce `java.nio.HeapByteBuffer.hashCode()` byte-for-byte in OpenCL C.** OpenJDK's implementation for a heap buffer with position 0 and limit 20 is:
    ```
    int h = 1; for (int i = 19; i >= 0; i--) h = 31 * h + (int)(byte)get(i);
    return h;
    ```
    Two correctness traps: (a) the cast is `(int)(byte)` — the byte is sign-extended (e.g. `0xFF` ⇒ `-1`, not `255`); (b) the loop runs **back-to-front** (last byte first). A JMH benchmark must verify byte-equality against `ByteBuffer.wrap(hash160).hashCode()` over a randomised corpus before the GPU value is trusted in a `Set.contains` path.
  - **Add a new persistence implementation that accepts a precomputed hash.** `HashSet<ByteBuffer>.contains(o)` unconditionally calls `o.hashCode()` — there is no JDK hook to pass in an external hash. So the optimization requires bypassing `java.util.HashSet` entirely. Add `HashSetPrecomputedHashAddressPresence` next to `HashSetAddressPresence` (`persistence/inmemory/`) with a custom open-addressing hash table keyed by the precomputed int hash (collisions resolved by `Arrays.equals(byte[], byte[])` against the stored hash160). Expose a new API `boolean containsAddress(byte[] hash160, int precomputedHash)` on `AddressPresence` (or a sibling interface) so `ConsumerJava` can forward the GPU-precomputed value without rewrapping in a `ByteBuffer`. Document in the class Javadoc that the int hash is reproduced from a frozen OpenJDK formula and a future JDK change would silently corrupt lookups — pin a JMH equality test that fails the build if the JDK ever drifts.
  - **Wire the configuration toggle.** Add `HASHSET_GPU_HASH` to `AddressLookupBackend` (preserve `HASHSET` as the JDK-`HashSet<ByteBuffer>` path) so both implementations live side-by-side and the JMH harness can A/B them on the same workload. Default stays `TRUNCATED_LONG_64` per the README recommendation; this is opt-in for HASHSET deployments only.
  - **Cost breakdown of one `Set<ByteBuffer>.contains(buf)` call**:

    | Step inside `contains(...)` | Approximate cost (warm L3 table) | GPU pre-compute helps? |
    |---|---|---|
    | `ByteBuffer.hashCode()` — 20 sign-extending multiply-adds (`h = 31*h + (int)(byte)b`) | **~20 ns** — long dependency chain, ILP-limited | ✅ **eliminated** |
    | `HashMap.spread(h)` — `h ^ (h >>> 16)` | ~1 ns | ✅ eliminated |
    | Bucket index `(n-1) & h` + array load `tab[i]` | ~5–80 ns (cache-state-dependent) | ❌ no — pre-computed hash doesn't fix cache miss |
    | Walk node chain (low load factor ⇒ usually 1 step) | ~3–10 ns | ❌ no |
    | `ByteBuffer.equals(other)` — content compare on hit (or first chain node) | ~15–25 ns (another byte loop) | ❌ no |
    | **Total per call (warm table)** | **~50–85 ns**, matches the README's "85 ns HASHSET" | **~25 % is the hash chain** |

    At Full-DB scale the bucket-array load becomes an L3/DRAM miss (50–100 ns), and total per-call cost rises to 130–180 ns; the hash chain's *fraction* of total time drops but its absolute cost (~20 ns) stays constant.

  - **Throughput math when 32 cores are saturated on `.contains()` (the real-world scenario).** `ConsumerJava` issues **two** `.contains()` calls per candidate (compressed + uncompressed hash160), so the per-candidate CPU cost is **2 × 85 ns = 170 ns** on the warm-table path.

    | Configuration | Per-candidate CPU time | Throughput on 32 saturated cores |
    |---|---|---|
    | Without GPU hash (today) | 170 ns | ~188 M candidates/sec |
    | With GPU pre-computed hash | 130 ns | ~246 M candidates/sec |
    | **Delta** | **−40 ns (~23 % faster per call)** | **+~30 % throughput, or equivalently ~7 of 32 cores freed** |

    That is not a marginal improvement when cores are saturated. Under CPU-bound saturation the +7.7 % PCIe-bandwidth cost is also not a real concern.

  - **Realistic ceiling — where this TODO sits relative to bigger wins.**

    | Optimization | Expected throughput gain when 32 cores are saturated on `.contains()` |
    |---|---|
    | **GPU pre-computed hash (this TODO)** | **~30 %** — frees ~7 of 32 cores; ship-worthy on its own |
    | **Pack hash160 into `(long, long, int)` and key the table on `long`** (i.e. use the existing `TRUNCATED_LONG_64` approach, not `HashSet<ByteBuffer>`) | **~2-3×** — eliminates both the hash loop and the 20-byte equality byte loop. Already implemented as a separate backend; the cheapest "fix" is to stop using HASHSET. |
    | **GPU-side presence check** (the "Push the `TRUNCATED_LONG_64` presence check into OpenCL" TODO below) | **~10–100× on the CPU lookup step in isolation**, but **end-to-end pipeline gain is GPU-headroom-dependent** — the kernel grows by 256 phases of cooperative tile loading plus per-phase binary searches; if the GPU is already near saturation on ECC the kernel slows enough that net throughput can regress. See the throughput-trade-off sub-bullet under that TODO for the measurement plan. |
    | **Batched lookups with software prefetch** (issue 8 candidate hashes, `__builtin_prefetch` their bucket addresses, then check) | **~2×** on cold tables; smaller on warm. Orthogonal to GPU-hash precompute. |

    Honest read: if `.contains()` saturation is the bottleneck *today*, this TODO is worth shipping for the 30 % it gives; **but** for the same investigation cycle it's worth measuring whether simply switching the active backend from HASHSET to TRUNCATED_LONG_64 (2-3×) or doing the GPU-presence-check work (10-100×) gives more and supersedes the need for this TODO at all.

  - **What pre-computed hash does *not* help.** Cache misses on the bucket array at scale (the table is 8× L3 at Light DB and out-of-cache entirely at Full DB); `ByteBuffer.equals(other)` byte compare on the matched node (~15-25 ns); GC pressure if `ConsumerJava.java:367-371`'s "thread-local reusable ByteBuffer" turns out to allocate per call rather than reuse (verify before benchmarking — at 188 M ops/sec a per-call `ByteBuffer.wrap()` would be ~9 GB/sec of allocation pressure).
  - **What needs to be designed first** (before any kernel changes): the canonical reference of `HeapByteBuffer.hashCode()` semantics that the JMH guard will pin against (capture the bytecode of `java.nio.HeapByteBuffer#hashCode` for the running JDK and assert it matches a known-good copy at build time, so a JDK upgrade can't silently corrupt the GPU formula); whether `ConsumerJava` carries the precomputed hash through `AbstractProducer`/`AbstractKeyProducerQueueBuffered` as a parallel `int[]`/`IntBuffer` next to the existing hash160 buffers, or extends the per-candidate result struct in place; whether the `HashSet_PrecomputedHash` map should fall back to JDK `HashSet<ByteBuffer>` semantics on CPU-only paths (e.g. `ProducerJava` producers that don't go through OpenCL) by computing the same hash on the CPU side using the same reference formula — yes, for consistency across producers.

- **Push the `TRUNCATED_LONG_64` presence check into the OpenCL pipeline.** Today the GPU kernel returns derived hash160 bytes per candidate key and the CPU loop calls `lookup.containsAddress(...)` on each. For large workgroup sizes this serialises the most expensive part of the pipeline (the LMDB / in-RAM membership check) on a single CPU thread, throwing away the GPU's parallelism. Move the presence check on-GPU as follows:
  - **Upload the snapshot once at startup.** Right after `TruncatedLong64SortedArrayPresence.populateFrom(lmdb)` builds the 256 sorted `long[]` buckets in host RAM, copy each bucket into device global memory (`cl_mem` buffer per bucket, plus a small offset/length index). At ~8 B/entry this fits comfortably in modern GPU VRAM for any practical database size (~1.1 GB for the Light DB, ~11 GB for the Full DB; the latter may need streaming on smaller cards).
  - **Output flags, not raw hashes.** The kernel's result per candidate becomes a small struct/bitmask: `{ uint flags; uchar20 hash160; }`. Bit 0 of `flags` = "probably present" set by the on-GPU presence check. CPU-side `ConsumerJava` only forwards results with the flag set to the LMDB delegate for exact verification, mirroring today's `AddressPresence` + delegate contract. False positives behave exactly like the `TRUNCATED_LONG_64` semantics on the CPU path (~7.5 × 10⁻¹¹ per query for the Full DB).
  - **Phased bucket processing inside the workgroup.** OpenCL local (shared) memory per workgroup is constrained (typically 32-64 KB); a full bucket (~43 MB at Full DB scale) does not fit. So the workgroup processes its candidates in **256 phases**, one per first-byte bucket, in lockstep across all workgroups:
    1. Each thread in the workgroup derives its candidate hash160 once and stores `(firstByte, longKey)` to private memory.
    2. For each phase `b` ∈ [0, 255]: cooperatively load bucket `b` from global memory in tiles that DO fit into local memory; every thread whose `firstByte == b` runs a branchless binary search of its `longKey` against the loaded tile; tiles are streamed through local memory until the bucket is exhausted; threads whose `firstByte != b` participate in the cooperative load (memory bandwidth) but skip the search.
    3. After phase 255 every thread has its `flags` bit set or cleared and the result struct is written to global memory.
    Alternative phase boundary: if the per-thread `firstByte` distribution is too skewed within a workgroup, sort candidates within the workgroup by `firstByte` first (one parallel radix pass over 256 keys) so the per-phase active-thread mask is large enough to be worth the cooperative load.
  - **What this buys — and the throughput trade-off that has to be measured first.** The naive framing is "the slow path (~108 ns/op CPU per `containsAddress`) collapses into the GPU's existing parallelism budget; the CPU only sees and follow-up-verifies the small set of 'probably present' candidates." That is only true if the GPU has *spare* compute and bandwidth headroom — adding work-per-thread is **not free**. Failure modes that can turn this from a win into a regression:
    - **Compute saturation.** The current kernel is dominated by secp256k1 scalar multiplication (compute-bound on the ECC inner loop). Each work-item that *also* runs 256 phases of cooperative tile loading + per-phase binary search lengthens the work-item; if the GPU was already at ~100 % ALU occupancy on ECC, the presence check serialises behind that and the wall-clock per work-item grows almost linearly with the added work.
    - **Memory-bandwidth competition.** ECC is compute-bound; the presence-check phase is memory-bound (~1.1 GB Light DB or ~11 GB Full DB streamed through global memory each scan). On a GPU whose global-memory bandwidth is already a constraint (compact consumer cards in particular), the presence-check phase steals bandwidth from kernels that may need it later, and the cooperative-load tile fills can stall warps that ECC was not stalling.
    - **VRAM displacement.** The 1.1–11 GB snapshot competes for VRAM with the workgroup's own state and any other kernel resources. On 8 GB cards the Full DB snapshot doesn't fit; on 12–16 GB cards it fits but leaves little headroom for batch growth.
    - **The crossover question** that decides whether this TODO is worth doing at all:

      | Scenario | CPU `.contains()` saturated? | Adding presence check to GPU is… |
      |---|---|---|
      | CPU has spare cores (consumer feed is the bottleneck) | No | **Likely a regression** — GPU slows, CPU lookups remain easy. Don't ship. |
      | CPU saturated, GPU has headroom | Yes | **Likely a clear win** — even a 10–30 % per-work-item slowdown on the GPU is dominated by the relief on the CPU side. |
      | CPU saturated and GPU near-saturated on ECC | Yes | **Maybe a wash** — the kernel gets slower in proportion to the CPU relief. Needs measurement. |

      Decide on **end-to-end pipeline throughput** (candidates verified per second, end-to-end), **not** on CPU `containsAddress` latency in isolation. The README's 108 ns/op figure is the right input for the CPU-side ceiling, but the GPU-side cost has to be measured directly because it depends on the specific GPU model, the snapshot size, and the workgroup configuration.
    - **The measurement plan that has to precede any kernel changes:**
      1. **Baseline today** — record candidates/sec, GPU kernel time per launch, and CPU `containsAddress` time per call at the configurations of interest (Light DB + TRUNCATED_LONG_64; Full DB + TRUNCATED_LONG_64; both with `loopCount` matching production).
      2. **CPU headroom probe** — saturate the CPU `containsAddress` path artificially (run more producer threads than `containsAddress` can handle) to verify the CPU side actually can become the bottleneck under realistic load. If it never saturates, this TODO targets a non-bottleneck and should be deprioritised.
      3. **Kernel-cost simulation** — without writing the full presence-check kernel, ship a stub that adds a deterministic, comparable-cost workload to each work-item (e.g. a fixed number of dummy reads from a 1 GB device buffer) so the kernel-slowdown side of the trade-off is quantified before the real implementation.
      4. **Decision threshold** — proceed with the real implementation only if step 2 confirms the CPU is the bottleneck *and* step 3 shows kernel slowdown ≤ the CPU relief at the workgroup size of interest. If either condition fails, the right answer is to fix the CPU side (switch backend, batch-prefetch, or do the smaller "GPU pre-computed hash" TODO above) instead.
    - **What this buys when the trade-off is favourable.** With the CPU saturated and the GPU having ≥ 20–30 % compute headroom: the slow path (~108 ns/op CPU) effectively disappears from the pipeline and the bottleneck shifts elsewhere (typically PCIe upload of the producer keystream or LMDB verification of the now-tiny "probably present" subset). For a typical workgroup of 256 threads at 10 M candidates/s under those conditions, the difference is ~100 ms/s of CPU lookup overhead vs ~negligible. Under the *unfavourable* trade-off, expect **net regression** — kernel time grows by more than the CPU saves, throughput drops, and the only thing gained is a more complex pipeline.
  - **What needs to be designed first** (before any OpenCL code is written): the cooperative-load tile size (function of GPU local memory + bucket size + workgroup size); how the result struct flows through the existing `OpenClTask` + `OpenCLGridResult` types; the upload path (one-shot at `OpenCLContext.init()` or per-kernel-launch); whether the snapshot is rebuilt on the GPU after each LMDB update or kept immutable for the run (current expectation: immutable per scan session, matches the CPU `populateFrom` contract).

- **Long-term vision: end-to-end address scan on the GPU; CPU is a thin verifier only.** The "push `TRUNCATED_LONG_64` presence check into OpenCL" TODO above is the first concrete step; this entry captures the broader end-state it leads to. **North star**: a single scan invocation is one GPU pipeline that emits only the small set of "probably present" candidates back to the CPU; the CPU's only remaining job is to verify those few candidates against LMDB. Everything currently sitting between GPU output and LMDB (`BLOOM`, `HASHSET`, `TRUNCATED_LONG_64` on the CPU path) becomes optional / disable-able because the GPU's own filter has already done the work.
  - **Two-phase per-launch pipeline on the GPU:**
    1. *Address generation phase* (already exists): every thread derives its candidate's hash160 and stores it to private memory.
    2. *Address lookup phase* (new): the workgroups consult the pre-loaded snapshot in global memory and write back a small `{flags, hash160}` record per thread. Flag bit 0 = "probably present", which is the only signal the CPU consumes.
  - **Snapshot lives in GPU global memory and is loaded once per session.** At startup the JVM builds the `TRUNCATED_LONG_64` snapshot (256 sorted `long[]` buckets, ~1.1 GB at Light DB scale, ~11 GB at Full DB) and uploads it to device global memory. Modern consumer GPUs have 8-24 GB of VRAM; the Light DB fits comfortably, the Full DB needs higher-end cards or out-of-core streaming. The upload is one-shot — the snapshot does not change during a scan session.
  - **Per-workgroup local-memory tiles.** Workgroup local (shared) memory is typically 32-64 KB on consumer GPUs and 96-100 KB on workstation cards — far smaller than even a single full bucket at Full DB scale (~43 MB). The lookup phase streams each bucket through local memory in tiles sized to the workgroup's local-memory budget; threads cooperatively load each tile, then every thread whose `firstByte` matches the bucket index runs a branchless binary search against the loaded tile before the workgroup advances to the next tile.
  - **All workgroups process the same bucket at the same time.** The 256 phases (one per first-byte bucket) advance in lockstep across all workgroups so that the streamed bucket data is read once from global memory per phase rather than per workgroup. Threads whose `firstByte` does not match the active bucket participate in the cooperative load (memory-bandwidth contribution) but skip the search.
  - **The "looser-but-larger candidate set is OK" trade-off.** A GPU-side filter does not need to be as exact as the CPU-side TRUNCATED_LONG_64. As long as the rate of "probably present" flags reaching the CPU stays low enough that CPU-side LMDB verification keeps up with the GPU's throughput, a coarser GPU filter is acceptable. Example: a smaller stored value per entry (4 bytes / 32 bits instead of 8) cuts VRAM cost in half and the corresponding ~N/2^32 false-positive rate is still low enough that the CPU handles the resulting "hits" without becoming the bottleneck.
  - **Configuration shift implied.** Today `addressLookupBackend` selects the in-RAM CPU accelerator. After this work it should add a value like `GPU_ONLY` (or be replaced by a richer `consumerJava.lookupChain: [...]` config) where the operator declares "the GPU filter is the front-line; the CPU pipeline only verifies the flagged candidates against LMDB". `BLOOM` / `HASHSET` / `TRUNCATED_LONG_64` remain available for CPU-only setups or as a fallback while the GPU pipeline is still being commissioned.
  - **Why this is the right end-state.** The CPU's address-check budget today (~108 ns/op for `TRUNCATED_LONG_64`) is the only synchronisation point between the GPU's parallelism and the verification path. Moving that check to the GPU collapses the CPU contribution to "follow up on the small flagged subset" — at typical false-positive rates this is a few candidates per million derivations, well below any conceivable CPU bottleneck. The CPU then truly becomes the orchestrator (start kernels, read flagged results, verify against LMDB, log hits) rather than the rate-limiting step.

## Open — cross-cutting (slice for this repo)

- **jqwik pin policy** — see [`../workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md). `jqwik.version ≤ 1.9.3` is mandatory.

- **`@VisibleForTesting` audit.** 10 sites remaining (down from 19 — see workspace `crossrepostatus.md` for the site-by-site audit). All 10 are legitimate per the design-fit review; no further cleanup recommended unless the source moves.

- **Null-safety further refinement.** JSpecify + NullAway are enforced at compile time in **strict JSpecify mode** with the extra options `CheckOptionalEmptiness`, `AcknowledgeRestrictiveAnnotations`, `AcknowledgeAndroidRecent`, `AssertsEnabled` (see `pom.xml`). Every package carries an explicit `@NullMarked` via `package-info.java` so the convention is visible to non-NullAway tools (IDEs, Kotlin, Checker Framework). The 50 `@Nullable` sites currently in the codebase are all legitimate. `OpenCLContext.getOpenClTask()` returns `Optional<OpenClTask>` rather than `@Nullable OpenClTask` to surface the lifecycle state in the type. Open follow-up: review any future-added public API surfaces for places where `@Nullable` would be more precise than the implicit non-null default; consider whether further `@Nullable T` returns should migrate to `Optional<T>` on a case-by-case basis (the project's established convention is `@Nullable`; Optional is used selectively for lifecycle-shaped APIs).

- **SpotBugs `effort=Max` + `threshold=Low`** — ✅ **enforced at the gate** (`76fd1a7`). `pom.xml` `<effort>Max</effort>` + `<threshold>Low</threshold>`; `spotbugs:check` is part of `mvn verify` and fails on any unsuppressed finding. The full clearing chain (191 → 0) is recorded in [`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md) under "SpotBugs Max+Low". `spotbugs-exclude.xml` carries narrow `<Match>` blocks with rationale for every structural false positive (Lombok-USBR, project-wide CRLF mitigation, generic-erasure CHECKCAST in keyproducer, `@FireAndForget` Future-DLS, Producer interface heterogeneous throws, drain-pattern PRMC, CWE-338 demo RNG, secp256k1 curve params, JOCL-spec nulls, preserved-for-revival private helpers, plus the two opt-in lifecycle items below).

- **Mutation-testing threshold expansion** — the gate now covers a verified-100% **15-class** list (`util.BitHelper` + `util.PrivateKeyTooLargeException` + `model.PublicKeyBytes`/`AddressToCoin`/`AddressType` + the 8 custom exceptions + `statistics.Statistics`/`ReadStatistic` + `configuration.CKeyProducerJavaIncremental`; 59 mutations, pitest-maven 1.25.3). The earlier `net.ladenthin.bitcoinaddressfinder.BitHelper` target was stale (BitHelper moved to `util/` in the restructure → gate matched nothing) and has been fixed. `model.Hash160` is deliberately excluded (its fast/slow hash paths are identical, so the `if(useFast)` negate mutant is equivalent). Still open (optional): config getters covered only by producer/keyproducer integration tests, and the larger orchestration classes (producer / consumer / engine / opencl) which need heavier fixtures.

- **Additional ArchUnit rules to consider** — public-fields-final, `noTestFrameworksInProduction`, `loggersArePrivateStaticFinal`, `noPackageCycles`, the full **`layeredArchitecture()`** rule, and **per-module banned-imports** (`joclConfinedToOpencl`, `networkInputLibsConfinedToKeyproducer`, `lmdbConfinedToPersistenceAndIo`) are all DONE. Still open:
  - No-public-mutable-static-state rule.

- **Cross-repo code-quality TODOs** — see [`../workspace/policies/code-quality-todos.md`](../workspace/policies/code-quality-todos.md) for the canonical `@VisibleForTesting` design-fit review (BAF site-by-site audit captured in [`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md)), package hierarchy review, and class/method naming review.

- **Drop the catch-rethrow `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION`
  suppression in `spotbugs-exclude.xml`** once a SpotBugs release
  ships with [PR #4087](https://github.com/spotbugs/spotbugs/pull/4087)
  merged. That PR fixes the detector to match the exception-handler
  ASTORE register against the local variable being thrown, so the
  catch-then-rethrow-of-the-same-RuntimeException pattern on
  `AbstractProducer.produceKeys` and `LMDBPersistence.addresses` will
  stop firing the warning. Tracking issue:
  [spotbugs/spotbugs#3918](https://github.com/spotbugs/spotbugs/issues/3918).
  Verification when removing the suppression: run `mvn spotbugs:check`
  and confirm zero `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION` findings on
  those two methods.

- **(Unblocked, optional) Drop the project-wide `OPM_OVERLY_PERMISSIVE_METHOD`
  suppression in `spotbugs-exclude.xml`.** The package-architecture refactor it
  was waiting on has now landed (the single root package was split into the
  layered packages — see "Done" history below), so cross-layer call sites are
  now stable and OPM findings would be actionable signals rather than
  correct-but-unstable noise. Re-enabling is **optional**: visibility
  minimisation is not a project goal (the original tightening pressure was
  fb-contrib noise, not a requirement). If re-enabled, delete the project-wide
  `<Match>` and triage the resulting findings (snapshot at suppression time:
  ~33 sites — Main CLI internal helpers, test-only public surface,
  abstract/concrete constructors, internal helpers, one `enum.valueOf` false
  positive).

## Done (kept for history)

### Layered-rule tightening + latent upward-edge fix (fact-based jdeps audit)

A bytecode-level (`jdeps`) audit of the compiled package graph found one latent
upward coupling that the layered rule did not catch: `util.Bech32Helper`
statically imported `io.AddressTxtLine.BITCOIN_CASH_PREFIX` — a Foundation→io
edge (latent `util`&harr;`io` cycle) hidden from ArchUnit only because the
`static final String` constant is inlined at compile time. The constant moved
to the `constants` leaf (`constants.AddressConstants.BITCOIN_CASH_PREFIX`); both
`io` and `util` now depend strictly downward on it.

With that edge gone, the `layeredArchitecture()` access lists were tightened to
the exact set of layers that reach each layer today (verified by jdeps):
`Pipeline` only by `Orchestration`; `InputOutput` only by
`Orchestration`/`Pipeline`/`Capabilities` (not `Entry`); `Config` not by
`Foundation`; `Foundation` not by `Config`. Any new unintended cross-layer edge
now fails the build. (jllama and plugin were audited the same way and were
already exact — no slack found.)

### Layered package restructure (flat root package → 10-layer hierarchy)

The 48 classes that previously sat flat in the root
`net.ladenthin.bitcoinaddressfinder` package were split (via `git mv`,
history preserved) into dedicated layered packages so the package
boundaries align with the architectural layers:

- **Foundation**: `model` (Hash160, PublicKeyBytes, AddressToCoin, AddressType),
  `util` (KeyUtility, PrivateKeyValidator, Bech32Helper, Base36Decoder,
  BitHelper, ByteBufferUtility, NetworkParameterFactory, ByteConversion,
  EndiannessConverter, PrivateKeyTooLargeException), `core` (Interruptable,
  Startable, FireAndForget, InterruptedRuntimeException), `secret`
  (SecretSupplier, RandomSecretSupplier, NoMoreSecretsAvailableException,
  BIP39Wordlist), `statistics` (Statistics, ReadStatistic).
- **InputOutput**: `io` (AbstractPlaintextFile, AddressFile, AddressTxtLine,
  SecretsFile, SeparatorFormat, FileHelper, AddressFormatNotAcceptedException).
- **Pipeline**: `producer` (Producer, AbstractProducer, ProducerJava,
  ProducerOpenCL, ProducerJavaSecretsFiles, ProducerState,
  ProducerStateProvider), `consumer` (Consumer, ConsumerJava).
- **Orchestration**: `engine` (Finder, Shutdown), `command`
  (AddressFilesToLMDB, LMDBToAddressFile).
- Absorbed into existing capability packages: OpenCL runtime
  (OpenCLContext, OpenClTask, OpenCLGridResult, ReleaseCLObject) → `opencl`;
  BIP39KeyProducer → `keyproducer`.

Test classes were mirrored into the same packages as their subjects
(standard Maven layout). The only in-class change was moving the pure
helper `calculateSecretKey(BigInteger, int)` from `AbstractProducer` to
`KeyUtility` (foundation) to break the single `opencl → producer`
back-edge; everything else was package moves + import updates +
cross-layer `public` promotions. The `secret` foundation package was
introduced to host the secret/mnemonic primitives that `KeyUtility`
depends on, breaking the `util ↔ keyproducer` and `producer ↔ keyproducer`
cycles.

Enforced by the new `layeredArchitecture()` ArchUnit rule in
`BitcoinAddressFinderArchitectureTest` (strict top-to-bottom: Entry →
Orchestration → Pipeline → Capabilities → InputOutput → Foundation →
Config → Constants), alongside the retained `noPackageCycles` and the
targeted leaf rules. All 13 architecture rules green; `module-info.java`
exports updated for the new packages.

### SpotBugs Max+Low concurrency refactors (replaces spin-sleep with event-driven primitives)

Three structural refactors landed alongside the Max+Low gate flip so the
remaining `MDM_THREAD_YIELD` sites were resolved at source rather than
suppressed. The Javadoc on each touched class records the rationale; the
ArchUnit comment in `BitcoinAddressFinderArchitectureTest` documents the
two `Thread.sleep` sites that remain (and why they are correct).

- **`AbstractProducer.waitTillProducerNotRunning`** — replaced the
  spin-on-`state==RUNNING` + 10 ms sleep with a `CountDownLatch`
  awaited via `cProducer.shutdownTimeoutSeconds`. The new
  `signalNotRunning()` helper counts down at both `NOT_RUNNING`
  transitions in `run()` and serves as the test seam. Eliminates
  up-to-10 ms shutdown-wake-up latency per producer; deletes the
  17-line apology Javadoc on `WAIT_TILL_NOT_RUNNING_RESTORES_INTERRUPT_FLAG`
  (commit `892b76a`).

- **`ConsumerJava.consumeKeysRunner`** — extracted the per-batch
  processing into a private `processBatch` helper, leaving
  `consumeKeys(ByteBuffer)` as a drain-only utility for tests. The
  runner now waits on `keysQueue.poll(delayEmptyConsumer, MILLISECONDS)`
  between drain cycles instead of `Thread.sleep(delayEmptyConsumer)`,
  so the worker wakes the instant a producer enqueues. Idle-to-active
  latency drops from up-to-100 ms (default) to ~0; steady-state
  throughput unchanged (commit `99f390f`).

- **`ProducerOpenCL.processSecretBase`** — replaced the spin on
  `ThreadPoolExecutor.getActiveCount()` with the JCIP §8.3.3
  `BoundedExecutor` pattern: a `Semaphore(maxResultReaderThreads)`
  acquired before `execute()` and released in the runnable's outer
  `finally`. The release-on-rejection path is wrapped via a `submitted`
  flag so a shutdown-race `RejectedExecutionException` does not leak a
  permit. **Important: the spin-wait was the only backpressure on the
  result-reader pool's unbounded inner `LinkedBlockingQueue` — without
  the semaphore the GPU would have submitted faster than the readers
  could drain, holding result buffers in memory indefinitely.** The
  semaphore is therefore the *only correct* backpressure primitive
  here, not just a polish. `getFreeThreads()` now returns
  `submitSlot.availablePermits()` (same semantics).
  Removes up-to-100 ms GPU-pacing latency (commit `09c5d52`).

  **Config breaking change (acknowledged)**: `delayBlockedReader` was
  the polling-delay knob feeding the deleted spin. The `Semaphore`
  wakes immediately, so the field is removed from `CProducerOpenCL`
  and from the three example JSON configs (`examples/config_Find_1OpenCLDevice.json`,
  `config_Find_1OpenCLDeviceAnd2CPUProducer.json`,
  `src/test/resources/testRoundtrip/config_Find_1OpenCLDevice.json`).
  External configs referencing it will deserialize-fail; users should
  delete the line.

### -Werror flip (BAF-specific, completed this session)

All blockers cleared and `<arg>-Werror</arg>` is on in `pom.xml`:

- Original 6-item pre-flip warnings — cleared per the 4274c25 / 5e3f6a8 / 523fc79 / 62603d3 / da4cab7 / 84b35cb tranche (Thread.getId; deprecated jocl CL_DEVICE_QUEUE_PROPERTIES; two `this` escapes in KeyProducerJavaSocket/Zmq; Closeable.close InterruptedException; explicit close on auto-closeable in OpenClTask).
- 14 Checker Framework `[type.anno.before.modifier]` warnings — `f37f162` (moved `@NonNull`/`@Nullable` after modifiers in 8 files).
- 5 final `sun.misc.Unsafe` proprietary-API warnings — `2881c96` (deleted `ByteBufferUtility#freeByteBuffer` entirely; the eager `Unsafe.invokeCleaner` path was already a no-op on OpenJ9 / GraalVM Native Image / Android per its own Javadoc — HotSpot now joins them via the JVM's built-in Cleaner).

### Strictness ladder

- **Error Prone bug-pattern promotions to `ERROR`** — 12 high-confidence patterns at `pom.xml:344`.
- **`-parameters` javac arg** — `pom.xml:315`.
- **`--release N`** — main compile `<release>21</release>` (`pom.xml:313`); `module-info-compile` execution stays at `--release 9`; `default-testCompile` overrides back to `<source>/<target>` because tests legitimately import `jdk.internal.ref.Cleaner` and `sun.nio.ch.DirectBuffer`.
- **Mutation-testing threshold enforcement (PIT)** — runs every CI build with `<mutationThreshold>100</mutationThreshold>`; `<targetClasses>` now an explicit 15-class verified-100% list (was the stale `BitHelper` target — see the open "Mutation-testing threshold expansion" item above for the current list and exclusions).
- **Checker Framework as a second static-nullness pass** — Nullness Checker (4.1.0) alongside NullAway. `src/etc/checker/objects.astub` overrides the CF 4.1.0 `Objects.requireNonNull` stub. JOCL-wrapping classes (`OpenCLContext`, `OpenClTask`, `opencl/OpenCLBuilder`) carry class-level `@SuppressWarnings({"nullness:argument", "nullness:dereference.of.nullable"})`. `KeyProducerJavaWebSocket` carries the documented this-escape suppression (Socket / Zmq were refactored to the `Startable` lifecycle so no suppression is needed there). `PublicKeyBytes.equals(Object)` takes `@Nullable Object`; `BIP39Wordlist.getWordListStream()` returns `@Nullable InputStream`.
- **JPMS `module-info.java`** — lives in `src/main/java9/` (a separate source root) so javac at source/target 21 does not auto-activate module mode on the test sources. The `module-info-compile` execution is bound to `prepare-package` rather than `compile` so `module-info.class` is not present in `target/classes/` while the test sources compile or run. The module opens `net.ladenthin.bitcoinaddressfinder.configuration` to `com.fasterxml.jackson.databind` so Jackson can populate the configuration POJOs reflectively on any non-public members added later. Module-level `@NullMarked` was intentionally NOT added — the per-package annotation covers the same scope and avoids pulling JSpecify into the module's `requires` graph. Local-dev caveat: `mvn test` after `mvn package` without an intervening `mvn clean` fails with `IllegalAccessError`; CI is unaffected because the Build and Test jobs run in separate runners with fresh checkouts.
- **Banned-API enforcement** — Maven Enforcer `bannedDependencies` + `dependencyConvergence` (`pom.xml:268-283`); ArchUnit `noSystemExit` / `noNewRandom` / `noThreadSleep` rules (`BitcoinAddressFinderArchitectureTest:137,164,178`); `sun.*` / `com.sun.*` / `jdk.internal.*` import ban (`BitcoinAddressFinderArchitectureTest:90-97`).
- **ArchUnit additions** — public-fields-final (`BitcoinAddressFinderArchitectureTest:120-130`).

### Persistence backends

- **HashSet snapshot** — `persistence/inmemory/HashSetAddressPresence.java` (presence-only).
- **TRUNCATED_LONG_64 backend** — `persistence/inmemory/TruncatedLong64SortedArrayPresence.java` (256-bucket sorted `long[]`).
- **BloomFilter extraction** — `persistence/bloom/BloomFilterAccelerator.java` (standalone wrapper; LMDBPersistence no longer carries the Bloom fields directly).
- **Backend config selector** — `configuration/AddressLookupBackend.java` enum + `CLMDBConfigurationReadOnly.addressLookupBackend` field + `ConsumerJava.java:183-189` dispatch. Default remains `BLOOM`.
- **Layered/chained backend contract** — `persistence/AddressPresence.java` (minimal "is this address present?") + `persistence/AddressLookup.java` (extends with `getAmount`). Decorators fall through on positive answers; self-contained snapshots return `requiresBackend()==false` after `populateFrom(lmdb)` and the LMDB env is closed.
- **Lookup benchmark (JMH)** — `src/test/java/net/ladenthin/bitcoinaddressfinder/benchmark/AddressLookupBenchmark.java` (`@Param({"LMDB_ONLY","BLOOM","HASHSET","TRUNCATED_LONG_64"})`, `Mode.AverageTime`, `OutputTimeUnit.NANOSECONDS`, 0xC0FFEE seed + 2 048 LMDB entries + Bloom FPP 0.01).
- **Removed dead `loadToMemoryCacheOnInit`** from all 4 stale example JSONs (`config_AddressFilesToLMDB.json`, `config_Find_1OpenCLDevice.json`, `config_Find_1OpenCLDeviceAnd2CPUProducer.json`, `config_Find_SecretsFile.json`) and from `README.md`.

**Historical context (kept for the pre-Bloom design rationale).** A HashSet-based in-memory persistence DID exist pre-Git but was removed in pre-Git commit `f153a1bdb363c16bbe86134d360f4c2e4423d3e7` ("Replace in-memory HashSet with Bloom filter for address lookup optimization", 2025-07-10), ~10 months before this repo's boot commit (`2c8e9f1`, 2026-05-08). That commit is not in this repository — only its post-state was imported. The current `HashSetAddressPresence` is the *resurrection* of that earlier design with a cleaner contract (no LMDB coupling once populated). The same removal commit also contained a commented-out `sortedAddressCache` variant using `Arrays.binarySearch(...)` — that is what `TruncatedLong64SortedArrayPresence` ships, with the additional optimization of truncating each hash160 to its first 8 bytes (256-bucket sharding plus the truncation gives a ~7.5×10⁻¹¹ false-positive rate at Full DB scale — negligible in practice). Memory cost reference: HashSet shape was ~50 B/entry (ByteBuffer wrapper + 20-byte payload + HashMap.Node), so ~6.6 GB for the README's 132M-entry light db and ~70 GB for the 1.377B-entry full db; TRUNCATED_LONG_64 cuts that roughly 10× (~660 MB / ~7 GB respectively). Future history lookups for the pre-Bloom design need access to that external repository.

### GPU grid-size sweep benchmark

- **GPU grid-size sweep DONE.** `src/test/java/net/ladenthin/bitcoinaddressfinder/benchmark/GridSizeSweepBenchmark.java` sweeps `CProducer.batchSizeInBits` × `CProducerOpenCL.loopCount`. Kernel entry is `OpenCLContext.createKeys(BigInteger privateKeyBase)`. Availability gate is `new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable()`. `@Fork` includes the project-canonical `--add-opens=java.base/sun.nio.ch=ALL-UNNAMED` + `--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED` so JMH's forked JVMs match the `argLine` Surefire uses. Throughput unit is kernel launches per second at each corner; candidates/sec = launches/sec × `(1 << batchSizeInBits)`. JMH's `@OperationsPerInvocation` cannot normalize this automatically because it needs a compile-time constant and `@Param` is runtime; documented in the class Javadoc.

  **NOT imported from the cjherm/BAF23 fork** (intentional scope cap): the `BenchmarkFactory` / `BenchmarkSeries` / `BenchmarkLogger` / `LatexContentCreator` harness, the `command: "BenchmarkSeries"` `CCommand` extension, and the SHA / RIPEMD-160 GPU-vs-CPU comparison rounds.

  **Context-reuse / init-cost-amortisation sweep — explicitly NOT imported.** The fork ships `CtxRoundsIteratorBenchmark` (fix `gridNumBits`, vary kernel-launches-per-context, measure init-cost amortisation curve). It is operationally meaningless for this codebase: `ProducerOpenCL` creates the `OpenCLContext` once in `initProducer()`, runs `createKeys(BigInteger)` on every produced batch, and closes the context once in `releaseProducer()`. The smallest production scan is ≳ 10⁶ kernel launches against the one long-lived context; init cost is already amortised to noise. Re-importing this idea later requires evidence that BAF's lifecycle has changed to short-burst / one-shot scans.

### Workspace migration

- **Abstract the Java and test writing guidelines to a workspace-level shared layer.** Canonical guides at [`../workspace/guides/src/CODE_WRITING_GUIDE-8.md`](../workspace/guides/src/CODE_WRITING_GUIDE-8.md) (Java 8 baseline) + [`CODE_WRITING_GUIDE-21.md`](../workspace/guides/src/CODE_WRITING_GUIDE-21.md) (Java 21 supplement, applies to this repo), and [`TEST_WRITING_GUIDE-8.md`](../workspace/guides/test/TEST_WRITING_GUIDE-8.md) + [`TEST_WRITING_GUIDE-21.md`](../workspace/guides/test/TEST_WRITING_GUIDE-21.md); canonical TDD skill at [`../workspace/.claude/skills/java-tdd-guide/SKILL.md`](../workspace/.claude/skills/java-tdd-guide/SKILL.md). BAF's `CODE_WRITING_GUIDE.md` / `TEST_WRITING_GUIDE.md` now contain only BAF-specific supplements.
- **Standardised CLAUDE.md template** — [`../workspace/templates/CLAUDE.md.template`](../workspace/templates/CLAUDE.md.template).
