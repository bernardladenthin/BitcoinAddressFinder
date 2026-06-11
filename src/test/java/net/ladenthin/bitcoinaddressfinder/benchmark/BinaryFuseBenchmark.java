// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
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
import org.openjdk.jmh.annotations.Warmup;

/**
 * Isolated latency and construction benchmark for {@link BinaryFuse8AddressPresence}
 * and {@link BinaryFuse16AddressPresence} — no LMDB dependency, no temp directory.
 *
 * <p>Three benchmark methods are provided:</p>
 * <ul>
 *   <li>{@link #lookupHit} — {@link AddressPresence#containsAddress(ByteBuffer)} on a key
 *       that is guaranteed to be present (no false-negative possible).</li>
 *   <li>{@link #lookupMiss} — {@link AddressPresence#containsAddress(ByteBuffer)} on a key
 *       that is <em>very likely</em> absent (random 20-byte value not in the populated set).
 *       A small fraction of misses will report {@code true} due to the filter's false-positive
 *       rate (0.4% for Fuse-8, 0.0015% for Fuse-16); this does not affect timing
 *       meaningfully.</li>
 *   <li>{@link #buildFilter} — end-to-end {@code populateFrom} construction measured per
 *       call. Construction is O(N) in {@code entryCount}; the timing grows proportionally.</li>
 * </ul>
 *
 * <p>Entry counts are chosen to expose L1/L2/L3 cache pressure:</p>
 * <ul>
 *   <li>{@code 1 024} entries — Fuse-8 fingerprint array ≈ 1.3 KB: fits in L1 cache.</li>
 *   <li>{@code 65 536} entries — Fuse-8 ≈ 85 KB: L2 territory.</li>
 *   <li>{@code 1 048 576} entries — Fuse-8 ≈ 1.36 MB: L3 territory.</li>
 * </ul>
 *
 * <p>Run locally (all variants):</p>
 * <pre>
 * mvn test-compile exec:java -Dexec.args="BinaryFuseBenchmark"
 * </pre>
 *
 * <p>Narrow to one filter type:</p>
 * <pre>
 * mvn test-compile exec:java \
 *   -Dexec.args="BinaryFuseBenchmark -p backend=BINARY_FUSE_8"
 * </pre>
 *
 * <p>Construction timing only:</p>
 * <pre>
 * mvn test-compile exec:java \
 *   -Dexec.args="BinaryFuseBenchmark\.buildFilter"
 * </pre>
 *
 * <p>Allocation profile (bytes per op):</p>
 * <pre>
 * mvn test-compile exec:java -Dexec.args="BinaryFuseBenchmark -prof gc"
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
@Fork(1)
public class BinaryFuseBenchmark {

    /** Length of a hash160 entry in bytes. */
    private static final int HASH160_BYTES = 20;

    /**
     * Number of pre-generated query buffers cycled through during lookup benchmarks.
     * Power-of-two so the cursor wraps with a bitmask instead of {@code %}.
     */
    private static final int QUERY_COUNT = 4096;

    /** Bitmask used in place of {@code % QUERY_COUNT}. */
    private static final int QUERY_MASK = QUERY_COUNT - 1;

    /** Fixed seed so the population and query arrays are reproducible across JVM forks. */
    private static final long RANDOM_SEED = 0xF11_7E83_B3C4_4242L;

    /** Which filter variant this trial measures. */
    @Param({"BINARY_FUSE_8", "BINARY_FUSE_16"})
    public AddressLookupBackend backend;

    /**
     * Number of hash160 entries inserted into the filter.
     * Three sizes probe L1 / L2 / L3 cache behaviour.
     */
    @Param({"1024", "65536", "1048576"})
    public int entryCount;

    private AddressPresence filter;

    /** Entries used for the {@link #buildFilter()} benchmark — reused across invocations. */
    private byte[][] buildEntries;

    /** Pre-selected hit queries (subset of the populated set). */
    private byte[][] hitQueries;

    /** Pre-generated miss queries (random values, very unlikely to collide). */
    private byte[][] missQueries;

    /** Single heap buffer re-used on every lookup call (heap is fine — no LMDB here). */
    private ByteBuffer reusedBuffer;

    private int hitCursor;
    private int missCursor;

    /**
     * Creates a new {@link BinaryFuseBenchmark} (no-arg constructor for JMH).
     */
    public BinaryFuseBenchmark() {
        // no-op
    }

    /**
     * Generates the entry set, builds the filter, and pre-populates the hit/miss
     * query arrays.  Runs once per JMH trial (per fork × {@code @Param} combination).
     */
    @Setup(Level.Trial)
    public void setUp() {
        Random rng = new Random(RANDOM_SEED);

        buildEntries = new byte[entryCount][HASH160_BYTES];
        for (int i = 0; i < entryCount; i++) {
            rng.nextBytes(buildEntries[i]);
        }

        filter = buildFilter(buildEntries, backend);

        // Hit queries: wrap around the populated entries.
        hitQueries = new byte[QUERY_COUNT][HASH160_BYTES];
        for (int i = 0; i < QUERY_COUNT; i++) {
            System.arraycopy(buildEntries[i % entryCount], 0, hitQueries[i], 0, HASH160_BYTES);
        }

        // Miss queries: fresh random bytes with a different seed.
        Random missRng = new Random(~RANDOM_SEED);
        missQueries = new byte[QUERY_COUNT][HASH160_BYTES];
        for (int i = 0; i < QUERY_COUNT; i++) {
            missRng.nextBytes(missQueries[i]);
        }

        reusedBuffer = ByteBuffer.allocate(HASH160_BYTES);
        hitCursor = 0;
        missCursor = 0;
    }

    /**
     * Measures {@link AddressPresence#containsAddress(ByteBuffer)} for a key that is
     * guaranteed to be present in the filter (no false-negative possible).
     *
     * @return the lookup result — consumed by JMH to prevent dead-code elimination
     */
    @Benchmark
    public boolean lookupHit() {
        byte[] q = hitQueries[hitCursor++ & QUERY_MASK];
        reusedBuffer.clear();
        reusedBuffer.put(q);
        reusedBuffer.flip();
        return filter.containsAddress(reusedBuffer);
    }

    /**
     * Measures {@link AddressPresence#containsAddress(ByteBuffer)} for a key that is
     * very likely absent from the filter (measures the common miss path).
     *
     * @return the lookup result — consumed by JMH to prevent dead-code elimination
     */
    @Benchmark
    public boolean lookupMiss() {
        byte[] q = missQueries[missCursor++ & QUERY_MASK];
        reusedBuffer.clear();
        reusedBuffer.put(q);
        reusedBuffer.flip();
        return filter.containsAddress(reusedBuffer);
    }

    /**
     * Measures end-to-end filter construction (population from a raw entry array).
     * Each invocation allocates a fresh filter; GC pressure is part of the
     * measured cost.  Construction time is O({@link #entryCount}).
     *
     * @return the constructed filter — consumed by JMH to prevent dead-code elimination
     */
    @Benchmark
    public AddressPresence buildFilter() {
        return buildFilter(buildEntries, backend);
    }

    private static AddressPresence buildFilter(byte[][] entries, AddressLookupBackend backend) {
        ArrayIterable iterable = new ArrayIterable(entries);
        return switch (backend) {
            case BINARY_FUSE_8  -> BinaryFuse8AddressPresence.populateFrom(iterable);
            case BINARY_FUSE_16 -> BinaryFuse16AddressPresence.populateFrom(iterable);
            default -> throw new IllegalArgumentException("Unsupported backend: " + backend);
        };
    }

    /**
     * Minimal {@link AddressIterable} backed by a pre-allocated {@code byte[][]}
     * so the benchmark does not measure memory allocation inside the supplier.
     */
    private static final class ArrayIterable implements AddressIterable {

        private final byte[][] entries;

        ArrayIterable(byte[][] entries) {
            this.entries = entries;
        }

        @Override
        public Stream<ByteBuffer> addresses() {
            return Arrays.stream(entries).map(ByteBuffer::wrap);
        }

        @Override
        public long count() {
            return entries.length;
        }
    }
}
