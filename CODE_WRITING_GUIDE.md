# Code Writing Guide — BitcoinAddressFinder (Project-Specific Supplement)

> **Canonical workspace rules** (named constants, custom domain
> exceptions, constructor injection, defensive null checks, helper
> classes as instance methods, `@VisibleForTesting`, SPDX license
> headers, concurrency primitives) live in
> [`../workspace/guides/CODE_WRITING_GUIDE.md`](../workspace/guides/CODE_WRITING_GUIDE.md).
>
> BAF is the only sibling repo on **Java 21**, so the Java 21 supplement
> [`../workspace/guides/CODE_WRITING_GUIDE-java21.md`](../workspace/guides/CODE_WRITING_GUIDE-java21.md)
> (records, switch expressions, text blocks, pattern matching, sealed
> types, `var`) also applies here.
>
> The TDD workflow lives in
> [`../workspace/.claude/skills/java-tdd-guide/SKILL.md`](../workspace/.claude/skills/java-tdd-guide/SKILL.md)
> (referenced locally as `.claude/skills/java-tdd-guide/SKILL.pointer.md`).
>
> This file contains only **BAF-specific** production-code conventions:
> the `BitHelper.RADIX_*` constants, C-prefixed configuration POJOs,
> the named domain exception types, the `Interruptable` contract, and
> the lambda-callback constructor injection pattern.

---

## 1. Radix Constants — BitHelper

Radix values (`16`, `10`, `2`) must always be referenced through `BitHelper` constants — never as bare integer literals:

```java
// ❌ BAD
new BigInteger("FF", 16);

// ✅ GOOD
new BigInteger("FF", BitHelper.RADIX_HEX);
```

Available constants:
- `BitHelper.RADIX_HEX` (16)
- `BitHelper.RADIX_DECIMAL` (10)
- `BitHelper.RADIX_BINARY` (2)

---

## 2. Configuration Objects — C-Prefix POJOs

All new features are driven by a C-prefixed configuration POJO:

```java
public class CMyFeature {
    public int threads = 1;
    public @Nullable String optionalParam;
}

// Constructor receives the config as first argument:
public MyFeature(CMyFeature config, KeyUtility keyUtility, Logger logger) {
    this.config = config;
    this.keyUtility = keyUtility;
    this.logger = logger;
}
```

---

## 3. Custom Domain Exceptions

Use project-specific exception types rather than generic ones:

| Exception | When to use |
|---|---|
| `KeyProducerIdNullException` | Key producer ID is null |
| `KeyProducerIdIsNotUniqueException` | Duplicate key producer ID |
| `KeyProducerIdUnknownException` | Unrecognized key producer ID |
| `NoMoreSecretsAvailableException` | Secret source exhausted |
| `PrivateKeyTooLargeException` | Private key exceeds secp256k1 range |
| `UnknownSecretFormatException` | Unrecognized secret format |
| `AddressFormatNotAcceptedException` | Invalid address format |

Create a new domain exception rather than throwing `IllegalArgumentException` or `RuntimeException` when a more specific type makes sense.

---

## 4. Graceful Shutdown — Interruptable Interface

All Producer/Consumer implementations must implement `Interruptable`:

```java
public class MyProducer extends AbstractProducer implements Interruptable {
    @Override
    public void interrupt() {
        // signal the work loop to stop
    }
}
```

---

## 5. Lambda Callbacks in Production Constructors

Inject behaviour via functional interfaces rather than subclassing:

```java
public AddressFile(
    File file,
    ReadStatistic readStatistic,
    Network network,
    Consumer<AddressToCoin> addressConsumer,
    Consumer<String> rejectedLineConsumer
) { ... }
```
