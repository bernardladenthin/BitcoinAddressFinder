---
name: tdd
description: Test-Driven Development workflow for BitcoinAddressFinder — delegates to the generic Java TDD skill and adds project-specific context
---

# TDD — Test-Driven Development for BitcoinAddressFinder

You are working on **BitcoinAddressFinder** (group `net.ladenthin`, Java 21, Maven).

## Generic Java TDD Guide

Follow all conventions from the generic Java TDD skill, which lives in the sibling `workspace`
repository at `../workspace/.claude/skills/java-tdd-guide/SKILL.md` (this repository carries only
the marker `.claude/skills/java-tdd-guide/SKILL.pointer.md`; the `workspace` repo must be in the
session scope for the harness to load it). That guide covers:

- Red → Green → Refactor workflow
- File headers (Apache 2.0 license)
- Test framework stack (JUnit Jupiter, Hamcrest, Mockito)
- Test naming, AAA structure, editor folds
- Assertions (Hamcrest only), exception testing
- Parameterized tests (`@ParameterizedTest` + `@MethodSource`), named constants, DRY
- Logger injection (constructor over setter)
- Null safety (JSpecify + NullAway)
- Records, immutability, concurrency
- Import style, anti-patterns, completeness checklist

This repository targets Java 21, so also read the Java 21 supplements of the version chain:
`../workspace/guides/test/TEST_WRITING_GUIDE-21.md` and
`../workspace/guides/src/CODE_WRITING_GUIDE-21.md` (each extends its `-8.md` baseline).

## Project-Specific Supplements

For BitcoinAddressFinder-specific conventions, also follow:

- **`CODE_WRITING_GUIDE.md`** — BitHelper radix constants, C-prefix configuration POJOs, custom domain exceptions, graceful shutdown (Interruptable), lambda callbacks
- **`TEST_WRITING_GUIDE.md`** — Marker annotations (@AwaitTimeTest, @ToStringTest, @OpenCLTest), timing/await tests, static address constants (StaticKey, P2PKH, P2SH, P2WPKH), platform assumptions, LMDB/OpenCL test patterns, producer test helpers, socket test utilities

## Package

```
net.ladenthin.bitcoinaddressfinder
```

## Build & Test

There is no Maven Wrapper in this repository; use an installed `mvn`.

```bash
mvn compile       # compile (NullAway enforced)
mvn test          # run all tests (~5 minutes; prefer a narrowed -Dtest=... while iterating, see CLAUDE.md)
mvn test -Dnet.ladenthin.bitcoinaddressfinder.disableLMDBTest=true  # skip LMDB tests
```
