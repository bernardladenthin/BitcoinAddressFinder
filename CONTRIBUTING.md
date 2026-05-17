# Contributing to BitcoinAddressFinder

Thank you for your interest in contributing to **BitcoinAddressFinder**. This document explains how to build, test, and submit changes.

## How to build and run

### Prerequisites

- **Java 21** (OpenJDK or any compatible distribution).
- **Maven 3.6.3+** — a Maven Wrapper (`./mvnw`) is included; no separate installation required.

### Common commands

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

### Running the tool

After building the assembly JAR, run it with a JSON configuration file:

```bash
java -jar target/bitcoinaddressfinder-*-jar-with-dependencies.jar \
  examples/config_Find_8CPUProducer.json
```

Pre-built example configuration files and Windows batch scripts live in the
[`examples/`](examples/) directory.

### JVM memory and module flags

Tests are launched with `-Xmx2g -Xms1g` and several `--add-opens` flags (for LMDB and internal module access). These are defined in `pom.xml` (`<argLine>`) and `.mvn/jvm.config` — the Maven Wrapper picks them up automatically.

## Reporting issues

Please use the GitHub issue tracker for bug reports, feature requests, and questions:

**<https://github.com/bernardladenthin/BitcoinAddressFinder/issues>**

When filing a bug, please include:

- Java version and OS.
- The configuration file (redact any sensitive key material).
- Full stack trace / log output.
- Steps to reproduce.

For security-sensitive issues, do **not** open a public issue — see [`SECURITY.md`](SECURITY.md).

## Pull request workflow

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/BitcoinAddressFinder.git
   ```
3. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feat/my-feature main
   ```
4. **Make changes** — write code and tests (see [Test policy](#test-policy)).
5. **Verify** that the full test suite passes before pushing:
   ```bash
   ./mvnw test
   ```
6. **Push** the branch to your fork and **open a Pull Request** against `main`.
7. A project maintainer will review the PR; address any requested changes and push additional commits to the same branch.
8. Once approved and all CI checks pass, the PR will be merged.

## Coding standards

The full architecture, design principles, Javadoc conventions, concurrency rules, and naming conventions are documented in:

- [`CLAUDE.md`](CLAUDE.md) — project overview, architecture, build, and conventions reference.
- [`CODE_WRITING_GUIDE.md`](CODE_WRITING_GUIDE.md) — project-specific code-writing rules.

Highlights:

- **Naming**: configuration POJOs are prefixed `C` (e.g., `CProducerJava`); producer implementations are suffixed with type (e.g., `ProducerOpenCL`); key-producer strategies follow `KeyProducerJava<Strategy>`.
- **License headers**: every source file carries the Apache 2.0 header, wrapped in `// @formatter:off` / `// @formatter:on`.
- **Records and immutability** are preferred for value objects.

## Test policy

> Every new feature or behaviour change MUST include automated tests. Pull requests that add or change functionality without corresponding tests will be asked to add tests before merge. Bug fixes SHOULD include a regression test.

Detailed test-writing rules — framework, structure, naming, isolation, timeouts, and data providers — are documented in [`TEST_WRITING_GUIDE.md`](TEST_WRITING_GUIDE.md).

**Static analysis:** the build runs **Error Prone** with the **NullAway** plugin at compile time. All code in the `net.ladenthin` package tree must carry proper JSpecify nullness annotations (`@Nullable` from `org.jspecify.annotations`; `@NonNull` is the implicit default). Unannotated nullable returns or parameters, and Error Prone diagnostics, will fail the build.

## Commit message convention

This project follows the [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) specification. Examples:

```
feat(keyproducer): add socket key producer
fix(consumer): close LMDB env on shutdown
docs(readme): refresh quickstart
chore(deps): bump guava to 33.5.0-jre
```

Common types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `build`, `ci`, `perf`. Keep the subject line ≤72 characters; use the body for the *why*.

## Communication channels

- **GitHub Issues** — bug reports, feature requests, questions:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/issues>
- **GitHub Security Advisories** — private vulnerability reports:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/security/advisories/new>

## Code of Conduct

All participants are expected to follow the project's [Code of Conduct](CODE_OF_CONDUCT.md). Reports of unacceptable behaviour should be sent to the enforcement contact listed in that document.

## Releasing

The release procedure for this project — including the version-bump, tagging, and Maven Central publication steps — is documented in [`docs/RELEASE.md`](docs/RELEASE.md). Only maintainers cut releases.

## License of contributions

By submitting a pull request you agree that your contribution will be licensed under the **Apache License, Version 2.0** — the same license that covers this project. See [`LICENSE`](LICENSE) for the full license text. There is no separate CLA; inbound contributions are inbound=outbound under Apache 2.0.
