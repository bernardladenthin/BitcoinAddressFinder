# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.7.0] - 2026-07-20

Major release centred on high-performance probabilistic address filters (in-memory and GPU-side),
a self-tuning benchmark mode, and a rebuilt runtime statistics log.

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
