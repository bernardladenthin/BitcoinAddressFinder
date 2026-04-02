---
name: java-tdd-guide
description: Bernard Ladenthin's personal Java Test-Driven Development skill — version 1.0.0 — Red → Green → Refactor workflow with project-independent conventions
---

# Java TDD Skill — Test-Driven Development

**Author:** Bernard Ladenthin  
**Version:** 1.0.0  
**License:** Apache 2.0  

This is a personal, reusable Java TDD guide for use across multiple projects. All examples are generic and project-independent. Project-specific patterns and constants are documented separately in each project's CLAUDE.md.

---

## TDD Workflow — Red → Green → Refactor

Follow the **Red → Green → Refactor** cycle rigorously. Every new behaviour must be covered by a failing test *before* the production code is written.

### 1 — Red (failing test first)
Write one test that precisely describes the next desired behaviour. The test must compile but **must fail** when run. Do not write any production code yet.

### 2 — Green (minimum production code)
Write the smallest change to production code that makes the failing test pass. Do not add code that is not driven by a test.

### 3 — Refactor
Improve the implementation and the test code without changing observable behaviour. All tests must stay green.

Repeat for each behaviour increment.

---

## Test File Structure

### File Header — Apache 2.0 License

Every test file **must** start with the formatter-off block enclosing the Apache 2.0 license header:

```java
// @formatter:off
/**
 * Copyright <YEAR> <Author> <email>
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
 */
// @formatter:on
package com.example.foo;
```

- The `// @formatter:off` / `// @formatter:on` pair wraps **only** the license block.
- The year must match the file creation year (not the current year).

---

## Test Framework Stack

| Concern | Mandatory choice |
|---|---|
| Runner | JUnit 4 (`@Test`, `@Before`, `@Rule`) |
| Parameterized | `@RunWith(DataProviderRunner.class)` + `@UseDataProvider` (only when the class has at least one `@UseDataProvider` method) |
| Assertions | Hamcrest only — `assertThat(actual, is(equalTo(expected)))` |
| Mocking | Mockito — `mock()`, `when()`, `verify()`, `ArgumentCaptor` |
| Temp files | `@Rule public TemporaryFolder folder = new TemporaryFolder()` |

**Never use:**
- `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull` from `org.junit.Assert`
- TestNG or JUnit 5

---

## Class Layout

```java
@RunWith(DataProviderRunner.class)   // only when @UseDataProvider is present
public class FooTest {

    // shared, constructed-once immutable fields
    private final Bar bar = new Bar();
    private final BazHelper helper = new BazHelper();

    // mocks that must be fresh per test — declare field here, initialize in @Before
    private Logger mockLogger;

    @Before
    public void setUp() {
        mockLogger = mock(Logger.class);
    }
    // Omit @Before entirely when it does no meaningful work.
```

Omit `@RunWith` if no data providers are used. Omit empty `@Before` methods.

---

## Test Method Naming

Pattern: **`methodUnderTest_inputOrCondition_expectedBehavior`**

```
foo_emptyInput_returnsNull
bar_validArgumentsGiven_returnsExpected
baz_negativeValue_throwsException
interrupt_queueNotEmpty_waitedForDuration
toString_whenCalled_containsClassNameAndIdentityHash
```

**Rules:**
- All three segments are **required**, separated by underscores.
- Use camelCase within each segment.
- Exception tests end with `_throwsException` or `_exceptionThrown`.
- No-op/smoke tests: `_noExceptionThrown`.
- `toString` tests: describe exact content (identity hash or structured format).
- Logging assertions: include `_logged` or `_logsError`.

---

## Test Body — AAA Structure

Every test body **must** follow Arrange / Act / Assert with explicit section comments:

```java
@Test
public void methodName_conditionGiven_expectedResult() {
    // arrange
    Foo sut = new Foo(42);

    // act
    String result = sut.bar();

    // assert
    assertThat(result, is(equalTo("expected")));
}
```

### `// pre-assert` — two valid positions

**1. Before `// act`** — assert a precondition or input invariant:

```java
// arrange
String input = "test-value";

// pre-assert
assertThat(input, not(emptyString()));

// act
String result = sut.process(input);

// assert
assertThat(result, is(equalTo("expected")));
```

**2. Between `// act` and `// assert`** — null-guard before accessing result fields:

```java
// act
FooResult result = sut.compute();

// pre-assert
assertThat(result, is(notNullValue()));

// assert
assertThat(result.getValue(), is(equalTo(expected)));
```

**Rules:**
- Do **not** use `Objects.requireNonNull(...)` in tests; use `// pre-assert` with `assertThat(x, is(notNullValue()))` instead.
- The `// arrange` section may be omitted only when there is genuinely nothing to arrange.
- Keep the act to a **single method call** whenever possible.

---

## Editor Folds — Mandatory Grouping

Tests within a class **must** be grouped using editor fold regions, one fold per method/feature under test:

```java
// <editor-fold defaultstate="collapsed" desc="methodName">
@Test
public void methodName_caseA_resultA() { ... }

@Test
public void methodName_caseB_resultB() { ... }
// </editor-fold>
```

**Rules:**
- The `desc` attribute equals the method name (or a short feature label).
- `defaultstate="collapsed"` is **mandatory** on every fold.
- All tests for the same method go inside a single fold.
- Tests for different methods **must** be in different folds.
- Order folds logically (simple cases first, edge cases and exceptions last).

---

## Assertions — Hamcrest Style

All assertions use Hamcrest `assertThat`:

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
assertThat(obj, instanceOf(Foo.class));
```

**Imports:**
```java
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;   // when many matchers are used
// or specific imports when only a few are needed
```

---

## Exception Testing

### Pattern A — Simple (no message check)

```java
@Test(expected = IllegalArgumentException.class)
public void foo_nullInput_throwsException() {
    // act
    sut.foo(null);
}
```

### Pattern B — With message verification (try/catch/fail)

```java
@Test
public void foo_invalidInput_throwsException() {
    try {
        // act
        sut.foo("invalid");
        fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
        // assert
        assertThat(e.getMessage(), containsString("expected error text"));
    }
}
```

Import for `fail()`:
```java
import static org.junit.Assert.fail;
```

---

## Data Providers (Parameterized Tests)

### Centralized Pattern

All data providers belong in a centralized `CommonDataProvider` class. Each provider follows this pattern:

```java
// 1. Constant for the provider name
public final static String DATA_PROVIDER_MY_CASES = "myCases";

// 2. Javadoc linking to which test it serves
/** For {@link FooTest}. */
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
    public void foo_inputGiven_returnsExpected(String input, String expected) {
        // arrange
        Foo sut = new Foo();

        // act
        String result = sut.bar(input);

        // assert
        assertThat(result, is(equalTo(expected)));
    }
}
```

### Enum-based providers

```java
@DataProvider
public static Object[][] allEnumValues() {
    return transformFlatToObjectArrayArray(MyEnum.values());
}
```

### Cartesian product providers

```java
@DataProvider
public static Object[][] typeAndSize() {
    return mergeMany(types(), sizes());
}
```

---

## Named Constants — DRY, No Magic Literals

Every semantic value must be a named `public static final` or `private static final` constant with Javadoc.

**Rules:**
- Every string, number, or flag literal that carries meaning **must** be a named constant.
- Constants belong at the class level, before constructors and methods.
- Name constants by their **meaning or role**, not the raw value.
- Each constant must have **Javadoc** explaining what it represents and why.
- Derived values must be computed from source constants, never duplicated.
- Radix values (`16`, `10`, `2`) should be referenced through helper constants, never as bare integers.

**Bad:**
```java
return new BigInteger("FFFFFFFFFFFFFFFF", 16);
if (batchSize > 256) { ... }
```

**Good:**
```java
/**
 * The maximum valid size for batch processing.
 * Chosen based on performance testing with typical workloads.
 */
public static final int MAX_BATCH_SIZE = 256;

/**
 * The hex value for all 64 bits set.
 * Derived from {@link #MAX_BATCH_SIZE} — do not duplicate the literal.
 */
public static final String MAX_VALUE_HEX = "FF".repeat(8);

if (batchSize > MAX_BATCH_SIZE) { ... }
```

---

## Logger Injection — Constructor Over Setter

When a class uses an SLF4J `Logger` and tests need to inject a mock logger, prefer **constructor-based injection**.

**Pattern — two constructors:**

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

**Rules:**
- The `logger` field should be `private final`.
- The production constructor delegates to the test constructor (or vice versa) — never duplicate initialization.
- The `@VisibleForTesting` constructor has package-private visibility.
- A `setLogger` method is a last resort — only if constructor injection is infeasible.

**Test usage:**
```java
Logger mockLogger = mock(Logger.class);
MyService service = new MyService(config, mockLogger);
```

---

## Null Safety — JSpecify Annotations

Use **JSpecify** `@Nullable` annotation for optional values; `@NonNull` is the default (no annotation needed).

```java
import org.jspecify.annotations.Nullable;

public @Nullable String getOptional() { return null; }

// Array null annotations — place between type and brackets
private byte @Nullable [] buffer;           // array itself may be null
public byte @NonNull [] getBuffer() { }     // array is guaranteed non-null
```

**Compiler enforcement:**
A static checker (e.g., NullAway) enforces null safety at compile time. Missing `@Nullable` on a nullable return or field causes a **compilation failure**.

---

## @VisibleForTesting Annotation

Mark package-private or protected members that are only exposed for testing:

```java
@VisibleForTesting
static Duration AWAIT_DURATION = Duration.ofSeconds(20);

@VisibleForTesting
final ExecutorService executor = Executors.newFixedThreadPool(4);
```

Tests may modify `@VisibleForTesting` static fields to shorten wait times or adjust test-specific behaviour.

---

## Mocking & Logger Verification

### Capturing and asserting log output

```java
ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
verify(logger, times(1)).info(captor.capture());
List<String> arguments = captor.getAllValues();
assertThat(arguments.get(0), is(equalTo("Initialized.")));
```

### Verifying no interaction

```java
verify(logger, never()).error(anyString());
```

### Verifying with argument matchers

```java
verify(logger).error(contains("expectedSubstring"));
verify(mockLogger, times(1)).info(eq("Message"), eq(expectedValue));
```

**Imports:**
```java
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
```

---

## Randomness — Always Fixed Seeds

All `Random` instances in tests **must** use a fixed seed:

```java
private final Random random = new Random(1337);
```

Never use `new Random()` (unseeded). Document the seed's significance when it matters:

```java
/** This random produces bits: 1, 0, 1, 0 — useful for testing boundary cases. */
private final Random random = new Random(1);
```

---

## TemporaryFolder / File System Tests

Use `TemporaryFolder` for tests that create files and directories:

```java
@Rule
public TemporaryFolder folder = new TemporaryFolder();

@Test
public void testFileHandling() throws IOException {
    // arrange
    File tempFile = folder.newFile("data.txt");
    File subdir = folder.newFolder("output");
    
    // use Files NIO for writing
    Files.writeString(tempFile.toPath(), "content");
    
    // act / assert
    // ...
}
```

**Rules:**
- Always use `folder.newFile(...)` and `folder.newFolder(...)` — never create manually.
- Cleanup is automatic when the test completes.

---

## Records & Immutability

Use Java `record` for immutable value objects:

```java
public record MyValue(@NonNull String name, int count) {
    // compact constructor for validation
    public MyValue {
        Objects.requireNonNull(name);
    }
}
```

**Rules:**
- `Objects.requireNonNull()` is **valid in production code** (not in tests).
- Mark immutable classes with `@Immutable` annotation when appropriate.
- For records containing mutable third-party types, suppress the Error Prone warning:

```java
@Immutable
public record Container(
    @SuppressWarnings("Immutable") MutableObject obj,
    @NonNull String name
) { }
```

---

## Concurrency Conventions

```java
// Thread-safe counters
private final AtomicLong hits = new AtomicLong();
private final AtomicInteger stateCounter = new AtomicInteger();

// Work queue
private final LinkedBlockingQueue<byte[]> workQueue;

// Thread pool — never raw Thread
private final ExecutorService executor = Executors.newFixedThreadPool(4);

// Shutdown synchronisation
private final CountDownLatch shutdownLatch = new CountDownLatch(1);
```

### Async/Concurrent Tests

Use `CountDownLatch` + `ExecutorService` + `Future` for coordinating async tests:

```java
@Test
public void asyncOperation_serverSendsData_clientReceives() throws Exception {
    // arrange
    int port = findFreePort();
    ExecutorService executorService = Executors.newCachedThreadPool();
    CountDownLatch serverStarted = new CountDownLatch(1);

    Future<Void> serverFuture = executorService.submit(() -> {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverStarted.countDown();                // signal ready
            try (Socket client = serverSocket.accept();
                 DataOutputStream out = new DataOutputStream(client.getOutputStream())) {
                out.write(data);
            }
        }
        return null;
    });

    serverStarted.await();                            // wait for server to be ready

    // act
    String result = connectAndFetch(port);

    // assert
    assertThat(result, is(equalTo(expected)));

    serverFuture.get(5, TimeUnit.SECONDS);            // ensure server completed cleanly
    executorService.shutdown();
}

private static int findFreePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
        return s.getLocalPort();
    }
}
```

---

## Equals / HashCode / ToString Contract Tests

Use a generic contract test helper with four instances (two for value A, two for value B):

```java
// arrange
Foo a1 = new Foo(valueA);
Foo a2 = new Foo(valueA);  // same data, different reference
Foo b1 = new Foo(valueB);
Foo b2 = new Foo(valueB);

// assert — A != B
EqualHashCodeToStringTestHelper helper = new EqualHashCodeToStringTestHelper(a1, a2, b1, b2);
helper.assertEqualsHashCodeToStringAIsDifferentToB();

// OR — A == B (same semantic content)
helper.assertEqualsHashCodeToStringAIsEqualToB();
```

For `toString()` tests verifying default object identity format:
```java
assertThat(output, matchesPattern("ClassName@\\p{XDigit}+"));
```

For `toString()` tests verifying structured content:
```java
assertThat(output, is(equalTo("Foo{name=bar, count=42}")));
```

---

## Import Style

Group imports in this order (no blank lines within groups, blank line between groups):

1. Standard Java (`java.*`, `javax.*`)
2. Third-party libraries (alphabetical)
3. Project classes (`com.example.*`)
4. Static imports (last, alphabetical)

```java
import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.mockito.Mock;

import com.example.foo.Foo;
import com.example.foo.Bar;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
```

Prefer **specific static imports** over wildcard when used 1–2 times. Use wildcard for Hamcrest matchers when many are used:
```java
import static org.hamcrest.Matchers.*;
```

---

## Array & Collection Assertions

### Do NOT use for-loops in assertions

**Problem:** For-loop iteration in assertions reduces readability.

```java
// ❌ BAD — uses for-loop
for (int i = 0; i < expected.length; i++) {
    assertThat(result[i], is(equalTo(expected[i])));
}
```

**Solution:** Compare entire arrays directly.

```java
// ✅ GOOD — compare whole array
assertThat(result, is(expected));
```

### Exception: iterating over all enum values is allowed

When verifying behaviour for **every value of an enum**, iterating via `EnumType.values()` is preferred. It ensures new enum constants are automatically covered:

```java
@Test
public void process_allEnumValues_succeeds() {
    for (MyEnum value : MyEnum.values()) {
        String result = sut.process(value);
        assertThat(result, not(emptyString()));
    }
}
```

### Pattern for zero-padded arrays

```java
@Test
public void decode_shorterInput_leftPaddedWithZeros() {
    // arrange
    byte[] original = {0x01, 0x02, 0x03};
    final int targetLength = 20;
    final int paddingLength = targetLength - original.length;
    byte[] expectedPadding = new byte[paddingLength];

    // act
    byte[] result = decoder.decode(original, targetLength);

    // assert
    assertThat(Arrays.copyOfRange(result, 0, paddingLength), is(expectedPadding));
    assertThat(Arrays.copyOfRange(result, paddingLength, targetLength), is(original));
}
```

---

## Constants Within a Fold (DRY)

When the same literal appears in **two or more tests in the same fold**, extract it as a `private static final` constant:

```java
// ✅ GOOD — one definition, both variants derived from it
private static final String CUSTOM_HEX = "FF";

sut.value = CUSTOM_HEX.toUpperCase();  // "FF"
sut.value = CUSTOM_HEX.toLowerCase();  // "ff"
assertThat(result, is(equalTo(new BigInteger(CUSTOM_HEX, 16))));
```

```java
// ❌ BAD — same literal repeated
sut.value = "FF";
sut.value = "ff";
assertThat(result, is(equalTo(BigInteger.valueOf(255))));
```

**Rule:** Constants belong to their fold. Do **not** share a constant between different folds even when values coincide — tests for different methods should remain logically independent.

---

## Preserving Existing Comments

When modifying existing test code (fixing bugs, applying guide compliance):

- **Keep all existing inline comments** that are correct and descriptive.
- **Only remove a comment** if it is factually wrong, misleading, or describes deleted code.
- **Add new comments** where added code is not self-explanatory.

Example — correct preservation:
```java
// arrange
String address = createAddress();

// Server socket binds        ← existing comment preserved
ServerSocket socket = new ServerSocket(port);

// act
String result = sut.process(address);

// assert
assertThat(result, not(emptyString()));
```

**Goal:** Minimize the diff to only lines that actually need changing.

---

## Anti-Patterns — What NOT To Do

| Anti-pattern | Correct alternative |
|---|---|
| `assertEquals(expected, actual)` | `assertThat(actual, is(equalTo(expected)))` |
| `assertTrue(condition)` | `assertThat(condition, is(true))` |
| `Assert.assertNotNull(x)` | `assertThat(x, is(notNullValue()))` |
| `Objects.requireNonNull(x)` as guard in test | `// pre-assert` with `assertThat(x, is(notNullValue()))` |
| Unseeded `new Random()` | `new Random(fixedSeed)` |
| Hard-coded address/constant strings | Use project-specific static constants |
| Missing `// arrange / act / assert` | Add the section comments always |
| Missing editor fold | Wrap each method group in `<editor-fold>` |
| Non-conforming test name like `testme()` | Rename to `methodName_condition_expectation()` |
| Empty `@Before` method | Remove it entirely |
| `@RunWith(DataProviderRunner.class)` without `@UseDataProvider` | Remove the `@RunWith` |
| For-loop iteration in assertions | Compare entire array at once — **exception:** `for (MyEnum v : MyEnum.values())` is allowed |
| Magic numbers like `result[9]` | Use `final int` constants: `result[targetLength - 1]` |
| Removing existing correct comments during fixes | Preserve comments; only remove factually wrong ones |

---

## Test Anatomy — Complete Reference Example

```java
// @formatter:off
/**
 * Copyright 2025 Your Name your.name@example.com
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
 */
// @formatter:on
package com.example.foo;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(DataProviderRunner.class)
public class FooTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Bar bar;

    @Before
    public void setUp() {
        bar = new Bar();
    }

    // <editor-fold defaultstate="collapsed" desc="someMethod">
    @Test
    public void someMethod_validInputGiven_returnsExpectedResult() {
        // arrange
        Foo sut = new Foo();

        // act
        String result = sut.someMethod("validInput");

        // assert
        assertThat(result, is(equalTo("expectedResult")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void someMethod_nullGiven_throwsException() {
        // arrange
        Foo sut = new Foo();

        // act
        sut.someMethod(null);
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_MY_CASES, location = CommonDataProvider.class)
    public void someMethod_parameterizedInput_returnsExpected(String input, String expected) {
        // arrange
        Foo sut = new Foo();

        // act
        String result = sut.someMethod(input);

        // assert
        assertThat(result, is(equalTo(expected)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="toString">
    @Test
    public void toString_whenCalled_containsClassNameAndIdentityHash() {
        // arrange
        Foo sut = new Foo();

        // act
        String output = sut.toString();

        // assert
        assertThat(output, not(emptyOrNullString()));
        assertThat(output, matchesPattern("Foo@\\p{XDigit}+"));
    }
    // </editor-fold>
}
```

---

## Completeness Checklist

Before submitting code:

- [ ] At least one test was written and failed **before** the production code was written.
- [ ] Every production behaviour is covered by at least one test.
- [ ] All tests pass: `./mvnw test` (or equivalent for your project).
- [ ] Compilation is clean (no null-safety errors).
- [ ] Every test class has the Apache 2.0 license header wrapped in `@formatter:off/on`.
- [ ] Test method names follow the `method_condition_expected` three-segment pattern.
- [ ] Every test body has `// arrange`, `// act`, `// assert` comments.
- [ ] All tests for the same method/feature are inside a single `<editor-fold defaultstate="collapsed">` block.
- [ ] No `assertEquals` / `assertTrue` / `assertFalse` / `assertNotNull` anywhere.
- [ ] Exception tests with message assertions use `try { ...; fail(...); } catch`.
- [ ] All `Random` instances use a fixed seed.
- [ ] Data providers are added to `CommonDataProvider` (or project equivalent), not inlined in test classes.
- [ ] `@RunWith(DataProviderRunner.class)` is present **only** when `@UseDataProvider` is used.
- [ ] Empty `@Before` methods are removed.
- [ ] All nullable fields/returns in new production code are annotated with `@Nullable`.
- [ ] Array nullable annotations follow the `byte @Nullable []` placement convention.
- [ ] Logger in new production classes is `private final` with constructor injection (or setter as last resort).
- [ ] `@VisibleForTesting` is applied to any member exposed solely for tests.
- [ ] Async tests use `CountDownLatch` + `ExecutorService` + `Future`; no raw `Thread` or polling with `Thread.sleep`.
- [ ] Async socket tests use `findFreePort()` and project-specific timeout constants — no magic port numbers.
- [ ] Nested concrete implementations of abstract classes are private static inner classes inside the test class.
- [ ] `Objects.requireNonNull()` is used only in production code, never in tests.
- [ ] Multi-line expected strings use Java text blocks (`""" ... """`).
- [ ] Records with mutable third-party fields use `@SuppressWarnings("Immutable")` on the specific field.
- [ ] Behaviour injection in production constructors uses functional interfaces (`Consumer<T>`, `Function<T,R>`) rather than subclassing.
- [ ] Existing correct inline comments in modified test code are preserved — only removed if factually wrong or describing deleted code.

---

## Project-Specific Extensions

Each project may define additional conventions beyond this generic guide. Refer to your project's CLAUDE.md or supplementary guide files for:

- Custom marker annotations (e.g., `@OpenCLTest`, `@ToStringTest`, `@AwaitTimeTest`)
- Static test data constants (e.g., known addresses, keys, fixtures)
- Project-specific helper classes and test utilities
- Database or library-specific test patterns (LMDB, OpenCL, WebSocket, ZMQ, etc.)
- Configuration POJO naming conventions (e.g., C-prefix)
- Custom domain exceptions
