# Code Writing Guide — BitcoinAddressFinder (Project-Specific Supplement)

This guide contains **project-specific** production code conventions that supplement the generic Java TDD skill (`.claude/skills/java-tdd-guide.md`). For general Java conventions (named constants, logger injection, null safety, records, concurrency, etc.), refer to the generic guide.

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
