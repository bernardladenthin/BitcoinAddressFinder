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

After modifying or creating any `*Test.java` file, automatically verify that all rules from the generic Java TDD skill (`.claude/skills/java-tdd-guide.md`) **and** the project-specific supplement (`TEST_WRITING_GUIDE.md`) are applied to the modified test class. Apply all fixable violations on your own without asking. Only report violations that cannot be resolved without a large refactoring. Consider the task complete only after all auto-fixable rules are satisfied.

---

## Code Writing Compliance

After modifying or creating any production `.java` file, automatically verify that all rules from the generic Java TDD skill (`.claude/skills/java-tdd-guide.md`) **and** the project-specific supplement (`CODE_WRITING_GUIDE.md`) are applied to the modified class. Apply all fixable violations on your own without asking. Only report violations that cannot be resolved without a large refactoring. Consider the task complete only after all auto-fixable rules are satisfied.

---

## Pull Request Workflow

### Step 1 — Detect whether `gh` is available

```bash
gh --version 2>/dev/null && echo "gh available" || echo "gh not available"
```

If `gh` is **not** available (e.g. local proxy remote), inform the user and stop. Do not attempt the remaining steps.

### Step 2 — Create the PR

Always create a PR immediately after the first push to a feature branch:

```bash
gh pr create \
  --title "<concise summary, ≤70 chars>" \
  --body "$(cat <<'EOF'
## Summary
- <bullet: what changed>
- <bullet: why>

## Test plan
- [ ] Affected test classes pass
- [ ] Full CI matrix passes

<session URL>
EOF
)"
```

Note the PR number printed — you need it in the next steps.

### Step 3 — Wait for all checks to complete

```bash
gh pr checks <PR-number> --watch --interval 30
```

If `--watch` is unavailable in the installed `gh` version, poll manually:

```bash
while gh pr checks <PR-number> | grep -qE "pending|in_progress|queued"; do
  sleep 30
done
gh pr checks <PR-number>
```

### Step 4 — Triage failures

For each failing check, fetch the log:

```bash
gh run list --branch <branch-name> --limit 10
gh run view <run-id> --log-failed
```

For **CodeQL annotation** failures, pull structured annotations directly (avoids parsing raw logs):

```bash
# get the annotations URL for the CodeQL check on the latest commit
gh api repos/{owner}/{repo}/commits/<sha>/check-runs \
  --jq '.check_runs[] | select(.name | test("CodeQL")) | .output.annotations_url'

# fetch the annotations (path, line number, message)
gh api <annotations-url> \
  --jq '.[] | {path: .path, line: .start_line, message: .message}'
```

### Step 5 — Fix, commit, push, repeat

1. Read the failure message or annotation.
2. Apply the fix.
3. Commit and push:
   ```bash
   git add <files>
   git commit -m "Fix <check-name>: <short description>"
   git push
   ```
4. Return to **Step 3** and wait for the re-run.
5. Repeat until `gh pr checks <PR-number>` shows every check as ✅ pass.

### Step 6 — Report to the user

Once all checks pass, summarise what was fixed and why. If a failure **cannot** be fixed automatically (e.g. requires a large refactor, changes public API, or disabling a security check) stop, explain the situation, and ask for direction instead of silently suppressing or working around it.

---

## Key Design Principles

1. **Performance first** — key generation is the hot path; minimize allocations, use byte arrays not objects.
2. **Pluggable producers** — new key generation strategies implement `KeyProducer` and `Producer`; register in config classes.
3. **Single consumer** — `ConsumerJava` is intentionally single-threaded to avoid LMDB contention; producers are the parallelism point.
4. **Graceful shutdown** — always implement `Interruptable`; propagate interrupts from `Main` → `Finder` → all producers/consumers.
5. **Null safety is required** — NullAway is error-level; annotate all nullable return types and parameters.
6. **Configuration-driven** — behaviour changes belong in config, not in code conditionals.
