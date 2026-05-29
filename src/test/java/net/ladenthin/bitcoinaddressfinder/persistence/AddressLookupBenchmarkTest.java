// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.LMDBPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.persistence.bloom.BloomFilterAccelerator;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.HashSetAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.SortedArrayAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.TruncatedLong64SortedArrayPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures lookup throughput of each {@link AddressLookupBackend} against the same
 * LMDB-backed data set, using random hash160 queries that mostly miss (which is the
 * realistic shape of the scan hot path).
 *
 * <p>This is not a JMH micro-benchmark; it is a JUnit comparison test that runs in CI
 * and logs a result table. It exists so backend changes (interface tweaks, snapshot
 * representation choices) can be evaluated locally without setting up a separate JMH
 * harness. Wall-clock numbers are reported in {@code ns/op} so they are stable across
 * machines for relative comparison.
 *
 * <p>Dataset shape (defaults):
 * <ul>
 *   <li>{@value #DB_ENTRY_COUNT} random hash160 entries in LMDB. With a uniform first
 *       byte distribution this is enough that almost every one of the 256 SortedArray
 *       buckets is non-empty, giving the binary-search path realistic coverage.</li>
 *   <li>{@value #LOOKUPS_PER_RUN} random lookups per measurement run; first
 *       {@value #WARMUP_LOOKUPS} are dropped to let the JIT warm up.</li>
 *   <li>Seeded RNG ({@value #RANDOM_SEED}) so populations and queries are deterministic
 *       across runs.</li>
 * </ul>
 */
public class AddressLookupBenchmarkTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddressLookupBenchmarkTest.class);

    /** Length of a hash160 entry in bytes. */
    private static final int HASH160_LENGTH = 20;

    /**
     * Number of random hash160 entries written into LMDB before measurement. 2048 is
     * enough to populate ~all 256 first-byte buckets so the SortedArray binary-search
     * path is exercised across most buckets.
     */
    private static final int DB_ENTRY_COUNT = 2048;

    /** Total lookups per measured backend (including warmup). */
    private static final int LOOKUPS_PER_RUN = 200_000;

    /** Lookups discarded before timing to let the JIT compile the hot path. */
    private static final int WARMUP_LOOKUPS = 50_000;

    /** Fixed seed so the population and the lookup keys are reproducible. */
    private static final long RANDOM_SEED = 0xC0FFEEL;

    /** Bloom-filter false-positive probability for the BLOOM backend. */
    private static final double BLOOM_FPP = 0.01;

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();

    @Test
    public void compareLookupThroughputAcrossAllBackends() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();

        File lmdbDir = createPopulatedLmdb();

        // Generate the random query set once; reuse for every backend so the comparison
        // is apples-to-apples (each backend answers the same K addresses in the same
        // order).
        byte[][] queries = generateRandomQueries(LOOKUPS_PER_RUN);

        LOGGER.info(
                "===== AddressLookupBenchmark | dbEntries={} | lookupsPerRun={} | warmup={} =====",
                DB_ENTRY_COUNT,
                LOOKUPS_PER_RUN,
                WARMUP_LOOKUPS);
        LOGGER.info(String.format("%-18s %12s %12s %12s", "Backend", "Build (ms)", "Hits", "ns / op"));

        for (AddressLookupBackend backend : AddressLookupBackend.values()) {
            measureBackend(lmdbDir, backend, queries);
        }
    }

    private void measureBackend(File lmdbDir, AddressLookupBackend backend, byte[][] queries) throws Exception {
        CLMDBConfigurationReadOnly cfg = new CLMDBConfigurationReadOnly();
        cfg.lmdbDirectory = lmdbDir.getAbsolutePath();
        cfg.addressLookupBackend = backend;
        cfg.bloomFilterFpp = BLOOM_FPP;

        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        LMDBPersistence lmdb = new LMDBPersistence(cfg, persistenceUtils);
        lmdb.init();

        long buildStartNs = System.nanoTime();
        AddressPresence lookup = buildLookup(lmdb, backend);
        long buildDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - buildStartNs);

        // Re-usable direct buffer (LMDB requires direct buffers for keys) to avoid
        // per-call allocation in the timing loop. Each query copies its bytes into this
        // buffer; position is rewound to 0 each iteration so the lookup contract
        // (position/limit unchanged on return) holds.
        ByteBuffer buf = ByteBuffer.allocateDirect(HASH160_LENGTH);

        // Warmup
        for (int i = 0; i < WARMUP_LOOKUPS; i++) {
            putQuery(buf, queries[i % queries.length]);
            lookup.containsAddress(buf);
        }

        // Measured loop
        long hits = 0;
        long start = System.nanoTime();
        for (int i = WARMUP_LOOKUPS; i < queries.length; i++) {
            putQuery(buf, queries[i]);
            if (lookup.containsAddress(buf)) {
                hits++;
            }
        }
        long elapsedNs = System.nanoTime() - start;
        long opCount = (long) queries.length - WARMUP_LOOKUPS;
        long nsPerOp = elapsedNs / opCount;

        LOGGER.info(String.format("%-18s %12d %12d %12d", backend, buildDurationMs, hits, nsPerOp));

        lmdb.close();
    }

    private static void putQuery(ByteBuffer buf, byte[] src) {
        buf.clear();
        buf.put(src);
        buf.flip();
    }

    private AddressPresence buildLookup(LMDBPersistence lmdb, AddressLookupBackend backend) {
        return switch (backend) {
            case LMDB_ONLY -> lmdb;
            case BLOOM -> BloomFilterAccelerator.populateFrom(lmdb, lmdb, BLOOM_FPP);
            case HASHSET -> HashSetAddressPresence.populateFrom(lmdb);
            case SORTED_ARRAY -> SortedArrayAddressPresence.populateFrom(lmdb);
            case TRUNCATED_LONG_64 -> TruncatedLong64SortedArrayPresence.populateFrom(lmdb);
        };
    }

    /**
     * Creates a fresh LMDB directory and writes {@value #DB_ENTRY_COUNT} random hash160
     * entries to it. Returns the directory so the same instance can be re-opened
     * read-only by each backend.
     */
    private File createPopulatedLmdb() throws Exception {
        Path dbDir = Files.createDirectory(folder.resolve("benchmark-lmdb"));

        CLMDBConfigurationWrite writeCfg = new CLMDBConfigurationWrite();
        writeCfg.lmdbDirectory = dbDir.toFile().getAbsolutePath();
        writeCfg.initialMapSizeInMiB = 64;

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
     * Generates {@code n} random 20-byte queries using the same seeded RNG family as the
     * populator but offset to a different sub-stream, so most queries miss while still
     * being deterministic. The first byte is uniformly distributed over 0..255 so all
     * 256 SortedArray buckets get hit roughly equally.
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
}
