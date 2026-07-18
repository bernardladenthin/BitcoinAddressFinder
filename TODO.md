# TODO — BitcoinAddressFinder

Open work items for this repo. Cross-cutting tracking lives in
[`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md);
items here are BAF-specific or are this repo's slice of a
cross-cutting initiative.

## Open — BAF-specific

### Persistence backends (genuinely open work)

Implementation of `BLOOM` / `HASHSET` / `TRUNCATED_LONG_64` /
`BINARY_FUSE_8` / `BINARY_FUSE_16` / `BLOCKED_BLOOM` is DONE
(see "Done" history below). The remaining open items in the
pluggable-persistence design plan:

- **Prefetch/readahead the LMDB scan during filter construction (I/O-latency bound, measured).** Every
  in-RAM backend is populated by a single-threaded cursor walk over the LMDB mmap, and on a database
  larger than free RAM that walk — not the filter work — is what dominates the build. Measured while
  building `BLOCKED_BLOOM` over the 1.377 B-entry Full DB (61 GB) with ~27 GB of the file warm in page
  cache, per 10 % segment:

  | segment | 0→9 % | 9→19 % | 19→29 % | 29→39 % | 39→49 % | 49→59 % | 59→69 % |
  |---|--:|--:|--:|--:|--:|--:|--:|
  | time | 48 s | 48 s | 47 s | 55 s | 110 s | 330 s | **325 s** |
  | rate | 2.85 M/s | 2.87 M/s | 2.96 M/s | 2.49 M/s | 1.25 M/s | 0.42 M/s | **0.42 M/s** |

  Two regimes, not an open-ended decline: ~2.9 M/s while pages are cached, then a ~7× drop that
  **plateaus at a steady ~0.42 M/s** once the walk is fully in cold territory (the last two segments
  match to within 2 %). The insert work per key is constant, so the entire curve is the cost of
  *reaching* the next key.

  **The dominant variable is free RAM, not cache warmth** — an earlier reading of these numbers had
  that causality backwards. The 1 869 s build above began with RAM already contended (~2 GB free
  after a 12 GB-heap JMH run), so pages were evicted before reuse and re-read. A later build of the
  *same* database starting from a deliberately emptied cache but **58 GB free** took **1 043 s** —
  nearly 2× faster despite being colder, because file pages could accumulate instead of evicting one
  another. Cache warmth helps only the prefix; headroom helps the whole pass. The process sat
  **Root cause is read amplification, not an idle disk.** Measured in the cold regime on the NVMe
  backing the database: **332 MB/s, 8 279 reads/s, 86 % disk-busy, queue depth 1**, with free RAM down
  to 2.3 GB and the JVM working set at 54 GB. Useful entry throughput at that moment was only
  0.43 M/s × ~44 B/entry ≈ **19 MB/s** — so roughly **17× more data is read than consumed**. Over the
  39 %→89 % span the disk moved ~460 GB for ~30 GB of file content, against a 61 GB file: pages are
  read, evicted before use, and re-read.

  Mechanism: the database is built by inserting *random* hash160 keys, so LMDB B-tree pages in **key
  order** are scattered in **file order**. A key-ordered cursor walk therefore jumps across the 61 GB
  file, and the OS's ~40 KB readahead (332 MB ÷ 8 279 reads) drags in neighbours that are evicted
  before their turn arrives. Once RAM is exhausted this degenerates into thrashing.

  Fix candidates, cheapest first:
  1. **`EnvFlags.MDB_NORDAHEAD` — implemented, but STILL UNVALIDATED (Windows no-op).** Available as
     the opt-in `CLMDBConfigurationReadOnly#useNoReadAhead`, default `false`. **LMDB does not
     implement this flag on Windows** — lmdbjava's javadoc: *"Don't do readahead (no effect on
     Windows) … The option is not implemented on Windows."* It is a POSIX-only lever.

     A cold-cache A/B was run on Windows before that was checked (each arm preceded by a 46 GiB
     `PageCacheBuster` pass): baseline 1 042.8 s vs `MDB_NORDAHEAD` 965.8 s. **That 7.4 % gap is
     run-to-run variance, not an effect** — both arms executed identical code paths, and FPR
     (0.004847) plus retained size (2052.1 MiB) were bit-identical. Useful byproduct: it calibrates
     the **noise floor of the full-DB build measurement at ~7-8 % for n = 1**, so smaller effects
     need repeats to be credible.

     **To settle it:** run the same A/B on Linux, where the flag actually does something, with
     alternating arm order, matched free RAM, and enough repeats to beat that ~8 % noise floor. Note
     CI already runs Ubuntu, so a Linux measurement is reachable. Lesson recorded: check a flag's
     platform support *before* spending measurement time on it.
  2. Raise the effective queue depth — the disk sat at **QD 1**, so it is latency-serialized even
     while 86 % busy; overlapping reads could recover throughput.
  3. Rebuild/compact the database in key order so the logical walk becomes physically sequential
     (largest win, but a one-off offline cost on a 61 GB store).

  Note this supersedes an earlier reading of these numbers as "the SSD is idle at 4 K IOPS, add
  readahead" — that sample was taken while the page cache was still warm and the working set growing.
  The disk is *not* idle, and adding more readahead would make the amplification worse. None of this
  touches filter logic and it benefits *every* backend (`HASHSET` and `TRUNCATED_LONG_64` walk the
  same cursor — the latter twice). Re-measure with `FilterMeasurementMain` (see
  `docs/performance.md` §6); the per-10 % progress lines make the curve directly visible.

- **Size blocked Bloom with a multiply-shift instead of power-of-two block counts.**
  `chooseLogBlocks` rounds the block count up to a power of two so the block index is a shift. The
  cost is up to 2x wasted memory at unlucky entry counts: at 100 M a requested 11 bits/entry becomes
  21.47 (256 MB instead of ~131 MB); at 10 M it becomes 13.42 (1.22x). That waste is not academic --
  it is the most likely cause of the weakest GPU result measured (RDNA3 at 100 M, only 1.13x over
  Fuse-8, where a 256 MB filter spills a 96 MB Infinity Cache that a correctly sized ~131 MB one
  largely would not).

  Fix: `block = mulhi(hash, numBlocks)` (Lemire's fastrange), which admits any block count. Both
  sides already use that primitive for the fuse base position (`Math.unsignedMultiplyHigh` /
  `mul_hi`), so the change itself is small. It alters the filter geometry, so it invalidates every
  recorded size and FPR figure and requires re-running the k-sweep, the sizing sweep and the GPU A/B.
  Highest-value remaining change: it improves RAM, CPU cache behaviour and the GPU spill case at once.

- **Port the GPU pre-filter to `BLOCKED_BLOOM` — probe measured (1.7-2.2x), integration outstanding.**
  The device probe `blockedbloom_contains` and the two A/B kernels `benchmarkFilterFuse8` /
  `benchmarkFilterBlockedBloom` are in `inc_ecc_secp256k1custom.cl`, driven by
  `GpuFilterProbeBenchmark` through `OpenCLContext.prepareBenchFilterProbe*` (JOCL stays inside the
  `opencl` package, per the `joclConfinedToOpencl` architecture rule). Measured on an RTX 3070:
  1.773 -> 1.024 ms at 10 M entries (1.73x) and 3.015 -> 1.351 ms at 100 M (2.23x), the advantage
  growing with filter size. Far larger than the CPU margin, which is the coalescing argument
  behaving as expected on hardware with no large cache.

  Still to do for production: new kernel arguments (the signature hard-codes `fuse8_fp` /
  `fuse8_meta`), host-side VRAM upload of the blocked payload (`BlockedBloomGpuFilterData` exists),
  and a `gpuFilterType` config field defaulting to Fuse-8 so existing configs are unaffected. Expect
  a much smaller end-to-end gain than the probe ratio: the probe is a minority of kernel time behind
  EC generation and hashing.

- **Add open-addressing primitive hash table backend** (fastutil `Long2LongOpenHashMap` or hand-rolled over `long[]`) — would offer O(1) average vs. the sorted array's O(log n) per lookup with similar cache profile. Deferred because `TRUNCATED_LONG_64` is already the fastest backend in practice and the marginal speedup would not change the recommended default.

- **Add standalone `BloomFilterPersistence`** (Bloom-only / probabilistic mode without a backing `AddressLookup`). The current `BloomFilterAccelerator` returns `requiresBackend()==true`; a pure-Bloom variant would return `false` and accept `getAmount` semantics of "unsupported / always `Coin.ZERO`". Not yet needed by any caller; ship only when someone asks for it.

### Self-contained jar — zero-download first run (configs + demo DB + launchers)

> **Status: idea / not yet investigated to implementation depth.** Goal: a newcomer
> runs BitcoinAddressFinder with **nothing to download but the jar** (and Java) — no
> hand-copied config, no `logbackConfiguration.xml`, no multi-GB LMDB download before
> the first run. Three independently shippable phases; each needs its own design pass
> before coding. Captured here so the idea isn't lost.

#### Phase 1 — Ship example configs *inside* the jar + classpath resolution + parse guard

- **Idea:** package `examples/config_*.json` (9 files today) into the jar and let the CLI
  accept a bare *name* that resolves *local file if it exists, else jar/classpath* (no silent
  shadowing of an edited local file).
- **Investigate first:**
  - `cli/Main.java` is filesystem-only today: `main()` → `loadConfiguration(Path)` (line ~177)
    → `readString` (`Files.readString`, line ~110) → `fromJson`/`fromYaml` (extension picks the
    parser, `.json/.js` vs `.yaml/.yml`). Refactor so a `(String content, String nameForExtension)`
    path shares the parser branch; **keep `loadConfiguration(Path)` intact** (used by `MainTest`).
  - The repo already loads classpath resources cleanly — mirror, don't invent: `getResourceAsStream`
    (`secret/BIP39Wordlist.java`) and Guava `Resources.getResource` (`opencl/OpenCLContext.java`).
  - Decide canonical home: move configs to `src/main/resources/config/` (packaged at `/config/...`),
    or copy at build time. `pom.xml` already copies `src/main/resources` into both the normal and
    `assembly` fat jar.
  - **Guard test:** add/extend a `ConfigFixturesParseTest` that loads each bundled config *from the
    classpath* and asserts it deserialises to a `CConfiguration` with the expected `command`/`finder`
    shape — this is what stops the shipped configs rotting.
  - **REUSE/CI:** `REUSE.toml` currently lists `examples/config_*.json`. Moving them means updating
    those annotation paths; every committed file needs SPDX metadata.
- **Tests to run:** `mvn test -Dtest='ConfigFixturesParseTest,MainTest'`.

#### Phase 2 — Embedded ~1000-address demo LMDB, built at `mvn` time, auto-fallback at runtime

- **Idea:** embed a tiny (~1000-address) demo LMDB so a first `Find` run works end-to-end with no DB
  download. **Decisions already taken:** demo DB is *generated during the build* (no binary committed
  to git), and it activates as an *auto-fallback when the configured `lmdbDirectory` is missing* —
  **plus a loud WARN every time** it is used (this tool scans for real balances; it must never
  silently use the tiny set).
- **Investigate first:**
  - LMDB is memory-mapped and **cannot** be opened from inside a jar: `persistence/lmdb/LMDBPersistence`
    `initReadOnly()` opens `new File(lmdbDirectory)` with `MDB_RDONLY_ENV | MDB_NOLOCK` → only `data.mdb`
    must be extracted to a temp dir (no `lock.mdb` needed). Need a new resource→temp extractor (Guava
    `Resources` + `Files.copy`, `deleteOnExit`) — none exists today.
  - Runtime seam for the fallback: `consumer/ConsumerJava.initLMDB()` (line ~301-303,
    `new LMDBPersistence(cfg, persistenceUtils); lmdb.init();`). Branch: if `lmdbDirectory` is
    empty / has no `data.mdb`, extract the embedded demo DB to temp, WARN, open read-only; else open
    the configured dir exactly as today. Build a read-only `CLMDBConfigurationReadOnly` for the temp dir.
  - Build a DB from keys: `command/AddressFilesToLMDB` writes hash160→coin via `putNewAmount(...)`;
    test path `LMDBBase.createAndFillAndOpenLMDB` → `TestAddressesLMDB.createTestLMDB`. Derivation via
    `model/PublicKeyBytes` + `Hash160`. A demo DB built from private keys `1..N` yields real **hits**
    when an incremental producer scans from `1`.
  - Build wiring: bind `exec-maven-plugin` (already present for JMH) to `process-classes` (after main
    compile, before `test`) to run a `demo/DemoLmdbGenerator` `main` writing into
    `target/classes/demo/lmdb/` → on the test classpath *and* packaged into the jar. `exec:java` runs
    in-process so it inherits `.mvn/jvm.config`'s lmdbjava `--add-opens`. Build outputs under `target/`
    are **not** REUSE-checked → a build-time demo DB needs **no** REUSE entry.
  - NullAway is error-level — annotate every new `@Nullable`/`@NonNull`.
  - **Smoke test idea:** with no `lmdb/` present, run a tiny incremental-from-1 `Find` config and assert
    the demo fallback activates and a hit is produced; add `config_Find_Demo.json`.
- **Tests to run:** `mvn test -Dtest='DemoDatabaseSmokeTest,ConsumerJavaTest'`, then `mvn package -P assembly`
  + `unzip -l target/*-jar-with-dependencies.jar | grep demo/lmdb` to confirm packaging.

#### Phase 3 — Zero/low-friction launchers (jar + a JDK only)

- **Idea:** with configs + demo DB in the jar, launchers need only deliver the **jar** and a **JDK 21**;
  first run needs no other download.
- **Investigate first:**
  - `examples/baf.sh` / `baf.ps1` / `baf.cmd`: local jar first → else download the pinned fat jar from
    Maven Central + verify SHA-256 → enforce Java 21 → run with the two required runtime `--add-opens`
    (`java.base/java.nio`, `java.base/sun.nio.ch`) and `-Dlogback.configurationFile=...`.
  - JBang quickstart (`quickstart.sh` / `.ps1` + `jbang-catalog.json`): JBang brings JDK 21 and runs the
    GAV `net.ladenthin:bitcoinaddressfinder:<version>` directly.
  - **Version-pin upkeep:** pin the current release (now **1.6.0**) + its fat-jar SHA-256 in `baf.*`,
    the GAV in catalog/quickstart, and the README; add a release version-bump checklist. Central vs
    GitHub-Release bytes are separate uploads — pin the checksum of whichever mirror the script fetches.
  - **Verification:** `shellcheck examples/*.sh`; PSScriptAnalyzer on `.ps1`; fresh-dir run downloads the
    jar once then runs offline.

#### Out of scope / notes
- Sandbox caveat: this environment may block external installs (JBang/Central/light-DB download) and the
  GPU/OpenCL path — the demo (CPU incremental + tiny LMDB) is fully exercisable offline; live fetches must
  be validated in CI or on a dev box.
- Optional niceties (not required): a `lmdbDirectory: "classpath:demo"` sentinel to *force* the demo even
  when a real dir exists; a `--extract-config <name>` to drop a bundled config to disk for editing.
- Snapshot resolution (timestamped metadata) is out — Release + pinned checksum only.

### GPU acceleration (three connected designs)

#### CPU-side filter (Part 1) vs GPU-side filter (Part 2)

All three GPU-acceleration TODOs below connect to a single architectural choice: **where does the address-presence filter live?**

| | **Part 1 — CPU-side filter** (current) | **Part 2 — GPU-side filter** (goal) |
|---|---|---|
| **Filter lives in** | CPU RAM | GPU VRAM |
| **Filter checked by** | CPU, after kernel returns | GPU, inside the kernel |
| **What crosses PCIe per batch** | every hash160 (all N work-items × 104 B) | only candidates that passed the filter (~0.4 % with Fuse-8) |
| **CPU-side lookup cost** | ~25–108 ns per candidate (depends on backend) | ~0 — only LMDB verification on the tiny trickle |
| **GPU VRAM consumed by filter** | 0 | ~149 MB (Light DB) / ~1.5 GB (Full DB) with Fuse-8 |
| **Kernel complexity** | unchanged | +1 hash call + 3 array reads + 1 XOR per work-item |
| **GPU needed for filter** | no — CPU-only configs work identically | yes — filter must be uploaded to VRAM at startup |
| **When it wins** | always (no extra GPU work) | when PCIe bandwidth or CPU filter throughput is the bottleneck |
| **When it regresses** | never | when GPU was already at ~100 % ALU occupancy on ECC |

**How "copy only on hit" works in the GPU-side filter (Part 2):**
There is no per-result "copy" flag. Instead the kernel uses an **atomic counter + compact output buffer**:

```opencl
__global volatile uint* hit_count   // initialised to 0 before kernel launch
__global uchar*         hit_results // pre-allocated for MAX_HITS entries

// Inside each work-item, after computing hash160 and running the Fuse-8 check:
if (fuse_hit) {
    uint idx = atomic_add(hit_count, 1u);  // claim the next output slot
    if (idx < MAX_HITS) {
        hit_results[idx * 20 .. idx*20+19] = my_hash160;
    }
}
// Work-items that did NOT hit write nothing — they have no slot in hit_results.
```

After the kernel finishes:
1. CPU reads `hit_count` (4 bytes PCIe transfer) → learns K hits.
2. CPU reads only the first K entries from `hit_results` (K × 20 bytes).
3. CPU calls LMDB for those K candidates. Everything else was silently discarded on the GPU.

At 0.4 % FPR with a batch of 2048 work-items: K ≈ 8 hits per batch. PCIe transfer shrinks from 2048 × 104 B = 212 KB to 4 B + 8 × 20 B = 164 B — a >1000× reduction in bus traffic. The CPU never sees or processes the 2040 non-hit results.

The current "flags byte per work-item" approach described in the GPU-side-filter TODO below is a softer version that keeps all N results on the PCIe bus but lets the CPU skip LMDB for non-flagged entries. The compact-buffer approach above is stricter: non-hits never cross the bus at all. The compact-buffer design requires changing `OpenCLGridResult` from a fixed-stride layout (offset = `work_item_index × CHUNK_SIZE`) to a variable-length layout (offset = `atomic_slot × 20`), which is a larger refactor but eliminates the PCIe bandwidth entirely.

**Critical constraint — vanity scanning always requires all results on the CPU side.**
The existing `ConsumerJava.processBatch()` runs **two independent checks** per work-item:
1. `containsAddress(hash160)` — the database filter (fast, CPU-side, subject to Part 2 optimization).
2. `vanityPattern.matcher(base58Address).matches()` — a Java regex against the base58-encoded address (`enableVanity = true`).

The vanity check runs on **every** work-item unconditionally — it has nothing to do with whether the address is in the database. A result could be a vanity match but a database miss, or a database hit but not a vanity match. Therefore:
- With `enableVanity = false` (pure database scanning): the compact-output-buffer approach works perfectly — non-hits never need to reach the CPU.
- With `enableVanity = true` (vanity scanning): **all results must still cross PCIe**, because the GPU has no base58 encoder or regex engine. The compact-buffer optimization cannot be applied to the vanity path.

This means the Part 2 GPU-side filter and the compact-output-buffer approach apply **only** to the `enableVanity = false` configuration. For vanity scanning the result struct stays full-width and every work-item still crosses the bus. A future vanity-on-GPU implementation would need an OpenCL base58 encoder + pattern matcher — tractable for simple prefix patterns (`1Abc...`), impractical for arbitrary Java regex.

---

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

- **Implement `BinaryFuse8AddressPresence` and `BinaryFuse16AddressPresence` — CPU-side Binary Fuse Filters for hash160 lookups.** ✅ **DONE** (`c603963`, `627b696`). No XOR / fuse filter backend exists in the codebase today. The JMH benchmark (`AddressLookupBenchmark`) already covers the four existing backends via `@Param({"LMDB_ONLY","BLOOM","HASHSET","TRUNCATED_LONG_64"})`; adding two more entries is the whole benchmark change. This is the **right first step** before the GPU-side filter — both variants are purely Java, prove the FPR/memory trade-off with real JMH numbers, and their construction logic (`populateFrom(LMDB)`) produces the same flat array the GPU will upload. Implementing **both 8-bit and 16-bit** now is deliberate: each serves a distinct use case (VRAM-constrained vs precision-required), and both must exist in the benchmark to let JMH pick the winner.

  **What is a Binary Fuse Filter?** A static probabilistic membership filter (no inserts after construction). Lookup: 3 array reads + XOR of fingerprints — branchless, no division. Memory: ~1.14 bytes/entry (8-bit) or ~2.28 bytes/entry (16-bit). No false negatives by construction — every stored key is always found. FPR ≈ 1/256 ≈ 0.4 % (8-bit) or 1/65536 ≈ 0.0015 % (16-bit). This is exactly the no-FN / FP-ok contract the GPU filter requires. Reference: *"Binary Fuse Filters: Fast and Smaller Than Xor Filters"* (Graf & Lemire, 2022).

  **Memory comparison for this project:**

  | Backend | Bytes/entry | Light DB (~132 M) | Full DB (~1.4 B) | FPR |
  |---|:---:|---:|---:|---|
  | `HASHSET` | ~80 | ~10.5 GB | ~112 GB | exact |
  | `TRUNCATED_LONG_64` | 8 | ~1.1 GB | ~11 GB | ~7.5 × 10⁻¹¹ |
  | **`BINARY_FUSE_8` (new)** | **1.14** | **~150 MB** | **~1.6 GB** | **~0.4 %** |
  | **`BINARY_FUSE_16` (new)** | **2.28** | **~300 MB** | **~3.2 GB** | **~0.0015 %** |

  For the Full DB: `BINARY_FUSE_8` fits in the RAM of any modern workstation; `TRUNCATED_LONG_64` requires ~11 GB. The 0.4 % FPR means ~2 M false-positive LMDB verifications per 500 M candidates — at 108 ns each that is ~216 ms/s of LMDB overhead, entirely negligible compared to the key-derivation cost. `BINARY_FUSE_16` trades ~2× more RAM for a ~270× lower FPR, which matters when LMDB I/O is the bottleneck.

  **OpenCL portability design.** Both variants must use a hash function that translates directly to OpenCL C without 128-bit arithmetic. The chosen kernel:

  ```
  // Java (identical logic to the OpenCL kernel below)
  long h   = murmur64(key ^ seed);        // Murmur3 finalizer (3 multiplies + shifts)
  byte fp  = (byte)(h ^ (h >>> 32));      // 8-bit fingerprint
  int  h0  = reduce((int)h,         seg); // reduce(x,m) = (uint)(x * (ulong)m >>> 32)
  int  h1  = reduce((int)(h >> 21), seg) + seg;
  int  h2  = reduce((int)(h >> 42), seg) + 2 * seg;
  return (table[h0] ^ table[h1] ^ table[h2]) == fp;
  ```

  The same three lines translate verbatim to OpenCL C with `ulong` / `uchar` / `ushort`. The construction algorithm (Java-only) generates the `table[]` array that the GPU will receive as a `__global uchar[]` or `__global ushort[]` buffer.

  **Implementation plan — all steps are purely Java, no native code:**

  1. **Add `BinaryFuse8AddressPresence` and `BinaryFuse16AddressPresence` to `persistence/inmemory/`.**
     Implement each algorithm inline (~250 lines each); do NOT add an external library dependency (BAF's `dependencyConvergence` + `bannedDependencies` enforcement makes transitive deps expensive, and the algorithm is compact enough to own). Both classes implement `AddressPresence` and mirror the static-factory pattern of `TruncatedLong64SortedArrayPresence`. The only difference between the two implementations is the fingerprint array type (`byte[]` vs `short[]`) and the comparison width.

     Construction uses the standard iterative-peeling XOR-filter algorithm: populate count and XOR-accumulator arrays, extract singleton positions into a peeling queue, record the reverse topological order, then walk back assigning fingerprints.

  2. **Add `BINARY_FUSE_8` and `BINARY_FUSE_16` to `AddressLookupBackend` enum.** Add Javadoc entries describing bytes/entry, FPR, no-FN guarantee, LMDB closed after population.

  3. **Wire into `ConsumerJava` dispatch.** Add `case BINARY_FUSE_8` and `case BINARY_FUSE_16` in the same switch that handles `TRUNCATED_LONG_64`; no other `ConsumerJava` changes needed.

  4. **Add to `AddressLookupBenchmark`.** Add `"BINARY_FUSE_8"` and `"BINARY_FUSE_16"` to the `@Param({…})` list and the matching `case` arms in `buildLookup()`.

  5. **Unit tests + mutation coverage.**
     - `BinaryFuse8AddressPresenceTest` and `BinaryFuse16AddressPresenceTest`: populate from a small fixed set; verify every member returns `true` (no-FN); verify FPR over a large random miss set is within expected bounds; verify `requiresBackend() == false`; verify buffer is not mutated by `containsAddress`; verify wrong-length buffer returns false.
     - Add both classes to the PIT `<targetClasses>` list once 100 % mutation coverage is reached.

  **After this TODO is done:** the GPU-side filter (see next TODO) reuses the same `murmur64` / `reduce` / fingerprint formula verbatim in OpenCL C. The Java and GPU lookup paths are verifiable against each other with identical test inputs.

- **GPU-side Binary Fuse 8 filter — Part 2 implementation plan (atomic steps).** Uses the CPU-side `BinaryFuse8AddressPresence` (✅ done) uploaded to GPU VRAM so the kernel checks the filter inline and transmits only hits over PCIe. Controlled by two new config flags: `enableGpuFilter` (default `false`) and `transferAll` (default `false`; forced `true` automatically when `ConsumerJava.enableVanity = true`, since vanity scanning requires all results on the CPU). Each of the 9 steps below is independently committable and must compile + pass existing tests before the next step begins.

  **Unified output buffer format (ONE physical layout, two write modes).** There is a *single* physical output layout, used unchanged by every kernel launch — there is **not** a separate full-transfer stride. This is deliberate: two different strides (104 vs 108) would make the destination-buffer sizing bound (`MAXIMUM_CHUNK_ELEMENTS` / `BIT_COUNT_FOR_MAX_CHUNKS_ARRAY`) depend on which format is active. With one stride the bound is computed off a single true entry size and the buffer is always `OUTPUT_HEADER_SIZE_BYTES + N × OUTPUT_ENTRY_SIZE_BYTES`, which is also exactly compact mode's worst case (every candidate is a filter hit), so one allocation safely covers both modes.

  Layout:
  - Byte 0: a 4-byte unsigned **count word**.
  - Byte `4 + j × 108`: entry `j`, always `[work_item_index:4][X:32][Y:32][hash160_u:20][hash160_c:20]` (108 bytes). The `work_item_index` is present in **every** entry. It is redundant in full-transfer mode (entry `i` is written at slot `i`), but carrying it keeps the stride identical to compact mode. The +4 bytes/entry costs ≈ 3.8 % PCIe in full-transfer mode only — the rare vanity/regex path, where the CPU regex dominates and the GPU is not the bottleneck.

  The count word selects how the reader walks the entries:
  - Count = `0xFFFFFFFF` (`OUTPUT_COUNT_FULL_TRANSFER_SENTINEL`): **full-transfer mode**. Every work-item is present; entry `i` is written densely at slot `i` with `work_item_index = i` (no atomics). The reader walks exactly `workSize` entries. Used when the GPU filter is disabled, or when `transfer_all` is forced (vanity/regex scanning needs every derived address on the CPU).
  - Count = K (any other value): **compact mode**. Only the work-items whose hash160 passed the GPU Binary Fuse 8 filter wrote an entry, each claiming its slot via `atomic_add`, so the K entries appear in nondeterministic order and `work_item_index` is essential. The reader walks exactly K entries. K cannot collide with the sentinel: the grid is capped at `2^BIT_COUNT_FOR_MAX_CHUNKS_ARRAY` (= 2²⁴) work-items, far below `0xFFFFFFFF`, so even an all-hit batch (K == workSize) stays under it.

  Both modes share the entry parser; they differ only in the loop bound (`workSize` vs K) and in how the count is produced (constant sentinel vs atomic counter). CPU reconstructs `secret = KeyUtility.calculateSecretKey(secretBase, work_item_index, USE_OR)` for every entry in both modes. PCIe saving at Fuse8 FPR 0.4 % (compact mode): ≈ 99.6 % bandwidth reduction. The mode flag `transfer_all` is a uniform kernel argument → zero branch divergence from mode selection; only the `if (hit)` branch inside compact mode may diverge (≈ 0.4 % of work-items enter it).

  **Step A — Layout constants** ✅ (`constants/OpenClKernelConstants.java`)
  Added `OUTPUT_HEADER_SIZE_BYTES = 4`, `OUTPUT_COUNT_FULL_TRANSFER_SENTINEL = 0xFFFF_FFFF`, the unified-entry offsets (`OUTPUT_ENTRY_INDEX_BYTE_OFFSET = 0`, `_X_BYTE_OFFSET = 4`, `_Y_BYTE_OFFSET = 36`, `_HASH160_UNCOMPRESSED_BYTE_OFFSET = 68`, `_HASH160_COMPRESSED_BYTE_OFFSET = 88`) and `OUTPUT_ENTRY_SIZE_BYTES = OUTPUT_HEADER_SIZE_BYTES + CHUNK_SIZE_NUM_BYTES = 108`. `MAXIMUM_CHUNK_ELEMENTS` is derived from the unified stride (`(Integer.MAX_VALUE − 4) / 108 = 19 884 107`); `BIT_COUNT_FOR_MAX_CHUNKS_ARRAY` is unchanged at 24. (Design note: the unified single-stride layout — full transfer also carries `work_item_index` — replaces the original dual-stride 104/108 plan, so the capacity bound has a single true entry size.)
  Tests: `mvn test -Dtest=BitcoinAddressFinderArchitectureTest` (constants-only change).

  **Step B — Config flags** ✅ (`configuration/CProducerOpenCL.java`)
  Add `boolean enableGpuFilter = false` and `boolean transferAll = false` with Javadoc.
  Tests: JSON round-trip test in `CProducerOpenCLTest` verifying both fields default to `false` and survive a Jackson serialise/deserialise cycle.

  **Step C — BinaryFuse8 getter exposure** ✅ (`persistence/inmemory/BinaryFuse8AddressPresence.java`)
  Add package-private getters: `getFingerprints()`, `getSeed()`, `getSegmentLength()`, `getSegmentLengthMask()`, `getSegmentCountLength()`.
  Tests (new, no GPU needed):
  - `getSeed_returnsInitialSeedForFirstSuccessfulBuild` — build a small filter, verify `getSeed()` is non-zero and matches the value `containsAddress` uses internally (cross-check by building an equivalent key manually).
  - `getSegmentLengthMask_isSegmentLengthMinusOne` — assert `getSegmentLengthMask() == getSegmentLength() - 1`.
  - `getFingerprints_lengthEqualsSlotCount` — assert `getFingerprints().length == slotCount()`.
  - `getters_doNotMutateFilter` — call all getters, then verify `containsAddress` still returns the same answers.

  **Step D — GPU VRAM upload** ✅ (`opencl/OpenCLContext.java`)
  **Architecture note (revised wiring).** The enforced layered-architecture test forbids `consumer → opencl` and `opencl → persistence`. So the upload is routed cleanly through the producer rather than the consumer: `OpenCLContext.uploadGpuFilter` takes **primitives only** (`byte[] fingerprints, int seedLo, int seedHi, int segLen, int segLenMask, int segCountLen`) — the OpenCL layer never references the persistence filter type. `BinaryFuse8AddressPresence.toGpuFilterData()` (public) returns a `BinaryFuse8GpuFilterData` record (a persistence-package carrier) that the **engine** (`Finder`, which may access both persistence and producer) reads and decomposes into those primitives, handing them to `ProducerOpenCL`, which uploads after its `OpenCLContext.init()` (Step H).
  `uploadGpuFilter` allocates two `cl_mem` buffers with `CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR` (the fingerprint slot array + a 5-int metadata buffer `[seedLo, seedHi, segLen, segLenMask, segCountLen]`) and retains them for kernel binding; an empty filter pads to a 1-byte fingerprint buffer (zero-size device buffers are invalid) and is detected via `segCountLen == 0`. `close()` releases both. Adds `isInitialized()`.
  Tests (skip if no OpenCL device):
  - `uploadGpuFilter_andClose_doesNotThrow` — build a 5-entry filter, upload its payload, call `close()`, assert no exception.
  - `isInitialized_falseBeforeInit_trueAfterInit_falseAfterRelease` — lifecycle state test including the filter upload path.
  - Plus non-GPU `toGpuFilterData_*` tests pinning the payload mirrors the getters and the empty-filter case.

  **Step E — Unified output buffer: count header + per-entry work_item_index** ✅ (kernel `.cl` + `OpenClTask` + `OpenCLGridResult`)
  Migrate the existing kernel output to the single unified 108-byte entry layout. `getDstSizeInBytes()` becomes `OUTPUT_HEADER_SIZE_BYTES + OUTPUT_ENTRY_SIZE_BYTES × overallWorkSize`. Work-item 0 writes `0xFFFFFFFFu` (the full-transfer sentinel) to output[0..3]. Each work-item writes its `work_item_index` at entry offset 0, then X/Y/hash160s at the unified entry offsets (shifted by the 4-byte index field). `getPublicKeyFromByteBufferXY` reads from `OUTPUT_HEADER_SIZE_BYTES + entry × OUTPUT_ENTRY_SIZE_BYTES` using the unified `OUTPUT_ENTRY_*` offsets. `getPublicKeyBytes()` reads the count word and asserts the sentinel (compact path not yet live).
  Tests (no GPU needed — hand-crafted `ByteBuffer`):
  - `getPublicKeyBytes_sentinelCount_dispatchesToFullTransfer` — ByteBuffer with `0xFFFFFFFF` at offset 0 followed by correct 108-byte unified entries; assert correct `PublicKeyBytes` array returned.
  - `getPublicKeyFromByteBufferXY_offsetShiftedByHeaderAndIndex` — verify the first key's X bytes are at byte `4 + 4 = 8` (header + index field), not byte 0.

  **Step F — In-kernel Fuse8 check + compact output path** ✅ (kernel `.cl` file + `OpenClTask` + `OpenCLContext`)
  Add 3 new kernel arguments (the 5 metadata ints are passed as one buffer rather than 5 scalars, matching the Step D upload): `fuse8_fp` (fingerprint buffer), `fuse8_meta` (`[seedLo, seedHi, segLen, segLenMask, segCountLen]` buffer), `transfer_all` (uint). The physical entry layout is unchanged from Step E (the unified 108-byte entry); only the *write decision* changes: in compact mode (`transfer_all == 0`) a work-item writes its entry **only if** its uncompressed OR compressed hash160 passes the filter, claiming the slot via `atomic_add` on the count word (which starts at 0). In full-transfer mode (`transfer_all != 0`) every work-item writes its entry at its own index and work-item 0 stamps the sentinel, exactly as Step E. The kernel's Fuse8 primitives (`fuse8_murmur64`/`fuse8_mix`/`fuse8_position`/`fuse8_fingerprint`/`fuse8_contains`) are a byte-exact port of the Java helpers, and `fuse8_key_from_ripemd` byte-swaps the two LE RIPEMD words to reproduce `ByteBuffer.getLong(0)`. `OpenClTask.executeKernel` binds the args, zero-initialises the count word via `clEnqueueWriteBuffer` before launch, and reads back only the bytes the count word reports (full → `workSize` entries; compact → K). `OpenCLContext` allocates a dummy empty filter on `init()` so the args are always bindable, tracks `gpuFilterUploaded`, and computes `transfer_all = transferAll || !gpuFilterUploaded`.
  Test: `Fuse8GpuHashParityTest` (no GPU) pins the key-extraction + hash formula against `BinaryFuse8AddressPresence.containsAddress` over 1 000 distinct hash160 inputs (members + non-members), so any kernel drift fails in pure Java. The kernel build + compact execution are exercised end-to-end in Step I (GPU-gated).

  **Hash strategy — MurmurHash3 finalizer + fused positions (real Binary Fuse Filter):**
  ```c
  static ulong murmur64(ulong h) {       // 5 lines — the entire hash primitive
      h ^= h >> 33;
      h *= 0xff51afd7ed558ccdUL;
      h ^= h >> 33;
      h *= 0xc4ceb9fe1a85ec53UL;
      h ^= h >> 33;
      return h;
  }
  // Key extraction: first 8 bytes of hash160 as big-endian uint64 — matches Java ByteBuffer.getLong(pos)
  // per-key mix: hash = murmur64(key + seed)   [one hash drives all three positions + fingerprint]
  // base = mul_hi(hash, segCountLen); h0 = base; h1 = base + segLen; h2 = base + 2*segLen
  // within-segment windows: h1 ^= (hash >> 18) & segLenMask; h2 ^= hash & segLenMask   [h0 has none]
  // fingerprint: (uchar)(hash ^ (hash >> 32))
  // XOR invariant: fp[h0]^fp[h1]^fp[h2] == fingerprint → hit
  ```
  **Critical**: key extraction in the kernel (`first 8 bytes of hash160 as big-endian ulong`) must match `BinaryFuse8AddressPresence.containsAddress()` exactly (`hash160.getLong(hash160.position())`). Any mismatch → false negatives (missed balance hits).

  Tests:
  - `Fuse8GpuHashParityTest` (no GPU needed — pure Java): reimplements the GPU hash logic in Java (`key = ByteBuffer.wrap(h160).getLong(0); hash = murmur64(key + seed); base = mulHigh(hash, segCountLen); h0 = base; h1 = base + segLen ^ ((hash >>> 18) & segLenMask); h2 = base + 2*segLen ^ (hash & segLenMask); ...`). For 1 000 distinct hash160 inputs, asserts that this Java reimplementation agrees with `BinaryFuse8AddressPresence.containsAddress()` on every hit/miss answer. This pins the exact key-extraction + hash formula in a runnable test — if the formulas drift, this test fails.

  **Step G — Java compact-mode reader** ✅ (`opencl/OpenCLGridResult.java`)
  `getPublicKeyBytes()` dispatches on the count word: `0xFFFFFFFF` → `readFullTransfer()` (walk `workSize` entries), otherwise → `readCompact(count)` (walk K entries). Because the entry layout is unified, both paths share the same per-entry parser: read `work_item_index` (entry offset 0), derive secret via `KeyUtility.calculateSecretKey(secretBase, index, USE_OR)`, read X/Y/hash160u/hash160c at the unified `OUTPUT_ENTRY_*` offsets, assemble `PublicKeyBytes` via `PublicKeyBytes.assembleUncompressedPublicKey(x, y)`.
  Tests (no GPU needed — hand-crafted `ByteBuffer`):
  - `readCompact_countZero_returnsEmptyArray`
  - `readCompact_countTwo_returnsCorrectSecretsAndHashes` — encode two known compact entries; assert both `PublicKeyBytes` have the expected secrets, uncompressed keys, and hash160s.
  - `readCompact_invalidSecretZero_returnsInvalidKeyOne` — compact entry with `work_item_index` that makes `secret = 0`; assert result is `PublicKeyBytes.INVALID_KEY_ONE`.
  - `getPublicKeyBytes_sentinelDispatch_doesNotCallCompactPath` — sentinel count must not be treated as a compact entry count.

  **Step H — Integration wiring** ✅ (`consumer/ConsumerJava.java`, `producer/ProducerOpenCL.java`, `engine/Finder.java`)
  **Architecture note (revised wiring).** Routed through the producer instead of the consumer (the layered architecture forbids `consumer → opencl`). `ConsumerJava.getGpuFilterData()` returns the `BinaryFuse8GpuFilterData` payload when the backend is `BINARY_FUSE_8`. `Finder.configureProducer()` then (1) calls `applyVanityFullTransferOverride()` — forces `transferAll = true` on every OpenCL producer config when `consumerJava.enableVanity = true`, with a warning that compact mode is disabled — and (2) calls `uploadGpuFilterToProducers()`, which for each `enableGpuFilter` producer not in full-transfer mode reads the consumer payload, decomposes it into primitives, and calls `ProducerOpenCL.setGpuFilter(...)`. `ProducerOpenCL.initProducer()` uploads the staged filter to VRAM right after `OpenCLContext.init()`.
  Tests:
  - `FinderTest`: `applyVanityFullTransferOverride` forces `transferAll = true` under vanity and leaves it `false` otherwise.
  - `ConsumerJavaTest` (LMDB-gated): `getGpuFilterData()` returns a payload for the `BINARY_FUSE_8` backend and empty for `LMDB_ONLY`.

  **Step I — End-to-end integration test + example config** ✅
  Added `OpenCLCompactOutputIntegrationTest`. All assertions are **exact** — no ± tolerances — because the test derives every candidate on the CPU and controls exactly which are inserted into the filter. It runs on any OpenCL 2.0+ device (the grid size is capped to the device's safe range, so a CPU OpenCL runtime such as pocl exercises the same compact path on a small batch and a GPU runs the full 256-wide batch); it self-skips when no OpenCL 2.0+ device is present. Three sub-tests: full-batch (all 2N hash160s inserted → `count == N` exactly, every returned key passes `runtimePublicKeyCalculationCheck()`); partial-batch (K=3 uncompressed hashes at indices 0/N·½/N−1 inserted → `count >= K` from the no-FN guarantee and `count < N`, with each inserted index present among the returned work-items); empty-filter (`count == 0`). Added `examples/config_Find_GPUFilterCompact.json` (BINARY_FUSE_8 backend + `enableGpuFilter: true`, `transferAll: false`).

  **Test setup (shared):** choose a fixed `secretBase` and `workSize = N` (e.g. N = 256). CPU-side, derive all N secrets `secretBase + i` for `i = 0 .. N-1` and compute their `hash160_uncompressed` + `hash160_compressed` via `KeyUtility`.

  *Full-batch test — count_out == N exactly:*
  1. Populate `BinaryFuse8AddressPresence` with all 2N hash160s (both variants for every key in the range).
  2. Upload filter to GPU. Run kernel with `secretBase`, `workSize = N`, `transfer_all = 0`.
  3. Assert `compact_count_out == N` — zero misses in the batch → zero false positives possible → count is provably exact, not approximate.
  4. Assert all N returned `PublicKeyBytes` pass `runtimePublicKeyCalculationCheck()`.

  *Partial-batch test — count_out is provably in [K, K+1]:*
  1. Choose a small K (e.g. K = 3) and populate the filter with only those K hash160_uncompressed values from the range (indices 0, N/2, N-1 — spread across the batch).
  2. Upload filter. Run same batch (workSize = N).
  3. Assert `compact_count_out >= K` — no-FN guarantee: every inserted address must be found.
  4. Assert `compact_count_out < N` — the batch is mostly misses.
  5. The K returned entries whose `work_item_index` matches an inserted key must each pass `runtimePublicKeyCalculationCheck()`.

  *Empty-filter test — count_out == 0:*
  1. Upload an empty filter (0 entries). Run the batch.
  2. Assert `compact_count_out == 0`.

  Add `examples/config_Find_GPUFilterCompact.json`. Mark each step ✅ in this TODO as it lands.

- **GPU-side no-false-negatives address filter with per-variant flag bitmask.** Today the GPU kernel returns raw hash160 bytes for every candidate key and the CPU calls `lookup.containsAddress(...)` twice per candidate (once for the uncompressed address, once for compressed). For large workgroup sizes this serialises the most expensive part of the pipeline on a single CPU thread. The goal is to move the address-presence filter onto the GPU so that the CPU only receives — and only queries LMDB for — the tiny subset of candidates that the GPU marked as "possibly found". The filter must satisfy the **no-false-negatives invariant**: if an address IS in the database, the GPU must always set its flag bit. False positives are acceptable — the CPU verifies any flagged candidate against LMDB and discards false positives there. This is exactly the bloom-filter contract: zero false negatives, bounded false positives.
  - **Upload the snapshot once at startup.** Right after `TruncatedLong64SortedArrayPresence.populateFrom(lmdb)` builds the 256 sorted `long[]` buckets in host RAM, copy each bucket into device global memory (`cl_mem` buffer per bucket, plus a small offset/length index). At ~8 B/entry this fits comfortably in modern GPU VRAM for any practical database size (~1.1 GB for the Light DB, ~11 GB for the Full DB; the latter may need streaming on smaller cards).
  - **Filter semantics and VRAM trade-offs.** The TRUNCATED_LONG_64 snapshot (sorted 64-bit truncated values, binary search per work-item) gives a near-zero false-positive rate (~7.5 × 10⁻¹¹ per query for the Full DB) and satisfies no-FN by construction — no entry is ever omitted. This is stronger than the filter contract strictly requires. For VRAM-constrained GPUs (8 GB cards with Full DB), alternative probabilistic structures use less memory at the cost of a higher but still acceptable FP rate:
    - **Bloom filter on GPU**: hash160 is already a strong 160-bit uniform hash, so different bit-window slices of the hash160 serve directly as the k independent probe positions — no separate hash computation. For 132 M entries (Light DB) a 512 MB filter gives ~32 bits/element and FP ≈ 10⁻⁹; for 1.4 B entries (Full DB) the same 512 MB gives ~3 bits/element and FP ≈ 10 % — unacceptable, requiring ~4–8 GB for a useful FP rate. GPU bloom filters are well-studied; the access pattern (k random bit reads) suits global memory with reasonable L2 hit rates for small-to-medium filters.
    - **Binary Fuse filter (XOR filter family)**: uses ~1.23 × log₂(1/ε) bits/element (vs ~1.44 for bloom). Lookup is 3 array accesses + XOR of 8-bit or 16-bit fingerprints derived from the hash160. For 132 M entries at FP ≈ 10⁻⁹: ~370 MB. For 1.4 B entries at the same rate: ~3.9 GB. Construction is offline (once, before upload); GPU-side lookup is simple and branch-free. Worth evaluating if VRAM is tight.
    - **Recommendation**: use TRUNCATED_LONG_64 (already implemented CPU-side) for the first GPU implementation. Switch to a binary fuse filter only if VRAM headroom on the target GPU makes TRUNCATED_LONG_64 impractical for the DB size in use.
  - **Output a flags byte, not raw hashes.** The kernel's per-work-item result struct gains a `uint8_t flags` field. The GPU runs the filter lookup **twice** — once for the uncompressed hash160 and once for the compressed hash160 — and encodes both results into separate bits:
    ```
    bit 0 (0x01): uncompressed hash160 lookup positive — no false negatives guaranteed
    bit 1 (0x02): compressed   hash160 lookup positive — no false negatives guaranteed
    bits 2–7:     reserved for future address-type variants (P2SH, bech32, …)
    ```
    CPU-side `ConsumerJava` iterates the result batch and only forwards work-items where `flags != 0` to the LMDB verification step. Work-items with `flags == 0` are discarded without any LMDB access. For a well-calibrated GPU filter (TRUNCATED_LONG_64 or a low-FP binary fuse filter) the number of LMDB queries per batch collapses from `batch_size × 2` to essentially zero except on real hits. The no-FN invariant ensures real hits are never discarded.

    Updated per-work-item result layout (extends the existing `PublicKeyBytes.java:240-242` struct):
    ```
    [0–31]   X coordinate (32 bytes, unchanged)
    [32–63]  Y coordinate (32 bytes, unchanged)
    [64–83]  hash160 uncompressed (20 bytes, unchanged)
    [84–103] hash160 compressed   (20 bytes, unchanged)
    [104]    flags (uint8_t)
    [105–107] padding to 4-byte alignment
    = 108 bytes/work-item (+4 bytes vs today's 104, +3.8 % PCIe bandwidth)
    ```
    `OpenCLGridResult` offset constants (`CHUNK_SIZE_*`) are updated accordingly; no other Java change is needed for `ConsumerJava` to read the new field.
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
      1. **Baseline today** — record candidates/sec, GPU kernel time per launch, and CPU `containsAddress` time per call at the configurations of interest (Light DB + TRUNCATED_LONG_64; Full DB + TRUNCATED_LONG_64; both with `keysPerWorkItem` matching production).
      2. **CPU headroom probe** — saturate the CPU `containsAddress` path artificially (run more producer threads than `containsAddress` can handle) to verify the CPU side actually can become the bottleneck under realistic load. If it never saturates, this TODO targets a non-bottleneck and should be deprioritised.
      3. **Kernel-cost simulation** — without writing the full presence-check kernel, ship a stub that adds a deterministic, comparable-cost workload to each work-item (e.g. a fixed number of dummy reads from a 1 GB device buffer) so the kernel-slowdown side of the trade-off is quantified before the real implementation.
      4. **Decision threshold** — proceed with the real implementation only if step 2 confirms the CPU is the bottleneck *and* step 3 shows kernel slowdown ≤ the CPU relief at the workgroup size of interest. If either condition fails, the right answer is to fix the CPU side (switch backend, batch-prefetch, or do the smaller "GPU pre-computed hash" TODO above) instead.
    - **What this buys when the trade-off is favourable.** With the CPU saturated and the GPU having ≥ 20–30 % compute headroom: the slow path (~108 ns/op CPU) effectively disappears from the pipeline and the bottleneck shifts elsewhere (typically PCIe upload of the producer keystream or LMDB verification of the now-tiny "probably present" subset). For a typical workgroup of 256 threads at 10 M candidates/s under those conditions, the difference is ~100 ms/s of CPU lookup overhead vs ~negligible. Under the *unfavourable* trade-off, expect **net regression** — kernel time grows by more than the CPU saves, throughput drops, and the only thing gained is a more complex pipeline.
  - **What needs to be designed first** (before any OpenCL code is written): the cooperative-load tile size (function of GPU local memory + bucket size + workgroup size); how the 108-byte result struct (with the new `flags` byte) flows through the existing `OpenClTask` + `OpenCLGridResult` types — specifically the `CHUNK_SIZE_*` offset constants and the Java-side extraction loop; the upload path (one-shot at `OpenCLContext.init()` or per-kernel-launch); whether the snapshot is rebuilt on the GPU after each LMDB update or kept immutable for the run (current expectation: immutable per scan session, matches the CPU `populateFrom` contract); and the filter-choice gate in configuration (new `addressLookupBackend` value `GPU_TRUNCATED_LONG_64`, with `GPU_BINARY_FUSE` reserved for the alternative filter path).

- **Long-term vision: end-to-end address scan on the GPU; CPU is a thin verifier only.** The "GPU-side no-false-negatives filter" TODO above is the first concrete step; this entry captures the broader end-state it leads to. **North star**: a single scan invocation is one GPU pipeline that emits only the small set of "possibly found" candidates back to the CPU; the CPU's only remaining job is to verify those few candidates against LMDB. Everything currently sitting between GPU output and LMDB (`BLOOM`, `HASHSET`, `TRUNCATED_LONG_64` on the CPU path) becomes optional / disable-able because the GPU's own filter has already done the work.
  - **Two-phase per-launch pipeline on the GPU:**
    1. *Address generation phase* (already exists): every thread derives both hash160 variants (uncompressed + compressed) and stores them to private memory.
    2. *Address lookup phase* (new): the workgroups consult the pre-loaded snapshot, run the filter for both hash160 variants, and write back a `{flags, hash160_uncompressed, hash160_compressed}` record per thread. `flags` bit 0 = uncompressed possibly found; bit 1 = compressed possibly found. The CPU only acts on records where `flags != 0`.
  - **Snapshot lives in GPU global memory and is loaded once per session.** At startup the JVM builds the `TRUNCATED_LONG_64` snapshot (256 sorted `long[]` buckets, ~1.1 GB at Light DB scale, ~11 GB at Full DB) and uploads it to device global memory. Modern consumer GPUs have 8-24 GB of VRAM; the Light DB fits comfortably, the Full DB needs higher-end cards or out-of-core streaming. The upload is one-shot — the snapshot does not change during a scan session.
  - **Per-workgroup local-memory tiles.** Workgroup local (shared) memory is typically 32-64 KB on consumer GPUs and 96-100 KB on workstation cards — far smaller than even a single full bucket at Full DB scale (~43 MB). The lookup phase streams each bucket through local memory in tiles sized to the workgroup's local-memory budget; threads cooperatively load each tile, then every thread whose `firstByte` matches the bucket index runs a branchless binary search against the loaded tile before the workgroup advances to the next tile.
  - **All workgroups process the same bucket at the same time.** The 256 phases (one per first-byte bucket) advance in lockstep across all workgroups so that the streamed bucket data is read once from global memory per phase rather than per workgroup. Threads whose `firstByte` does not match the active bucket participate in the cooperative load (memory-bandwidth contribution) but skip the search.
  - **The "looser-but-larger candidate set is OK" trade-off.** A GPU-side filter does not need to be as exact as the CPU-side TRUNCATED_LONG_64. As long as the rate of "probably present" flags reaching the CPU stays low enough that CPU-side LMDB verification keeps up with the GPU's throughput, a coarser GPU filter is acceptable. Example: a smaller stored value per entry (4 bytes / 32 bits instead of 8) cuts VRAM cost in half and the corresponding ~N/2^32 false-positive rate is still low enough that the CPU handles the resulting "hits" without becoming the bottleneck.
  - **Configuration shift implied.** Today `addressLookupBackend` selects the in-RAM CPU accelerator. After this work it should add a value like `GPU_ONLY` (or be replaced by a richer `consumerJava.lookupChain: [...]` config) where the operator declares "the GPU filter is the front-line; the CPU pipeline only verifies the flagged candidates against LMDB". `BLOOM` / `HASHSET` / `TRUNCATED_LONG_64` remain available for CPU-only setups or as a fallback while the GPU pipeline is still being commissioned.
  - **Why this is the right end-state.** The CPU's address-check budget today (~108 ns/op for `TRUNCATED_LONG_64`) is the only synchronisation point between the GPU's parallelism and the verification path. Moving that check to the GPU collapses the CPU contribution to "follow up on the small flagged subset" — at typical false-positive rates this is a few candidates per million derivations, well below any conceivable CPU bottleneck. The CPU then truly becomes the orchestrator (start kernels, read flagged results, verify against LMDB, log hits) rather than the rate-limiting step.

### Standalone consumer (checker) service — network-fed key checking

Today BAF couples key generation and address checking in one process. The idea here is the **inverse**: a dedicated checker service that owns the LMDB database, exposes a network endpoint (socket / WebSocket / ZeroMQ), and receives batches of raw private keys (or hash160 values) from external producers — which may run on a different machine, a cluster of GPUs, or a completely separate codebase.

This is architecturally the mirror image of the existing `KeyProducerJavaSocket` / `KeyProducerJavaWebSocket` / `KeyProducerJavaZmq` inputs, which let external sources feed keys *into* BAF. What is missing is a mode where BAF acts purely as a **checking endpoint** — no key generation at all, just receive → derive addresses → LMDB lookup → report hits.

**Why it makes sense:**
- Separation of concerns: key generation (GPU-heavy) and address checking (DB-heavy) have different hardware profiles. Running them on separate machines lets each be optimised independently.
- Horizontal scaling: multiple GPU nodes can feed a single checker, or a single GPU node can fan out to multiple checker replicas with sharded databases.
- Interoperability: third-party key generators (hashcat, custom FPGA tooling, cloud spot instances) can contribute to a scan without being aware of BAF internals — they just push key bytes to a socket.

**Sketch of the design:**
- New `CCommand` value `CheckerService` (or `ConsumerService`) that starts a `ConsumerJava` wired to a network-facing key receiver instead of a local producer queue.
- The network receiver implements `Producer` (or a new `KeySource` abstraction) and enqueues received key batches into the existing `LinkedBlockingQueue<byte[]>`. From `ConsumerJava`'s perspective nothing changes.
- Protocol: raw binary frames of packed private keys (same layout as the existing queue entries) over TCP/WebSocket/ZMQ — no new serialisation format needed.
- Hit reporting: existing log output is sufficient for a first version; a structured hit-callback endpoint (HTTP POST, ZMQ PUB) can be added later.
- Configuration: `CProducerCheckerService` (mirrors `CProducerJavaSocket` shape); the checker service is just another producer config entry with `listenAddress` + `listenPort` + protocol selector.

**What this is NOT:** this is not the GPU-side presence-check optimisation (pushing `TRUNCATED_LONG_64` into the OpenCL kernel). That optimisation keeps everything in one process and moves the lookup to the GPU. The checker service idea moves the lookup to a *separate process/machine*, which is the opposite trade-off — more network overhead, but full decoupling of key generation hardware from database hardware.

**Prerequisite before implementing:** define the wire format for key batches (size, byte order, compressed vs uncompressed flag) so that non-BAF producers can implement it without reading BAF source. Document it in `docs/wire-protocol.md`.

### Decouple "producing" from "finding" — a proper network API (finding service + result feedback + web UI)

**Vision.** Split the project's two concerns — **producing candidate keys** and **finding** (derive address → check DB → report) — into cleanly separated, independently runnable parts, *within this existing project*. Today they are coupled in one `Finder` process. Shrink and separate them so the **finding** side becomes a self-contained service reachable over the network that **accepts keys, key ranges, or hash160s** from any source; so a **lot more producers** can be added (in-process or fully external) without touching the finder; and so operation/monitoring can move to a **web UI**. This is the umbrella the **Standalone consumer (checker) service** entry above is the first slice of, and it feeds the **Cross-platform GUI** entry below.

#### Current state (researched) — what the "raw sockets" actually are

Three network key-input transports already exist, all **inbound ingestion only**, all feeding the shared `LinkedBlockingQueue<byte[]>` through `AbstractKeyProducerQueueBuffered`:

| Transport | Class | Mode(s) | Framing today |
|---|---|---|---|
| TCP | `KeyProducerJavaSocket` | SERVER (accept) / CLIENT (connect) | reads **fixed 32-byte frames** back-to-back, no delimiter/header |
| WebSocket | `KeyProducerJavaWebSocket` | embedded server | one **binary message == 32 bytes** per key (else rejected/logged) |
| ZeroMQ | `KeyProducerJavaZmq` | PULL, BIND / CONNECT | one **message == 32 bytes** per key |

The "wire format" is therefore implicit and minimal: **the unit is exactly 32 bytes = one raw big-endian *unsigned private key*** (decoded by `keyUtility.bigIntegerFromUnsignedByteArray`; length must equal `PRIVATE_KEY_MAX_NUM_BYTES`). What is **missing** (the gap to fill):
- No protocol **version / handshake**, no **message types**, no **batch header** (one key per message on WS/ZMQ is very chatty).
- **No key-range submission.** Only the local `KeyProducerJavaIncremental` enumerates ranges; the network path cannot say "scan start..start+N". (Earlier drafts of this TODO wrongly implied ranges over sockets — they do not exist.)
- No way to submit **hash160s directly** (needed for the pure checker mode).
- No **acknowledgements / errors** back to the sender (it can't tell if keys were accepted, invalid, or dropped).
- No **result feedback** at all — hits/vanity are `ConsumerJava` **log-only** (`HIT_PREFIX` / `VANITY_HIT_PREFIX`); a remote sender gets nothing and cannot correlate a hit to what it sent.
- No **backpressure** (the queue is unbounded → memory risk under a fast sender), no **auth/TLS** (it moves private keys in the clear).

#### Proposed "very good API" — one transport-agnostic protocol, offered over the best-fit transports

**Topology + ownership (confirmed).** The **server** (e.g. one PC with a GPU) runs with **its own configured settings** and owns them — clients never configure it. In the normal case the two client roles are *different machines*: a **key-sender** client feeds keys to be checked (→ server's *receiver*), and a **completely separate result-listener** client only subscribes to events (→ server's *sender/publisher*). A **web UI is the special case** that does both. Every event is **broadcast to all subscribers** and is **self-describing** (carries its key/address), so a listener that did not submit still knows what each event refers to — no per-client correlation required.

**Define one small, versioned, typed, framed message schema** (document it in `docs/wire-protocol.md` + a machine schema), split by direction:

*Client → server (ingestion / `receiver`):*
- `HELLO` / capability + version negotiation (key formats, compression, max batch, supported coins).
- `SUBMIT_KEYS` — a **batch** of N×32-byte keys in one frame. The minimal baseline is "just a private key to check"; batching kills the one-message-per-key overhead.
- `SUBMIT_RANGE` — `{startKey:32, count, increment}` (+ optional compressed/coin flags). Orders of magnitude less bandwidth than enumerating keys; the finder expands it (reuse `KeyProducerJavaIncremental`). **The single highest-value ingestion capability.** *(optional extension)*
- `SUBMIT_HASH160` — pre-derived 20-byte hashes for the pure checker mode (skip EC derivation). *(optional extension)*
- `ACK` / `ERROR` — accepted count, or rejection with a code (bad length, out-of-range, backpressure, unknown type).

*Server → subscribers (notification / `sender`) — queue/job granularity + configured hits ONLY:*
- `CONFIG` — sent **once on connect**: a snapshot of the server's running configuration so a new subscriber knows the setup (device, backend, coins, vanity pattern, …).
- `KEY_QUEUED` — "the service received a key/range to scan; it is now on the queue." Emitted per **submitted** unit (sender-controlled volume).
- `GENERATION_STARTED` — emitted per key/key-range **taken** from the queue (scanning/generation began for that unit).
- `VANITY_HIT` — a configured vanity-pattern match.
- `HIT` — a database/GPU **found**: secret + address + variant (uncompressed/compressed/coin).
- `PROGRESS` *(optional)* — periodic **aggregate** for a range scan (position, keys/sec, ETA) for the UI.
- `PING` / `PONG`, `BYE`.
- **Hard non-goal:** never emit an event per *produced* key — only the **configured results of interest** (vanity hits / DB founds) plus the queue-lifecycle events above. Streaming every generated key would be orders of magnitude too slow and defeats the purpose.

**Flow control:** **credit-based backpressure** on ingestion (server advertises how many it can accept) replacing the unbounded queue; on the notification side, bounded per-subscriber queues with explicit drop (for lossy `PROGRESS`) rather than silent loss, while founds stay on the durable log sink.

**Transport recommendation (honest trade-offs):**
- **WebSocket = primary.** Already a dependency (`Java-WebSocket`); bidirectional and **self-framing** (message boundaries built in — no manual length-prefix); carries **binary** (bulk keys) *and* **text/JSON** (control); and is **browser-native**, so the *same* endpoint feeds the web UI (ingest + feedback + monitoring over one connection). Best single choice for "a very good network API + the planned UI."
- **gRPC (bidirectional streaming) = optional, for typed multi-language producers.** Protobuf gives a language-neutral contract with codegen (hashcat/FPGA/cloud producers in any language), built-in flow control and deadlines. Cost: adds `grpc-java` (heavier; friction with the repo's `dependencyConvergence` + `bannedDependencies`), and needs grpc-web for browsers. (Note: protobuf is already a transitive dep via bitcoinj, so the schema language is familiar.)
- **ZeroMQ = optional, for scale fan-out.** Great for many-producers→one-finder or one-producer→many-checkers, but no built-in request/response correlation and not browser-friendly. Keep for the horizontal-scaling story.
- **Raw TCP = keep as a minimal/low-overhead option, but versioned + length-prefixed** (the current "read 32 bytes forever" is fragile and un-versioned).

Recommendation: implement the schema once, ship **WebSocket first** (covers the API *and* the UI), add gRPC/ZMQ later only if a concrete scale/interop need appears.

#### Async notifications + n-to-n (the "secondary results channel" question)

The current transports are **one-directional and not n-to-n**: TCP `KeyProducerJavaSocket` in SERVER mode calls `serverSocket.accept()` exactly **once** and then reads 32-byte frames from that *single* client forever (one connection per producer instance — not even n-to-1); WebSocket's server is multi-client but only ingests; ZMQ uses a single PULL socket. None of them ever send anything back. So "async notification of results/progress" is genuinely new wiring, and the natural shape is **two logical channels with opposite fan patterns**:

- **Ingestion channel — fan-in (n producers → 1 finder):** n key producers submit keys/ranges/hash160s.
- **Notification channel — fan-out (1 finder → n listeners):** the finder publishes `RESULT` / `VANITY_HIT` / `PROGRESS` to all subscribers.

**Is a separate (secondary) results socket good? — Yes, as the default, with a single-socket option per transport.** Trade-off:

- **Separate results channel (recommended baseline).** Keys and results have *different* fan patterns (fan-in vs fan-out) and *different* audiences — a **pure monitoring listener (e.g. the web UI) is not a key producer and should not need submit rights**; independent lifecycle, auth scope, and backpressure; idiomatic for ZMQ. **No mapping / no correlation by design:** every event is **broadcast to every subscriber** and is **self-describing** (carries its key/address), so the server keeps no per-client routing state and a listener that never submitted still sees everything. Cost is just two endpoints to configure.
- **Single bidirectional connection (per-transport option).** For **WebSocket / gRPC streaming** the client can submit and subscribe on the *same* connection (one endpoint) — convenient for the web-UI special case. It still **broadcasts all events to all subscribers** (no per-connection filtering), and a pure listener that never submitted still needs the subscribe role + the shared subscriber registry.

**Recommended model: two roles, not two hard-coded sockets.** A client may take a **submit** role, a **subscribe** role, or both. Each transport maps the roles idiomatically:

| Transport | Ingestion (fan-in) | Notification (fan-out) |
|---|---|---|
| ZeroMQ | `PULL` (bind; n `PUSH` connect) | **separate `PUB`** socket (n `SUB` connect) — the idiomatic "secondary socket" |
| WebSocket | one server, many clients in *submit* role | same server broadcasts to all clients in *subscribe* role (role negotiated per connection or via path `/submit` vs `/events`) |
| Raw TCP | accept-**loop** many clients (fix the single-`accept()` limitation) + a connection registry | either a **second port** for events, or write events back on each subscriber's socket from the registry |

**Concrete answer — "one server, many subscribers, broadcast status to all":** this maps directly onto **WebSocket** and is the recommended primary. `WebSocketServer` already accepts many clients on one port, tracks them (`getConnections()`), and has built-in `broadcast(...)`; reuse the *same* server — `onMessage` ingests keys, an outbound `broadcast(status)` fans results/progress out to every connected subscriber (optionally a dedicated events-only server on its own port). Note it is **not** a literal "same class, send instead of listen" for the other transports: raw TCP needs a *new* broadcaster (accept-**loop** + connection registry + length-prefixed framing + per-client write isolation, because the existing class accepts a single client and only reads fixed 32-byte frames), and ZMQ uses a **separate `PUB` socket** (socket types are fixed — a `PULL` cannot send). Distinguish *submit* vs *subscribe* roles per connection (HELLO message or `/submit` vs `/events` path) with separate auth scopes — a pure status listener must not be able to submit keys.

**Backpressure + QoS (important for fan-out):** a slow listener must **never** stall the finding hot path. Split QoS by event kind:
- `PROGRESS` is **lossy-OK** — best-effort broadcast, drop for slow subscribers (ZMQ `PUB` with a send HWM does this natively; WebSocket: bounded per-subscriber queue, drop-oldest).
- `RESULT` (founds) is **precious** — keep the existing durable log/file sink as the source of truth so a found key is *never* lost to a dropped notification, and optionally offer an at-least-once acked delivery for subscribers that need it.

**Implementation seam — a `connection` package with two sub-packages (named from the server's perspective):**
- `connection.receiver` — accepts incoming private keys (a `KeySource` that feeds the existing queue). Today's socket key producers are this role.
- `connection.sender` — publishes `CONFIG`-on-connect + the event stream to all subscribers (a `ResultSink`/event-bus the consumer publishes to, plus a subscriber registry and per-subscriber bounded queues).

Each transport provides the impls it needs in each sub-package (see the adapter matrix below). The consumer publishes to the transport-agnostic `ResultSink`; producers and listeners are independent clients — a listener need not be a producer and vice versa.

**Adapter matrix (transport × direction) — do we implement a sender per receiver?** Conceptually yes: each transport can have an **ingestion adapter** (`KeySource`, today's receiver key producers already are this) *and* a **notification adapter** (`ResultSink`). But these are two different *roles*, not a receiver subclassed into a sender, and the directions are **not symmetric per transport** — so it is not 6 mandatory classes:

| Transport | Ingestion (`KeySource`) | Notification (`ResultSink`) | Notes |
|---|---|---|---|
| WebSocket | `onMessage` | `broadcast(...)` | a **single dual-role class** can do both (bidirectional, multi-client, broadcast built-in) |
| ZeroMQ | `PULL` adapter | **separate `PUB`** adapter | two thin adapters — socket types are fixed |
| Raw TCP | existing single-accept reader | **new** broadcaster (accept-loop + registry + length-prefixed framing + per-client write isolation) | sender is much heavier than the receiver; not a flip |

Fill the matrix by **value, not parity**: WebSocket both (API + web UI) first; ZMQ both (`PULL`+`PUB`) for cluster scale; raw TCP `KeySource` now, `ResultSink` optional/last (most work, least unique benefit). What makes the senders cheap is a **shared protocol codec** + the shared `ResultSink` event model, so each transport adapter is thin byte-moving plumbing.

#### Statistics publisher (thread-independent, change-fired) — feeds `connection.sender`

**Current state (researched).** Runtime metrics live in two places, both written from many threads:
- `RuntimeStatistics` (shared; created by `Finder`, injected into producers + consumer): `batchesByProducer` (per-producer label → `AtomicLong`, **incremented by each producer** on every dispatched batch) and a late-bound `runningProducersGauge` (computed from producer `RUNNING` states).
- `ConsumerJava` `AtomicLong` counters, mutated on the **consumer hot path**: `checkedKeys` (per address check), `checkedKeysSumOfTimeToCheckContains`, `hits`, `vanityHits`, `consumerReadyCount`, `producerBlockedCount`; plus derived `keysQueue.size()`, `runningConsumerCount()`, `startTime`.
- These are surfaced **pull/poll-only**: `ConsumerJava.startStatisticsTimer()` runs a `ScheduledExecutorService` every `printStatisticsEveryNSeconds` (default 60s), reads everything, renders a string via `Statistics.createStatisticsMessage(...)`, and `LOGGER.info`s it. **The log is the only sink.**

**Proposed.** Add a **thread-independent `StatisticsPublisher`** that pushes updates to subscribers via the `ResultSink` (`connection.sender`), without touching the hot path:
- Extract the per-tick aggregation into an immutable **`StatisticsSnapshot`** (uptime, keys, keys/sec, avg contains-time, batches-by-producer, producers running, consumers running, ready, blocked, queue size, hits, vanity hits). Feed the *same* snapshot to **both** the existing log line and the publisher — one source of truth, log behaviour unchanged.
- The publisher **owns its own thread/scheduler**, reads the lock-free atomics, and broadcasts. Producers/consumer keep doing **only atomic increments** as today; a slow subscriber can never stall them (bounded per-subscriber queues, drop the lossy snapshot).

**Critical: "fire on every change" must be coalesced, not literal.** `checkedKeys` increments millions of times/sec — publishing per-increment is the same firehose the per-produced-key non-goal forbids. So split by rate:
- **Discrete, low-rate changes** → fire **immediately** on change: a `HIT`, a `VANITY_HIT`, a producer started/stopped, `KEY_QUEUED`, `GENERATION_STARTED`. (These are exactly the discrete events in the schema above.)
- **High-frequency aggregate counters** (`checkedKeys`/keys-per-sec, queue size, avg-time) → publish a **coalesced `StatisticsSnapshot` on a tick** (the configured cadence, or faster — e.g. 1 s — for a responsive UI), never per-increment. A `dirty` flag skips ticks where nothing changed (idle); optionally emit only the fields that changed.

So the publisher's two outputs map onto the existing event set: discrete changes = `HIT`/`VANITY_HIT`/`KEY_QUEUED`/`GENERATION_STARTED`; the coalesced snapshot = `PROGRESS`/`STATS`. (The separate address-file-import path has its own `ReadStatistic` progress, which could publish the same way if a UI ever drives imports.)

#### Deliverables (ordered)

1. **Finding service that accepts keys / ranges / hash160s (do first).** New run mode (`CCommand`, e.g. `FindService`) wiring the consumer to a network *key source* instead of an in-process producer queue. Introduce a `KeySource` abstraction feeding the existing queue; producers (`ProducerJava`, `ProducerOpenCL`, third-party) become **clients** of the protocol. Add `SUBMIT_RANGE` (reuse the incremental expander) and `SUBMIT_HASH160` (checker mode).
2. **Notification / result feedback channel (second) — `connection.sender`.** A structured **outbound** event stream published from the consumer via a transport-agnostic `ResultSink`/event-emitter: `CONFIG` on connect, then `KEY_QUEUED`, `GENERATION_STARTED`, `VANITY_HIT`, `HIT` (founds), and optional aggregate `PROGRESS` — **configured results of interest only, never per produced key**. Optional and **backpressure-safe (never blocks the finding hot path)**. Broadcast to all subscribers over WebSocket / ZMQ `PUB` / TCP. This is what lets a separate listener or web UI watch a scan live instead of tailing logs.
3. **Web UI (third; ties into the Cross-platform GUI entry below).** Configure/start/stop a scan and **monitor** it (live keys/sec, range progress, hits, vanity) by subscribing to the Deliverable-2 stream over the same WebSocket. JSON config stays as the headless interface.

#### Guardrails

- Preserve the layered architecture: the network *source* and *feedback emitter* are new edges — introduce them as their own small packages/interfaces (`KeySource` → existing `LinkedBlockingQueue`; `ResultSink` ← consumer events), not by threading sockets through `ConsumerJava`.
- The feedback channel publishes events; UI / CLI / remote producer are just subscribers — finding must not depend on any UI.
- **Security first:** ingestion and feedback move **private keys and hits**. Bind to localhost by default; require auth (token / mTLS) and TLS before any remote-exposed default; document the exposure model in `docs/wire-protocol.md`. Add bounded queues so a hostile/fast sender cannot OOM the finder.

### Cross-platform GUI (desktop + mobile) — beginner-friendly launcher

BAF is currently a CLI + JSON-config tool. Adding a minimal GUI would make it accessible to users who are not comfortable with JSON configuration files. The target experience: download one file, double-click, pick a GPU or CPU, point at the Light DB, click Start — and see a live counter of keys/second and any hits.

**Scope for a first version (desktop only):**
- 1-GPU or 1-CPU random key generator (single `producerOpenCL` or `producerJava` with `keyProducerJavaRandom`)
- Light DB read-only check (`TRUNCATED_LONG_64` or `BLOOM` backend)
- Live statistics panel: keys/sec, total scanned, uptime, hits
- Start / Pause / Stop controls
- No LMDB import/export UI — the CLI covers that; the GUI is scan-only

**Cross-platform UI framework investigation (must settle this before implementing):**

The choice of UI toolkit determines whether the same codebase can later reach Android.

| Framework | Desktop (Win/Mac/Linux) | Android | Notes |
|---|:---:|:---:|---|
| **JavaFX (OpenJFX)** | ✅ | ❌ (not natively) | Standard Java desktop toolkit; good styling via CSS; ships as Maven dep (`org.openjfx`); well-documented |
| **JavaFX + Gluon Mobile** | ✅ | ✅ | Gluon's `client-maven-plugin` cross-compiles JavaFX to Android/iOS via GraalVM Native Image; complex build matrix but same Java/JavaFX codebase throughout |
| **Compose Multiplatform (JetBrains)** | ✅ | ✅ | Kotlin-first; supports Desktop + Android + iOS + Web from one codebase; modern declarative UI; BAF's Java core would be called from a Kotlin UI module — interop is clean |
| **Swing** | ✅ | ❌ | Still works; ugly by default; no path to mobile; not recommended for new work |
| **SWT** | ✅ | ❌ | Eclipse toolkit; native widgets; no mobile path |

**Recommended investigation order:**
1. **JavaFX (desktop only first)** — lowest friction: pure Java, Maven dep, no Kotlin, no native build. Delivers the desktop goal immediately. Prototype this first.
2. **Compose Multiplatform** — if Android is a real goal, evaluate whether the Kotlin UI layer calling the Java BAF core (via a thin adapter module) is maintainable. The BAF library itself stays Java 21; only the UI module is Kotlin. This is the cleanest path to a single-source desktop + Android app.
3. **Gluon Mobile** — only investigate if JavaFX is chosen for desktop and Android is required without rewriting the UI in Kotlin. The GraalVM Native Image build for Android is heavyweight to maintain.

**Android considerations:**
- BAF uses Java 21 features (records, sealed types, text blocks, pattern matching) which are not available on all Android versions via `d8`/`r8`. The GPU pipeline (JOCL) has no Android equivalent — the Android version would be CPU-only (`KeyProducerJavaRandom` + `ConsumerJava` with `TRUNCATED_LONG_64`).
- A pragmatic split: ship the desktop GUI as a standalone JavaFX module; ship the Android app as a separate Kotlin + Compose module that uses a stripped-down BAF core (CPU-only, no JOCL dependency, Java 8–compatible subset or a dedicated `baf-core-android` artifact).
- The checker-service TODO above would let the Android app act as a *remote viewer* — the heavy scanning runs on a desktop/server and the Android app displays live stats and hits over a WebSocket, without running the scan itself.

**Module layout when implemented:**
```
BitcoinAddressFinder/
├── baf-core/          # existing library code (producer/consumer/persistence/…)
├── baf-cli/           # existing Main.java entry point
├── baf-gui-desktop/   # JavaFX desktop app (new)
└── baf-gui-android/   # Android / Compose module (new, later)
```

Until the investigation settles on a toolkit, no UI code should be added to the existing modules. Record the toolkit decision and its rationale in `docs/gui-toolkit-decision.md` before starting implementation.

### OpenCL backend abstraction & multi-device coverage (migrated from GitHub issues)

- **Pluggable OpenCL backend behind a small device/library abstraction** (migrated from GitHub issue #22 "Can jogamp be used to improve OpenCL handling?"). Today the GPU layer is bound directly to **JOCL** (`org.jocl`, `jocl 2.0.6`): `opencl/OpenCLBuilder` enumerates platforms/devices via raw `org.jocl.CL` calls, `opencl/OpenCLContext` compiles/runs the kernel, `opencl/OpenClTask` sets kernel args, and `opencl/OpenCLDevice` is a hand-written value type. The goal is a **tiny internal API** (interface) for "list platforms/devices" + "build a context / run the kernel grid" so the OpenCL implementation can be **switched between backends** without touching producers/config.
  - **Step 1 — define the abstraction over the existing JOCL impl.** Extract a minimal interface set (e.g. an `OpenClBackend` / device-enumeration + context-and-grid-execution contract) and make the current JOCL code the first implementation behind it. No behaviour change; pure introduction of the seam. Keep `jocl` confined to the `opencl` package (already enforced by the `joclConfinedToOpencl` ArchUnit rule).
  - **Step 2 — wire a second backend behind the same API.** Add **JogAmp JOCL** (`com.jogamp.opencl`, OO `CLPlatform`/`CLDevice`/`CLContext`) as an alternative implementation selectable at runtime/config, so the two bindings can be A/B'd (device enumeration, kernel build/run, native-lib packaging). Decide based on results whether JogAmp simplifies the layer enough to become the default or stays optional.
  - Open questions to settle when picked up: where the backend is selected (new `configuration` field vs auto-detect), how kernel source/args map across the two APIs, and the native-library/packaging impact of adding JogAmp.

- **Add a test exercising two OpenCL devices simultaneously** (migrated from GitHub issue #6). Current OpenCL coverage drives a **single** device (`ProbeAddressesOpenCLTest`, gated by `OpenCLPlatformAssume`). Add a test that runs **two `producerOpenCL` instances concurrently** (the multi-device path the project supports via multiple `producerOpenCL` entries) and asserts both produce and feed the consumer correctly at the same time.
  - **Scope: two _physical_ OpenCL devices** — e.g. a machine with two GPUs, or one GPU plus a CPU that exposes an OpenCL device. (Not two logical handles to the same device.) Each `producerOpenCL` targets a distinct `(platformIndex, deviceIndex)`.
  - Availability gate: the test must self-skip unless **≥ 2 distinct physical OpenCL devices** are enumerated (extend the `OpenCLPlatformAssume` pattern). Most CI has 0–1 device, so it will usually skip, like the existing OpenCL tests; it is meant to run on a real dual-device host.

## Open — cross-cutting (slice for this repo)

- **jqwik pin policy** — see [`../workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md). `jqwik.version ≤ 1.9.3` is mandatory.

- **`@VisibleForTesting` audit.** 10 sites remaining (down from 19 — see workspace `crossrepostatus.md` for the site-by-site audit). All 10 are legitimate per the design-fit review; no further cleanup recommended unless the source moves.

- **Null-safety further refinement.** JSpecify + NullAway are enforced at compile time in **strict JSpecify mode** with the extra options `CheckOptionalEmptiness`, `AcknowledgeRestrictiveAnnotations`, `AcknowledgeAndroidRecent`, `AssertsEnabled` (see `pom.xml`). Every package carries an explicit `@NullMarked` via `package-info.java` so the convention is visible to non-NullAway tools (IDEs, Kotlin, Checker Framework). The 50 `@Nullable` sites currently in the codebase are all legitimate. `OpenCLContext.getOpenClTask()` returns `Optional<OpenClTask>` rather than `@Nullable OpenClTask` to surface the lifecycle state in the type. Open follow-up: review any future-added public API surfaces for places where `@Nullable` would be more precise than the implicit non-null default; consider whether further `@Nullable T` returns should migrate to `Optional<T>` on a case-by-case basis (the project's established convention is `@Nullable`; Optional is used selectively for lifecycle-shaped APIs).

- **SpotBugs `effort=Max` + `threshold=Low`** — ✅ **enforced at the gate** (`76fd1a7`). `pom.xml` `<effort>Max</effort>` + `<threshold>Low</threshold>`; `spotbugs:check` is part of `mvn verify` and fails on any unsuppressed finding. The full clearing chain (191 → 0) is recorded in [`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md) under "SpotBugs Max+Low". `spotbugs-exclude.xml` carries narrow `<Match>` blocks with rationale for every structural false positive (Lombok-USBR, project-wide CRLF mitigation, generic-erasure CHECKCAST in keyproducer, `@FireAndForget` Future-DLS, Producer interface heterogeneous throws, drain-pattern PRMC, CWE-338 demo RNG, secp256k1 curve params, JOCL-spec nulls, preserved-for-revival private helpers, plus the two opt-in lifecycle items below).

- **Mutation-testing threshold expansion** — the gate now covers a verified-100% **15-class** list (`util.BitHelper` + `util.PrivateKeyTooLargeException` + `model.PublicKeyBytes`/`AddressToCoin`/`AddressType` + the 8 custom exceptions + `statistics.Statistics`/`ReadStatistic` + `configuration.CKeyProducerJavaIncremental`; 65 mutations, pitest-maven 1.25.4). The earlier `net.ladenthin.bitcoinaddressfinder.BitHelper` target was stale (BitHelper moved to `util/` in the restructure → gate matched nothing) and has been fixed. `model.Hash160` is deliberately excluded (its fast/slow hash paths are identical, so the `if(useFast)` negate mutant is equivalent). Still open (optional): config getters covered only by producer/keyproducer integration tests, and the larger orchestration classes (producer / consumer / engine / opencl) which need heavier fixtures.

- **Additional ArchUnit rules to consider** — public-fields-final, `noTestFrameworksInProduction`, `loggersArePrivateStaticFinal`, `noPackageCycles`, the full **`layeredArchitecture()`** rule, **per-module banned-imports** (`joclConfinedToOpencl`, `networkInputLibsConfinedToKeyproducer`, `lmdbConfinedToPersistenceAndIo`), and the **no-public-mutable-static-state** rule (`noPublicMutableStaticFields` — public static fields must be final; 0 violations, pure drift-guard) are all DONE. No further ArchUnit rules open.

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
  runner now waits on `keysQueue.poll(queuePollTimeoutMillis, MILLISECONDS)`
  between drain cycles instead of `Thread.sleep(queuePollTimeoutMillis)`,
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

- **GPU grid-size sweep DONE.** `src/test/java/net/ladenthin/bitcoinaddressfinder/benchmark/GridSizeSweepBenchmark.java` sweeps `CProducer.batchSizeInBits` × `CProducerOpenCL.keysPerWorkItem`. Kernel entry is `OpenCLContext.createKeys(BigInteger privateKeyBase)`. Availability gate is `new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable()`. `@Fork` `jvmArgsAppend` carries the full project-canonical master JVM-flag list (the same 24-entry `--add-opens`/`--add-exports` set, in the same order, as `pom.xml` `<argLine>`, `.mvn/jvm.config` and `examples/*.bat`) so JMH's forked JVMs match the JVM Surefire uses. Throughput unit is kernel launches per second at each corner; candidates/sec = launches/sec × `(1 << batchSizeInBits)`. JMH's `@OperationsPerInvocation` cannot normalize this automatically because it needs a compile-time constant and `@Param` is runtime; documented in the class Javadoc.

  **NOT imported from the cjherm/BAF23 fork** (intentional scope cap): the `BenchmarkFactory` / `BenchmarkSeries` / `BenchmarkLogger` / `LatexContentCreator` harness, the `command: "BenchmarkSeries"` `CCommand` extension, and the SHA / RIPEMD-160 GPU-vs-CPU comparison rounds.

  **Context-reuse / init-cost-amortisation sweep — explicitly NOT imported.** The fork ships `CtxRoundsIteratorBenchmark` (fix `gridNumBits`, vary kernel-launches-per-context, measure init-cost amortisation curve). It is operationally meaningless for this codebase: `ProducerOpenCL` creates the `OpenCLContext` once in `initProducer()`, runs `createKeys(BigInteger)` on every produced batch, and closes the context once in `releaseProducer()`. The smallest production scan is ≳ 10⁶ kernel launches against the one long-lived context; init cost is already amortised to noise. Re-importing this idea later requires evidence that BAF's lifecycle has changed to short-burst / one-shot scans.

### Workspace migration

- **Abstract the Java and test writing guidelines to a workspace-level shared layer.** Canonical guides at [`../workspace/guides/src/CODE_WRITING_GUIDE-8.md`](../workspace/guides/src/CODE_WRITING_GUIDE-8.md) (Java 8 baseline) + [`CODE_WRITING_GUIDE-21.md`](../workspace/guides/src/CODE_WRITING_GUIDE-21.md) (Java 21 supplement, applies to this repo), and [`TEST_WRITING_GUIDE-8.md`](../workspace/guides/test/TEST_WRITING_GUIDE-8.md) + [`TEST_WRITING_GUIDE-21.md`](../workspace/guides/test/TEST_WRITING_GUIDE-21.md); canonical TDD skill at [`../workspace/.claude/skills/java-tdd-guide/SKILL.md`](../workspace/.claude/skills/java-tdd-guide/SKILL.md). BAF's `CODE_WRITING_GUIDE.md` / `TEST_WRITING_GUIDE.md` now contain only BAF-specific supplements.
- **Standardised CLAUDE.md template** — [`../workspace/templates/CLAUDE.md.template`](../workspace/templates/CLAUDE.md.template).

### Dedicated sequential-only "addition-walk" OpenCL kernel (160-bit, output-only)

- New standalone kernel for contiguous scanning: single host-supplied anchor, pure affine point-addition walk (no per-key `k·G`, no comb/wNAF), 160-bit MSB-zero range, compact/output-only. Full design, rationale, constraints, and validation: [`docs/performance.md`](docs/performance.md) §8 "Future work".
- **Persistent / warp-synchronous "megakernel" variant** (different execution model, large rewrite): launch once with occupancy-maximal resident threads, each walking its own keyspace stripe in registers and looping add → hash → Fuse8 → append-hit, running until a host stop flag with async output draining. GPU-only compute ⇒ a thin host (seed initial keys, drain hits, checkpoint the frontier) that could be reimplemented standalone (e.g. in Rust); reading back each thread's frontier yields a compact `ddrescue`-style resumable coverage/map file (provable gap-free searched-domain record). Details + gotchas (TDR/watchdog, async drain, divergence/safegcd): [`docs/performance.md`](docs/performance.md) §8.
