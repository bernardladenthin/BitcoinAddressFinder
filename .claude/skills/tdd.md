---
name: tdd
description: Test-Driven Development workflow for BitcoinAddressFinder with Red → Green → Refactor cycle and project-specific test conventions
---

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

### File header — mandatory on every `.java` file (both test and production)
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
package net.ladenthin.bitcoinaddressfinder[.sub.package];
```
- Use the **file creation year**, not the current year.
- `// @formatter:off/on` wraps **only** the license block — nothing else.

---

## Test File Conventions

### Test framework stack
| Concern | Mandatory choice |
|---|---|
| Runner | JUnit 4 (`@Test`, `@Before`, `@Rule`) |
| Parameterized | `@RunWith(DataProviderRunner.class)` + `@UseDataProvider` — present **only** when the class has at least one `@UseDataProvider` method; omit `@RunWith` otherwise |
| Assertions | Hamcrest only — `assertThat(actual, is(equalTo(expected)))` |
| Mocking | Mockito — `mock()`, `when()`, `verify()`, `ArgumentCaptor` |
| Temp files | `@Rule public TemporaryFolder folder = new TemporaryFolder()` |

**Never use** `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull`, TestNG, or JUnit 5.

### Class layout
```java
@RunWith(DataProviderRunner.class)   // only when @UseDataProvider is present in this class
public class FooTest {

    // shared, constructed-once immutable fields
    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    // mocks that must be fresh per test — declare field here, initialize in @Before
    private Logger mockLogger;

    @Before
    public void setUp() {
        mockLogger = mock(Logger.class);
    }
    // Omit @Before entirely when it does no meaningful work.
```

### Test method naming — all three segments required
Pattern: **`methodUnderTest_inputOrCondition_expectedBehavior`**
```
fromLine_addressLineIsEmpty_returnNull
createECKey_testUncompressed_returnsCorrectHash
getMaxPrivateKeyForBatchSize_bitSizeNegative_throwsException
interrupt_keysQueueNotEmpty_waitedInternallyForTheDuration
```
- camelCase within each segment, underscore **only** between segments.
- Exception tests end with `_throwsException` or `_exceptionThrown`.
- No-op/smoke tests: `_noExceptionThrown`.
- `toString` tests: `toString_whenCalled_containsClassNameAndIdentityHash`.
- Logging assertions: include `_logsError`, `_logged`, or `_containsClassNameAndIdentityHash`.

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

`// pre-assert` may appear in **two** positions only:

**1. Before `// act`** — assert a precondition or input invariant:
```java
// arrange
P2PKH address = P2PKH.BitcoinCashWithPrefix;

// pre-assert
assertThat(address.getPublicAddress(), startsWith(AddressTxtLine.BITCOIN_CASH_PREFIX));

// act
byte[] hash160 = AddressTxtLine.extractPKHFromBitcoinCashAddress(address.getPublicAddress());
```

**2. Between `// act` and `// assert`** — null-guard before accessing result fields:
```java
// act
AddressToCoin result = new AddressTxtLine().fromLine(input, keyUtility);

// pre-assert
assertThat(result, is(notNullValue()));

// assert
assertThat(result.hash160(), is(equalTo(expectedHash)));
```

Never use `Objects.requireNonNull` as a null guard in tests — use `// pre-assert` with
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
- `defaultstate="collapsed"` is **required** on every fold.
- Different methods → different folds; same method → single fold.
- Logical order: simple cases first, edge cases and exceptions last.

### Assertion style reference
```java
// equality
assertThat(result, is(equalTo(expected)));

// null / not-null
assertThat(result, is(nullValue()));
assertThat(result, is(notNullValue()));

// booleans
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

// long counters (AtomicLong fields exposed via .get())
assertThat(consumerJava.hits.get(), is(equalTo(1L)));
```

**Imports:**
```java
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;          // wildcard when many matchers are used
// or specific imports when only a few are needed:
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
```

### Exception testing

**Pattern A — simple, no message check:**
```java
@Test(expected = IllegalArgumentException.class)
public void foo_nullInput_throwsException() {
    // act
    sut.foo(null);
}
```

**Pattern B — with message assertion (try/catch + fail):**
```java
@Test
public void fromLine_addressLineIsEmpty_throwsAddressFormatNotAcceptedException() {
    try {
        // act
        new AddressTxtLine().fromLine("", keyUtility);
        fail("Expected AddressFormatNotAcceptedException");
    } catch (AddressFormatNotAcceptedException e) {
        // assert
        assertThat(e.getMessage(), containsString(AddressTxtLine.REASON_EMPTY));
    }
}
```
Import for `fail()`:
```java
import static org.junit.Assert.fail;
```

### Data providers — always in CommonDataProvider
```java
// 1. Add a name constant to CommonDataProvider:
public final static String DATA_PROVIDER_MY_CASES = "myCases";

// 2. Add the @DataProvider method (Javadoc links to the test that uses it):
/** For {@link FooTest}. */
@DataProvider
public static Object[][] myCases() {
    return new Object[][] {
        { inputA, expectedA },
        { inputB, expectedB },
    };
}

// 3. Consume in FooTest (requires @RunWith on the class):
@Test
@UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_MY_CASES, location = CommonDataProvider.class)
public void foo_inputGiven_returnsExpected(TypeA input, TypeB expected) {
    // arrange
    Foo sut = new Foo();

    // act
    TypeB result = sut.foo(input);

    // assert
    assertThat(result, is(equalTo(expected)));
}
```

Enum-based providers (iterate all enum values):
```java
@DataProvider
public static Object[][] staticP2PKHAddresses() {
    return transformFlatToObjectArrayArray(P2PKH.values());
}
```

Cartesian product providers:
```java
@DataProvider
public static Object[][] keyProducerTypeAndBitSize() {
    return mergeMany(keyProducerTypes(), bitSizesAtMostMax());
}
```

### Marker annotations
| Annotation | When |
|---|---|
| `@AwaitTimeTest` | Test uses timing assertions (`Duration.ofMillis`, `greaterThan`, etc.) |
| `@ToStringTest` | Test validates a `toString()` implementation |
| `@OpenCLTest` | Test calls a native OpenCL function (add `assumeOpenCL...` as first statement) |

Apply alongside `@Test`:
```java
@AwaitTimeTest
@Test
public void interrupt_queueNotEmpty_waitedForDuration() { ... }

@ToStringTest
@Test
public void toString_whenCalled_containsClassNameAndIdentityHash() { ... }
```

### Timing / await tests
```java
@AwaitTimeTest
@Test
public void interrupt_keysQueueNotEmpty_waitedInternallyForTheDuration()
        throws IOException, InterruptedException {
    // Override static duration to a testable value
    ConsumerJava.AWAIT_DURATION_QUEUE_EMPTY = AwaitTimeTests.AWAIT_DURATION; // 20 s

    // ... arrange ...

    long beforeAct = System.currentTimeMillis();
    // act
    consumerJava.interrupt();
    long afterAct = System.currentTimeMillis();

    Duration waitTime = Duration.ofMillis(afterAct - beforeAct);

    // assert
    assertThat(waitTime, is(greaterThan(
        ConsumerJava.AWAIT_DURATION_QUEUE_EMPTY.minus(AwaitTimeTests.IMPRECISION)
    )));
}
```
- `AwaitTimeTests.AWAIT_DURATION` = 20 s
- `AwaitTimeTests.IMPRECISION` = 2 s

### toString tests
```java
@ToStringTest
@Test
public void toString_whenCalled_containsClassNameAndIdentityHash() {
    // arrange
    ConsumerJava sut = /* construct */;

    // act
    String output = sut.toString();

    // assert
    assertThat(output, not(emptyOrNullString()));
    assertThat(output, matchesPattern("ConsumerJava@\\p{XDigit}+"));
}
```

For structured toString (not identity-hash format):
```java
assertThat(output, is(equalTo("AddressToCoin{hash160=..., coin=100000000, type=P2PKH_OR_P2SH}")));
```

### Equals / hashCode / toString contract tests
Use `EqualHashCodeToStringTestHelper` with four instances (two for value A, two for value B):
```java
// arrange
Foo a1 = new Foo(valueA);
Foo a2 = new Foo(valueA);  // same data, different reference
Foo b1 = new Foo(valueB);
Foo b2 = new Foo(valueB);

// assert — A != B
new EqualHashCodeToStringTestHelper(a1, a2, b1, b2)
    .assertEqualsHashCodeToStringAIsDifferentToB();

// OR — A == B (same semantic content)
new EqualHashCodeToStringTestHelper(a1, a2, b1, b2)
    .assertEqualsHashCodeToStringAIsEqualToB();
```

### Logger mocking
```java
// inline creation (preferred when mock is used in only one test method)
Logger logger = mock(Logger.class);
when(logger.isTraceEnabled()).thenReturn(true);
when(logger.isDebugEnabled()).thenReturn(true);
objectUnderTest.setLogger(logger);

// capture and assert log messages
ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
verify(logger, times(1)).info(captor.capture());
List<String> arguments = captor.getAllValues();
assertThat(arguments.get(0), is(equalTo("Init producer.")));

// assert no error logging
verify(logger, never()).error(anyString());

// assert with argument matchers
verify(logger).error(contains("fromPrivateCompressed.getPubKeyHash()"));
verify(mockLogger, times(1)).info(eq("Received key: {}"), eq(expectedHex));
```

Import:
```java
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.*;
```

### Static address constants — never hard-code raw strings
| Class | Purpose |
|---|---|
| `StaticKey` | A single known private key with all derived forms |
| `TestAddresses42` | A set of addresses derived from seed 42 |
| `TestAddresses1337` | A set of addresses derived from seed 1337 |
| `P2PKH` enum | Known valid P2PKH public addresses with expected hashes |
| `P2SH` enum | Known valid P2SH script hash addresses |
| `P2WPKH` enum | Known valid native SegWit addresses |
| `StaticUnsupportedAddress` enum | Addresses that must be rejected |

### Randomness — always fixed seeds
```java
private final Random random = new Random(1337);  // never new Random() without seed
// document seed significance when it matters:
/** This random produces private key bits: 1; 0; 1; 0 */
private final Random random = new Random(1);
```

### LMDB / file-system tests
```java
@Rule
public TemporaryFolder folder = new TemporaryFolder();

// create a temporary file:
File tempFile = folder.newFile("addresses.txt");

// create a subfolder:
File subdir = folder.newFolder("lmdb");

// write content using modern Java NIO:
Files.writeString(tempFile.toPath(), "content");
Files.write(tempFile.toPath(), bytes, StandardOpenOption.APPEND);

// pre-populate a test LMDB database:
TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
TestAddressesFiles testAddresses = new TestAddressesFiles(compressed);
File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, false);
```
Always use `folder.newFolder(...)` and `folder.newFile(...)` — never create folders/files manually.

For tests that share LMDB setup, extend `LMDBBase`:
```java
public class MyLMDBTest extends LMDBBase {
    // LMDBBase provides: @Rule folder, network, keyUtility, and LMDB helpers
}
```

### Accessing test resources (integration/config tests)
Use `Path.of("src","test","resources")` to locate test resource files:
```java
private final Path resourceDirectory = Path.of("src", "test", "resources");
private final Path testRoundtripDirectory = resourceDirectory.resolve("roundtrip");
private final Path configFile = testRoundtripDirectory.resolve("config_AddressFilesToLMDB.json");
```

### Producer tests
Tests for `Producer` subclasses can extend `AbstractProducerTest` which provides
shared verify helpers:
```java
AbstractProducerTest.verifyInitProducer(producer);
AbstractProducerTest.verifyReleaseProducer(producer);
```

### Async / concurrent tests (Socket, WebSocket, ZMQ producers)
Use `CountDownLatch` + `ExecutorService` + `Future` for coordinating async server-client tests:
```java
@Test
public void createSecrets_serverSendsOneKey_keyReceived() throws Exception {
    // arrange
    int port = findFreePort();
    ExecutorService executorService = Executors.newCachedThreadPool();
    CountDownLatch serverStarted = new CountDownLatch(1);

    Future<Void> serverFuture = executorService.submit(() -> {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverStarted.countDown();                // signal ready
            try (Socket client = serverSocket.accept();
                 DataOutputStream out = new DataOutputStream(client.getOutputStream())) {
                out.write(secretBytes);
            }
        }
        return null;
    });

    serverStarted.await();                            // wait for server to be ready
    ConnectionUtils.waitUntilTcpPortOpen("localhost", port, timeout);

    KeyProducerJavaSocket producer = createProducer(port);

    // act
    BigInteger[] result = producer.createSecrets(1, true);

    // assert
    assertThat(result[0], is(equalTo(expectedKey)));

    serverFuture.get(5, TimeUnit.SECONDS);            // ensure server completed cleanly
    executorService.shutdown();
}

/** Utility method — finds an OS-assigned free TCP port. */
private static int findFreePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
        return s.getLocalPort();
    }
}
```
- Use `TestTimeProvider` constants for socket timeouts rather than magic numbers:
  ```java
  config.timeout = TestTimeProvider.DEFAULT_SOCKET_TIMEOUT;
  config.readRetryCount = TestTimeProvider.DEFAULT_RETRY_COUNT;
  ```
- Declare cleanup helpers as private methods to keep test methods readable:
  ```java
  private void cleanup(@Nullable KeyProducerJavaSocket client,
                       @Nullable Future<?> serverFuture,
                       @Nullable ServerSocket serverSocket) { ... }
  ```
  Note: helper method parameters may be `@Nullable` — annotate them accordingly.

### Nested static inner classes (testing abstract classes)
When a test needs a concrete implementation of an abstract class, define it as a
private static inner class inside the test class:
```java
public class AbstractPlaintextFileTest {

    private static class RecordingPlaintextFile extends AbstractPlaintextFile {
        final List<String> processedLines = new ArrayList<>();

        @Override
        protected void processLine(String line) {
            processedLines.add(line);
        }
    }

    @Test
    public void processLine_fileWithTwoLines_bothLinesReceived() throws Exception {
        // arrange
        RecordingPlaintextFile sut = new RecordingPlaintextFile();
        // ...
    }
}
```

### Multi-line string assertions (Java text blocks)
For expected outputs that span multiple lines, use Java 15+ text blocks:
```java
final String expected = """
    --- Info for OpenCL device: My GPU ---
    cl_device_id:    cl_device_id[0x0]
    name:            My GPU
    """;
assertThat(actual, is(equalTo(expected)));
```

### AtomicInteger / AtomicBoolean for concurrent test state
Use `AtomicInteger` / `AtomicBoolean` to track state from lambda/thread callbacks in tests:
```java
final AtomicInteger stateReceived = new AtomicInteger(0);
producer.setOnKeyReceived(key -> stateReceived.incrementAndGet());
// act ...
assertThat(stateReceived.get(), is(equalTo(1)));
```

### OpenCL tests
```java
@OpenCLTest
@Test
public void build_oneOpenCLDevice_returnsPlatformWithDevice() {
    // Must be the first statement:
    new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
    // test body that calls OpenCL native APIs
}
```
Do **not** gate the entire class — gate only the individual methods that invoke native code.
Tests that only instantiate JOCL wrapper objects (e.g. `cl_device_id`) as plain Java objects
do **not** need `@OpenCLTest` or an assume call.

### Import order (test and production)
1. `java.*` / `javax.*` (standard library)
2. Third-party libraries (alphabetical by package)
3. `net.ladenthin.*` (project classes)
4. Static imports (last, alphabetical)

No wildcard imports in production code. Test files may use wildcard for Hamcrest matchers
(`import static org.hamcrest.Matchers.*`) when many matchers are needed.

---

## Production Code Conventions (for TDD green phase)

### Null safety — JSpecify + NullAway
```java
import org.jspecify.annotations.Nullable;

// @NonNull is the DEFAULT — no annotation needed for non-null
// @Nullable must be explicit for anything that can be null

public @Nullable ConsumerJava consumerJava;          // nullable field
private final @Nullable Pattern vanityPattern;       // nullable final field

// Array null annotations — place between type and brackets:
private byte @Nullable [] uncompressedKeyHash;       // array itself may be null
public byte @NonNull [] getUncompressedKeyHash() {}  // array is guaranteed non-null

// Standalone annotation on its own line (alternative style):
@NonNull
private BigInteger currentValue;
```

NullAway enforces these at compile time — missing `@Nullable` on a nullable return/field
causes a **compilation failure**.

### Logger pattern
Production classes declare a mutable protected logger so tests can inject a mock:
```java
protected Logger logger = LoggerFactory.getLogger(this.getClass());

// Expose a setter annotated for testing:
@VisibleForTesting
public void setLogger(Logger logger) {
    this.logger = logger;
}
```

### @VisibleForTesting
Mark package-private or protected members that are only exposed for testing:
```java
@VisibleForTesting
static Duration AWAIT_DURATION_QUEUE_EMPTY = Duration.ofMinutes(1);

@VisibleForTesting
final ExecutorService producerExecutorService = Executors.newCachedThreadPool();
```
Tests modify static `Duration` fields directly to shorten wait times for `@AwaitTimeTest` tests.

### Configuration objects (C-prefix POJOs)
All new features are driven by a C-prefixed configuration POJO:
```java
// Configuration POJO:
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

### Records for immutable value objects
```java
public record MyValue(@NonNull String name, int count) {
    // compact constructor for validation
    public MyValue {
        Objects.requireNonNull(name);
    }
}
```

`Objects.requireNonNull()` **is valid in production code** to assert non-null preconditions
at runtime. It is banned only in test code (use `// pre-assert` with Hamcrest there instead).

For records containing mutable third-party types that cannot be made immutable, suppress
the Error Prone warning on the specific field:
```java
@Immutable
public record OpenCLDevice(
    @SuppressWarnings("Immutable") cl_device_id device,
    @NonNull String name,
    long maxWorkGroupSize
) implements Serializable { }
```

For records whose collections must be immutable, use Guava's `ImmutableList`:
```java
public record OpenCLDevice(..., @NonNull ImmutableList<@NonNull Long> maxWorkItemSizes) {
    public static OpenCLDevice of(..., List<Long> sizes) {
        return new OpenCLDevice(..., ImmutableList.copyOf(sizes));
    }
}
```

### Lambda callbacks in production constructors
Inject behaviour via functional interfaces rather than subclassing:
```java
public AddressFile(
    File file,
    ReadStatistic readStatistic,
    Network network,
    Consumer<AddressToCoin> addressConsumer,    // callback for each parsed address
    Consumer<String> rejectedLineConsumer       // callback for unrecognised lines
) { ... }
```

### Graceful shutdown — Interruptable interface
All Producer/Consumer implementations must implement `Interruptable`:
```java
public class MyProducer extends AbstractProducer implements Interruptable {
    @Override
    public void interrupt() {
        // signal the work loop to stop
    }
}
```

### Custom domain exceptions — not generic ones
Use the project's existing exception types:
```
KeyProducerIdNullException
KeyProducerIdIsNotUniqueException
KeyProducerIdUnknownException
NoMoreSecretsAvailableException
PrivateKeyTooLargeException
UnknownSecretFormatException
AddressFormatNotAcceptedException
```
Create a new domain exception rather than throwing `IllegalArgumentException` or
`RuntimeException` when a more specific type makes sense.

### Concurrency conventions
```java
// Thread-safe counters:
protected final AtomicLong hits = new AtomicLong();
protected final AtomicLong checkedKeys = new AtomicLong();

// Work queue:
private final LinkedBlockingQueue<byte[]> keysQueue;

// Thread pool — never raw Thread:
private final ExecutorService executorService = Executors.newCachedThreadPool();

// Shutdown synchronisation:
private final CountDownLatch latch = new CountDownLatch(1);
```

### @Immutable annotation (Error Prone)
Apply to classes that are truly immutable (e.g., records, value objects):
```java
import com.google.errorprone.annotations.Immutable;

@Immutable
public record OpenCLPlatform(...) { ... }
```

---

## Checklist before marking a TDD cycle complete

- [ ] At least one test was written and failed **before** the production code was written.
- [ ] Every production behaviour is covered by at least one test.
- [ ] All tests pass: `./mvnw test`
- [ ] Compilation is clean (no NullAway errors): `./mvnw compile`
- [ ] Every test class has the Apache 2.0 license header wrapped in `@formatter:off/on`.
- [ ] Test method names follow the `method_condition_expected` three-segment pattern.
- [ ] Every test body has `// arrange`, `// act`, `// assert` comments.
- [ ] All tests for the same method/feature are inside a single `<editor-fold>` block.
- [ ] No `assertEquals` / `assertTrue` / `assertFalse` / `assertNotNull` anywhere.
- [ ] Exception tests with message assertions use `try { ...; fail(...); } catch`.
- [ ] All `Random` instances use a fixed seed.
- [ ] No hard-coded raw address strings — use `StaticKey`, `P2PKH`, `P2WPKH`, etc.
- [ ] Data providers are added to `CommonDataProvider`, not inlined in the test class.
- [ ] `@RunWith(DataProviderRunner.class)` is present **only** when `@UseDataProvider` is used.
- [ ] Empty `@Before` methods are removed.
- [ ] All nullable fields/returns in new production code are annotated with `@Nullable`.
- [ ] Array nullable annotations follow the `byte @Nullable []` placement convention.
- [ ] Logger in new production classes is `protected`, non-final, with a `setLogger()` setter.
- [ ] `@VisibleForTesting` is applied to any member exposed solely for tests.
- [ ] New configuration fields are in a C-prefixed POJO, not inlined as constructor params.
- [ ] Async tests use `CountDownLatch` + `ExecutorService` + `Future`; no raw `Thread` or `Thread.sleep` polling.
- [ ] Socket tests use `findFreePort()` and `TestTimeProvider` constants — no magic port numbers.
- [ ] Nested concrete implementations of abstract classes are private static inner classes inside the test class.
- [ ] `Objects.requireNonNull()` is used only in production code, never in tests.
- [ ] Multi-line expected strings use Java text blocks (`""" ... """`).
- [ ] Records with mutable third-party fields use `@SuppressWarnings("Immutable")` on the specific field.
- [ ] Behaviour injection in production constructors uses functional interfaces (`Consumer<T>`, `Function<T,R>`) rather than subclassing.
- [ ] Existing correct inline comments in modified test code are preserved — only removed if factually wrong or describing deleted code.
