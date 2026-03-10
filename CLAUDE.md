# CLAUDE.md — BitcoinAddressFinder

This document provides guidance for AI assistants working on the BitcoinAddressFinder codebase.

---

## Project Overview

**BitcoinAddressFinder** is a high-performance, open-source tool for scanning Bitcoin and altcoin private keys and checking whether the derived addresses have balances. It supports 100+ cryptocurrencies and uses both CPU (Java) and GPU (OpenCL) key generation strategies.

- **Group ID:** `net.ladenthin`
- **Artifact ID:** `bitcoinaddressfinder`
- **Version:** 1.5.0
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
│           └── *.java                # JUnit 4 tests
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

- **JUnit 4** (4.13.2) — test runner
- **Hamcrest** (3.0) — matchers
- **Mockito** (5.22.0) — mocking
- **junit-dataprovider** (1.13.1) — data-driven tests

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
| `assembly.yml` | Push to `main`/`releases/*`, all PRs | Compile, test, build fat JAR, upload artifacts |
| `matrixci.yml` | Push/PR | Matrix test across 8 JVM distributions × 3 OS |
| `coverage.yml` | Push/PR | JaCoCo coverage + Coveralls upload |
| `codeql.yml` | Schedule/Push | GitHub CodeQL security scanning |
| `claude-code-review.yml` | PR | AI-powered code review |
| `claude.yml` | Issue/PR comment with `@claude` | Claude Code interactive assistant |

The matrix CI tests Java 21 on:
- **Distributions:** adopt, adopt-openj9, corretto, dragonwell, liberica, microsoft, temurin, zulu
- **OS:** Ubuntu, Windows, macOS (some combos excluded for incompatibility)

---

## Dependencies Summary

| Dependency | Version | Purpose |
|---|---|---|
| `bitcoinj-core` | 0.17 | Bitcoin crypto, address derivation |
| `lmdbjava` | 0.9.3 | LMDB database bindings |
| `jocl` | 2.0.6 | Java OpenCL bindings |
| `gson` | 2.13.2 | JSON config parsing |
| `snakeyaml` | 2.6 | YAML config parsing |
| `guava` | 33.5.0-jre | Google core utilities |
| `commons-codec` | 1.21.0 | Base58, hex encoding |
| `commons-io` | 2.21.0 | I/O utilities |
| `Java-WebSocket` | 1.6.0 | WebSocket producer |
| `jeromq` | 0.6.0 | ZeroMQ producer |
| `jspecify` | 1.0.0 | Nullness annotations |
| `slf4j-api` | 2.0.17 | Logging facade |
| `logback-classic` | 1.5.32 | SLF4J implementation |

Test-only:
| `junit` | 4.13.2 | Test runner |
| `hamcrest` | 3.0 | Assertion matchers |
| `mockito-core` | 5.22.0 | Mocking |
| `junit-dataprovider` | 1.13.1 | Data-driven tests |

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

## Test Writing Compliance

After modifying or creating any `*Test.java` file, automatically verify that all rules from `TEST_WRITING_GUIDE.md` are applied to the modified test class. Apply all fixable violations on your own without asking. Only report violations that cannot be resolved without a large refactoring. Consider the task complete only after all auto-fixable rules are satisfied.

---

## Pull Request Workflow

After pushing commits to a feature branch, **always** create a pull request automatically using the `gh` CLI (if available):

```bash
gh pr create \
  --title "<short summary of changes>" \
  --body "$(cat <<'EOF'
## Summary
- <bullet points describing what changed and why>

## Test plan
- [ ] Relevant test classes pass
- [ ] Full CI matrix passes

<session URL>
EOF
)"
```

After creating the PR, **monitor CI results** and fix any failures without waiting to be asked:

1. Poll check status: `gh pr checks <number> --watch` (or `gh run list` / `gh run view`)
2. If a check fails, read the log (`gh run view <run-id> --log-failed`), identify the root cause, fix it, commit, and push.
3. Repeat until all required checks pass.
4. If `gh` is not available in the current environment, note this to the user so they can verify CI results manually.

---

## Key Design Principles

1. **Performance first** — key generation is the hot path; minimize allocations, use byte arrays not objects.
2. **Pluggable producers** — new key generation strategies implement `KeyProducer` and `Producer`; register in config classes.
3. **Single consumer** — `ConsumerJava` is intentionally single-threaded to avoid LMDB contention; producers are the parallelism point.
4. **Graceful shutdown** — always implement `Interruptable`; propagate interrupts from `Main` → `Finder` → all producers/consumers.
5. **Null safety is required** — NullAway is error-level; annotate all nullable return types and parameters.
6. **Configuration-driven** — behaviour changes belong in config, not in code conditionals.
