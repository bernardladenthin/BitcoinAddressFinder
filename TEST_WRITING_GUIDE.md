# Unit Test Writing Guide — BitcoinAddressFinder (Project-Specific Supplement)

> **Canonical workspace rules** follow the versioned chain in
> `workspace/guides/test/`:
> [`TEST_WRITING_GUIDE-8.md`](../workspace/guides/test/TEST_WRITING_GUIDE-8.md)
> (Java 8 baseline — JUnit Jupiter framework choices, AAA structure
> with `// pre-assert` semantics, both `<editor-fold>` and `@Nested`
> grouping styles, naming pattern, Hamcrest assertions, exception
> testing, parameterized tests via `@MethodSource`, logger mocking
> with Mockito or LogCaptor, import grouping, DRY constants per fold)
> **then**
> [`TEST_WRITING_GUIDE-21.md`](../workspace/guides/test/TEST_WRITING_GUIDE-21.md)
> (Java 21 supplement — records as test fixtures, pattern matching in
> assertions, text blocks for fixture JSON/Markdown, `var` for test
> locals). Both apply to BAF.
> The TDD workflow lives in
> [`../workspace/.claude/skills/java-tdd-guide/SKILL.md`](../workspace/.claude/skills/java-tdd-guide/SKILL.md)
> (referenced locally as `.claude/skills/java-tdd-guide/SKILL.pointer.md`).
> This file contains only **BAF-specific** test conventions: custom
> marker annotations (`@AwaitTimeTest`, `@ToStringTest`, `@OpenCLTest`),
> timing-test patterns with `AwaitTimeTests` constants, the static
> address constants (`StaticKey`, `TestAddresses42`, `P2PKH` enum,
> etc.), the `OpenCLPlatformAssume` / `LMDBPlatformAssume` gate
> pattern, LMDB test helpers via `LMDBBase`, shared `KeyUtility` /
> `BitHelper` test fields, `AbstractProducerTest` helpers, and socket
> test utilities via `TestTimeProvider` / `ConnectionUtils`.

---

## 1. Custom Marker Annotations

Apply the appropriate marker annotation alongside `@Test` when applicable:

| Annotation | When to use |
|---|---|
| `@AwaitTimeTest` | Test involves timing assertions (`Duration.ofMillis`, `greaterThan`) |
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

## 2. Timing / Await Tests

Tests that assert on timing durations must:
1. Be annotated with `@AwaitTimeTest`.
2. Use `AwaitTimeTests.AWAIT_DURATION` (20 s) as the configurable duration.
3. Use `AwaitTimeTests.IMPRECISION` (2 s) as the tolerance.
4. Override the corresponding **config field on the POJO** (no static
   mutation, no test-order coupling). The production timeouts that have
   been moved to config so far:
   - `CConsumerJava.awaitQueueEmptySeconds` (default 60)
   - `CFinder.awaitTerminateSeconds` (default 31 536 000 000 — ~100 k years)

```java
@AwaitTimeTest
@Test
public void interrupt_keysQueueNotEmpty_waitedForDuration()
        throws IOException, InterruptedException {
    CConsumerJava cConsumerJava = new CConsumerJava();
    cConsumerJava.awaitQueueEmptySeconds = AwaitTimeTests.AWAIT_DURATION.toSeconds();
    // ... arrange, including building the ConsumerJava from cConsumerJava ...

    long beforeAct = System.currentTimeMillis();
    consumerJava.interrupt();
    long afterAct = System.currentTimeMillis();

    Duration waitTime = Duration.ofMillis(afterAct - beforeAct);

    assertThat(waitTime, is(greaterThan(
        AwaitTimeTests.AWAIT_DURATION.minus(AwaitTimeTests.IMPRECISION)
    )));
}
```

---

## 3. Static Address Constants — Never Hard-Code Raw Strings

Use the static address enums and helper classes for test data:

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

## 4. Platform Assumptions

Tests that require a specific platform conditionally skip using assume classes:

```java
@OpenCLTest
@Test
public void build_oneOpenCLDevice_returnsPlatformWithDevice() {
    new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
    // test body
}
```

Available assume classes:
- `OpenCLPlatformAssume` — OpenCL GPU device required
- `LMDBPlatformAssume` — LMDB native library required
- `PlatformAssume` — generic OS assumptions

---

## 5. OpenCL Tests

### When `@OpenCLTest` + assume IS required

A test must be annotated with `@OpenCLTest` and call `assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable()` as its **first statement** when the test body invokes OpenCL API functions:

- `CL.stringFor_*` or any `CL.*` native call
- `OpenCLBuilder.build()` or methods that load/query the OpenCL runtime
- Any code path triggering native library loading

**Rules:**
- Annotate with `@OpenCLTest`.
- Call assume as the **first statement**.
- Do not gate the entire class — only gate individual methods.

### When `@OpenCLTest` + assume is NOT required

Tests that **only** use JOCL wrapper types (`cl_device_id`, `cl_context_properties`, `cl_platform_id`) as plain Java objects — without calling native OpenCL API functions — do **not** need `@OpenCLTest`.

**Decision rule:** "Does this test call any method that invokes a native OpenCL function?" If yes → `@OpenCLTest` + assume. If no → no annotation needed.

---

## 6. LMDB / File System Tests

Use project-specific helpers for LMDB test databases:

```java
TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
TestAddressesFiles testAddresses = new TestAddressesFiles(compressed);
File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, false);
```

For tests that share LMDB setup, extend `LMDBBase`:

```java
public class MyLMDBTest extends LMDBBase {
    // LMDBBase provides: @Rule folder, network, keyUtility, and LMDB helpers
}
```

---

## 7. Shared Test Fields

Common shared fields for BitcoinAddressFinder tests:

```java
private final Network network = new NetworkParameterFactory().getNetwork();
private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
private final BitHelper bitHelper = new BitHelper();
```

---

## 8. Producer Tests

Tests for `Producer` subclasses can extend `AbstractProducerTest` which provides shared verify helpers:

```java
AbstractProducerTest.verifyInitProducer(producer);
AbstractProducerTest.verifyReleaseProducer(producer);
```

---

## 9. Socket Test Utilities

- Use `TestTimeProvider` constants for socket timeouts:
  ```java
  config.timeout = TestTimeProvider.DEFAULT_SOCKET_TIMEOUT;
  config.readRetryCount = TestTimeProvider.DEFAULT_RETRY_COUNT;
  ```
- Use `ConnectionUtils.waitUntilTcpPortOpen(...)` for server readiness checks.

---

## 10. Accessing Test Resources

Use `Path.of("src","test","resources")` to locate test resource files:

```java
private final Path resourceDirectory = Path.of("src", "test", "resources");
private final Path testRoundtripDirectory = resourceDirectory.resolve("roundtrip");
private final Path configFile = testRoundtripDirectory.resolve("config_AddressFilesToLMDB.json");
```
