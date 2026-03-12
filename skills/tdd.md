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

## 3. Real-World Example: PrivateKeyValidator

The `PrivateKeyValidator` helper class is a concrete implementation of the refactoring pattern described above.

### Extraction Details

**Original State:** Five related static methods in `KeyUtility`:
- `getMaxPrivateKeyForBatchSize(int batchSizeInBits)`
- `isInvalidWithBatchSize(BigInteger, BigInteger)`
- `isOutsidePrivateKeyRange(BigInteger)`
- `returnValidPrivateKey(BigInteger)`
- `replaceInvalidPrivateKeys(BigInteger[])`

**Refactored:** Extracted to `PrivateKeyValidator` as non-static instance methods.

**Injection Locations:**
- `AbstractProducer` — instantiates in constructor, inherited by `ProducerJava` and `ProducerOpenCL`
- `OpenClTask` — instantiates in constructor for batch validation
- `PublicKeyBytes` — instantiates for key state validation

### Test Coverage

`PrivateKeyValidatorTest` provides comprehensive coverage:
- **Batch size validation**: boundary tests for 0 bits, max bits, overflow cases
- **Range validation**: tests for MIN/MAX boundaries and out-of-range keys
- **Key correction**: tests for valid, too-small, and too-large keys
- **Array correction**: tests for mixed valid/invalid arrays
- **Data-provider tests**: uses `CommonDataProvider` for batch size edge cases

### Benefits Realized

1. **Isolated Testing**: Each validation method can be tested directly without instantiating higher-level classes.
2. **Clear Dependencies**: Callers explicitly pass or store the validator, making dependencies visible.
3. **Future Extension**: If validation rules change (e.g., support for alternative curves), subclassing becomes possible.
4. **Code Reuse**: Multiple call sites (`OpenClTask`, `AbstractProducer`, `PublicKeyBytes`) share the same validation logic without static coupling.
