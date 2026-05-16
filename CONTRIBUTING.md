# Contributing to BitcoinAddressFinder

Thank you for your interest in contributing to **BitcoinAddressFinder**!
This document explains everything you need to get started.

---

## Table of Contents

1. [How to Build and Run](#1-how-to-build-and-run)
2. [Filing Issues](#2-filing-issues)
3. [Pull Request Workflow](#3-pull-request-workflow)
4. [Coding Standards](#4-coding-standards)
5. [Test Policy](#5-test-policy)
6. [Communication Channels](#6-communication-channels)
7. [License of Contributions](#7-license-of-contributions)

---

## 1. How to Build and Run

### Prerequisites

- **Java 21** (OpenJDK or any compatible distribution)
- **Maven 3.6.3+** — a Maven Wrapper (`./mvnw`) is included; no separate installation required

### Common Commands

```bash
# Compile only
./mvnw compile

# Run the full test suite
./mvnw test

# Build a fat JAR with all dependencies (assembly profile)
./mvnw package -P assembly

# Build without running tests
./mvnw package -DskipTests

# Generate a JaCoCo code-coverage report (target/site/jacoco/index.html)
./mvnw test jacoco:report

# Skip LMDB-specific tests when native LMDB libraries are unavailable
./mvnw test -Dnet.ladenthin.bitcoinaddressfinder.disableLMDBTest=true
```

### Running the Tool

After building the assembly JAR, run it with a JSON configuration file:

```bash
java -jar target/bitcoinaddressfinder-*-jar-with-dependencies.jar \
  examples/config_Find_8CPUProducer.json
```

Pre-built example configuration files and Windows batch scripts live in the
[`examples/`](examples/) directory.

### JVM Memory and Module Flags

Tests are launched with `-Xmx2g -Xms1g` and several `--add-opens` flags (for
LMDB and internal module access). These are defined in `pom.xml` (`<argLine>`)
and `.mvn/jvm.config` — the Maven Wrapper picks them up automatically.

---

## 2. Filing Issues

Please use the GitHub issue tracker for bug reports, feature requests, and
questions:

**<https://github.com/bernardladenthin/BitcoinAddressFinder/issues>**

When filing a bug, please include:

- Java version and OS
- The configuration file (redact any sensitive key material)
- Full stack trace / log output
- Steps to reproduce

---

## 3. Pull Request Workflow

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/BitcoinAddressFinder.git
   ```
3. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feat/my-feature main
   ```
4. **Make changes** — write code and tests (see [Test Policy](#5-test-policy)).
5. **Verify** that the full test suite passes before pushing:
   ```bash
   ./mvnw test
   ```
6. **Push** the branch to your fork and **open a Pull Request** against `main`.
7. A project maintainer will review the PR; address any requested changes and
   push additional commits to the same branch.
8. Once approved and all CI checks pass, the PR will be merged.

---

## 4. Coding Standards

### Null Safety (NullAway + JSpecify)

All code in the `net.ladenthin` package tree must carry proper nullness
annotations:

- Use `@Nullable` (from `org.jspecify.annotations`) on any value that may be
  `null`; `@NonNull` is the implicit default.
- **NullAway** is enabled at compile time and treats violations as errors.
  Unannotated nullable returns or parameters will fail the build.

### Static Analysis (Error Prone)

**Error Prone** is active at compile time. Fix all Error Prone diagnostics
before opening a PR.

### Naming Conventions

- Configuration POJOs: prefix `C` (e.g., `CProducerJava`, `CKeyProducerJavaRandom`).
- Producer implementations: suffix with type (e.g., `ProducerJava`, `ProducerOpenCL`).
- Key-producer strategies follow the pattern `KeyProducerJava<Strategy>`.

### License Headers

All source files must include the Apache 2.0 license header exactly as it
appears in existing source files. Use `// @formatter:off` / `// @formatter:on`
around the header to exclude it from auto-formatting.

### Additional Conventions

See [`CLAUDE.md`](CLAUDE.md) for a full description of architecture, design
principles, Javadoc conventions (HTML entities instead of bare Unicode), and
concurrency rules enforced in this project.

---

## 5. Test Policy

> Every new feature or behavior change MUST include automated tests. Pull
> requests that add or change functionality without corresponding tests will be
> asked to add tests before merge. Bug fixes SHOULD include a regression test.

### Framework

Tests use **JUnit 4** with **Mockito** for mocking and **Hamcrest** for
assertions. Data-driven tests use **junit-dataprovider**.

### Running Tests

```bash
# Full test suite
./mvnw test

# Skip LMDB tests (when native libs are absent)
./mvnw test -Dnet.ladenthin.bitcoinaddressfinder.disableLMDBTest=true
```

### Conventions

- Tests run in forked JVMs (one fork, no reuse) for isolation.
- Per-test timeout: 60 seconds.
- The test-to-source line ratio is approximately 1.7 : 1; new code is expected
  to maintain this density.
- Utility base classes for tests: `LMDBBase`, `BIP39DataProvider`,
  `TestTimeProvider`, `KeyProducerTestUtility`.

Detailed test-writing rules are documented in [`TEST_WRITING_GUIDE.md`](TEST_WRITING_GUIDE.md).

---

## 6. Communication Channels

- **GitHub Issues** — bug reports, feature requests, questions:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/issues>
- **GitHub Discussions** — broader conversations and Q&A:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/discussions>

---

## 7. License of Contributions

By submitting a pull request you agree that your contribution will be licensed
under the **Apache License, Version 2.0** — the same license that covers this
project. See [`LICENSE`](LICENSE) for the full license text.
