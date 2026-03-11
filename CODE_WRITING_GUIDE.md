# Code Writing Guide — BitcoinAddressFinder

This guide is the authoritative reference for writing and improving production code in this project.

---

## 1. Logger Injection — Constructor Over Setter

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
