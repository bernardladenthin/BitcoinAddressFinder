// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.lmdb;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.ladenthin.bitcoinaddressfinder.LMDBPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in concurrency stress harness for the read-only LMDB path, targeting the native
 * crash tracked in issue #50 (and upstream lmdbjava/lmdbjava#253): a sporadic {@code SIGSEGV}
 * in {@code mdb_txn_renew0} when many threads open read transactions concurrently.
 *
 * <p><b>This is a crash-provoker, not a normal assertion test.</b> A real reproduction is a
 * native segfault that kills the JVM (it cannot be caught as a Java exception), and it is
 * probabilistic — so a passing run proves nothing, only a crash is meaningful. For that
 * reason it is <b>off by default</b> and must be explicitly enabled:
 *
 * <pre>
 * mvn test -Dtest=LmdbConcurrentReadStressTest \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbStressTest=true \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbStressTest.threads=64 \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbStressTest.seconds=30
 * </pre>
 *
 * <p>Intended use: run it (ideally repeatedly) before and after changing the read-env flags
 * ({@code EnvFlags.MDB_NOTLS} + a {@code maxReaders} {@code >=} thread count, per the
 * lmdbjava maintainer guidance on #253) to gauge whether the change removes the crash.
 */
public class LmdbConcurrentReadStressTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LmdbConcurrentReadStressTest.class);

    /** System property that enables this opt-in test. */
    public static final String PROP_ENABLE = "net.ladenthin.bitcoinaddressfinder.lmdbStressTest";
    /** System property overriding the number of concurrent reader threads (default 32). */
    public static final String PROP_THREADS = PROP_ENABLE + ".threads";
    /** System property overriding the run duration in seconds (default 10). */
    public static final String PROP_SECONDS = PROP_ENABLE + ".seconds";

    private static final int DEFAULT_THREADS = 32;
    private static final int DEFAULT_SECONDS = 10;
    private static final int CORPUS_SIZE = 256;
    private static final int SHUTDOWN_AWAIT_SECONDS = 30;

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);
    @SuppressWarnings("unused")
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));

    /** Creates a new {@link LmdbConcurrentReadStressTest}. */
    public LmdbConcurrentReadStressTest() {}

    @Test
    public void concurrentReads_manyThreads_noJavaLevelFailure() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean(PROP_ENABLE),
                "opt-in LMDB concurrency stress test; enable with -D" + PROP_ENABLE + "=true");
        new LMDBPlatformAssume().assumeLMDBExecution();

        final int threads = Integer.getInteger(PROP_THREADS, DEFAULT_THREADS);
        final int seconds = Integer.getInteger(PROP_SECONDS, DEFAULT_SECONDS);

        // Build a small read-only LMDB to read from.
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, false);

        CLMDBConfigurationReadOnly config = new CLMDBConfigurationReadOnly();
        config.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        LMDBPersistence persistence = new LMDBPersistence(config, persistenceUtils);
        persistence.init();

        final byte[][] hash160Corpus = buildHash160Corpus(CORPUS_SIZE);
        final AtomicBoolean keepRunning = new AtomicBoolean(true);
        final AtomicLong totalReads = new AtomicLong();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                final long seed = t;
                pool.submit(() -> {
                    // Each thread uses its own direct key buffer and opens its own read txn
                    // per lookup (the same pattern ConsumerJava uses).
                    final ByteBuffer key =
                            ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES);
                    final Random random = new Random(seed);
                    try {
                        while (keepRunning.get()) {
                            byte[] hash160 = hash160Corpus[random.nextInt(hash160Corpus.length)];
                            key.rewind();
                            key.put(hash160);
                            key.flip();
                            persistence.containsAddress(key);
                            totalReads.incrementAndGet();
                        }
                    } catch (Throwable e) {
                        errors.add(e);
                    }
                });
            }

            Thread.sleep((long) seconds * 1000L);
            keepRunning.set(false);
            pool.shutdown();
            pool.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            persistence.close();
        }

        LOGGER.info(
                "LMDB concurrent-read stress: {} reads across {} threads in {}s, {} Java-level errors",
                totalReads.get(),
                threads,
                seconds,
                errors.size());

        assertThat("concurrent LMDB reads raised Java-level throwables: " + errors, errors.isEmpty(), is(equalTo(true)));
        assertThat("expected the stress loop to perform reads", totalReads.get() > 0L, is(equalTo(true)));
    }

    /**
     * Builds a corpus of 20-byte hash160 values (a mix of compressed/uncompressed hashes
     * derived from distinct private keys). Most are misses against the tiny test LMDB, which
     * is fine — every lookup still opens a read transaction and performs a {@code Dbi.get},
     * which is the path under test.
     *
     * @param count number of hash160 entries to generate
     * @return the hash160 corpus
     */
    private byte[][] buildHash160Corpus(int count) {
        byte[][] corpus = new byte[count][];
        for (int i = 0; i < count; i++) {
            PublicKeyBytes publicKeyBytes = PublicKeyBytes.fromPrivate(BigInteger.valueOf(1000L + i));
            corpus[i] = (i % 2 == 0) ? publicKeyBytes.getUncompressedKeyHash() : publicKeyBytes.getCompressedKeyHash();
        }
        return corpus;
    }
}
