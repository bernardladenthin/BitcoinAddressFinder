# Unit Test Writing Guide — BitcoinAddressFinder

Derived by analysis of all 73 test files in `src/test/java/net/ladenthin/bitcoinaddressfinder/`.
This guide is the authoritative reference for writing and improving tests in this project.

---

## 1. File Structure & Header

Every test file **must** start with the formatter-off block enclosing the Apache 2.0 license header, exactly as shown:

```java
// @formatter:off
/**
 * Copyright <YEAR> Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;
```

- The `// @formatter:off` / `// @formatter:on` pair wraps **only** the license block.
- The year must match the file creation year (not the current year).

---

## 2. Test Framework

| Concern | Choice |
|---|---|
| Test runner | JUnit 4 (`@Test`, `@Before`, `@Rule`, etc.) |
| Parameterized tests | `com.tngtech.java.junit.dataprovider` (`@RunWith(DataProviderRunner.class)`, `@UseDataProvider`) |
| Assertions | Hamcrest only — `assertThat(actual, is(equalTo(expected)))` |
| Mocking | Mockito (`mock()`, `verify()`, `when()`, `ArgumentCaptor`) |
| Temp file system | JUnit `@Rule public TemporaryFolder folder = new TemporaryFolder()` |

**Do NOT use:**
- `assertEquals` / `assertTrue` / `assertFalse` from `org.junit.Assert` — use Hamcrest equivalents.
- TestNG or JUnit 5.

---

## 3. Class-Level Setup

### Class Declaration

```java
@RunWith(DataProviderRunner.class)   // only when @UseDataProvider is used in this class
public class FooTest {
```

Omit `@RunWith` if no data providers are used.

### Shared Instance Fields

Declare shared utilities as `private final` instance fields:

```java
private final Network network = new NetworkParameterFactory().getNetwork();
private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
private final BitHelper bitHelper = new BitHelper();
```

Mocks that need fresh state per test are declared at field level but initialized in `@Before`:

```java
private Logger mockLogger;

@Before
public void setUp() {
    mockLogger = mock(Logger.class);
}
```

An empty `@Before` method should be omitted entirely. Only keep it if it does meaningful work.

### TemporaryFolder (file-system tests)

```java
@Rule
public TemporaryFolder folder = new TemporaryFolder();
```

---

## 4. Code Folding — Grouping Tests by Method Under Test

Tests within a class **must** be grouped using NetBeans-style editor fold regions, one fold per method/feature under test:

```java
// <editor-fold defaultstate="collapsed" desc="methodName">
@Test
public void methodName_conditionA_expectedResultA() { ... }

@Test
public void methodName_conditionB_expectedResultB() { ... }
// </editor-fold>
```

Rules:
- The `desc` attribute equals the method name (or a short feature label for non-method groups).
- `defaultstate="collapsed"` is mandatory on every fold.
- All tests for the same method go inside a single fold.
- Tests that exercise different methods **must** be in different folds.
- The fold order in the file should match logical reading order (simple cases first, edge cases and exceptions last).

---

## 5. Test Method Naming

Pattern: **`methodUnderTest_inputOrCondition_expectedBehavior`**

```
fromLine_addressLineIsEmpty_returnNull
createECKey_TestUncompressed
getMaxPrivateKeyForBatchSize_batchSize0_returnsMaxPrivateKey
getMaxPrivateKeyForBatchSize_bitSizeNegative_throwsException
putNewAmount_initialLMDBSetTo1MiB_fillWithTooMuchValues_exceptionThrown
interrupt_keysQueueNotEmpty_consumerNotRunningWaitedInternallyForTheDuration
```

Rules:
- All three segments are **required** and separated by underscores.
- Use camelCase within each segment.
- The `expected` segment describes the observable outcome, not the implementation step.
  - Good: `_returnsMaxPrivateKey`, `_throwsException`, `_returnNull`, `_noExceptionThrown`
  - Bad: `_works`, `_correct`, `_test`
- Exception tests: the segment ends with `_throwsException` or `_exceptionThrown`.
- No-op / smoke tests: use `_noExceptionThrown`.
- Logging assertion tests: include `_logsError` / `_logged` / `_containsClassNameAndIdentityHash`.
- `toString` tests: use `_whenCalled_containsClassNameAndIdentityHash` (for default toString) or describe the exact content.

---

## 6. Test Body — AAA Structure

Every test body **must** follow the Arrange / Act / Assert structure with explicit section comments:

```java
@Test
public void methodName_conditionGiven_expectedResult() {
    // arrange
    Foo foo = new Foo(42);

    // act
    String result = foo.bar();

    // assert
    assertThat(result, is(equalTo("expected")));
}
```

### `// pre-assert` — two valid positions

`// pre-assert` is a named section that asserts a condition without it being the primary assertion of the test. It may appear in **two** positions:

**1. Before `// act`** — to assert on the initial state or verify preconditions of the input:

```java
// arrange
P2PKH address = P2PKH.BitcoinCashWithPrefix;

// pre-assert
assertThat(address.getPublicAddress(), startsWith(AddressTxtLine.BITCOIN_CASH_PREFIX));

// act
byte[] hash160 = AddressTxtLine.extractPKHFromBitcoinCashAddress(address.getPublicAddress());

// assert
assertThat(actualHashHex, is(equalTo(address.getPublicKeyHashAsHex())));
```

**2. Between `// act` and `// assert`** — as a guard check whose failure would make the primary assertions meaningless (e.g., a null-check before accessing fields):

```java
// act
AddressToCoin addressToCoin = new AddressTxtLine().fromLine(input, keyUtility);

// pre-assert
assertThat(addressToCoin, is(notNullValue()));

// assert
assertThat(addressToCoin.hash160(), is(equalTo(expectedHash)));
assertThatDefaultCoinIsSet(addressToCoin);
```

Rules:
- A `// pre-assert` between `// act` and `// assert` must check only a **prerequisite** for the primary assertions — not the outcome itself. Null-guard checks are the canonical use-case.
- Do **not** use `Objects.requireNonNull(...)` as a guard check; use `assertThat(x, is(notNullValue()))` inside a `// pre-assert` section instead.
- `// arrange` section may be omitted only when there is genuinely nothing to arrange (the object is created in the act).
- Never merge arrange and act into a single line if it harms readability.
- Keep the act to a **single method call** whenever possible.

---

## 7. Assertions — Hamcrest Style

All assertions use the Hamcrest `assertThat` form:

```java
// equality
assertThat(result, is(equalTo(expected)));

// null / not null
assertThat(result, is(nullValue()));
assertThat(result, is(notNullValue()));

// boolean
assertThat(flag, is(true));
assertThat(flag, is(false));

// negation
assertThat(result, is(not(equalTo(unexpected))));

// strings
assertThat(message, containsString("substring"));
assertThat(message, matchesPattern("Regex\\d+"));
assertThat(output, not(emptyOrNullString()));

// collections
assertThat(list, hasSize(3));
assertThat(list, is(empty()));
assertThat(list, hasItems("a", "b"));

// numbers / comparable
assertThat(index, is(lessThan(colonIndex)));
assertThat(waitTime, is(greaterThan(minExpected)));

// type
assertThat(obj, instanceOf(KeyProducerJavaRandom.class));
```

**Imports to use:**
```java
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;   // or import specific matchers
```

Do **not** use:
- `org.junit.Assert.assertEquals`
- `org.junit.Assert.assertTrue` / `assertFalse`
- `Assert.assertNotNull`

---

## 8. Exception Testing

### Simple expected exception (no message check needed)

```java
@Test(expected = IllegalArgumentException.class)
public void getMaxPrivateKeyForBatchSize_bitSizeNegative_throwsException() {
    // act
    KeyUtility.getMaxPrivateKeyForBatchSize(-1);
}
```

### Exception with message verification

Use try/catch when the exception message must be asserted:

```java
@Test
public void nextKey_counterOverflow_throwsException() {
    // arrange
    BIP39KeyProducer producer = ...;
    producer.counter.set(Integer.MAX_VALUE);

    try {
        // verify first call does NOT throw
        producer.nextKey();
    } catch (NoMoreSecretsAvailableException e) {
        fail("Exception thrown too early: " + e.getMessage());
    }

    // act — this call should throw
    // (declare @Test(expected=...) on the method)
    producer.nextKey();
}
```

### Exception with full message assertions

```java
PrivateKeyTooLargeException ex = new PrivateKeyTooLargeException(key, maxKey, batch);
assertThat(ex.getMessage(), containsString("0x" + key.toString(16)));
assertThat(ex.getMessage(), containsString("batchSizeInBits = " + batch));
```

---

## 9. Data Providers (Parameterized Tests)

### Centralized in CommonDataProvider

All data providers belong in `CommonDataProvider`. Each provider follows this pattern:

```java
// 1. Constant for the provider name
public final static String DATA_PROVIDER_MY_CASES = "myCases";

// 2. Javadoc linking to which test it serves
/**
 * For {@link FooTest}.
 */
@DataProvider
public static Object[][] myCases() {
    return new Object[][] {
        { inputA, expectedA },
        { inputB, expectedB },
    };
}
```

### Consuming a data provider

```java
@RunWith(DataProviderRunner.class)
public class FooTest {

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_MY_CASES, location = CommonDataProvider.class)
    public void foo_inputGiven_returnsExpected(TypeA input, TypeB expected) {
        // arrange
        Foo foo = new Foo();

        // act
        TypeB result = foo.bar(input);

        // assert
        assertThat(result, is(equalTo(expected)));
    }
}
```

### Enum-based providers

Iterate over all enum values using the shared helper:

```java
@DataProvider
public static Object[][] staticP2PKHAddresses() {
    return transformFlatToObjectArrayArray(P2PKH.values());
}
```

### Cartesian product providers

Use `mergeMany(dp1, dp2)` from `CommonDataProvider` for cross-product combinations:

```java
@DataProvider
public static Object[][] keyProducerTypeAndBitSize() {
    return mergeMany(keyProducerTypes(), bitSizesAtMostMax());
}
```

---

## 10. Custom Marker Annotations

Apply the appropriate marker annotation alongside `@Test` when applicable:

| Annotation | When to use |
|---|---|
| `@AwaitTimeTest` | Test involves `ExecutorService.awaitTermination` or `Thread.sleep` timing |
| `@ToStringTest` | Test validates a `toString()` implementation |
| `@OpenCLTest` | Test requires an OpenCL-capable device |

Example:

```java
@ToStringTest
@Test
public void toString_whenCalled_containsClassNameAndIdentityHash() {
    // ...
    assertThat(output, matchesPattern("ProducerJava@\\p{XDigit}+"));
}
```

---

## 11. Timing / Await Tests

Tests that assert on timing durations must:
1. Be annotated with `@AwaitTimeTest`.
2. Use `AwaitTimeTests.AWAIT_DURATION` (20 s) as the configurable duration.
3. Use `AwaitTimeTests.IMPRECISION` (2 s) as the tolerance.
4. Override the static constant before the test runs.

```java
@AwaitTimeTest
@Test
public void interrupt_keysQueueNotEmpty_consumerNotRunningWaitedInternallyForTheDuration()
        throws IOException, InterruptedException {
    // Change await duration
    ConsumerJava.AWAIT_DURATION_QUEUE_EMPTY = AwaitTimeTests.AWAIT_DURATION;

    // ... arrange ...

    long beforeAct = System.currentTimeMillis();
    consumerJava.interrupt();

    long afterAct = System.currentTimeMillis();
    Duration waitTime = Duration.ofMillis(afterAct - beforeAct);

    assertThat(waitTime, is(greaterThan(
        ConsumerJava.AWAIT_DURATION_QUEUE_EMPTY.minus(AwaitTimeTests.IMPRECISION)
    )));
}
```

---

## 12. Mocking & Logger Verification

### Injecting a mock logger

Prefer constructor injection over setter injection (see `CODE_WRITING_GUIDE.md` for the production-side pattern):

```java
Logger logger = mock(Logger.class);
when(logger.isTraceEnabled()).thenReturn(true);
ObjectUnderTest objectUnderTest = new ObjectUnderTest(config, logger);
```

Legacy code may still use `setLogger()`. When touching such code, migrate to constructor injection where feasible.

### Capturing and asserting log output

```java
ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
verify(logger, times(1)).info(logCaptor.capture());
List<String> arguments = logCaptor.getAllValues();
assertThat(arguments.get(0), is(equalTo("Init producer.")));
```

### Verifying no interaction

```java
verify(logger, never()).error(anyString());
```

### Verifying with argument matchers

```java
verify(logger).error(contains("fromPrivateCompressed.getPubKeyHash()"));
verify(mockLogger, times(1)).info(eq("Received key: {}"), eq(expectedHex));
```

---

## 13. Platform Assumptions

Tests that require a specific platform conditionally skip using assume classes:

```java
@Test
@OpenCLTest
public void testRoundtripOpenCLProducer_...() {
    new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
    // test body
}
```

Assume classes available:
- `OpenCLPlatformAssume` — OpenCL GPU device required
- `LMDBPlatformAssume` — LMDB native library required
- `PlatformAssume` — generic OS assumptions

---

## 14. Equals / HashCode / ToString Contract Tests

Use `EqualHashCodeToStringTestHelper` for objects with value semantics. Create two independent instances of "A" and two of "B":

```java
// arrange
FooObject a1 = new FooObject(valueForA);
FooObject a2 = new FooObject(valueForA);   // same data, different reference
FooObject b1 = new FooObject(valueForB);
FooObject b2 = new FooObject(valueForB);

// assert — A equals A, B equals B, A not equal to B
new EqualHashCodeToStringTestHelper(a1, a2, b1, b2)
    .assertEqualsHashCodeToStringAIsDifferentToB();

// OR — A equals B (same semantic content)
new EqualHashCodeToStringTestHelper(a1, a2, b1, b2)
    .assertEqualsHashCodeToStringAIsEqualToB();
```

For `toString()` tests that verify default Java object identity format:
```java
assertThat(output, matchesPattern("ClassName@\\p{XDigit}+"));
```

For `toString()` tests that verify structured content:
```java
assertThat(output, is(equalTo("AddressToCoin{hash160=..., coin=100000000, type=P2PKH_OR_P2SH}")));
```

---

## 15. Randomness — Fixed Seeds for Reproducibility

All `Random` instances in tests **must** use a fixed seed:

```java
private final Random random = new Random(1337);
// or
Random random = new Random(42);
```

Never use `new Random()` (unseeded) in tests. Document the seed's significance in a comment when it matters:

```java
/**
 * This random is fine to produce with lower private key bits: 1; 0; 1; 0
 */
private final Random random = new Random(1);
```

---

## 16. Static Address Constants

Use the static address enums and helper classes for test data — never hard-code raw addresses inline:

| Class | Purpose |
|---|---|
| `StaticKey` | A single known private key with all derived forms |
| `TestAddresses42` | A set of addresses derived from seed 42 |
| `TestAddresses1337` | A set of addresses derived from seed 1337 |
| `P2PKH` enum | Known valid P2PKH public addresses with expected hashes |
| `P2SH` enum | Known valid P2SH script hash addresses |
| `P2WPKH` enum | Known valid native SegWit addresses |
| `StaticUnsupportedAddress` enum | Addresses that must be rejected |

---

## 17. LMDB / File System Tests

- Always use `folder.newFolder("lmdb")` from `TemporaryFolder` — never create folders manually.
- Use `TestAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, ...)` to create pre-populated test databases.
- Use `TestAddressesFiles` to provide corresponding address file sets.

```java
TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
TestAddressesFiles testAddresses = new TestAddressesFiles(compressed);
File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, false);
```

---

## 18. OpenCL Tests

### When `@OpenCLTest` + assume IS required

A test must be annotated with `@OpenCLTest` and call `assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable()` (or a similar assume method) as its **first statement** when the test body actually invokes OpenCL API functions — for example:

- `CL.stringFor_*` or any other `CL.*` native call
- `OpenCLBuilder.build()` or any method that loads or queries the OpenCL runtime
- Any code path that triggers native library loading

```java
@OpenCLTest
@Test
public void build_oneOpenCLDevice_returnsPlatformWithDevice() {
    new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
    // test body that calls OpenCL APIs
}
```

Rules:
- Annotate with `@OpenCLTest`.
- Call the assume method as the **first statement** of the test body.
- Do not gate the entire class — only gate individual test methods.

### When `@OpenCLTest` + assume is NOT required

Tests that **only** use JOCL wrapper types (e.g. `cl_device_id`, `cl_context_properties`, `cl_platform_id`) as plain Java objects — without calling any native OpenCL API function — do **not** need `@OpenCLTest` or an assume call. These wrapper classes are ordinary Java objects that can be instantiated and passed around without loading the OpenCL native library.

```java
// No @OpenCLTest needed — cl_context_properties is just a Java object here
@Test
public void constructor_validArguments_returnsPlatform() {
    // arrange
    cl_context_properties properties = new cl_context_properties();
    // ... build list of plain Java objects ...

    // act
    OpenCLPlatform platform = new OpenCLPlatform(0, properties, deviceList);

    // assert
    assertThat(platform.getProperties(), is(equalTo(properties)));
}
```

**Decision rule:** Ask "does this test call any method that ultimately invokes a native OpenCL function?" If yes → `@OpenCLTest` + assume. If no (only Java object construction and list operations) → no annotation needed.

---

## 19. Import Style

Group imports in this order (no blank lines within groups, blank line between groups):
1. Standard Java (`java.*`, `javax.*`)
2. Third-party libraries (alphabetical)
3. Project classes (`net.ladenthin.*`)
4. Static imports (last, alphabetical)

Prefer **specific static imports** over wildcard when used once or twice:
```java
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
```

Use wildcard static import when many matchers are used:
```java
import static org.hamcrest.Matchers.*;
```

---

## 20. Array & Collection Assertions — Avoiding Iteration

### Do NOT use for-loops in assertions

**Problem:** For-loop iteration in assertions reduces readability and makes tests harder to maintain.

```java
// ❌ BAD — uses for-loop to iterate assertions
@Test
public void decodeBytes_resultsAllZeros() {
    byte[] result = decoder.decodeBytes(input, 10);
    for (int i = 0; i < 10; i++) {
        assertThat(result[i], is(equalTo((byte) 0)));
    }
}
```

**Solution:** Compare entire arrays directly using `Arrays.equals()` or array comparison matchers.

```java
// ✅ GOOD — compare entire arrays at once
@Test
public void decodeBytes_resultsAllZeros() {
    byte[] result = decoder.decodeBytes(input, 10);
    byte[] expected = new byte[10];  // automatically all zeros
    assertThat(result, is(expected));
}
```

### Pattern for zero-padded arrays

When testing that an array is padded with zeros, use `Arrays.copyOfRange()` to compare sections:

```java
@Test
public void decodeBytes_shorterInput_leftPaddedWithZeros() {
    // arrange
    byte[] original = {0x01, 0x02, 0x03};
    final int targetLength = 20;
    final int paddingLength = targetLength - original.length;
    byte[] expectedPadding = new byte[paddingLength];

    // act
    byte[] result = decoder.decodeBytes(original, targetLength);

    // assert
    assertThat(Arrays.copyOfRange(result, 0, paddingLength), is(expectedPadding));
    assertThat(Arrays.copyOfRange(result, paddingLength, targetLength), is(original));
}
```

### Avoid magic numbers — use array.length

**Problem:** Hard-coded array lengths make tests fragile and difficult to understand.

```java
// ❌ BAD — magic numbers scattered throughout
byte[] result = decoder.decode(input, 10);
assertThat(result[9], is(equalTo((byte) 0xFF)));  // Why index 9? Why 10?
for (int i = 0; i < 9; i++) {
    assertThat(result[i], is(equalTo((byte) 0)));
}
```

**Solution:** Use variables with intent-revealing names and derive indices from array properties.

```java
// ✅ GOOD — clear naming and derived values
final int targetLength = 10;
byte[] result = decoder.decode(input, targetLength);
assertThat(result[targetLength - 1], is(equalTo((byte) 0xFF)));  // Last element
byte[] expectedPadding = new byte[targetLength - 1];
assertThat(Arrays.copyOfRange(result, 0, targetLength - 1), is(expectedPadding));
```

### Use array.length over hardcoded constants

When creating expected arrays or ranges, always reference `.length`:

```java
// ❌ BAD
byte[] original = {0x12, 0x34};
// ... later in test ...
assertThat(Arrays.copyOfRange(result, 3, 5), is(original));

// ✅ GOOD — derives from actual array length
byte[] original = {0x12, 0x34};
final int targetLength = 5;
final int paddingLength = targetLength - original.length;
// ... later in test ...
assertThat(Arrays.copyOfRange(result, paddingLength, targetLength), is(original));
```

### Reference example: Array comparison

```java
@Test
public void decodeBase36_exactLength_decodesCorrectly() {
    // arrange
    byte[] original = {0x01, 0x02, 0x03, 0x04, 0x05};
    String encoded = new BigInteger(1, original).toString(36);

    // act
    byte[] result = decoder.decodeBase36ToFixedLengthBytes(encoded, original.length);

    // assert — entire array comparison, no loop
    assertThat(result, is(original));
}

@Test
public void decodeBase36_zeroBytes_producesAllZeros() {
    // arrange
    final int targetLength = 20;
    byte[] expected = new byte[targetLength];  // All zeros by default

    // act
    byte[] result = decoder.decodeBase36ToFixedLengthBytes("0", targetLength);

    // assert — compare whole array
    assertThat(result, is(expected));
}

@Test
public void decodeBase36_shorterInput_leftPaddedWithZeros() {
    // arrange
    byte[] original = {0x01, 0x02, 0x03};
    String encoded = new BigInteger(1, original).toString(36);
    final int targetLength = 20;
    final int paddingLength = targetLength - original.length;
    byte[] expectedPadding = new byte[paddingLength];

    // act
    byte[] result = decoder.decodeBase36ToFixedLengthBytes(encoded, targetLength);

    // assert — compare sections without loops
    assertThat(Arrays.copyOfRange(result, 0, paddingLength), is(expectedPadding));
    assertThat(Arrays.copyOfRange(result, paddingLength, targetLength), is(original));
}
```

---

## 22. What NOT To Do

| Anti-pattern | Correct alternative |
|---|---|
| `assertEquals(expected, actual)` | `assertThat(actual, is(equalTo(expected)))` |
| `assertTrue(condition)` | `assertThat(condition, is(true))` |
| `Assert.assertNotNull(x)` | `assertThat(x, is(notNullValue()))` |
| `Objects.requireNonNull(x)` as a guard in tests | `// pre-assert` section with `assertThat(x, is(notNullValue()))` |
| Null-guard inside `// assert` when more assertions follow | Move to a `// pre-assert` section between `// act` and `// assert` |
| `System.out.println(...)` in test | Remove; use logger assertions instead |
| Unseeded `new Random()` | `new Random(fixedSeed)` |
| Hard-coded address strings | Use `StaticKey`, `P2PKH`, etc. |
| Missing `// arrange / act / assert` | Add the section comments always |
| Missing editor fold | Wrap each method group in `<editor-fold>` |
| Non-conforming test name like `testme()` | Rename to `methodName_condition_expectation()` |
| Empty `@Before` method | Remove it |
| `@RunWith(DataProviderRunner.class)` without `@UseDataProvider` | Remove the `@RunWith` |
| For-loop iteration in assertions like `for (int i = ...) assertThat(...)` | Compare entire array at once: `assertThat(result, is(expected))` |
| Magic numbers like `result[9]` or `result.length - 1` | Use `final int` constants: `result[targetLength - 1]` |

---

## 23. Test Anatomy — Complete Reference Example

```java
// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class ExampleTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));

    // <editor-fold defaultstate="collapsed" desc="someMethod">
    @Test
    public void someMethod_validInputGiven_returnsExpectedResult() {
        // arrange
        Example sut = new Example();

        // act
        String result = sut.someMethod("validInput");

        // assert
        assertThat(result, is(equalTo("expectedResult")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void someMethod_nullGiven_throwsException() {
        // arrange
        Example sut = new Example();

        // act
        sut.someMethod(null);
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_MY_CASES, location = CommonDataProvider.class)
    public void someMethod_parameterizedInput_returnsExpectedResult(String input, String expected) {
        // arrange
        Example sut = new Example();

        // act
        String result = sut.someMethod(input);

        // assert
        assertThat(result, is(equalTo(expected)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="toString">
    @ToStringTest
    @Test
    public void toString_whenCalled_containsClassNameAndIdentityHash() {
        // arrange
        Example sut = new Example();

        // act
        String output = sut.toString();

        // assert
        assertThat(output, not(emptyOrNullString()));
        assertThat(output, matchesPattern("Example@\\p{XDigit}+"));
    }
    // </editor-fold>
}
```
