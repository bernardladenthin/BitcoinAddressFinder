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

## Test Writing Compliance

After modifying or creating any `*Test.java` file, automatically verify that all rules from the generic Java TDD skill (`.claude/skills/java-tdd-guide/SKILL.md`) **and** the project-specific supplement (`TEST_WRITING_GUIDE.md`) are applied to the modified test class. Apply all fixable violations on your own without asking. Only report violations that cannot be resolved without a large refactoring. Consider the task complete only after all auto-fixable rules are satisfied.

---

## Code Writing Compliance

After modifying or creating any production `.java` file, automatically verify that all rules from the generic Java TDD skill (`.claude/skills/java-tdd-guide/SKILL.md`) **and** the project-specific supplement (`CODE_WRITING_GUIDE.md`) are applied to the modified class. Apply all fixable violations on your own without asking. Only report violations that cannot be resolved without a large refactoring. Consider the task complete only after all auto-fixable rules are satisfied.

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

## Javadoc Conventions

### HTML Entities

In Javadoc comments, never use bare Unicode characters for operators and symbols. Use HTML entities instead:

| Symbol | HTML entity |
|---|---|
| `<` | `&lt;` |
| `>` | `&gt;` |
| `≤` | `&#x2264;` |
| `≥` | `&#x2265;` |
| `→` | `&#x2192;` |
| `←` | `&#x2190;` |
| `≠` | `&#x2260;` |

Use numeric hex entities (`&#xNNNN;`) for any Unicode symbol outside ASCII. Named entities (`&lt;`, `&gt;`) are acceptable for `<` and `>`.

## SpotBugs Suppressions

`spotbugs-exclude.xml` at the repo root contains documented suppressions for findings that are by-design or false positives. **When refactoring or renaming code referenced in that file, re-check the affected `<Match>` blocks:**

- `<Class>`, `<Method>`, and `<Field>` filters use exact string matches — a rename silently disables the suppression and may either un-suppress a real bug or leave a stale entry behind.
- After refactors, run `mvn -B -ntp -DskipTests -Dgpg.skip=true verify` and confirm the BugInstance count is unchanged. A drop means a suppression is now stale and should be deleted; an increase means a new finding needs its own decision (fix vs. suppress).
- Keep the rationale comment on each `<Match>` accurate — if the original justification no longer applies to the post-refactor code, remove the suppression rather than leave outdated reasoning in place.
- Never use `--` inside `<!-- ... -->` comment bodies in `spotbugs-exclude.xml` — XML forbids it and the entire filter file silently stops loading (every previously suppressed finding reappears).

## Open TODOs

- **`@VisibleForTesting` audit.** 18 existing usages — review each for accuracy (still needed? still test-only?), and walk the production tree for any package-private/protected members that exist purely for tests but are *not* annotated; either add the annotation or move into the test tree.
- **Null-safety review.** JSpecify + NullAway are already enforced at compile time. Review remaining unannotated public API surfaces (parameters, return types) for places where `@Nullable` would be more precise than the implicit non-null default.
- **Complete the logger DI migration.** `OpenCLContext` was migrated as the first example (constructor Logger parameter removed, `private static final LOGGER`, tests use `LogCaptor.forClass(...)` instead of `mock(Logger.class)`). The remaining 9 classes covered by the LO_SUSPECT suppression block in `spotbugs-exclude.xml` still take a Logger constructor parameter: `KeyProducerJava` (base class), `KeyProducerJavaBip39/Incremental/Random/Socket/WebSocket/Zmq`, and `AbstractKeyProducerQueueBuffered` (two constructors). Migrating all of them lets the entire LO_SUSPECT_LOG_PARAMETER + LO_SUSPECT_LOG_CLASS suppression block be deleted; per the "SpotBugs Suppressions" section above the spotbugs BugInstance count must stay unchanged after the deletion.
- **Add at least one LogCaptor smoke test** outside the OpenCLContext example (the production code has 52 logger sites) so the SLF4J → Logback binding is exercised in tests.

- **Pluggable persistence backends (LMDB / HashMap / BloomFilter) + lookup benchmark.** A HashSet-based in-memory persistence DID exist pre-Git. Evidence in this repo, even though no commit ever contained the Java implementation:
  - `README.md:512-515` documents a `loadToMemoryCacheOnInit` config flag that "loads all addresses into a Java HashSet for true O(1) lookups vs LMDB's O(log n)".
  - All 4 `examples/config_Find_*.json` files set `"loadToMemoryCacheOnInit": false`.
  - **No `loadToMemoryCacheOnInit` field exists in any configuration POJO**, and **no `HashSet` reference exists anywhere under `persistence/`**. The feature was lost during the pre-Git to Git import (boot commit `2c8e9f1`, 2026-05-08); Jackson silently ignores the unknown JSON key, so operators following the README hint get no behaviour change at all.

  Today the layer is:
  - `persistence/Persistence.java` is already a clean interface (init/close, count, getAmount, containsAddress, putNewAmount, putAllAmounts, writeAllAmountsToAddressFile).
  - `persistence/lmdb/LMDBPersistence.java` is the only implementation. `ConsumerJava` is already typed against the interface (line 75 `protected @Nullable Persistence persistence`); only `initLMDB()` and one `import LMDBPersistence` couple the consumer to LMDB.
  - `BloomFilter` is currently a **bolted-on accelerator inside `LMDBPersistence`** (fields at lines 66, build at 110-136, query at 281-282) - not an independent backend; the LMDB query is still done when `mightContain` returns true.

  Plan to capture (do later, not now):
  1. **Either resurrect the in-memory HashSet/HashMap backend or remove the dead README/config references.** Today the README and example configs lie to operators about a working feature; either re-implement (preferred per owner's wish for a HashMap backend) or strip the references in the same commit.
  2. **Pull the BloomFilter accelerator out of `LMDBPersistence`** into its own concern (e.g. `persistence/bloom/BloomFilterAccelerator`) so it can wrap any backend, not just LMDB. Keep the existing `useBloomFilter` config working.
  3. **Add standalone backends in sibling sub-packages.** The operator picks the right trade-off for their data set size and host memory:
     - `persistence/hashmap/HashMapPersistence` - in-memory `Map<ByteBuffer, Coin>` or `Map<byte[]wrap, Coin>`; pay attention to memory budget (realistic address sets are 10^8 - 10^9 entries; rough estimate 24-32 bytes per entry of overhead alone, 5-15 GB heap for the full set). Note the README's full database has 1.377 billion entries - well outside any reasonable Java heap.
     - `persistence/array/SortedArrayPersistence` (or similar) - cache-friendly array-backed "is it present?" lookup. Two shapes are interesting and should both be benchmarked:
       - **Sorted `long[]` of hash160-prefixes** indexed via `Arrays.binarySearch`. O(log n) per lookup but excellent cache locality (no pointer chasing, no boxed objects), so often faster in practice than a HashSet for ~10^8-10^9 entries when the host RAM allows it. Memory ~ 8 bytes per entry plus a small Coin side-table when value lookups are required.
       - **Open-addressing primitive hash table** (e.g. fastutil `Long2LongOpenHashMap` or roll-our-own over `long[]`) - O(1) average, similar cache profile, slightly more memory than the sorted variant but no log-factor.
       Whether to truncate hash160 to a 64-bit prefix or store the full 20 bytes is a separate design decision (prefix = collisions need disambiguation against LMDB or full bytes side-table; full = ~2.5x memory).
     - `persistence/bloom/BloomFilterPersistence` - Bloom-only "is it possibly present?" mode for false-positive-tolerant scans; `getAmount` would have to return `Coin.ZERO` or be unsupported, so this needs a contract decision on the interface (probably an `isProbabilistic()` flag or a thrown `UnsupportedOperationException`).
  4. **Make the backend selectable from config** under `consumerJava` (currently `lmdbConfigurationReadOnly` is mandatory). Likely shape: `consumerJava.persistence: { type: "LMDB"|"HASHMAP"|"SORTED_ARRAY"|"OPEN_HASH_ARRAY"|"BLOOM", ... }` with the existing LMDB config as one variant. Default stays LMDB for backward compat with existing example configs.
  5. **Optional: a layered/chained backend** so the operator can pick "Bloom in front of LMDB" (current behaviour, but explicit), "HashMap in front of LMDB" (warm-cache mode for hot addresses), or "SortedArray in front of LMDB" (cache-friendly read-only front for the hot set) via composition of `Persistence` implementations. This is essentially what the README-documented `loadToMemoryCacheOnInit` flag was meant to do.
  6. **JMH lookup benchmark** comparing `containsAddress` + `getAmount` throughput on identical datasets across ALL backends (cold and warm). The repo already has a JMH benchmark module - extend it. Measure for each backend:
     - throughput (ops/s) cold and warm
     - p50/p99 lookup latency
     - build / load time
     - steady-state RSS (host memory footprint)
     - false positive rate where applicable (Bloom; truncated SortedArray)
     so the operator can pick the right trade-off for their data set size and host.

  **Out of scope of this TODO:** actually executing any of the above. This entry is a research-and-plan placeholder so the architecture decision is on record. The dead README/config references should at least be addressed in a near-term cleanup commit even if the full backend work is deferred.
