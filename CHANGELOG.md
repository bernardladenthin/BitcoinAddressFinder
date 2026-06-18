# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.6.0...HEAD
[1.6.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.3.0-SNAPSHOT...v1.4.0
[1.3.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.2.0-SNAPSHOT...v1.3.0-SNAPSHOT
[1.2.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.1.0-SNAPSHOT...v1.2.0-SNAPSHOT
[1.1.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.0.0-SNAPSHOT...v1.1.0-SNAPSHOT
[1.0.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/releases/tag/v1.0.0-SNAPSHOT
