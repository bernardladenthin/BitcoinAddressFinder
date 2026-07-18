// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.LMDBPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.bloom.BloomFilterAccelerator;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BlockedBloomAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.HashSetAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.TruncatedLong64SortedArrayPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Network;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Per-backend lookup-latency benchmark for every {@link AddressLookupBackend} value.
 *
 * <p>Successor to the {@code AddressLookupBenchmarkTest} JUnit timing test
 * (commit {@code 051bf70}, 2026-05-29). The original was explicitly authored as
 * <em>&quot;JUnit comparison benchmark (not JMH)&quot;</em> with the file Javadoc
 * noting it &quot;exists so backend changes can be evaluated locally without
 * setting up a separate JMH harness&quot;. By the time it was added, the JMH
 * harness already existed (see {@link BitHelperBenchmark}, {@link ByteSwapBenchmark},
 * {@link PublicKeyHashBenchmark}, all introduced 2 days earlier in PR #215), so the
 * shortcut was unnecessary. This file is the proper migration.</p>
 *
 * <p>What is measured: average wall-clock time per single {@link AddressPresence#containsAddress(ByteBuffer)}
 * call, separated per backend, with JMH-driven JIT warmup, fork isolation, and
 * optional GC-allocation profiling via {@code -prof gc}. The dataset shape mirrors
 * the original to keep the relative ordering apples-to-apples:</p>
 *
 * <ul>
 *   <li>{@value #DB_ENTRY_COUNT} random hash160 entries in LMDB. With a uniform
 *       first-byte distribution this populates ~all 256
 *       {@link TruncatedLong64SortedArrayPresence} buckets so the binary-search
 *       path is exercised across most buckets.</li>
 *   <li>{@value #QUERY_COUNT} pre-generated random 20-byte queries (power of two so
 *       the JMH inner loop can mask instead of {@code %}). The seeded RNG
 *       ({@value #RANDOM_SEED}) keeps populations and queries deterministic across
 *       runs.</li>
 *   <li>Re-used direct {@link ByteBuffer} (LMDB requires direct buffers for keys)
 *       so per-call allocation does not skew the measurement &mdash; same trick the
 *       original test used.</li>
 * </ul>
 *
 * <p>Each measurement iteration walks through the {@value #QUERY_COUNT} queries in
 * order using a counter modulo the array size; over the full warmup + measurement
 * budget the JIT sees a stable hot path.</p>
 *
 * <p>Availability: requires the LMDB native library (same gate as
 * {@code AddressLookupBenchmarkTest} used). On hosts without LMDB,
 * {@link LMDBPlatformAssume#assumeLMDBExecution()} throws
 * {@code AssumptionViolatedException} from {@code @Setup}, which JMH reports as
 * {@code ERROR} on every {@code @Param} row &mdash; matching how
 * {@link GridSizeSweepBenchmark} handles missing OpenCL devices.</p>
 *
 * <p>Run locally:</p>
 * <pre>
 * mvn test-compile exec:java -Dexec.args=&quot;AddressLookupBenchmark&quot;
 *
 * # narrow to a single backend
 * mvn test-compile exec:java \
 *   -Dexec.args=&quot;AddressLookupBenchmark -p backend=TRUNCATED_LONG_64&quot;
 *
 * # allocation profile (per-op bytes allocated)
 * mvn test-compile exec:java -Dexec.args=&quot;AddressLookupBenchmark -prof gc&quot;
 * </pre>
 *
 * <p>This benchmark is intentionally NOT executed by {@code mvn test} (no
 * {@code @Test} annotation, lives in the JMH-runner path).</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(
        value = 1,
        // Master JVM-flag list — keep identical (same set + same order) to the pom.xml
        // <argLine>, .mvn/jvm.config and examples/*.bat so the JMH fork matches the JVM
        // Surefire uses (lmdbjava reflects into sun.nio.ch / jdk.internal.ref).
        jvmArgsAppend = {
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "--add-exports=java.base/java.lang=ALL-UNNAMED",
            "--add-exports=java.base/java.io=ALL-UNNAMED",
            "--add-exports=java.base/java.nio=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-exports=jdk.management/com.sun.management.internal=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        })
public class AddressLookupBenchmark {

    /** Length of a hash160 entry in bytes. */
    private static final int HASH160_LENGTH = 20;

    /**
     * Number of random hash160 entries written into LMDB before measurement.
     * 2048 is enough to populate ~all 256 first-byte buckets so the
     * {@link TruncatedLong64SortedArrayPresence} binary-search path is
     * exercised across most buckets.
     */
    private static final int DB_ENTRY_COUNT = 2048;

    /**
     * Number of pre-generated random queries the {@code @Benchmark} method
     * cycles through. Power-of-two so the inner-loop cursor can use a bitmask
     * instead of {@code %}.
     */
    private static final int QUERY_COUNT = 8192;

    /** Bitmask used in place of {@code % QUERY_COUNT}. */
    private static final int QUERY_MASK = QUERY_COUNT - 1;

    /** Fixed seed so the population and the lookup keys are reproducible. */
    private static final long RANDOM_SEED = 0xC0FFEEL;

    /** Bloom-filter false-positive probability for the BLOOM backend. */
    private static final double BLOOM_FPP = 0.01;

    /** Which lookup backend this trial measures. */
    @Param({"LMDB_ONLY", "BLOOM", "HASHSET", "TRUNCATED_LONG_64", "BINARY_FUSE_8", "BINARY_FUSE_16", "BLOCKED_BLOOM"})
    public AddressLookupBackend backend;

    private Path tempDir;
    private LMDBPersistence lmdb;
    private AddressPresence lookup;
    private byte[][] queries;
    private ByteBuffer reusedKeyBuffer;
    private int queryCursor;

    /**
     * Creates a new {@link AddressLookupBenchmark} (no-arg constructor for JMH).
     */
    public AddressLookupBenchmark() {
        // no-op
    }

    /**
     * Populates LMDB, generates the random query set, and builds the lookup for
     * the current {@code @Param} backend. Runs once per JMH trial (per fork).
     *
     * <p>Skips the trial cleanly (JMH reports {@code ERROR} on every data row)
     * if the LMDB native library is unavailable on this host, mirroring the
     * convention {@link GridSizeSweepBenchmark} uses for OpenCL availability.</p>
     *
     * @throws Exception if LMDB initialisation or population fails
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();

        tempDir = Files.createTempDirectory("address-lookup-benchmark-lmdb-");
        File lmdbDir = createPopulatedLmdb(tempDir);

        CLMDBConfigurationReadOnly cfg = new CLMDBConfigurationReadOnly();
        cfg.lmdbDirectory = lmdbDir.getAbsolutePath();
        cfg.addressLookupBackend = backend;
        cfg.bloomFilterFpp = BLOOM_FPP;

        Network network = new NetworkParameterFactory().getNetwork();
        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        lmdb = new LMDBPersistence(cfg, persistenceUtils);
        lmdb.init();

        lookup = buildLookup(lmdb, backend);

        queries = generateRandomQueries(QUERY_COUNT);
        reusedKeyBuffer = ByteBuffer.allocateDirect(HASH160_LENGTH);
        queryCursor = 0;
    }

    /**
     * Measures a single {@link AddressPresence#containsAddress(ByteBuffer)} call
     * against the current backend. The return value is returned (not consumed
     * via {@link org.openjdk.jmh.infra.Blackhole}) so JMH's automatic
     * return-value sink prevents dead-code elimination.
     *
     * @return the lookup result &mdash; consumed by JMH automatically
     */
    @Benchmark
    public boolean containsAddress() {
        byte[] q = queries[queryCursor++ & QUERY_MASK];
        reusedKeyBuffer.clear();
        reusedKeyBuffer.put(q);
        reusedKeyBuffer.flip();
        return lookup.containsAddress(reusedKeyBuffer);
    }

    /**
     * Closes LMDB and removes the temp directory created in {@link #setUp()}.
     *
     * @throws IOException if the temp directory cannot be deleted
     */
    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        if (lmdb != null) {
            lmdb.close();
            lmdb = null;
        }
        if (tempDir != null) {
            deleteRecursively(tempDir);
            tempDir = null;
        }
    }

    private static AddressPresence buildLookup(LMDBPersistence lmdb, AddressLookupBackend backend) {
        return switch (backend) {
            case LMDB_ONLY -> lmdb;
            case BLOOM -> BloomFilterAccelerator.populateFrom(lmdb, lmdb, BLOOM_FPP);
            case HASHSET -> HashSetAddressPresence.populateFrom(lmdb);
            case TRUNCATED_LONG_64 -> TruncatedLong64SortedArrayPresence.populateFrom(lmdb);
            case BINARY_FUSE_8 -> BinaryFuse8AddressPresence.populateFrom(lmdb);
            case BINARY_FUSE_16 -> BinaryFuse16AddressPresence.populateFrom(lmdb);
            case BLOCKED_BLOOM -> BlockedBloomAddressPresence.populateFrom(lmdb);
        };
    }

    /**
     * Creates a fresh LMDB directory under {@code parent} and writes
     * {@value #DB_ENTRY_COUNT} random hash160 entries to it.
     */
    private File createPopulatedLmdb(Path parent) throws Exception {
        Path dbDir = Files.createDirectory(parent.resolve("benchmark-lmdb"));

        CLMDBConfigurationWrite writeCfg = new CLMDBConfigurationWrite();
        writeCfg.lmdbDirectory = dbDir.toFile().getAbsolutePath();
        writeCfg.initialMapSizeInMiB = 64;

        Network network = new NetworkParameterFactory().getNetwork();
        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        Random rng = new Random(RANDOM_SEED);

        try (LMDBPersistence writer = new LMDBPersistence(writeCfg, persistenceUtils)) {
            writer.init();
            byte[] entry = new byte[HASH160_LENGTH];
            for (int i = 0; i < DB_ENTRY_COUNT; i++) {
                rng.nextBytes(entry);
                ByteBuffer key = byteBufferUtility.byteArrayToByteBuffer(entry);
                writer.putNewAmount(key, Coin.ZERO);
            }
        }
        return dbDir.toFile();
    }

    /**
     * Generates {@code n} random 20-byte queries using a different sub-stream
     * (seeded with the bitwise NOT of {@link #RANDOM_SEED}) so most queries miss
     * while still being deterministic. The first byte is uniformly distributed
     * over {@code 0..255} so all 256 SortedArray buckets get hit roughly
     * equally.
     */
    private static byte[][] generateRandomQueries(int n) {
        Random rng = new Random(~RANDOM_SEED);
        byte[][] queries = new byte[n][];
        for (int i = 0; i < n; i++) {
            byte[] q = new byte[HASH160_LENGTH];
            rng.nextBytes(q);
            queries[i] = q;
        }
        return queries;
    }

    /**
     * Best-effort recursive delete of the temp LMDB directory. JMH-managed
     * teardown means we own the path; failure here is a leak (not a
     * correctness issue) so we surface the IOException only for the top-level
     * walk and let the caller log/ignore.
     */
    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort; LMDB may still hold a handle on Windows
                }
            });
        }
    }
}
