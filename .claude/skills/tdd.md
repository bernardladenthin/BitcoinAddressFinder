# TDD — Test-Driven Development for BitcoinAddressFinder

You are working on **BitcoinAddressFinder** (group `net.ladenthin`, Java 21, Maven).
Follow the **Red → Green → Refactor** cycle rigorously. Every new behaviour must be
covered by a failing test *before* the production code is written.

---

## Workflow

### 1 — Red (failing test first)
Write one test that precisely describes the next desired behaviour. The test must
compile but **must fail** when run. Do not write any production code yet.

### 2 — Green (minimum production code)
Write the smallest change to production code that makes the failing test pass.
Do not add code that is not driven by a test.

### 3 — Refactor
Improve the implementation and the test code without changing observable behaviour.
All tests must stay green.

Repeat for each behaviour increment.

---

## Project-Specific Test Conventions (authoritative: `TEST_WRITING_GUIDE.md`)

### File header — mandatory on every `.java` test file
```java
// @formatter:off
/**
 * Copyright <YEAR> Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder<.sub.package>;
```
- Use the **actual creation year** of the file, not today's date.
- `// @formatter:off/on` wraps **only** the license block.

### Test framework stack
| Concern | Mandatory choice |
|---|---|
| Runner | JUnit 4 (`@Test`, `@Before`, `@Rule`) |
| Parameterized | `@RunWith(DataProviderRunner.class)` + `@UseDataProvider` from `com.tngtech.java.junit.dataprovider` — **only** when the class actually has a `@UseDataProvider`; omit `@RunWith` otherwise |
| Assertions | Hamcrest only — `assertThat(actual, is(equalTo(expected)))` |
| Mocking | Mockito — `mock()`, `when()`, `verify()`, `ArgumentCaptor` |
| Temp files | `@Rule public TemporaryFolder folder = new TemporaryFolder()` |

**Never use** `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull`, TestNG, or JUnit 5.

### Class layout
```java
@RunWith(DataProviderRunner.class)  // only when @UseDataProvider is present
public class FooTest {

    // shared immutable fields
    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    // mocks that need a fresh instance per test — declare here, init in @Before
    private Logger mockLogger;

    @Before
    public void setUp() {
        mockLogger = mock(Logger.class);
    }
    // Omit @Before entirely if it does no meaningful work.
```

### Test method naming — all three segments required
Pattern: **`methodUnderTest_inputOrCondition_expectedBehavior`**
```
fromLine_addressLineIsEmpty_returnNull
createECKey_testUncompressed_returnsCorrectHash
getMaxPrivateKeyForBatchSize_bitSizeNegative_throwsException
interrupt_keysQueueNotEmpty_waitedInternallyForTheDuration
```
- camelCase within each segment, underscore between segments.
- Exception tests end with `_throwsException` or `_exceptionThrown`.
- No-op/smoke: `_noExceptionThrown`.
- `toString` tests: `toString_whenCalled_containsClassNameAndIdentityHash`.

### Test body — strict AAA with section comments
```java
@Test
public void methodName_condition_expected() {
    // arrange
    Foo sut = new Foo(42);

    // act
    String result = sut.bar();

    // assert
    assertThat(result, is(equalTo("expected")));
}
```
`// pre-assert` may appear:
- **Before `// act`** — to verify a precondition or input invariant.
- **Between `// act` and `// assert`** — null-guard before accessing fields of the result.

Never use `Objects.requireNonNull` as a null guard in tests; use a `// pre-assert` with
`assertThat(x, is(notNullValue()))` instead.

### Editor folds — mandatory grouping
```java
// <editor-fold defaultstate="collapsed" desc="methodName">
@Test
public void methodName_caseA_resultA() { ... }

@Test
public void methodName_caseB_resultB() { ... }
// </editor-fold>
```
- One fold per method/feature under test.
- `defaultstate="collapsed"` is required on every fold.
- Different methods → different folds; same method → same fold.

### Assertion style reference
```java
assertThat(result, is(equalTo(expected)));
assertThat(result, is(nullValue()));
assertThat(result, is(notNullValue()));
assertThat(flag, is(true));
assertThat(flag, is(false));
assertThat(result, is(not(equalTo(unexpected))));
assertThat(message, containsString("substring"));
assertThat(message, matchesPattern("Regex\\d+"));
assertThat(list, hasSize(3));
assertThat(list, is(empty()));
assertThat(index, is(lessThan(colonIndex)));
assertThat(obj, instanceOf(KeyProducerJavaRandom.class));
```
Imports:
```java
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;   // wildcard when many matchers; specific otherwise
```

### Exception testing
```java
// simple — no message check needed
@Test(expected = IllegalArgumentException.class)
public void foo_nullInput_throwsException() {
    // act
    sut.foo(null);
}

// with message assertion — use try/catch in the @Test body
```

### Data providers — always in CommonDataProvider
```java
// In CommonDataProvider:
public final static String DATA_PROVIDER_MY_CASES = "myCases";

/** For {@link FooTest}. */
@DataProvider
public static Object[][] myCases() {
    return new Object[][] {
        { inputA, expectedA },
        { inputB, expectedB },
    };
}

// In FooTest:
@Test
@UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_MY_CASES, location = CommonDataProvider.class)
public void foo_input_expected(TypeA input, TypeB expected) { ... }
```

### Marker annotations
| Annotation | When |
|---|---|
| `@AwaitTimeTest` | Test uses `ExecutorService.awaitTermination` or `Thread.sleep` timing |
| `@ToStringTest` | Test validates a `toString()` implementation |
| `@OpenCLTest` | Test calls a native OpenCL function (add `assumeOpenCL...` as first statement) |

### Randomness — always fixed seeds
```java
private final Random random = new Random(1337);  // never new Random()
```

### Static address constants — never hard-code raw strings
Use: `StaticKey`, `TestAddresses42`, `TestAddresses1337`, `P2PKH` enum, `P2SH` enum,
`P2WPKH` enum, `StaticUnsupportedAddress` enum.

### LMDB / file-system tests
```java
File lmdbFolderPath = new TestAddressesLMDB()
    .createTestLMDB(folder, new TestAddressesFiles(compressed), useStaticAmount, false);
```
Always use `folder.newFolder(...)` — never create folders manually.

### Logger mocking
```java
Logger logger = mock(Logger.class);
when(logger.isTraceEnabled()).thenReturn(true);
objectUnderTest.setLogger(logger);

ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
verify(logger, times(1)).info(captor.capture());
assertThat(captor.getAllValues().get(0), is(equalTo("expected message")));
verify(logger, never()).error(anyString());
```

### Null safety in production code
- Annotate nullable returns and parameters with `@Nullable` from `org.jspecify.annotations`.
- `@NonNull` is the default; omit the annotation for non-null.
- NullAway runs at compile time and is error-level — unannotated code that could be null
  causes a build failure.

### Import order
1. `java.*` / `javax.*`
2. Third-party (alphabetical)
3. `net.ladenthin.*`
4. Static imports (last, alphabetical)

---

## Checklist before marking a TDD cycle complete

- [ ] At least one test was written and failed **before** the production code was written.
- [ ] Every production behaviour is covered by at least one test.
- [ ] All tests pass (`./mvnw test`).
- [ ] Every test class has the Apache 2.0 license header wrapped in `@formatter:off/on`.
- [ ] Test method names follow the `method_condition_expected` pattern.
- [ ] `// arrange`, `// act`, `// assert` comments are present in every test body.
- [ ] All tests for the same method are inside a single `<editor-fold>` block.
- [ ] No `assertEquals` / `assertTrue` / `assertFalse` / `assertNotNull` anywhere.
- [ ] All `Random` instances use a fixed seed.
- [ ] No hard-coded raw address strings — use static address constants.
- [ ] Data providers are added to `CommonDataProvider`, not inlined.
- [ ] `@RunWith(DataProviderRunner.class)` is present **only** when `@UseDataProvider` is used.
- [ ] Empty `@Before` methods are removed.
- [ ] All new production-code nullable fields/returns are annotated with `@Nullable`.
- [ ] `./mvnw compile` succeeds with zero NullAway errors.
