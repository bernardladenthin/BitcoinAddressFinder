# TDD Skill — BitcoinAddressFinder

This skill documents **test-driven development practices and refactoring patterns** used in the BitcoinAddressFinder project. Apply these rules alongside `TEST_WRITING_GUIDE.md`, `CODE_WRITING_GUIDE.md`, and `CLAUDE.md`.

---

## 1. Extract Static Methods Into a Non-Static Helper Class

### Motivation

Static utility methods embedded in a domain class (e.g. `AddressTxtLine`) are hard to unit-test in isolation and cannot be mocked or injected. Extracting them into a dedicated, non-static helper class (e.g. `Bech32Helper`) allows:

- Direct, focused unit tests for each method without going through the owning class.
- Easier mocking when a consumer needs to stub the helper in its own tests.
- A single, named home for related utilities — consistent with the `BitHelper` pattern already established in the project.

### Pattern

**Before:** static method buried in a larger class

```java
// AddressTxtLine.java — hard to test in isolation
private static byte[] decodeBech32CharsetToValues(String base32String) { ... }
```

**After:** non-static method on a dedicated helper class

```java
// Bech32Helper.java — small, focused, testable
public class Bech32Helper {
    public byte[] decodeBech32CharsetToValues(String base32String) { ... }
}
```

The owning class holds an instance field and delegates:

```java
// AddressTxtLine.java
Bech32Helper bech32Helper = new Bech32Helper();
// ...
byte[] payload = bech32Helper.extractPKHFromBitcoinCashAddress(address);
```

### Naming Conventions

- Helper class names end with `Helper` (e.g. `BitHelper`, `Bech32Helper`).
- The corresponding test class ends with `HelperTest` (e.g. `BitHelperTest`, `Bech32HelperTest`).
- Public constants on the helper follow `public static final` with a Javadoc comment as required by `CODE_WRITING_GUIDE.md`.

### When to Apply

Apply this extraction when:

1. A class contains ≥ 2 closely related static (or effectively-static) methods that operate on the same concept.
2. Those methods are not yet tested directly — their test coverage is indirect, piggybacking on a higher-level test class.
3. The methods are stable enough that the refactoring does not break any public API contract.

Do **not** apply this extraction for one-off utility methods that logically belong to their host class and have no reuse outside it.

### Accompanying Test Migration

When the extracted methods already have tests in the original test class (e.g. `AddressTxtLineTest`), **move** those tests to the new test class:

1. Copy the test methods verbatim into `<HelperName>Test.java`.
2. Delete them from the original test class.
3. Clean up any imports in the original test class that are no longer needed after the removal.
4. Add new tests for any methods that were not yet directly covered (see `TEST_WRITING_GUIDE.md` for the required test structure).

---

## 2. Test Class for a Helper — Required Coverage

Every helper class created by the extract pattern above **must** have a corresponding `*HelperTest` class that covers:

| Category | Required tests |
|---|---|
| Happy path per public method | At least one test verifying correct output for representative input |
| Public constants | `CHARSET`, `RADIX_*`, etc. are tested indirectly by the happy-path tests |
| Boundary / error cases | E.g. invalid character → `IllegalArgumentException` |
| Moved tests from original class | All tests that previously exercised these methods indirectly, now calling the helper directly |

Follow all rules in `TEST_WRITING_GUIDE.md`: AAA structure, editor-fold groups, Hamcrest assertions, no raw JUnit asserts.

### Reference Example

`BitHelper` and `BitHelperTest` are the canonical model for this pattern in the project. `Bech32Helper` and `Bech32HelperTest` are the second instance. When adding a new helper class, follow the same structure.

```java
// Bech32HelperTest.java — canonical structure

public class Bech32HelperTest {

    private final KeyUtility keyUtility = new KeyUtility(
        new NetworkParameterFactory().getNetwork(), new ByteBufferUtility(false));

    // <editor-fold defaultstate="collapsed" desc="decodeBech32CharsetToValues">
    @Test
    public void decodeBech32CharsetToValues_fullCharset_returnsValuesZeroToThirtyOne() {
        // arrange
        Bech32Helper sut = new Bech32Helper();
        byte[] expected = {0,1,2,...,31};

        // act
        byte[] result = sut.decodeBech32CharsetToValues(Bech32Helper.CHARSET);

        // assert
        assertThat(result, is(expected));
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeBech32CharsetToValues_invalidCharacter_throwsException() {
        // arrange
        Bech32Helper sut = new Bech32Helper();

        // act
        sut.decodeBech32CharsetToValues("i"); // 'i' is not in the Bech32 character set
    }
    // </editor-fold>
}
```

---

## 3. Constructor Injection in Tests — Lightweight Mocking

### Motivation

Tests should **inject dependencies directly through constructors**, without external test frameworks or DI containers. This approach:

- Keeps tests fast — no container initialization overhead.
- Makes dependencies explicit — the test code clearly shows what the unit under test depends on.
- Enables fine-grained control — tests can create custom objects, mocks, or test doubles for specific scenarios.
- Avoids test coupling — tests don't depend on framework infrastructure (Spring `@SpringBootTest`, etc.).

### Pattern

When testing a class with dependencies, create the object with mocks or test doubles directly:

```java
// ✅ GOOD — lightweight, explicit constructor injection

@Test
public void processKey_validKey_logsAndStores() {
    // arrange
    Logger mockLogger = mock(Logger.class);
    Persistence mockPersistence = mock(Persistence.class);
    KeyUtility realKeyUtility = new KeyUtility(...);  // can be real or mock

    MyService service = new MyService(mockLogger, mockPersistence, realKeyUtility);

    // act
    service.processKey(testKey);

    // assert
    verify(mockLogger).info(contains("processed"));
    verify(mockPersistence).store(any());
}
```

### What NOT to Do

**Do not use test frameworks that hide constructor injection:**

```java
// ❌ BAD — uses Spring @SpringBootTest (overkill for unit tests)
@RunWith(SpringRunner.class)
@SpringBootTest
public class MyServiceTest {

    @MockBean
    private Logger logger;

    @Autowired
    private MyService service;  // Spring wires it; you can't see the dependencies

    @Test
    public void processKey_validKey_logsAndStores() {
        // test body
    }
}
```

**Costs of the framework approach:**
- Slow test startup (Spring container initialization: 1–2 seconds per test class)
- Opaque — you can't see what dependencies are injected
- Harder to control edge cases (e.g., pass a `Random` with a fixed seed for reproducibility)
- Test coupling — tests depend on Spring infrastructure, making them brittle

### When Constructor Injection Isn't Enough

If a class has **8+ parameters**, the test still becomes unwieldy:

```java
// Hard to read even with constructor injection
MyService service = new MyService(mock1, mock2, mock3, mock4, mock5, mock6, mock7, mock8);
```

**Solution:** Refactor the class (split into smaller classes) rather than introducing a test framework. See `CODE_WRITING_GUIDE.md`, Section 3.

---

## 3. DI Frameworks — When to Avoid

**Never introduce an external DI framework (Spring, Guice, Dagger) to solve construction complexity.**

The codebase intentionally avoids DI frameworks for these reasons:

1. **Startup cost** — BitcoinAddressFinder is a CLI tool; framework initialization overhead (1–3 seconds) is unacceptable.
2. **Configuration bloat** — A DI framework would require parallel configuration (annotations/XML) alongside the existing JSON config.
3. **Testing overhead** — Test frameworks (e.g., `@SpringBootTest`) are slower and less transparent than direct injection.
4. **Null safety** — Error Prone / NullAway compile-time checks are more powerful than framework-managed injection.

**Recommended alternatives:**

| Problem | Solution |
|---|---|
| Shallow construction (3–5 params) | Use constructor injection (current approach) |
| Deeper construction (6–8 params) | Refactor into smaller classes |
| Very deep construction (9+ params) | Use a builder pattern (not a DI framework) |
| Pluggable strategies | Use lightweight `Function<Config, Instance>` factories (see `Finder.processKeyProducers`) |

For a detailed analysis, see `DEPENDENCY_INJECTION_ANALYSIS.md`.
