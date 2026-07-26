# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **The same GPU behind two OpenCL platforms is swept once** — a duplicate ICD registration (common
  on Windows AMD systems, where driver and runtime both register) makes one card enumerate twice, so
  a two-card machine appeared as four devices and would have been measured, and then driven, twice
  over. Devices are now de-duplicated by a fingerprint of the *physical* card, taken from
  `cl_khr_device_uuid`, else `cl_khr_pci_bus_info`, else AMD's `cl_amd_device_attribute_query`. The
  first two are Khronos standards that NVIDIA and Intel report as well, so this is not AMD-specific.
  An unavailable fingerprint never merges: a rig of identical cards keeps every device, because the
  device name is deliberately not used as a signal.
- **The fat jar runs with a bare `java -jar`** — the assembly manifest now carries the `Add-Opens`
  that lmdbjava needs to reflect into `java.nio`/`sun.nio.ch`, so the launcher scripts are no longer
  required for LMDB access (they remain useful for the heap and Logback settings). Guarded by
  `JvmModuleFlagConsistencyTest`, which asserts the manifest entry equals the `java.base` opens in
  `.mvn/jvm.config` — the pom comment asked for that sync, nothing enforced it.
- **`TuneConfiguration` measures every GPU, and by default every one it detects** — it used to take
  `producerOpenCL.get(0)` silently. A dual-GPU configuration produced a full-looking report for one
  device and a paste-ready configuration whose second entry still held the operator's guesses,
  visually identical to the measured one; on a contributor's laptop that guess was 2.3× off that
  device's optimum. All configured entries are now swept in turn, each keeping its own winner, and an
  empty `producerOpenCL` list — previously an error — means "sweep every detected GPU", which is the
  state most first runs are in. CPU OpenCL runtimes (pocl, Intel's CPU ICD) are excluded, devices are
  measured one at a time so they do not contend, and the emitted configuration gives each producer
  its own key producer per the shipped examples' convention.
- **Light and Full DB entry counts refreshed** — `targetDatabaseEntries` defaults to `141045995`
  (Light, 2026-07-20 publication) instead of the stale `132288304`; the Full tier reference is
  `1472947953`. Historical measurement tables keep the counts they were measured at.
- **The tuning sweep extends itself instead of advising a re-run** — when the winner lands on the
  largest `keysPerWorkItem` or `batchSizeInBits` tried, `TuneConfiguration` keeps measuring past the
  configured candidates (doubling, or one bit at a time) until an extra arm stops improving. A winner
  at the edge is a lower bound, not a peak, and "widen the list and run it again" only reaches
  someone who reads the guide first — a 20-minute measurement is run once. Two field sweeps both won
  at the shipped list's last value of 256; widening by hand afterwards gained 17 % and 104 %. Costs
  one arm per step, bounded by `maxExtensionArms` (default 4), disabled with
  `extendSweepWhenWinnerIsAtTheEdge: false`. `batchSizeInBits=24` is treated as the framework
  maximum rather than a truncation, so it extends no further and the report says why.
- **Separate CI pipeline for the measurement tooling** — `.github/workflows/measurement-tools.yml`
  runs the `register_machine.py` tests on Python 3.9 and 3.13, path-filtered to
  `docs/measurements/**`. Kept out of the Java build, which shares none of its setup and takes
  minutes rather than a second.
- **Step-by-step tuning guide** — [`docs/tuning-your-gpu.md`](docs/tuning-your-gpu.md) walks through
  finding device indices, writing a tuning config, capturing the log, reading the report, tuning a
  second GPU and contributing the numbers back, with every command explained. Linked from the
  README's `TuneConfiguration` section.
- **`TuneConfiguration` distinguishes "too slow to measure" from a failure** — an arm that ran
  without error but completed no batch inside its measurement window now reports `NOT MEASURED:` with
  the reason and the remedy, instead of a bare `0.00 candidates/s` that reads like a driver
  rejection. Happens at large `batchSizeInBits` with tiny `keysPerWorkItem`.
- **First contributed multi-GPU machine in the measurement registry** —
  `coreultra7155h-95g-win11` (Lenovo ThinkPad P14s Gen 5: Core Ultra 7 155H, RTX 500 Ada + integrated
  Arc Pro), with `tuner_coreultra7155h.csv` carrying all 84 measured arms for both devices. Peaks:
  **110.7 M keys/s** on the RTX at `batchSizeInBits=24, keysPerWorkItem=2048` and **36.5 M keys/s**
  on the Arc at `23, 2048`. Until now every registered machine was high-end desktop hardware with a
  single GPU.

### Changed
- **`machines.json` records `gpu` as a list** — multi-GPU machines are the normal case (any laptop
  with a discrete card also has an integrated one, and this project runs on both), so the field
  holds every device instead of a comma-joined string that readers had to split. Existing entries
  migrated; `register_machine.py --set gpu="A,B"` accepts a comma-separated value. `plot.py` reads
  only `cpu.l3_mb` from the registry and is unaffected.
- **Statistics line reports candidates per producer instead of dispatched batches** — the
  `Batches per producer` group is replaced by `Keys per producer`, which prints each producer's
  generated-candidate count and its **share of the total**
  (`gpu0 (Incremental, GPU)=19 G (94.0%), gpu1 (Random, GPU)=1 G (6.0%)`). Batch counts are not
  comparable between producers: one batch yields `2^batchSizeInBits` candidates, so on a multi-GPU
  run with different batch sizes the old field read as a near-even split where the real work split
  was an order of magnitude apart. `RuntimeStatistics` still tracks batch counts (used by
  `TuneConfiguration`); only the rendered field changed.

### Fixed
- **Device de-duplication requires the name to agree, not just the identity** — a fingerprint shared
  by *differently named* devices is a driver defect rather than one card seen twice, and acting on it
  would silently halve a multi-GPU machine with nothing in the run reporting it. The name remains a
  veto and never a merge signal (merging on names would collapse a rig of identical cards). The
  asymmetry decides it: a missed merge costs one redundant sweep, a wrong merge costs half the
  hardware. Cheap insurance, since two of the three fingerprint sources have not yet run on real
  hardware — the only dual-ICD machine available reports neither Khronos extension.
- **`disableAddressLookup: true` no longer opens LMDB** — it opened the environment unconditionally
  and failed with `ESRCH` / `InaccessibleObjectException` when no database existed, which is exactly
  the situation disabling the lookup is meant to permit. A pure key-generation run, or the tuner's
  paste-ready config before a database has been imported, now works with no database on disk.
- **Example launchers and the tuning guide referenced a stale jar version** — 22 `run_*` scripts and
  `docs/tuning-your-gpu.md` still named `1.7.0`. Bumped to the project version and guarded by
  `ExampleRunScriptJarVersionTest`, which reads the version from `pom.xml` so it cannot drift again.
- **`TuneConfiguration` no longer reports a failing arm as a measurement** — an arm measured while
  the device was throwing errors was reported with whatever throughput it managed, indistinguishable
  from a healthy arm. A real sweep on an Intel Arc recorded `1,022,288 candidates/s` for an arm
  during which the producer hit `CL_OUT_OF_RESOURCES` **40 times**, and it was the arm immediately
  before every larger grid began failing — so the row that would have explained the cascade was
  presented as normal. Neither failure mode could reach the thread that started the producer: a
  fatal exception ends the run loop quietly, and a per-secret failure is caught, logged and skipped
  while the producer stays alive and looks healthy. Producers now record both
  (`ProducerStateProvider#getLastFailure()`), and the sweep marks such an arm `FAILED` with the
  cause, discarding the rate.
- **REUSE compliance restored** — the licensing check had been failing on `main`. All measurement
  data and generated plots are now covered by globs in `REUSE.toml` (so new machines and benchmark
  runs stay compliant without an edit), and four example configs that were never added to the
  existing list are included. 486/486 files compliant, was 462/486.
- **`register_machine.py` no longer hides every non-NVIDIA GPU** — `detect_gpu()` returned as soon
  as `nvidia-smi` named anything, which made the platform-wide enumeration unreachable on exactly
  the machines that need it: a laptop with an NVIDIA card *and* an integrated GPU recorded only the
  NVIDIA. All sources are now consulted and merged, with duplicates dropped. Detection reports what
  the OS enumerates, which need not match the OpenCL device list — `OpenCLInfo` stays authoritative
  for which devices this tool can drive.
- **Rate and count formatting no longer collapses precision at a unit boundary** — `formatRate` and
  `formatCount` rounded the scaled value to a whole number, so everything from 1.000 to 1.499 G/s
  printed as `1 G/s` and tuned runs became impossible to compare once they crossed 1 G/s (reported
  in #250). The same collapse happened at the `k/s` and `M/s` boundaries. Both now keep four
  significant digits: `1.400 G/s`, `130.0 M/s`, `2.013 k/s`, `412/s`.
- **Multi-line reports are readable again** — the `TuneConfiguration` report, the OpenCL device dump
  and the transformed-configuration echo were folded onto a single line separated by ` | ` by the
  log pattern's CRLF guard, forcing users to reformat them by hand to read them (reported in #250).
  They are now emitted one log record per line via `util.MultilineLogger`. The guard is unchanged
  and undiminished: every emitted line still carries a prefix the appender wrote, and the split
  consumes every line-break form so no line can carry another past it.

## [1.7.0] - 2026-07-23

Major release centred on high-performance probabilistic address filters (in-memory and GPU-side),
a self-tuning benchmark mode, a rebuilt runtime statistics log, and a reworked bulk-import pipeline.

### Added
- **Binary Fuse filters as address-lookup backends** — `BINARY_FUSE_8` (~1.13 B/entry, ~0.39 % FPR)
  and `BINARY_FUSE_16` (~2.25 B/entry, ~0.0016 % FPR) in-memory presence filters in front of LMDB,
  built by multi-pass peeling with progress logging. LMDB stays open to verify the handful of
  survivors, so a false positive can never be reported as a hit.
- **`BLOCKED_BLOOM` address-lookup backend** — a 512-bit-block Bloom filter sized with fastrange
  (exact, no power-of-two rounding) and configurable geometry (`blockedBloomBitsPerEntry`,
  `blockedBloomK`).
- **GPU-side filter cascade** — `producerOpenCL.gpuFilterType` (`FUSE_8` / `FUSE_16`) builds a filter
  from the database and uploads it to the GPU, so the kernel checks each derived hash160 inline and
  transfers back only the survivors ("compact mode"), collapsing the PCIe read-back. Independent of
  the CPU `addressLookupBackend`; works with the `LMDB_ONLY` default.
- **`TuneConfiguration` command** — measures the net end-to-end throughput of every
  `batchSizeInBits × keysPerWorkItem` arm on your own hardware and emits the winning config as
  ready-to-paste JSON, plus a measured database-lookup cost. The sweep runs up to the framework
  maximum `batchSizeInBits = 24`.
- **Rebuilt runtime statistics log** — reports `Generated` (candidate keys the producers actually
  compute) next to `-> LMDB` (survivors reaching the database) and the `pre-filtered` share, over a
  trailing windowed rate that auto-scales `/s … G/s`. Replaces the old consumer-only rate, which made
  a filtered GPU run look idle.
- **Filter build progress logging** — `reading → indexing → peeling → assigning → ready` phases with
  rate/ETA for every in-memory filter build.
- **Measurement suite** — storage-free filter comparison (`FilterMeasurementMain`,
  `bench_filters.sh`), GPU filter-probe and JMH benchmarks, and CSV-backed data under
  `docs/measurements/` with generated plots and tables (`plot.py`, self-registering `machines.json`).
- Example configs for the GPU filter cascade and per-tier filter choices; `useNoReadAhead` on the
  read-only LMDB configuration.
- **Decoupled, multi-threaded `AddressFilesToLMDB` import** — a single buffered reader streams file
  lines into a bounded queue, `threads` parser workers decode them, and one writer stores them in
  LMDB in batches (one transaction per `writeBatchSize` entries). New `CAddressFilesToLMDB` fields
  `threads` (default 1), `writeBatchSize` (default 32768) and `queueCapacity` (default 200000). Adds
  per-file byte-offset read progress and an "X/Y files" counter. `threads = 1` preserves the exact
  deterministic import order; higher values parse in parallel — order-independent when
  `useStaticAmount = true`, otherwise a non-determinism warning is logged.
- **`CompactLMDB` command** — writes a compacted copy of an existing LMDB database via LMDB's
  `MDB_CP_COMPACT` (free/dead pages omitted, pages laid out sequentially → a smaller, read-denser
  `data.mdb` in a separate target directory). The source is opened read-only and left unchanged; the
  compacted copy is re-opened and its entry count verified against the source. Ships an example
  `config_CompactLMDB.json` and run scripts.
- **Durable LMDB flush on close** — the writable env keeps its fast sync-free write flags
  (`MDB_NOSYNC` / `MDB_MAPASYNC`) during the import for speed and now forces one full `env.sync(true)`
  when closing, so a normal shutdown leaves the whole database on disk.
- **`LMDBDelta` command** — streams every address present in one or more source LMDB databases but
  **not** in a reference database to a plaintext file (one mainnet Base58 `1…` address per line,
  re-importable with `AddressFilesToLMDB`). It walks the key-ordered databases as a near-zero-memory
  k-way cursor merge (memory is just one 20-byte key per cursor, independent of the delta size), logs a
  per-source summary of how many delta addresses each source contained, and reports periodic progress
  with rate/ETA. See `examples/config_LMDBDelta.json` and the `run_LMDBDelta` launchers.

### Changed
- **`FUSE_16` is the recommended default GPU pre-filter** (best net end-to-end throughput; fits VRAM
  even at the Full DB tier). Documented in the README, `docs/filter-selection.md`, and
  `docs/performance.md`.
- Blocked Bloom `DEFAULT_K` 8 → 6, matching the fastrange density; the in-RAM Bloom filter is now
  keyed on 8 bytes instead of 20 (~22 % faster lookups).
- JSON/YAML config serialization uses field-only visibility so round-trips are stable (fixes derived
  getters such as `getOverallWorkSize` leaking into the emitted configuration).
- Dependency bumps: `bcprov-jdk15to18` 1.84 → 1.85, plus routine updates to `junit-jupiter`,
  `jackson`, `logback-classic`, `nullaway`, `pitest-maven`, `spotless`, the Checker Framework, and CI
  actions.
- **GPU example configs default to the inlined kernel** (`producerOpenCL.noInlineHelpers: false`) for
  full runtime throughput — measured ~3.6× faster than the out-of-lined kernel on an AMD RX 7900 XTX
  (RDNA3) and ~4.5× on NVIDIA; the trade-off is a one-time slow first compile on AMD (then
  `comgr`-cached). The runtime auto-default is unchanged (out-of-line on AMD to keep CI compiles fast)
  and now logs symmetric warnings so the compile-vs-speed trade-off is never silent.

### Fixed
- **GPU Fuse-16 upload at the Full DB tier** — the `short[]` fingerprints were flattened into a
  `byte[]` that overflowed `Integer.MAX_VALUE` (`NegativeArraySizeException`); they are now uploaded
  directly as `short[]`.
- **GPU filter upload under host-memory pressure (RDNA3)** — `clCreateBuffer` with
  `CL_MEM_COPY_HOST_PTR` failed with `CL_MEM_OBJECT_ALLOCATION_FAILURE` while the build's transient
  heap garbage was still resident; the heap is now reclaimed (`System.gc()`, injectable) immediately
  before the upload. Verified on an RX 7900 XTX.
- **`TuneConfiguration` on smaller GPUs** — an arm whose grid is too large for the device is caught
  and recorded as unusable instead of aborting the whole sweep, so `batchSizeInBits` candidates up to
  the hard framework cap can be swept on any card.
- Measurement plots — the GPU filter-probe chart now shows the 1 B blocked-Bloom bars
  (density-encoded filter names were not matched), and the blocked-Bloom sizing figure plots one
  coherent series instead of a zigzag across machines and `k`.
- Documentation corrected for the current statistics-log format, the keys-vs-addresses relationship
  (each key yields both a compressed and an uncompressed address, so addresses examined are twice the
  `Generated` rate and `pre-filtered` is measured against that doubled figure), and stale constant
  references.
- **Corrected the light/full prepared-database backend recommendation** in the README from
  `TRUNCATED_LONG_64` to `BINARY_FUSE_8` — `TRUNCATED_LONG_64` has the worst lookup latency of any
  backend at 100 M+ entries (it was already flagged as "never" for this in `docs/filter-selection.md`),
  while `BINARY_FUSE_8` costs ~7× less RAM and is far faster; documented the `FUSE_16` GPU +
  `BINARY_FUSE_8` CPU cascade at both tiers.
- **Reconciled the Full-DB backend recommendation across all docs.** An earlier recommendation flip
  (blocked Bloom → `BINARY_FUSE_8`, on total cost once Fuse-8's Full-DB build was shown feasible)
  updated `docs/filter-selection.md` and most of the README but missed several spots, leaving them
  contradictory. `docs/performance.md`, the remaining README conclusions, and the `BLOCKED_BLOOM`
  enum Javadoc now consistently recommend `BINARY_FUSE_8` on total cost, with `BLOCKED_BLOOM` framed
  as the rebuild-heavy / heap-constrained niche. The stale `BLOCKED_BLOOM` enum Javadoc (pre-fastrange
  power-of-two sizing, 1.56/2.06 B/entry, 0.49/0.18 % FPR) was corrected to the current fastrange
  numbers (1.375 B/entry at the default 11 bits/entry, ~0.76 % FPR); a stale README lookup-latency
  table was re-synced to the CSV-authoritative one; and the Full-DB RAM figures for the fuse filters
  were corrected (~1.8/3.6 GB → ~1.5/3.1 GB).

[Full changelog](https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.6.1...v1.7.0)

## [1.6.1] - 2026-06-18

### Fixed
- Release/CI: re-release of `1.6.0` to complete the publish pipeline. The `1.6.0`
  run published to Maven Central but did not attach the signed artifacts to the
  GitHub release. No functional code changes versus `1.6.0`.

## [1.6.0] - 2026-06-18

### Added
- OpenSSF Best Practices passing-level artifacts: `CONTRIBUTING.md`, `SECURITY.md`,
  `CHANGELOG.md`, and `docs/RELEASE.md`.

### Changed
- Refactored `PrivateKeyValidator` from static helper methods on `KeyUtility` into a
  dedicated non-static helper class, improving testability and dependency clarity.
- Migrated JSON and YAML configuration parsing from Gson + SnakeYAML to Jackson.
- Migrated publishing from OSSRH (legacy) to the Sonatype Central Publisher Portal.
- Unified the release pipeline into a single `publish.yml` workflow with a manual
  start-gate.

### Notes
- See the `Release Process` template in [`docs/RELEASE.md`](docs/RELEASE.md).

## [1.5.0] - 2025-07-29

### Added
- Key Producer for Sockets.

[Full changelog](https://github.com/bernardladenthin/BitcoinAddressFinder/compare/1.4.0...v1.5.0)

## [1.4.0] - 2025-07-05

First non-SNAPSHOT release; published to Maven Central.

### Changed
- Bump `com.google.guava:guava` from 33.4.6-jre to 33.4.7-jre
  ([#52](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/52)).
- Bump `com.google.code.gson:gson` from 2.12.1 to 2.13.0
  ([#51](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/51)).
- Bump `commons-io:commons-io` from 2.18.0 to 2.19.0
  ([#54](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/54)).
- Bump `com.google.guava:guava` from 33.4.7-jre to 33.4.8-jre
  ([#55](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/55)).
- Bump `com.google.code.gson:gson` from 2.13.0 to 2.13.1
  ([#56](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/56)).
- Bump `org.mockito:mockito-core` from 5.17.0 to 5.18.0
  ([#58](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/58)).
- Bump `org.apache.maven:maven-artifact` from 3.9.9 to 3.9.10
  ([#59](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/59)).

### New Contributors
- @dependabot made their first contribution in
  [#52](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/52).

[Full changelog](https://github.com/bernardladenthin/BitcoinAddressFinder/compare/1.3.0-SNAPSHOT...1.4.0)

## [1.3.0-SNAPSHOT] - 2024-04-06

First Java 21+ snapshot build.

- Example configurations:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/tree/1.3.0-SNAPSHOT/examples>
- LMDB database:
  <https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database>

## [1.2.0-SNAPSHOT] - 2024-03-28

Earlier Java 21+ snapshot build.

- Example configurations:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/tree/1.2.0-SNAPSHOT/examples>
- LMDB database:
  <https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database>

## [1.1.0-SNAPSHOT] - 2022-12-19

Java 11+ snapshot build.

- Example configurations:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/tree/1.1.0-SNAPSHOT/examples>
- LMDB database:
  <https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database>

## [1.0.0-SNAPSHOT] - 2021-02-10

First public pre-release version (Java 8).

- LMDB database:
  <https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database>

[Unreleased]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.6.1...HEAD
[1.6.1]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.6.0...v1.6.1
[1.6.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.3.0-SNAPSHOT...v1.4.0
[1.3.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.2.0-SNAPSHOT...v1.3.0-SNAPSHOT
[1.2.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.1.0-SNAPSHOT...v1.2.0-SNAPSHOT
[1.1.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.0.0-SNAPSHOT...v1.1.0-SNAPSHOT
[1.0.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/releases/tag/v1.0.0-SNAPSHOT
