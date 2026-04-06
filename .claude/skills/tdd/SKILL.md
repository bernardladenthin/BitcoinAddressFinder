---
name: tdd
description: Test-Driven Development workflow for BitcoinAddressFinder — delegates to the generic Java TDD skill and adds project-specific context
---

# TDD — Test-Driven Development for BitcoinAddressFinder

You are working on **BitcoinAddressFinder** (group `net.ladenthin`, Java 21, Maven).

## Generic Java TDD Guide

Follow all conventions from the generic Java TDD skill (`.claude/skills/java-tdd-guide/SKILL.md`). That guide covers:

- Red → Green → Refactor workflow
- File headers (Apache 2.0 license)
- Test framework stack (JUnit 4, DataProviderRunner, Hamcrest, Mockito)
- Test naming, AAA structure, editor folds
- Assertions (Hamcrest only), exception testing
- Data providers, named constants, DRY
- Logger injection (constructor over setter)
- Null safety (JSpecify + NullAway)
- Records, immutability, concurrency
- Import style, anti-patterns, completeness checklist

## Project-Specific Supplements

For BitcoinAddressFinder-specific conventions, also follow:

- **`CODE_WRITING_GUIDE.md`** — BitHelper radix constants, C-prefix configuration POJOs, custom domain exceptions, graceful shutdown (Interruptable), lambda callbacks
- **`TEST_WRITING_GUIDE.md`** — Marker annotations (@AwaitTimeTest, @ToStringTest, @OpenCLTest), timing/await tests, static address constants (StaticKey, P2PKH, P2SH, P2WPKH), platform assumptions, LMDB/OpenCL test patterns, producer test helpers, socket test utilities

## Package

```
net.ladenthin.bitcoinaddressfinder
```

## Build & Test

```bash
./mvnw compile       # compile (NullAway enforced)
./mvnw test          # run all tests
./mvnw test -Dnet.ladenthin.bitcoinaddressfinder.disableLMDBTest=true  # skip LMDB tests
```
