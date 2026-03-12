# Code Writing Guide — BitcoinAddressFinder

This guide is the authoritative reference for writing and improving production code in this project.

---

## 1. Named Constants — DRY, No Inline Literals

The primary motivation is **Don't Repeat Yourself (DRY)**. Every meaningful value must exist in exactly **one** authoritative place — a named constant — so that a future change to the value requires editing only one line. Inline literals scatter the same meaning across multiple call sites, making the code fragile and hard to maintain.

### Rules

- Every string, number, or flag literal that carries semantic meaning **must** be a named `public static final` or `private static final` constant. Inline magic values are **prohibited**.
- Constants must be placed at the top of the class, before constructors and methods.
- The name must describe the **meaning or role** of the value, not the value itself.
- Each constant must have a **Javadoc comment** that explains what the value represents, why it has that specific value, and any relevant cross-references (e.g. spec references, related constants, or affected classes).
- When a derived value (e.g., a `BigInteger` parsed from a hex string) is needed, define **both** the source constant and the derived constant, and compute the derived one from the source — never duplicate the raw literal.
- Radix values (`16`, `10`, `2`) must always be referenced through `BitHelper.RADIX_HEX`, `BitHelper.RADIX_DECIMAL`, etc. — never as bare integer literals.

```java
// ❌ BAD — magic literals inline, no single source of truth
return new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
if (batchSize > 256) { ... }
```

```java
// ✅ GOOD — one authoritative constant with Javadoc; derived constant computed from it
/**
 * The maximum valid private key for secp256k1, equal to the curve group order minus one.
 * Expressed as an uppercase hex string for use in configuration and serialization.
 *
 * @see #MAX_PRIVATE_KEY
 */
public static final String MAX_PRIVATE_KEY_HEX = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141";

/**
 * {@link #MAX_PRIVATE_KEY_HEX} parsed into a {@link BigInteger} for arithmetic comparisons.
 * Derived from {@link #MAX_PRIVATE_KEY_HEX} — do not duplicate the literal here.
 */
public static final BigInteger MAX_PRIVATE_KEY = new BigInteger(MAX_PRIVATE_KEY_HEX, BitHelper.RADIX_HEX);

if (batchSize > MAX_BATCH_SIZE) { ... }
```

---

## 2. Logger Injection — Constructor Over Setter

When a class uses an SLF4J `Logger` and tests need to inject a mock logger, prefer **constructor-based injection** over a setter method.

### Pattern

Provide two constructors:

1. **Default constructor** — creates its own logger via `LoggerFactory`. This is the constructor used by production code.
2. **`@VisibleForTesting` constructor** — accepts a `Logger` parameter and delegates to the default constructor (or vice versa). This is the constructor used by tests.

The second constructor calls the first (or a shared initializer) so that all field initialization is centralized.

```java
public class MyService {

    private final Logger logger;

    // Production constructor — creates its own logger
    public MyService(Config config) {
        this(config, LoggerFactory.getLogger(MyService.class));
    }

    // Test constructor — accepts an injected logger
    @VisibleForTesting
    MyService(Config config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }
}
```

### Rules

- The `logger` field should be `private final` (not `protected`, not mutable).
- The production constructor must delegate to the test constructor (or the other way around) — never duplicate initialization logic.
- The `@VisibleForTesting` constructor has package-private visibility.
- A `setLogger` method is the **last resort** — only use it when constructor injection is not feasible (e.g., the object is created by a framework that controls construction).

### Test usage

```java
@Test
public void init_validConfig_logsExpectedMessage() {
    // arrange
    Logger mockLogger = mock(Logger.class);
    MyService service = new MyService(config, mockLogger);

    // act
    service.init();

    // assert
    verify(mockLogger).info(eq("Initialized."));
}
```

---

## 3. Constructor Limits — Keeping Dependencies Shallow

### Motivation

Classes should have **shallow constructor parameters** (3–8 parameters). If a constructor exceeds 8 parameters, it signals over-complicated responsibility. Before adding parameters, consider:

1. **Refactor into smaller classes** — Split the class into two or more focused classes, each with fewer dependencies.
2. **Extract a helper class** — Wrap groups of related parameters into a single helper object.
3. **Use a builder pattern** — For complex objects with many optional dependencies, a builder is more readable than a long constructor.

### Rules

- **Constructors with 3–5 parameters** are normal and acceptable.
- **Constructors with 6–7 parameters** warrant a review — do the dependencies form a coherent unit, or should the class be split?
- **Constructors with 8+ parameters** are a code smell. Do not add external DI frameworks to hide the complexity; instead, refactor.
- **Never introduce a DI framework (Spring, Guice, Dagger)** to reduce constructor parameter counts. This trades one problem (long constructors) for another (framework overhead, startup cost, hidden configuration). See `DEPENDENCY_INJECTION_ANALYSIS.md`.

### Example: Splitting Responsibilities

```java
// ❌ BAD — 10 parameters indicate over-coupled concerns
public MyService(
    Config config,
    KeyUtility keyUtility,
    Logger logger,
    Database db,
    Cache cache,
    MetricsCollector metrics,
    ThreadPool threadPool,
    LoadBalancer lb,
    SecurityManager secMgr,
    Auditor auditor
) { ... }

// ✅ GOOD — split into focused classes
public class KeyProducerService {
    public KeyProducerService(KeyUtility keyUtility, Logger logger) { ... }
}

public class StorageService {
    public StorageService(Database db, Cache cache, MetricsCollector metrics) { ... }
}

public class OrchestratorService {
    public OrchestratorService(KeyProducerService kp, StorageService storage) { ... }
}
```

In the split design:
- Each class has 2–3 dependencies (shallow).
- Relationships are explicit and easy to test.
- No DI framework needed.
