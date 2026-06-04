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

Tests run with `-Xmx2g -Xms1g` and several `--add-opens` / `--add-exports` to allow LMDB and internal module access. These are configured in `pom.xml` (`<argLine>`) and `.mvn/jvm.config`.

**Error Prone** static analysis with **NullAway** is active at compile time:
- All code in `net.ladenthin` packages must carry proper `@Nullable` / `@NonNull` annotations.
- Compilation will fail on potential null-pointer issues unless annotated.

---

## Project Structure

```
BitcoinAddressFinder/
├── src/
│   ├── main/
│   │   ├── java/net/ladenthin/bitcoinaddressfinder/
│   │   │   ├── cli/                  # CLI entry point (Main.java)
│   │   │   ├── configuration/        # Config POJOs (C-prefixed)
│   │   │   ├── keyproducer/          # Key generation strategies
│   │   │   ├── persistence/          # Persistence abstraction + LMDB impl
│   │   │   ├── opencl/               # OpenCL device/platform wrappers
│   │   │   ├── eckey/                # Secp256k1 ECC utilities
│   │   │   └── *.java                # Core domain classes
│   │   └── resources/
│   │       ├── *.cl / *.h            # OpenCL kernels and headers
│   │       └── simplelogger.properties
│   └── test/
│       └── java/net/ladenthin/bitcoinaddressfinder/
│           └── *.java                # JUnit 6 (Jupiter) tests
├── examples/                         # Sample JSON configs and run scripts
│   ├── config_*.json
│   ├── run_*.bat
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
- `returnValidPrivateKey(BigInteger)`
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
- Test timeout: 60 seconds per test.
- Base/utility test classes: `LMDBBase`, `BIP39DataProvider`, `TestTimeProvider`, `KeyProducerTestUtility`.
- LMDB tests can be skipped with system property:
  ```
  -Dnet.ladenthin.bitcoinaddressfinder.disableLMDBTest=true
  ```
- Test ratio is approximately 1.7:1 (test lines to source lines). New code should have corresponding tests.

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

---

## Dependencies Summary

| Dependency | Version | Purpose |
|---|---|---|
| `bitcoinj-core` | 0.17.1 | Bitcoin crypto, address derivation |
| `bcprov-jdk15to18` | 1.84 | Bouncy Castle crypto provider (bitcoinj transitive; pinned to fix GHSA-c3fc-8qff-9hwx, GHSA-p93r-85wp-75v3) |
| `protobuf-javalite` | 4.34.1 | Protocol Buffers (bitcoinj transitive; pinned to latest) |
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

## jqwik Policy

See [`../workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md).

## Open TODOs

- **jqwik pin policy** — see [`../workspace/policies/jqwik-prompt-injection.md`](../workspace/policies/jqwik-prompt-injection.md). `jqwik.version ≤ 1.9.3` is mandatory.

- **`@VisibleForTesting` audit.** 19 existing usages across 6 files — review each for accuracy (still needed? still test-only?), and walk the production tree for any package-private/protected members that exist purely for tests but are *not* annotated; either add the annotation or move into the test tree.
- **Null-safety further refinement.** JSpecify + NullAway are enforced at compile time **in strict JSpecify mode** with the extra options `CheckOptionalEmptiness`, `AcknowledgeRestrictiveAnnotations`, `AcknowledgeAndroidRecent`, `AssertsEnabled` (see `pom.xml`). Every package carries an explicit `@NullMarked` via `package-info.java` so the convention is visible to non-NullAway tools (IDEs, Kotlin, Checker Framework). The 39 `@Nullable` sites currently in the codebase are all legitimate (config POJOs populated from JSON, lifecycle handles set in `init()`/cleared in `close()`, exception fields, and defensively-nullable parameters). `OpenCLContext.getOpenClTask()` returns `Optional<OpenClTask>` rather than `@Nullable OpenClTask` to surface the lifecycle state in the type. Remaining open work: review any future-added public API surfaces for places where `@Nullable` would be more precise than the implicit non-null default, and consider whether further `@Nullable T` returns should migrate to `Optional<T>` on a case-by-case basis (the project's established convention is `@Nullable`; Optional is used selectively for lifecycle-shaped APIs).

- **Make `-Werror` clean (BAF-specific).** `pom.xml` enables `-Xlint:all,-serial,-options,-classfile` (warnings visible) but stops short of `-Werror`. All six per-warning items below have been cleared on the `claude/inspiring-carson-D831V` branch; the remaining work to flip the switch is the ~30 outstanding Error Prone warnings tracked separately:
  1. ~~`cli/Main.java:279` — `Thread.getId()` deprecated in Java 19+. Replace with `Thread.threadId()`.~~ **DONE** (commit 4274c25 — swapped to `Thread.threadId()`).
  2. ~~`opencl/OpenCLBuilder.java:150` — `CL_DEVICE_QUEUE_PROPERTIES` is deprecated upstream in JOCL. Either suppress with `@SuppressWarnings("deprecation")` (with a comment linking to the upstream issue) or migrate to the replacement constant.~~ **DONE** (commit 5e3f6a8 — narrow `@SuppressWarnings("deprecation")` on a local `queuePropertiesConstant` capture with a comment noting JOCL has not yet exposed the OpenCL 2.0+ replacement `CL_DEVICE_QUEUE_ON_HOST_PROPERTIES`).
  3. ~~`keyproducer/KeyProducerJavaSocket.java:45,49` — possible `this` escape before subclass is fully initialized (two related warnings).~~ **DONE** (commit 523fc79 — implements new `Startable` interface; `setupSocket()` body moved into `start()`, called via `Finder.processKeyProducers` polymorphic dispatch after construction).
  4. ~~`keyproducer/KeyProducerJavaZmq.java:50` — same `this` escape pattern; same fix.~~ **DONE** (commit 62603d3 — same Startable refactor; `receiverThread` field changed from `final Thread` to `@Nullable Thread`, assigned in `start()`).
  5. ~~`persistence/Persistence.java:22` — the `close()` declaration throws `InterruptedException`, which `AutoCloseable`'s try-with-resources contract handles awkwardly. Decide between (a) narrowing the throws clause and wrapping with `RuntimeException`, or (b) explicit `@SuppressWarnings("try")` on the interface with a doc note.~~ **DONE** (commit da4cab7 — narrowed `Persistence.close()` with `@Override void close();` overriding `AutoCloseable#close()` to drop the `throws Exception`; Javadoc documents the no-checked-exceptions contract so callers do not have to declare/catch a phantom `InterruptedException`).
  6. ~~`OpenClTask.java:368` — explicit `close()` call on an auto-closeable resource. Rewrite the surrounding block as try-with-resources or suppress with a rationale.~~ **DONE** (commit 84b35cb — removed the redundant explicit `destinationArgument.close()`; the destination argument lifecycle is now handled by the surrounding try-with-resources / `OpenClTask.close()` path, no suppression required).

- **Further-strictness open points (cross-repo, not yet done).** Items below are tracked across all four Bernard-Ladenthin Java repos and can be picked up incrementally:
  - **SpotBugs `effort=Max` + `threshold=Low`** — currently default effort/threshold. Raising both surfaces more findings (and takes longer per build). Worth a one-off experiment to triage what appears before committing.
  - ~~**Error Prone bug-pattern promotions to `ERROR`**~~ — **DONE for this repo**: 12 high-confidence patterns are promoted via `-Xep:<Name>:ERROR` in `pom.xml:344` (`BoxedPrimitiveEquality`, `EqualsHashCode`, `EqualsIncompatibleType`, `IdentityBinaryExpression`, `SelfAssignment`, `SelfComparison`, `SelfEquals`, `DeadException`, `FormatString`, `InvalidPatternSyntax`, `OptionalEquality`, `ImpossibleNullComparison`). Long tail of remaining EP warnings is tracked separately for the eventual `-Werror` flip.
  - **`javac -Werror` + `-Xlint:all,-serial,-options`** — currently warnings pass. The six pre-`-Werror` items above (1–6) are all cleared; ready for the switch but not yet flipped. Long tail of Error Prone warnings still needs addressing first.
  - ~~**`-parameters` javac arg**~~ — **DONE for this repo** at `pom.xml:315` (`<parameters>true</parameters>` for the main compile; intentionally off for default-testCompile).
  - **`--release N`** instead of `-source N -target N` — main compile still uses `<source>21</source>/<target>21>`; only the `module-info-compile` execution uses `--release 9`. Switching the main compile to `<release>21</release>` is straightforward but not yet done.
  - **Mutation-testing threshold enforcement (PIT)** — `streambuffer` enforces 100 % mutation coverage over its whole package. **This repo and `llamacpp-ai-index-maven-plugin` / `java-llama.cpp` use a "single class, full plumbing" pattern**: PIT is wired in `pom.xml` and runs on every CI build (on the ubuntu-latest leg of the `test` job) with `<mutationThreshold>100</mutationThreshold>`, but `<targetClasses>` is narrowed to `net.ladenthin.bitcoinaddressfinder.BitHelper`. The CI invocation is `mvn test-compile org.pitest:pitest-maven:mutationCoverage`, which intentionally STOPS before the `prepare-package` phase so the JPMS `module-info-compile` execution does not fire — `module-info.class` stays out of `target/classes/` and the forked PIT JVMs continue to run in classpath mode consistent with the rest of the test suite. Expand `<targetClasses>` incrementally as classes reach parity (README "Future improvements" TODO tracks this).
  - **Checker Framework as a second static-nullness pass** — **DONE across all four repos** (`streambuffer`, `llamacpp-ai-index-maven-plugin`, `java-llama.cpp`, and this repo). The Nullness Checker (4.1.0) is wired in `pom.xml` and runs alongside NullAway. BAF-specific notes: an `Objects.requireNonNull` stub override lives at `src/etc/checker/objects.astub` (CF 4.1.0 ships a wrong stub that annotates `obj` as `@NonNull`); the three JOCL-wrapping classes (`OpenCLContext`, `OpenClTask`, `opencl/OpenCLBuilder`) carry class-level `@SuppressWarnings({"nullness:argument", "nullness:dereference.of.nullable"})` because the JOCL upstream API is unannotated; `KeyProducerJavaWebSocket` carries the documented this-escape suppression (`KeyProducerJavaSocket` and `KeyProducerJavaZmq` were later refactored to the `Startable` lifecycle so no suppression is needed there — see the `-Werror` entry above); `PublicKeyBytes.equals(Object)` takes `@Nullable Object`; `BIP39Wordlist.getWordListStream()` returns `@Nullable InputStream`. Cross-repo work on this item is complete.
  - **JPMS `module-info.java` with `@NullMarked` at module level** — **DONE across all four repos** (this repo plus `streambuffer`, `llamacpp-ai-index-maven-plugin`, `java-llama.cpp`). BAF-specific notes: (1) `module-info.java` lives in `src/main/java9/` (a separate source root), not under `src/main/java/`, because javac at source/target 21 auto-activates module mode whenever it sees a `module-info.java` on its source path — even when excluded from the compile execution — which would force every ordinary source's classpath dependencies (slf4j, guava, bitcoinj, jackson, jspecify, etc.) to be re-declared via `requires`. (2) The `module-info-compile` execution is bound to `prepare-package` rather than `compile` so `module-info.class` is not present in `target/classes/` while the test sources compile or run; if it were, the test JVM would activate module mode and reject the unprefixed `--add-exports java.base/jdk.internal.ref=ALL-UNNAMED` and `--add-opens java.base/sun.nio.ch=ALL-UNNAMED` flags that `ByteBufferUtility` and friends rely on. Local-dev caveat: `mvn test` after `mvn package` without an intervening `mvn clean` fails with `IllegalAccessError`; CI is unaffected because the Build and Test jobs run in separate runners with fresh checkouts. (3) The module opens `net.ladenthin.bitcoinaddressfinder.configuration` to `com.fasterxml.jackson.databind` so Jackson can populate the configuration POJOs via reflection on any non-public members added later (current fields are all public so a plain `exports` would also work, but `opens` is forward-compatible). (4) Module-level `@NullMarked` was intentionally NOT added — the per-package annotation covers the same scope and avoids pulling JSpecify into the module's `requires` graph.
  - ~~**Banned-API enforcement**~~ — **DONE for this repo**: Maven Enforcer `bannedDependencies` + `dependencyConvergence` at `pom.xml:268-283`; ArchUnit `noSystemExit` / `noNewRandom` / `noThreadSleep` rules in `BitcoinAddressFinderArchitectureTest:137,164,178`; the `sun.*` / `com.sun.*` / `jdk.internal.*` import ban is at `BitcoinAddressFinderArchitectureTest:90-97`.
  - **Additional ArchUnit rules to consider** — public-fields-final is **DONE** at `BitcoinAddressFinderArchitectureTest:120-130`. Still open: `layeredArchitecture().consideringAllDependencies()`, per-module banned-imports lists, no-public-mutable-static-state rule.

- **Pluggable persistence backends + lookup benchmark — mostly DONE.** Originally a "research-and-plan placeholder" entry; the bulk of the 6-point plan has shipped on this branch. Status of each plan item:
  1. ~~**Either resurrect the in-memory HashSet/HashMap backend or remove the dead README/config references.**~~ **DONE — all four parts.** `persistence/inmemory/HashSetAddressPresence.java` is the resurrected HashSet snapshot (populated from LMDB once at startup, LMDB env then closed and GC'd). `README.md` no longer references `loadToMemoryCacheOnInit`. The four example JSONs (`config_AddressFilesToLMDB.json`, `config_Find_1OpenCLDevice.json`, `config_Find_1OpenCLDeviceAnd2CPUProducer.json`, `config_Find_SecretsFile.json`) no longer carry the dead key either. Implicit default remains `addressLookupBackend: BLOOM` per `CLMDBConfigurationReadOnly.java:43`; operators who want a self-contained in-memory snapshot should set `TRUNCATED_LONG_64` (or `HASHSET` if exact-bit storage is required and memory is plentiful).
  2. ~~**Pull the BloomFilter accelerator out of `LMDBPersistence`.**~~ **DONE**: `persistence/bloom/BloomFilterAccelerator.java` is the standalone wrapper; LMDBPersistence no longer carries the Bloom fields directly. The accelerator can wrap any `AddressLookup`, not just LMDB.
  3. **Standalone backends in sibling sub-packages** — partially done:
     - ✅ HashSet snapshot: `persistence/inmemory/HashSetAddressPresence.java` (presence-only; `Set<ByteBuffer>.contains` on a populated copy of the LMDB key set; ~50 B/entry overhead).
     - ✅ Sorted-array-of-truncated-prefixes: `persistence/inmemory/TruncatedLong64SortedArrayPresence.java` (256-bucket sorted `long[]`; ~10&#x00d7; more compact than HASHSET at near-HASHSET lookup latency; the highest-throughput backend per README's lookup-latency table).
     - ❌ **Still open**: open-addressing primitive hash table backend (fastutil `Long2LongOpenHashMap` or hand-rolled over `long[]`) — would offer O(1) average vs. the sorted array's O(log n) per lookup with similar cache profile. Unimplemented because TRUNCATED_LONG_64 is already the fastest backend in practice and the marginal speedup would not change the recommended default.
     - ❌ **Still open**: standalone `BloomFilterPersistence` (Bloom-only / probabilistic mode without a backing `AddressLookup`). The current `BloomFilterAccelerator` returns `requiresBackend()==true`; a pure-Bloom variant would return `false` and accept `getAmount` semantics of "unsupported / always Coin.ZERO". Not yet needed by any caller; ship only when someone asks for it.
  4. ~~**Make the backend selectable from config.**~~ **DONE**: `AddressLookupBackend` enum in `configuration/AddressLookupBackend.java` exposes 4 values (`LMDB_ONLY`, `BLOOM`, `HASHSET`, `TRUNCATED_LONG_64`); `CLMDBConfigurationReadOnly.addressLookupBackend` is the config field (defaults to `BLOOM` for historical-behaviour compatibility, per its own Javadoc); `ConsumerJava.java:183-189` dispatches the switch. The Javadoc on `AddressLookupBackend` documents the trade-off matrix between RAM cost / lookup latency / whether the LMDB env can be closed after population.
  5. ~~**Optional: layered/chained backend.**~~ **DONE** as the project's actual lookup contract: `persistence/AddressPresence.java` is the minimal "is this address present?" seam with a `requiresBackend()` discriminator, and `persistence/AddressLookup.java` extends it with `getAmount` for full key&#x2192;value backends. Decorators (e.g. `BloomFilterAccelerator`) keep a reference to a delegate and fall through on positive answers; self-contained snapshots (HashSet, TRUNCATED_LONG_64) return `requiresBackend()==false` after `populateFrom(lmdb)` and the LMDB env is closed. `AddressLookup` Javadoc documents the canonical chain (e.g. `LMDB &#x2192; BloomFilterAccelerator(lmdb) &#x2192; consumerJava.useLookup(...)`).
  6. **Lookup benchmark** — partially done: `src/test/java/net/ladenthin/bitcoinaddressfinder/persistence/AddressLookupBenchmarkTest.java` times `containsAddress` across all 4 backends on the same dataset (Bloom FPP 0.01; warm path; per-backend `populateFrom` timing). **Open follow-up**: it is currently a JUnit-driven timing test, not a real JMH benchmark. Decide whether to migrate to the `benchmark/` JMH module (alongside `ByteSwapBenchmark`, `PublicKeyHashBenchmark`, `BitHelperBenchmark`) — would give p50/p99 latency, GC-allocation profile, and cold-vs-warm separation that the current loop does not capture. Migration is mechanical; defer until a specific question motivates it.

  **Historical context (kept for the pre-Bloom design rationale).** A HashSet-based in-memory persistence DID exist pre-Git but was removed in pre-Git commit `f153a1bdb363c16bbe86134d360f4c2e4423d3e7` ("Replace in-memory HashSet with Bloom filter for address lookup optimization", 2025-07-10), ~10 months before this repo's boot commit (`2c8e9f1`, 2026-05-08). That commit is not in this repository — only its post-state was imported. The current `HashSetAddressPresence` is the *resurrection* of that earlier design with a cleaner contract (no LMDB coupling once populated). The same removal commit also contained a commented-out `sortedAddressCache` variant using `Arrays.binarySearch(...)` — that is what `TruncatedLong64SortedArrayPresence` ships, with the additional optimization of truncating each hash160 to its first 8 bytes (256-bucket sharding plus the truncation gives a ~7.5&#x00d7;10&#x207b;&#xb9;&#xb9; false-positive rate at Full DB scale — negligible in practice). Memory cost reference: HashSet shape was ~50 B/entry (ByteBuffer wrapper + 20-byte payload + HashMap.Node), so ~6.6 GB for the README's 132M-entry light db and ~70 GB for the 1.377B-entry full db; TRUNCATED_LONG_64 cuts that roughly 10&#x00d7; (~660 MB / ~7 GB respectively). Future history lookups for the pre-Bloom design need access to that external repository.

  **Genuinely open work** (concise summary, sorted easiest first):
  - ~~Remove `"loadToMemoryCacheOnInit": false` from the 4 stale example JSON files.~~ **DONE.**
  - Migrate `AddressLookupBenchmarkTest` to the JMH `benchmark/` module (1–2 h).
  - Add open-addressing primitive hash table backend (defer until measured ROI).
  - Add standalone `BloomFilterPersistence` (defer until a caller asks for probabilistic-only mode).

- **GPU grid-size and context-reuse JMH benchmarks — grid-size sweep DONE.** Mainline had JMH micro-benchmarks for pure-Java helpers (`ByteSwapBenchmark`, `PublicKeyHashBenchmark`, `BitHelperBenchmark`) but no parameter sweep for the OpenCL kernel. The grid-size sweep idea (from the cjherm/BitcoinAddressFinder23 fork's `ChunkSizeIteratorBenchmark`) has shipped as `src/test/java/net/ladenthin/bitcoinaddressfinder/benchmark/GridSizeSweepBenchmark.java`.

  **Key adaptations from the fork's design vs. mainline reality** (and corrections to the earlier draft of this TODO):
  - Mainline has **no `gridNumBits`, no `chunkMode`, no `kernelMode` field**. The actual levers are `CProducer.batchSizeInBits` (work size = `1 << batchSizeInBits`) and `CProducerOpenCL.loopCount`. The benchmark sweeps those two.
  - Kernel entry is `OpenCLContext.createKeys(BigInteger privateKeyBase)` (single base, returns one `OpenCLGridResult`), not `getResult(BigInteger[])`. Constructor is `new OpenCLContext(CProducerOpenCL, BitHelper)`, two args.
  - Availability gate is the existing `new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable()`. If absent, JMH reports `ERROR` per data point (no silent downgrade).
  - `@Fork` includes the project-canonical `--add-opens=java.base/sun.nio.ch=ALL-UNNAMED` + `--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED` so JMH's forked JVMs match the `argLine` Surefire uses.
  - Throughput unit is **kernel launches per second** at each `(batchSizeInBits, loopCount)` corner. Candidates/sec = launches/sec &#x00D7; `(1 << batchSizeInBits)`. JMH's `@OperationsPerInvocation` cannot normalize this automatically because it needs a compile-time constant and `@Param` is runtime; documented in the class Javadoc.

  **NOT imported from the fork** (intentional scope cap): the `BenchmarkFactory` / `BenchmarkSeries` / `BenchmarkLogger` / `LatexContentCreator` harness, the `command: "BenchmarkSeries"` `CCommand` extension, and the SHA / RIPEMD-160 GPU-vs-CPU comparison rounds.

  **Context-reuse / init-cost-amortisation sweep — explicitly NOT imported.** The fork ships `CtxRoundsIteratorBenchmark` (fix `gridNumBits`, vary kernel-launches-per-context, measure init-cost amortisation curve). It is operationally meaningless for this codebase: `ProducerOpenCL` creates the `OpenCLContext` once in `initProducer()` (`ProducerOpenCL.java:60-63`), runs `createKeys(BigInteger)` on every produced batch (`ProducerOpenCL.java:67-77`), and closes the context once in `releaseProducer()` (`ProducerOpenCL.java:151-155`). The smallest production scan is &#x2273; 10&#x2076; kernel launches against the one long-lived context; init cost is already amortised to noise. The amortisation curve would be true but produce no operator-actionable number. Re-importing this idea later requires evidence that BAF's lifecycle has changed to short-burst / one-shot scans.

  **Fork files re-read during the import** (under `/tmp/cjherm-baf23/` if cloned again): `benchmark/types/ChunkSizeIteratorBenchmark.java` (the gridNumBits sweep — the source of the idea — imported), `benchmark/types/CtxRoundsIteratorBenchmark.java` (the context-reuse sweep — inspected and rejected as inapplicable, see above), `benchmark/MeasurementRound.java` (the measurement loop — JMH covers this), `examples/benchmark*.json` (the operator-facing param sets the fork chose — useful as a sanity check on `@Param` ranges).

- **Cross-repo code-quality TODOs** — see [`../workspace/policies/code-quality-todos.md`](../workspace/policies/code-quality-todos.md) for the canonical `@VisibleForTesting` design-fit review (BAF has 19 sites; site-by-site audit is captured in [`../workspace/crossrepostatus.md`](../workspace/crossrepostatus.md)), package hierarchy review, and class/method naming review (CRITICAL + MODERATE findings tracked in `crossrepostatus.md`).

- ~~**Abstract the Java and test writing guidelines to a workspace-level shared layer.**~~ **DONE.** Canonical guides at [`../workspace/guides/src/CODE_WRITING_GUIDE-8.md`](../workspace/guides/src/CODE_WRITING_GUIDE-8.md) (Java 8 baseline) + [`CODE_WRITING_GUIDE-21.md`](../workspace/guides/src/CODE_WRITING_GUIDE-21.md) (Java 21 supplement, applies to this repo), and [`TEST_WRITING_GUIDE-8.md`](../workspace/guides/test/TEST_WRITING_GUIDE-8.md) + [`TEST_WRITING_GUIDE-21.md`](../workspace/guides/test/TEST_WRITING_GUIDE-21.md); canonical TDD skill at [`../workspace/.claude/skills/java-tdd-guide/SKILL.md`](../workspace/.claude/skills/java-tdd-guide/SKILL.md). BAF's `CODE_WRITING_GUIDE.md` / `TEST_WRITING_GUIDE.md` now contain only BAF-specific supplements.

- ~~**Adopt a standard `CLAUDE.md` template/tool for cross-repo consistency.**~~ **DONE.** Template at [`../workspace/templates/CLAUDE.md.template`](../workspace/templates/CLAUDE.md.template).

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
  - **Cost breakdown of one `Set<ByteBuffer>.contains(buf)` call** (the per-step accounting that justifies the percentage):

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

    That is not a marginal improvement when cores are saturated. The earlier "marginal / superseded" framing was wrong for this workload; under CPU-bound saturation the +7.7 % PCIe-bandwidth cost is also not a real concern — CPU saturation and bandwidth saturation are different bottlenecks and one rules out the other.

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
