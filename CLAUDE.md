# CLAUDE.md — BitcoinAddressFinder

This document provides guidance for AI assistants working on the BitcoinAddressFinder codebase.

---

## Project Overview

**BitcoinAddressFinder** is a high-performance, open-source tool for scanning Bitcoin and altcoin private keys and checking whether the derived addresses have balances. It supports 100+ cryptocurrencies and uses both CPU (Java) and GPU (OpenCL) key generation strategies.

- **Group ID:** `net.ladenthin`
- **Artifact ID:** `bitcoinaddressfinder`
- **Version:** 1.6.0-SNAPSHOT
- **Java:** 21
- **License:** Apache 2.0
- **Author:** Bernard Ladenthin (Copyright 2017–2025)
- **Main class:** `net.ladenthin.bitcoinaddressfinder.cli.Main`

---

## Build System

The project uses **Maven** (minimum 3.6.3) with a Maven Wrapper.

### Common Commands

```bash
# Compile only
./mvnw compile

# Run all tests
./mvnw test

# Build fat JAR with all dependencies
./mvnw package -P assembly

# Build without tests
./mvnw package -DskipTests

# Generate coverage report
./mvnw test jacoco:report

# Skip LMDB-specific tests (useful when LMDB native libs unavailable)
./mvnw test -Dnet.ladenthin.bitcoinaddressfinder.disableLMDBTest=true
```

### JVM / Compiler Flags

Tests run with `-Xmx2g` (no eager `-Xms` — forks are short-lived, one fresh JVM per test class with `reuseForks=false`, so an eager initial heap only inflated the spawn-time footprint) and several `--add-opens` / `--add-exports` to allow LMDB and internal module access. These are configured in `pom.xml` (`<argLine>`) and `.mvn/jvm.config`.

**Error Prone** static analysis with **NullAway** is active at compile time:
- All code in `net.ladenthin` packages must carry proper `@Nullable` / `@NonNull` annotations.
- Compilation will fail on potential null-pointer issues unless annotated.

### Verifying Javadoc locally (release builds)

The javadoc jar is attached only by the publish/deploy job (`mvn -P release deploy`).
So a javadoc break is **invisible to `mvn test` and to PR CI** and only fails the
snapshot publish on `main`. To reproduce the deploy-time javadoc step locally, run the
**full lifecycle**:

```bash
mvn -P release clean package -DskipTests -Dgpg.skip=true \
    -Dnet.ladenthin.bitcoinaddressfinder.disableLMDBTest=true
# expected: BUILD SUCCESS + target/*-javadoc.jar
```

Do **not** rely on a standalone `mvn javadoc:jar` — it runs before
`target/classes/module-info.class` exists, so javadoc takes the classpath-mode path
and cannot reproduce the JPMS module-mode failure that only appears in the full
build. `attach-javadocs` is bound to `prepare-package` and the `maven-javadoc-plugin`
block is declared **before** `maven-compiler-plugin` on purpose, so javadoc runs while
`target/classes/` is still module-descriptor-free and stays in classpath mode (module
mode is unusable here — the module declares no `requires` and `module-info.java` lives
in `src/main/java9`, off javadoc's source path). See the extensive comments on those two
`pom.xml` executions before touching their phase or ordering.

**The single surviving `-Dmaven.javadoc.skip=true` in CI (BAF-only, do not remove blindly).**
After a cross-repo cleanup that deleted every other javadoc-skip flag from all four sibling
pipelines, exactly **one** remains: the `build` job in `.github/workflows/publish.yml`. It is
necessary because that job runs plain `mvn package`, which triggers `attach-javadocs` at
`prepare-package`; BAF's javadoc is module-aware (`<source>21</source>` + `module-info.java`
in `src/main/java9`), so an unguarded run can flip into JPMS module mode and fail with
`No source files for package net.ladenthin...`. The Java-8 siblings (java-llama.cpp,
streambuffer, llamacpp-ai-index — all `<source>8>`) stay in classpath mode regardless and
therefore carry **no** skip flag; the redundant/no-op flags they used to have were removed,
and streambuffer/llamacpp let javadoc build during their `verify` test job so it is gated in
PR CI. BAF cannot do the same cheaply (the module-mode trap needs the full `-P release
package` ordering), which is why BAF's javadoc is validated only at publish. Before dropping
this last flag, prove `mvn package` builds BAF's javadoc clean — a standalone `mvn
javadoc:jar` cannot, it always runs classpath mode and hides the trap.

### JPMS module descriptor (`module-info.java`) handling — why it's hidden until the end

BAF ships a JPMS module descriptor (module `net.ladenthin.bitcoinaddressfinder`, exports
every package, `requires static lombok`, opens `configuration` to Jackson) **but goes to
unusual lengths to keep `module-info` invisible to every build tool until the last moment.**
This is the single most surprising part of the build; the authoritative rationale lives in
the `pom.xml` comments on the `module-info-compile` and `default-testCompile` executions and
the `maven-javadoc-plugin` block — read those before touching any of it. Summary:

- **`module-info.java` lives in `src/main/java9`, not `src/main/java`.** If it were on the
  main source path, `javac` would auto-flip the *whole* main compile into module mode, which
  (a) breaks Error Prone / NullAway / Checker Framework and (b) forbids the system-module
  `--add-exports`/`--add-opens` the build relies on. It is compiled alone, at `release 9`, in
  a dedicated `module-info-compile` execution — purely an informational descriptor for
  module-path consumers. (This is **not** a Java-8 leftover; BAF is Java 21. The Java-8 sibling
  repos keep `module-info.java` in `src/main/java`, which is why they don't hit the javadoc bug.)
- **`module-info-compile` is bound to `prepare-package` (after `test`).** If
  `target/classes/module-info.class` exists during tests, Maven Surefire runs them in **JPMS
  module mode** (main code as the *named* module on the module path) instead of on the
  classpath (the *unnamed* module). The test JVM's `add-opens java.base/{sun.nio.ch,
  jdk.internal.ref,java.nio}=ALL-UNNAMED` flags (see `<argLine>`) only open those packages to
  the **unnamed** module, and **lmdbjava** (`LMDBPersistence` → `org.lmdbjava.ByteBufferProxy`)
  reflectively reaches `sun.nio.ch.DirectBuffer` / `jdk.internal.ref.Cleaner` — so in module
  mode they stop applying and LMDB tests fail with `IllegalAccessError` on
  `sun.nio.ch.DirectBuffer`. Local-dev caveat: `mvn test` after `mvn package` without an
  intervening `mvn clean` fails this way (the package phase left `module-info.class` behind).
- **`attach-javadocs` runs at `prepare-package`, before `module-info-compile`** (the
  `maven-javadoc-plugin` block is declared before `maven-compiler-plugin` so it wins the
  same-phase ordering). Javadoc must see a module-descriptor-free `target/classes` so it stays
  in classpath mode — see the "Verifying Javadoc locally" section above.
- **The internal-JDK `--add-opens`/`--add-exports` are for lmdbjava, not project code.** The
  only former internal-JDK user, `ByteBufferUtility#freeByteBuffer`, was deleted (direct
  buffers are reclaimed by the JVM's built-in Cleaner); no project source imports
  `jdk.internal.ref` or `sun.nio.ch`. The `jdk.compiler/*` opens are for Error Prone.

**Invariant to preserve:** never let `module-info.class` reach `target/classes` before tests
*and* before the javadoc jar is built. Both consumers (Surefire, javadoc) break in module mode.

---

## Project Structure

```
BitcoinAddressFinder/
├── src/
│   ├── main/
│   │   ├── java/net/ladenthin/bitcoinaddressfinder/   # layered packages (top → bottom)
│   │   │   ├── cli/                  # Entry: CLI entry point (Main.java)
│   │   │   ├── engine/              # Orchestration: Finder, Shutdown
│   │   │   ├── command/            # Orchestration: CCommand impls (LMDB import/export)
│   │   │   ├── producer/          # Pipeline: runtime key producers (queue feeders)
│   │   │   ├── consumer/         # Pipeline: ConsumerJava + Consumer contract
│   │   │   ├── keyproducer/      # Capability: key/secret generation strategies (+ BIP39KeyProducer)
│   │   │   ├── opencl/           # Capability: OpenCL device/platform + runtime (Context/Task/GridResult)
│   │   │   ├── persistence/      # Capability: presence backends (+ bloom, inmemory, lmdb)
│   │   │   ├── io/               # InputOutput: address/secret file reading + parsing
│   │   │   ├── model/            # Foundation: value/domain types (Hash160, PublicKeyBytes, …)
│   │   │   ├── util/             # Foundation: stateless crypto/byte helpers (KeyUtility, BitHelper, …)
│   │   │   ├── core/             # Foundation: lifecycle contracts (Interruptable, Startable, …)
│   │   │   ├── secret/           # Foundation: secret-supply primitives (SecretSupplier, BIP39Wordlist)
│   │   │   ├── statistics/       # Foundation: Statistics, ReadStatistic
│   │   │   ├── configuration/    # Config POJOs (C-prefixed); depends only on constants
│   │   │   └── constants/        # Constants leaf (secp256k1 spec, kernel layout)
│   │   └── resources/
│   │       ├── *.cl / *.h            # OpenCL kernels and headers
│   │       └── simplelogger.properties
│   └── test/
│       └── java/net/ladenthin/bitcoinaddressfinder/
│           └── *.java                # JUnit 6 (Jupiter) tests
├── examples/                         # Sample JSON configs and run scripts
│   ├── config_*.json
│   ├── run_*.bat                     # Windows launchers
│   ├── run_*.sh                      # Linux/macOS launchers (mirror the .bat flags)
│   ├── logbackConfiguration.xml
│   ├── addresses/                    # Sample address files
│   └── secrets/                      # Sample secrets files
├── helper/                           # Utility scripts (address scraping, wallet ops)
├── .github/workflows/                # CI/CD pipelines
├── pom.xml
├── .mvn/jvm.config
└── README.md
```

---

## Core Architecture

### Producer–Consumer Pattern

The central design separates **key production** from **address checking**:

```
[Producers] → LinkedBlockingQueue<byte[]> → [ConsumerJava]
```

- **`Producer` (interface)** — generates private keys and pushes them to the queue.
- **`Consumer` (interface)** — reads keys from the queue, derives addresses, and checks the LMDB database.
- **`Finder`** — orchestrates producers and consumers, manages thread pools, and handles shutdown.

### Key Components

| Class | Role |
|---|---|
| `cli/Main.java` | Entry point; parses args, loads config, starts `Finder` |
| `Finder.java` | Orchestrator; starts/stops producers and consumer |
| `ProducerJava.java` | CPU-based key generator using `KeyProducer` strategies |
| `ProducerOpenCL.java` | GPU-accelerated key generator via JOCL |
| `ProducerJavaSecretsFiles.java` | Reads keys from a secrets file |
| `ConsumerJava.java` | Derives addresses, queries LMDB, logs hits |
| `PublicKeyBytes.java` | Public key representation and address derivation |
| `KeyUtility.java` | Cryptographic helpers (record class) |
| `OpenCLContext.java` | OpenCL context setup and kernel management |
| `OpenClTask.java` | Executes a batch of key operations on the GPU |
| `AddressFilesToLMDB.java` | Imports address files into LMDB database |
| `LMDBToAddressFile.java` | Exports LMDB database to address files |
| `NetworkParameterFactory.java` | Creates BitcoinJ `NetworkParameters` for 100+ coins |
| `SeparatorFormat.java` | Parses address file formats (various separators) |
| `Shutdown.java` | Graceful shutdown logic with 30-second timeout |

### Key Producer Strategies (`keyproducer/`)

| Class | Strategy |
|---|---|
| `KeyProducerJavaRandom` | `SecureRandom` private key generation |
| `KeyProducerJavaIncremental` | Sequential range scanning |
| `KeyProducerJavaBip39` | BIP39 mnemonic phrase derivation |
| `KeyProducerJavaSocket` | TCP socket input |
| `KeyProducerJavaWebSocket` | WebSocket server input |
| `KeyProducerJavaZmq` | ZeroMQ message broker input |

### Configuration Classes (`configuration/`)

All configuration POJOs are prefixed with `C`:

| Class | Purpose |
|---|---|
| `CConfiguration` | Root config object |
| `CCommand` | Command enum: `Find`, `AddressFilesToLMDB`, `LMDBToAddressFile`, `OpenCLInfo` |
| `CFinder` | Finder-level settings |
| `CConsumerJava` | Consumer settings |
| `CProducer` | Abstract producer settings |
| `CProducerJava` | Java producer settings |
| `CProducerOpenCL` | OpenCL producer settings |
| `CProducerJavaSecretsFiles` | Secrets file producer settings |
| `CKeyProducerJava*` | Per-strategy key producer settings |
| `CLMDBConfigurationReadOnly/Write` | LMDB database config |

### Persistence Layer (`persistence/`)

- `Persistence` interface abstracts database operations.
- `LMDBPersistence` implements high-performance O(1) address lookups using LMDB.
- `PersistenceUtils` provides helper methods.

### Helper Classes (Validation & Utilities)

Helper classes encapsulate single-responsibility validation and utility logic, improving testability and separation of concerns:

| Class | Purpose | Injection Pattern |
|---|---|---|
| `PrivateKeyValidator` | Non-static validation of private keys against secp256k1 constraints | Created in constructors where needed; no external dependencies |
| `KeyUtility` (record) | Cryptographic utilities requiring network/buffer context | Injected as dependency into producers/consumers |
| `ByteBufferUtility` | ByteBuffer conversion utilities | Passed as constructor parameter |

**Design Pattern: Static to Helper Migration**

Certain utility methods that were previously static (e.g., key range validation) have been refactored into dedicated helper classes as non-static instance methods. This improves:
- **Testability**: Instances are easier to mock in tests
- **Clarity**: Dependencies are explicit
- **Extensibility**: Future subclasses can override behavior

Example: `PrivateKeyValidator` contains the following methods (formerly static in `KeyUtility`):
- `getMaxPrivateKeyForBatchSize(int batchSizeInBits)`
- `isInvalidWithBatchSize(BigInteger, BigInteger)`
- `isOutsidePrivateKeyRange(BigInteger)`
- `coerceToValidPrivateKey(BigInteger)`
- `replaceInvalidPrivateKeys(BigInteger[])`

Callers instantiate `PrivateKeyValidator` directly (stateless) or receive it via dependency injection where appropriate.

---

## Configuration Format

The tool is driven entirely by JSON configuration files. The `CCommand` enum selects the operation mode.

Example (find with 8 CPU producers):
```json
{
  "command": "Find",
  "finder": {
    "producers": [
      {
        "producerJava": {
          "threads": 8,
          "keyProducers": [{ "keyProducerJavaRandom": {} }]
        }
      }
    ],
    "consumerJava": {
      "lmdbConfigurationReadOnly": { "lmdbDirectory": "/path/to/lmdb" }
    }
  }
}
```

See `examples/config_*.json` for all configuration variants.

---

## Testing

### Frameworks

- **JUnit 6** (6.0.3) — `junit-jupiter` (JUnit Jupiter) for all tests
- **Hamcrest** (3.0) — matchers
- **Mockito** (5.23.0) — mocking

### Conventions

- Tests use forked JVMs (1 fork, no reuse) for isolation.
- There is **no JUnit per-test timeout**. The only hard bound is the Surefire
  whole-fork timeout (`forkedProcessTimeoutInSeconds`, 180s in `pom.xml`): with
  `reuseForks=false` it must cover JVM startup + every method of one test class +
  JVM shutdown. A fork that exceeds it is killed and the build fails with
  "There was a timeout in the fork" — without per-test diagnostics. Test classes
  must therefore never leak resources (sockets, ZMQ contexts, executors) that
  could stall fork shutdown.
- Base/utility test classes: `LMDBBase`, `BIP39DataProvider`, `TestTimeProvider`, `KeyProducerTestUtility`.
- LMDB tests can be skipped with system property:
  ```
  -Dnet.ladenthin.bitcoinaddressfinder.disableLMDBTest=true
  ```
- Test ratio is approximately 1.7:1 (test lines to source lines). New code should have corresponding tests.

### Test execution policy for AI assistants

**A full `mvn test` run takes ~5 minutes on this repo (1 785 tests, forked JVMs, LMDB and OpenCL probe paths).** That is far too slow to invoke after every small edit. Default to the **narrowest meaningful test surface** for the change at hand and only run a full suite when the user explicitly asks for it.

Recommended scoping by change type:

- **Constant / leaf-class extraction** → `mvn test -Dtest=BitcoinAddressFinderArchitectureTest`. The architecture tests catch package-layering regressions in seconds. Pair with `mvn compile` and `mvn test-compile` for compile-clean confirmation.
- **Refactor inside one class** → `mvn test -Dtest=<ThatClass>Test`. Run sibling tests only if the refactor touches their fixtures.
- **Configuration-POJO / wire-format change** → `mvn test -Dtest='ConfigFixturesParseTest,<TouchedPojo>Test'` so the example JSON fixtures and the POJO unit tests both run.
- **Architecture-rule edit** → `mvn test -Dtest=BitcoinAddressFinderArchitectureTest` only.
- **Cross-cutting code-quality cleanup** (e.g. renaming a widely-used helper) → first run `mvn compile` + `mvn test-compile` to confirm the rename compiles, then a narrowed `-Dtest=` glob (e.g. `'*Test,*ArchitectureTest'`) or whatever surface the change actually touches.
- **OpenCL / LMDB integration touchups** → run the matching `*OpenCLTest` / `LMDBPersistenceTest` directly. Both are slow and noisy when they are not the target of the change.

**Full `mvn test` is reserved for the user to ask for explicitly** ("run the full tests", "do a full surefire run", or similar). Otherwise: narrow scope, report which tests were exercised, and call out anything that *would* exercise the change but was deliberately skipped so the user can decide whether to widen.

### Running JMH benchmarks locally (GPU / FUSE_8 etc.)

JMH benchmarks live under `src/test/java/.../benchmark/` (e.g. `GpuFuse8FilterBenchmark`,
`GridSizeSweepBenchmark`). They have **no `@Test`** annotation and never run under `mvn test`;
GPU benchmarks self-skip when no OpenCL 2.0+ device is present.

The README documents the canonical `mvn test-compile exec:java -Dexec.args="<Benchmark>"`
invocation. **That form was observed to fail locally on Windows**: the JVM JMH *forks* cannot
find `org.openjdk.jmh.runner.ForkedMain` (`ClassNotFoundException`), because the pom's
`exec-maven-plugin` runs JMH in-process and the fork does not inherit exec's classpath. The
reliable local recipe is to launch JMH directly so the fork inherits a real `-cp`:

```bash
# 1. materialise the full test-scope classpath (includes jmh-core)
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp-test.txt -DincludeScope=test

# 2. run JMH directly; prepend the project's own classes to the dependency classpath.
#    The --add-opens set must match pom.xml <argLine> (lmdbjava reflects into sun.nio.ch).
java --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-opens=java.base/java.io=ALL-UNNAMED \
     --add-opens=java.base/java.nio=ALL-UNNAMED \
     --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
     --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
     -cp "target/test-classes;target/classes;$(cat target/cp-test.txt)" \
     org.openjdk.jmh.Main GpuFuse8FilterBenchmark -p batchSizeInBits=19 -f 1 -wi 1 -w 30 -i 1 -r 200
```

(On a POSIX shell use `:` instead of `;` as the classpath separator.) **For GPU benchmarks
prefer one long measurement iteration over many short samples** (`-i 1 -r 200`, ~3.3 min per
arm, plus a short `-wi 1 -w 30` to let the GPU reach steady clocks): the relevant one-time
cost here is OpenCL **kernel compilation**, which happens once in `OpenCLContext.init()`
(JMH `@Setup`, outside the timed region) — JVM warmup iterations do not help, so spending the
budget on duration rather than sample count gives the steadier read. For a quick sanity check
use `-wi 2 -i 4` (~2 min for both arms). Use `OpenCLInfo` (`{"command":"OpenCLInfo"}`) to
confirm a device is present and pick `platformIndex` / `deviceIndex` before benchmarking.
`GpuFuse8FilterBenchmark` was validated this way on an NVIDIA RTX 3070 (OpenCL 3.0 CUDA): a
single ~200 s iteration gave compact FUSE_8 mode ≈ **1.28× faster** than full transfer at
`batchSizeInBits = 19` (matching the README key-gen table's grid size; the gain grows with
batch size, ≈ 1.7× at grid 20). The full two-arm long run took ~7m45s of wall time.

---

## Code Conventions

### Null Safety

- Use **JSpecify** annotations: `@Nullable` (from `org.jspecify.annotations`) for optional values, `@NonNull` is the default.
- **NullAway** enforces null safety at compile time for the entire `net.ladenthin` package tree. Unannotated code will cause compilation errors.

### Naming

- Configuration POJOs: prefix `C` (e.g., `CProducerJava`, `CKeyProducerJavaRandom`).
- Producer implementations: suffix with type (e.g., `ProducerJava`, `ProducerOpenCL`).
- Key producer strategies: `KeyProducerJava<Strategy>` pattern.

### Annotations

- `@VisibleForTesting` — marks package-private or protected members exposed only for testing.
- `@ToStringTest` — marks fields that should be verified in `toString()` tests.
- `@Nullable` / implicit `@NonNull` from JSpecify.

### License Headers

All source files must include the Apache 2.0 license header. See any existing source file for the exact template. Use `// @formatter:off` / `// @formatter:on` around the header to exclude it from auto-formatting.

### Error Handling

Use custom domain exceptions rather than generic ones:
- `KeyProducerIdNullException`
- `KeyProducerIdIsNotUniqueException`
- `KeyProducerIdUnknownException`
- `NoMoreSecretsAvailableException`
- `PrivateKeyTooLargeException`
- `UnknownSecretFormatException`

### Concurrency

- `LinkedBlockingQueue<byte[]>` for producer → consumer communication.
- `ExecutorService` / `ThreadPoolExecutor` for thread pool management.
- `AtomicLong` for thread-safe statistics counters.
- `CountDownLatch` for shutdown synchronization.
- Do not introduce raw `Thread` usage; use executor services.

### Records

Some utility classes are implemented as Java `record` types (e.g., `KeyUtility`). Prefer records for immutable value objects.

---

## OpenCL / GPU Code

OpenCL kernels live in `src/main/resources/` as `.cl` and `.h` files:

| File | Purpose |
|---|---|
| `inc_ecc_secp256k1custom.cl` | Elliptic curve scalar multiplication |
| `inc_hash_sha256.cl` | SHA-256 (ported from hashcat) |
| `inc_hash_ripemd160.cl` | RIPEMD-160 (ported from hashcat) |
| `inc_defines.h` | Common preprocessor defines |
| `inc_platform.cl` | Platform-specific abstractions |
| `inc_types.h` | OpenCL type definitions |

GPU code is bound through JOCL (`jocl` 2.0.6). `OpenCLBuilder` constructs the platform/device hierarchy; `OpenCLContext` manages kernel compilation and execution; `OpenClTask` processes a batch of keys on the GPU.

---

## CI/CD Pipelines (`.github/workflows/`)

| Workflow | Trigger | Purpose |
|---|---|---|
| `publish.yml` | Push, PR, manual dispatch | Unified build/test/coverage/package pipeline; publishes snapshots and Maven Central releases |
| `codeql.yml` | Schedule/Push | GitHub CodeQL security scanning |
| `scorecard.yml` | Schedule / Push | OpenSSF Scorecard supply-chain security analysis |
| `osv-scanner.yml` | Schedule / Push / PR | Google OSV-Scanner dependency vulnerability scan |
| `reuse.yml` | Push / PR | FSFE REUSE license-compliance check |
| `claude-code-review.yml` | PR | AI-powered code review |
| `claude.yml` | Issue/PR comment with `@claude` | Claude Code interactive assistant |

### Coverage source — pocl (`test-opencl`) job, not the ubuntu `test` matrix

BAF's `publish.yml` has **two** ubuntu test jobs:

- **`test` (ubuntu)** — standard matrix; no OpenCL ICD installed. `@OpenCLTest` classes
  (`OpenCLContext`, `OpenClTask`, `OpenCLGridResult`, `ProducerOpenCL`, etc.) self-skip via
  `OpenCLPlatformAssume`. The JaCoCo report from this job shows 0% for the entire OpenCL
  pipeline and is kept as an artifact (`jacoco-report`) but is **not** sent to Coveralls/Codecov.
- **`test-opencl` (ubuntu + pocl)** — installs `pocl-opencl-icd` (a conformant OpenCL 3.0 CPU
  implementation). `@OpenCLTest` classes actually execute here. This job runs the **full** test
  suite (`mvn test jacoco:report`) and its report (`jacoco-report-opencl`) is the source
  consumed by the `report` job → Coveralls/Codecov.

This is intentional and documented in `workspace/crossrepostatus.md` "Deliberate non-parity".
No other sibling repo has OpenCL code, so this distinction is BAF-only.

---

## Dependencies Summary

| Dependency | Version | Purpose |
|---|---|---|
| `bitcoinj-core` | 0.17.1 | Bitcoin crypto, address derivation |
| `bcprov-jdk15to18` | 1.84 | Bouncy Castle crypto provider (bitcoinj transitive; pinned to fix GHSA-c3fc-8qff-9hwx, GHSA-p93r-85wp-75v3) |
| `protobuf-javalite` | 4.35.1 | Protocol Buffers (bitcoinj transitive; pinned to latest) |
| `jsr305` | 3.0.2 | Findbugs nullability annotations (bitcoinj transitive, runtime) |
| `jcip-annotations` | 1.0 | JCIP concurrency annotations (bitcoinj transitive, runtime) |
| `lmdbjava` | 0.9.3 | LMDB database bindings |
| `jocl` | 2.0.6 | Java OpenCL bindings |
| `jackson-databind` | 2.21.3 | JSON config parsing |
| `jackson-dataformat-yaml` | 2.21.3 | YAML config parsing |
| `guava` | 33.6.0-jre | Google core utilities |
| `commons-codec` | 1.22.0 | Base58, hex encoding |
| `commons-io` | 2.22.0 | I/O utilities |
| `Java-WebSocket` | 1.6.0 | WebSocket producer |
| `jeromq` | 0.6.0 | ZeroMQ producer |
| `jspecify` | 1.0.0 | Nullness annotations |
| `slf4j-api` | 2.0.18 | Logging facade |
| `logback-classic` | 1.5.32 | SLF4J implementation |

Test-only:
| `junit-jupiter` | 6.0.3 | JUnit 6 (Jupiter) test framework |
| `hamcrest` | 3.0 | Assertion matchers |
| `mockito-core` | 5.23.0 | Mocking |

---

## Running the Tool

```bash
# Build fat JAR
./mvnw package -P assembly -DskipTests

# Run (replace config path as needed)
java -jar target/bitcoinaddressfinder-1.5.0-jar-with-dependencies.jar \
  examples/config_Find_8CPUProducer.json
```

Pre-built run scripts exist in `examples/` for each operation mode (`run_*.bat` for Windows).

---

## Test / Code Writing Compliance

After modifying or creating any `.java` file:

- For `*Test.java` files, follow the workspace version chain:
  [`../workspace/guides/test/TEST_WRITING_GUIDE-8.md`](../workspace/guides/test/TEST_WRITING_GUIDE-8.md)
  (baseline) **and**
  [`TEST_WRITING_GUIDE-21.md`](../workspace/guides/test/TEST_WRITING_GUIDE-21.md)
  (this repo targets Java 21) **and** this repo's own
  `TEST_WRITING_GUIDE.md` (BAF-specific supplement).
- For production sources, follow the workspace version chain:
  [`../workspace/guides/src/CODE_WRITING_GUIDE-8.md`](../workspace/guides/src/CODE_WRITING_GUIDE-8.md)
  (baseline) **and**
  [`CODE_WRITING_GUIDE-21.md`](../workspace/guides/src/CODE_WRITING_GUIDE-21.md)
  (this repo targets Java 21) **and** this repo's own
  `CODE_WRITING_GUIDE.md`.
- For TDD workflow see [`../workspace/.claude/skills/java-tdd-guide/SKILL.md`](../workspace/.claude/skills/java-tdd-guide/SKILL.md).
- Apply all fixable violations automatically; report only those that
  cannot be resolved without a large refactor.

---

## Pull Request Workflow

See [`../workspace/workflows/pull-request-workflow.md`](../workspace/workflows/pull-request-workflow.md).

---

## Key Design Principles

1. **Performance first** — key generation is the hot path; minimize allocations, use byte arrays not objects.
2. **Pluggable producers** — new key generation strategies implement `KeyProducer` and `Producer`; register in config classes.
3. **Single consumer** — `ConsumerJava` is intentionally single-threaded to avoid LMDB contention; producers are the parallelism point.
4. **Graceful shutdown** — always implement `Interruptable`; propagate interrupts from `Main` → `Finder` → all producers/consumers.
5. **Null safety is required** — NullAway is error-level; annotate all nullable return types and parameters.
6. **Configuration-driven** — behaviour changes belong in config, not in code conditionals.

## Javadoc Conventions

See [`../workspace/policies/javadoc-conventions.md`](../workspace/policies/javadoc-conventions.md).

## SpotBugs Suppressions

See [`../workspace/policies/spotbugs-suppressions.md`](../workspace/policies/spotbugs-suppressions.md).

## Spotless Formatting

See [`../workspace/policies/spotless-formatting.md`](../workspace/policies/spotless-formatting.md).
Run `mvn spotless:apply` before every commit that touches `.java` files.

## jqwik Policy

See [`../workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md).

## Lombok Config

See [`../workspace/policies/lombok-config.md`](../workspace/policies/lombok-config.md).

## JPMS Module Descriptor

Cross-repo pattern + the Java-bump javadoc trap:
[`../workspace/policies/jpms-module-descriptor.md`](../workspace/policies/jpms-module-descriptor.md).
BAF is the worked example; its repo-specific handling (why `module-info.java` lives in
`src/main/java9`, the `prepare-package` timing vs Surefire module mode, the lmdbjava internal-JDK
flags) is in the **"JPMS module descriptor (`module-info.java`) handling"** subsection under
Build System above.

## Open TODOs

Open TODOs for this repo live in [`TODO.md`](TODO.md). Cross-repo status
tracking lives in [`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md).
