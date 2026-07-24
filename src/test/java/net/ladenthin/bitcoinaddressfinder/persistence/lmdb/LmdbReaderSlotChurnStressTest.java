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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
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
import org.lmdbjava.ByteBufferProxy;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in <b>thread-churn</b> reproduction harness for the native crash tracked in issue #50
 * (and upstream lmdbjava/lmdbjava#253): a sporadic {@code SIGSEGV} in {@code mdb_txn_renew0}
 * when read transactions are opened concurrently.
 *
 * <p><b>Why this differs from {@link LmdbConcurrentReadStressTest}.</b> That sibling harness
 * uses a <em>fixed</em> thread pool: every worker thread lives for the whole run, so each grabs
 * its LMDB reader slot exactly once and never exits mid-run. The {@code mdb_txn_renew0} crash
 * lives in reader-slot (re)acquisition — without {@code MDB_NOTLS} a slot is bound to a thread
 * via a pthread TLS key and freed by that key's destructor <em>when the thread exits</em>. A
 * fixed pool therefore almost never exercises the slot free / realloc race, which is the leading
 * theory for why the fixed-pool harness has never reproduced the crash. This harness instead
 * <b>continuously spawns short-lived threads</b> (default: one lookup, then the thread dies),
 * keeping {@code concurrency} of them live at once — maximising slot allocation racing thread-exit
 * slot-freeing, the exact {@code mdb_txn_renew0} window.
 *
 * <p><b>This is a crash-provoker, not a normal assertion test.</b> A real reproduction is a
 * native segfault that kills the JVM (uncatchable as a Java exception) and is probabilistic, so a
 * <em>passing</em> in-JVM run proves nothing; only a crash is meaningful. That is why it is
 * <b>off by default</b>. To get a reproduction <em>rate</em> across many forked JVMs, use
 * {@code LmdbCrashReproDriverTest}, which forks {@link #main(String[])} below.
 *
 * <p><b>Enable and run (prod mode — reproduces the bug as shipped):</b>
 *
 * <pre>
 * mvn test -Dtest=LmdbReaderSlotChurnStressTest \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbChurn=true \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbChurn.concurrency=128 \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbChurn.seconds=20 \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbChurn.lookupsPerThread=1
 * </pre>
 *
 * <p><b>Raw mode</b> (Steps 2 &amp; 5 — bisect the trigger and validate the fix) opens its own
 * {@link Env} against the same test database with tunable flags, bypassing the production flags
 * that {@link LMDBPersistence} hardcodes. Set {@code .mode=raw} and toggle {@code .notls} /
 * {@code .nolock} / {@code .maxReaders}. Expectation: {@code .notls=true} with
 * {@code .maxReaders >= concurrency} removes the crash.
 *
 * <pre>
 * mvn test -Dtest=LmdbReaderSlotChurnStressTest \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbChurn=true \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbChurn.mode=raw \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbChurn.notls=true \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbChurn.maxReaders=256
 * </pre>
 */
public class LmdbReaderSlotChurnStressTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LmdbReaderSlotChurnStressTest.class);

    /** System property that enables this opt-in test. */
    public static final String PROP_ENABLE = "net.ladenthin.bitcoinaddressfinder.lmdbChurn";
    /** Max number of concurrently-live short-lived reader threads (default 128). */
    public static final String PROP_CONCURRENCY = PROP_ENABLE + ".concurrency";
    /** Run duration in seconds (default 20). */
    public static final String PROP_SECONDS = PROP_ENABLE + ".seconds";
    /** Lookups each short-lived thread performs before exiting (default 1 → maximal churn). */
    public static final String PROP_LOOKUPS_PER_THREAD = PROP_ENABLE + ".lookupsPerThread";
    /** {@code prod} (real {@code containsAddress}, shipped flags) or {@code raw} (own env). */
    public static final String PROP_MODE = PROP_ENABLE + ".mode";
    /** Raw mode only: add {@code EnvFlags.MDB_NOTLS} (default false). */
    public static final String PROP_NOTLS = PROP_ENABLE + ".notls";
    /** Raw mode only: add {@code EnvFlags.MDB_NOLOCK} (default true, mirrors production). */
    public static final String PROP_NOLOCK = PROP_ENABLE + ".nolock";
    /** Raw mode only: {@code Env.Builder.setMaxReaders} (default 0 → leave lmdbjava default). */
    public static final String PROP_MAX_READERS = PROP_ENABLE + ".maxReaders";
    /**
     * Scenario selector: {@code churn} (steady-state reader-slot churn, the default) or
     * {@code closerace} (close the env while reads are in flight — the actual issue #50 crash,
     * a use-after-unmap: a consumer thread's {@code containsAddress} → {@code mdb_txn_begin}
     * dereferences the LMDB mmap after {@code Env.close()} has unmapped it during shutdown).
     */
    public static final String PROP_SCENARIO = PROP_ENABLE + ".scenario";
    /** {@code closerace} only: number of reader threads hammering {@code containsAddress}. */
    public static final String PROP_READER_THREADS = PROP_ENABLE + ".readerThreads";
    /** {@code closerace} only: warm-up before the env is closed, in ms (default 1000). */
    public static final String PROP_WARMUP_MILLIS = PROP_ENABLE + ".warmupMillis";

    private static final int DEFAULT_CONCURRENCY = 128;
    private static final int DEFAULT_SECONDS = 20;
    private static final int DEFAULT_LOOKUPS_PER_THREAD = 1;
    private static final int DEFAULT_READER_THREADS = 128;
    private static final int DEFAULT_WARMUP_MILLIS = 1000;
    private static final int CORPUS_SIZE = 256;
    private static final int HASH160_BYTES = OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES;
    private static final int DRAIN_AWAIT_SECONDS = 60;
    private static final int READER_JOIN_MILLIS = 2000;

    /**
     * Physical LMDB sub-database name, mirroring the private
     * {@code LMDBPersistence.DB_NAME_HASH160_TO_COINT}. Duplicated (not shared) so this
     * harness stays test-only; if the production name ever changes, raw mode fails loudly
     * when opening the dbi.
     */
    private static final String DB_NAME_HASH160_TO_COINT = "hash160toCoin";

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);

    @SuppressWarnings("unused")
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));

    /** Creates a new {@link LmdbReaderSlotChurnStressTest}. */
    public LmdbReaderSlotChurnStressTest() {}

    @Test
    public void readerSlotChurn_shortLivedThreads_noJavaLevelFailure() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean(PROP_ENABLE),
                "opt-in LMDB reader-slot churn test; enable with -D" + PROP_ENABLE + "=true");
        Assumptions.assumeFalse(
                "closerace".equalsIgnoreCase(System.getProperty(PROP_SCENARIO, "churn")),
                "scenario=closerace selects the close-during-read test instead");
        new LMDBPlatformAssume().assumeLMDBExecution();

        final int concurrency = Integer.getInteger(PROP_CONCURRENCY, DEFAULT_CONCURRENCY);
        final int seconds = Integer.getInteger(PROP_SECONDS, DEFAULT_SECONDS);
        final int lookupsPerThread = Integer.getInteger(PROP_LOOKUPS_PER_THREAD, DEFAULT_LOOKUPS_PER_THREAD);
        final String mode = System.getProperty(PROP_MODE, "prod");

        File lmdbDirectory = buildTestLmdb(folder, persistenceUtils);
        byte[][] corpus = buildHash160Corpus(CORPUS_SIZE);

        ChurnResult result;
        if ("raw".equalsIgnoreCase(mode)) {
            boolean notls = Boolean.getBoolean(PROP_NOTLS);
            boolean nolock = System.getProperty(PROP_NOLOCK) == null || Boolean.getBoolean(PROP_NOLOCK);
            int maxReaders = Integer.getInteger(PROP_MAX_READERS, 0);
            LOGGER.info(
                    "churn mode=raw notls={} nolock={} maxReaders={} concurrency={} seconds={} lookupsPerThread={}",
                    notls,
                    nolock,
                    maxReaders,
                    concurrency,
                    seconds,
                    lookupsPerThread);
            result = runRawChurn(
                    lmdbDirectory, corpus, notls, nolock, maxReaders, concurrency, seconds, lookupsPerThread);
        } else {
            LOGGER.info(
                    "churn mode=prod concurrency={} seconds={} lookupsPerThread={}",
                    concurrency,
                    seconds,
                    lookupsPerThread);
            result = runProdChurn(lmdbDirectory, persistenceUtils, corpus, concurrency, seconds, lookupsPerThread);
        }

        LOGGER.info(
                "LMDB reader-slot churn: {} reads, {} threads spawned, {} Java-level errors",
                result.reads,
                result.threadsSpawned,
                result.errors.size());

        assertThat(
                "concurrent LMDB reads raised Java-level throwables: " + result.errors,
                result.errors.isEmpty(),
                is(equalTo(true)));
        assertThat("expected the churn loop to perform reads", result.reads > 0L, is(equalTo(true)));
    }

    /**
     * The actual issue #50 crash: close the LMDB env while reader threads are mid-{@code
     * containsAddress}. A reader inside the native {@code mdb_txn_begin} when {@code Env.close()}
     * unmaps the mmap dereferences freed memory → {@code SIGSEGV (SEGV_MAPERR)} in {@code
     * mdb_txn_renew0}. <b>A reproduction crashes this JVM</b>; run it under {@code
     * LmdbCrashReproDriverTest} (scenario=closerace) for a crash rate instead of a killed fork.
     */
    @Test
    public void closeDuringRead_race_reproducesUseAfterUnmap() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean(PROP_ENABLE),
                "opt-in LMDB close-during-read test; enable with -D" + PROP_ENABLE + "=true");
        Assumptions.assumeTrue(
                "closerace".equalsIgnoreCase(System.getProperty(PROP_SCENARIO, "churn")),
                "enable with -D" + PROP_SCENARIO + "=closerace");
        new LMDBPlatformAssume().assumeLMDBExecution();

        final int readerThreads = Integer.getInteger(PROP_READER_THREADS, DEFAULT_READER_THREADS);
        final int warmupMillis = Integer.getInteger(PROP_WARMUP_MILLIS, DEFAULT_WARMUP_MILLIS);

        File lmdbDirectory = buildTestLmdb(folder, persistenceUtils);
        byte[][] corpus = buildHash160Corpus(CORPUS_SIZE);
        LOGGER.info("closerace readerThreads={} warmupMillis={}", readerThreads, warmupMillis);

        ChurnResult result =
                runCloseDuringReadRace(lmdbDirectory, persistenceUtils, corpus, readerThreads, warmupMillis);

        // If this JVM survived, no native crash occurred this run (the race did not land). Only
        // UNEXPECTED Java throwables (not the benign post-close AlreadyClosedException) are failures.
        LOGGER.info(
                "closerace survived: {} reads across {} readers, {} unexpected Java-level errors",
                result.reads,
                result.threadsSpawned,
                result.errors.size());
        assertThat(
                "close-during-read raised unexpected Java-level throwables: " + result.errors,
                result.errors.isEmpty(),
                is(equalTo(true)));
    }

    // ------------------------------------------------------------------------------------------
    // Child-JVM entry point (forked by LmdbCrashReproDriverTest to get a crash rate).
    // ------------------------------------------------------------------------------------------

    /**
     * Standalone entry point for a forked child JVM. Opens the (already-built) read-only LMDB at
     * {@code args[0]} via the real {@link LMDBPersistence} (production flags — reproduces the bug
     * as shipped) and runs the prod-mode churn. Exits 0 on a clean finish; a native
     * {@code mdb_txn_renew0} crash kills this JVM with a non-zero / signal exit that the parent
     * driver counts.
     *
     * <p>Args: {@code <lmdbDir> [concurrency] [seconds] [lookupsPerThread]}. The scenario and its
     * close-race parameters come from system properties ({@link #PROP_SCENARIO},
     * {@link #PROP_READER_THREADS}, {@link #PROP_WARMUP_MILLIS}) so the driver can forward them
     * as {@code -D} flags.
     *
     * @param args the child parameters
     * @throws Exception if the LMDB cannot be opened
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: LmdbReaderSlotChurnStressTest <lmdbDir> [concurrency] [seconds] [lookups]");
            System.exit(2);
            return;
        }
        File lmdbDirectory = new File(args[0]);
        Network net = new NetworkParameterFactory().getNetwork();
        PersistenceUtils utils = new PersistenceUtils(net);
        byte[][] corpus = buildHash160Corpus(CORPUS_SIZE);

        String scenario = System.getProperty(PROP_SCENARIO, "churn");
        ChurnResult result;
        if ("closerace".equalsIgnoreCase(scenario)) {
            int readerThreads = Integer.getInteger(PROP_READER_THREADS, DEFAULT_READER_THREADS);
            int warmupMillis = Integer.getInteger(PROP_WARMUP_MILLIS, DEFAULT_WARMUP_MILLIS);
            result = runCloseDuringReadRace(lmdbDirectory, utils, corpus, readerThreads, warmupMillis);
            System.out.println("child closerace done: reads=" + result.reads
                    + " readers=" + result.threadsSpawned
                    + " unexpectedJavaErrors=" + result.errors.size());
        } else {
            int concurrency = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_CONCURRENCY;
            int seconds = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_SECONDS;
            int lookupsPerThread = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_LOOKUPS_PER_THREAD;
            result = runProdChurn(lmdbDirectory, utils, corpus, concurrency, seconds, lookupsPerThread);
            System.out.println("child churn done: reads=" + result.reads
                    + " threadsSpawned=" + result.threadsSpawned
                    + " javaErrors=" + result.errors.size());
        }
        System.exit(result.errors.isEmpty() ? 0 : 3);
    }

    // ------------------------------------------------------------------------------------------
    // Close-during-read race (the actual issue #50 crash).
    // ------------------------------------------------------------------------------------------

    /**
     * Reproduces issue #50: start {@code readerThreads} threads hammering the real
     * {@code containsAddress} in tight loops, let them reach steady state, then close the env
     * <b>while reads are still in flight</b> — exactly the {@code ConsumerJava.interrupt()}
     * path where {@code awaitTermination} times out and {@code persistence.close()} runs with
     * live readers. A reader inside the native {@code mdb_txn_begin} when {@code Env.close()}
     * unmaps the mmap crashes this JVM with {@code SIGSEGV (SEGV_MAPERR)} in
     * {@code mdb_txn_renew0}. If no reader is mid-native-call at the instant of close, the JVM
     * survives and readers see the benign {@code AlreadyClosedException} (ignored here).
     *
     * <p>Deliberately uses raw {@link Thread}: readers must be genuinely concurrent with the
     * close call, and {@link LMDBPersistence#close()} performs no reader coordination — that
     * missing coordination is the bug.
     */
    @SuppressWarnings("CatchAndPrintStackTrace")
    private static ChurnResult runCloseDuringReadRace(
            File lmdbDirectory, PersistenceUtils persistenceUtils, byte[][] corpus, int readerThreads, int warmupMillis)
            throws InterruptedException {
        CLMDBConfigurationReadOnly config = new CLMDBConfigurationReadOnly();
        config.lmdbDirectory = lmdbDirectory.getAbsolutePath();
        LMDBPersistence persistence = new LMDBPersistence(config, persistenceUtils);
        persistence.init();

        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicBoolean closing = new AtomicBoolean(false);
        final AtomicLong reads = new AtomicLong();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final List<Thread> readers = new ArrayList<>(readerThreads);

        for (int i = 0; i < readerThreads; i++) {
            final long seed = i;
            Thread reader = new Thread(
                    () -> {
                        final ByteBuffer key = ByteBuffer.allocateDirect(HASH160_BYTES);
                        final Random random = new Random(seed);
                        while (!stop.get()) {
                            try {
                                key.rewind();
                                key.put(corpus[random.nextInt(corpus.length)]);
                                key.flip();
                                persistence.containsAddress(key);
                                reads.incrementAndGet();
                            } catch (Throwable t) {
                                // After close() the env is gone: AlreadyClosedException (and friends)
                                // are the EXPECTED Java-level outcome, not a defect. Only throwables
                                // seen while the env is still open count as unexpected.
                                if (!closing.get()) {
                                    errors.add(t);
                                }
                                return;
                            }
                        }
                    },
                    "lmdb-reader-" + seed);
            reader.setDaemon(true);
            reader.start();
            readers.add(reader);
        }

        // Let readers saturate the native mdb_txn_begin path before the race.
        Thread.sleep(warmupMillis);

        // THE RACE: close the env out from under live readers (no drain first — that is the bug).
        closing.set(true);
        persistence.close();

        stop.set(true);
        for (Thread reader : readers) {
            reader.join(READER_JOIN_MILLIS);
        }
        return new ChurnResult(reads.get(), readerThreads, errors);
    }

    // ------------------------------------------------------------------------------------------
    // Churn engines.
    // ------------------------------------------------------------------------------------------

    private static ChurnResult runProdChurn(
            File lmdbDirectory,
            PersistenceUtils persistenceUtils,
            byte[][] corpus,
            int concurrency,
            int seconds,
            int lookupsPerThread)
            throws InterruptedException {
        CLMDBConfigurationReadOnly config = new CLMDBConfigurationReadOnly();
        config.lmdbDirectory = lmdbDirectory.getAbsolutePath();
        LMDBPersistence persistence = new LMDBPersistence(config, persistenceUtils);
        persistence.init();
        try {
            return churn(concurrency, seconds, lookupsPerThread, (key, random) -> {
                key.rewind();
                key.put(corpus[random.nextInt(corpus.length)]);
                key.flip();
                persistence.containsAddress(key);
            });
        } finally {
            persistence.close();
        }
    }

    private static ChurnResult runRawChurn(
            File lmdbDirectory,
            byte[][] corpus,
            boolean notls,
            boolean nolock,
            int maxReaders,
            int concurrency,
            int seconds,
            int lookupsPerThread)
            throws InterruptedException {
        List<EnvFlags> flags = new ArrayList<>();
        flags.add(EnvFlags.MDB_RDONLY_ENV);
        if (nolock) {
            flags.add(EnvFlags.MDB_NOLOCK);
        }
        if (notls) {
            flags.add(EnvFlags.MDB_NOTLS);
        }
        Env.Builder<ByteBuffer> builder =
                Env.create(ByteBufferProxy.PROXY_OPTIMAL).setMaxDbs(1);
        if (maxReaders > 0) {
            builder.setMaxReaders(maxReaders);
        }
        try (Env<ByteBuffer> env = builder.open(lmdbDirectory, flags.toArray(new EnvFlags[0]))) {
            Dbi<ByteBuffer> dbi = env.openDbi(DB_NAME_HASH160_TO_COINT);
            return churn(concurrency, seconds, lookupsPerThread, (key, random) -> {
                key.rewind();
                key.put(corpus[random.nextInt(corpus.length)]);
                key.flip();
                try (Txn<ByteBuffer> txn = env.txnRead()) {
                    dbi.get(txn, key);
                }
            });
        }
    }

    /**
     * The churn core: continuously spawn short-lived reader threads, keeping {@code concurrency}
     * of them live at once, for {@code seconds}. Each thread performs {@code lookupsPerThread}
     * lookups with its own thread-confined direct key buffer, then exits — so OS threads are
     * created and destroyed at a high rate while concurrency stays saturated.
     *
     * <p>Deliberately uses raw {@link Thread} (not an executor): the whole point is to churn
     * thread <em>lifecycles</em> so the pthread-TLS reader-slot destructor races slot allocation.
     * A pooled executor would reuse threads and defeat the reproduction.
     */
    @SuppressWarnings("BusyWait")
    private static ChurnResult churn(int concurrency, int seconds, int lookupsPerThread, Lookup lookup)
            throws InterruptedException {
        final Semaphore permits = new Semaphore(concurrency);
        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicLong reads = new AtomicLong();
        final AtomicLong threadsSpawned = new AtomicLong();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final long deadlineNanos = System.nanoTime() + (long) seconds * 1_000_000_000L;

        long seed = 0;
        while (System.nanoTime() < deadlineNanos && errors.isEmpty()) {
            permits.acquire();
            if (System.nanoTime() >= deadlineNanos || !errors.isEmpty()) {
                permits.release();
                break;
            }
            final long threadSeed = seed++;
            Thread worker = new Thread(
                    () -> {
                        final ByteBuffer key = ByteBuffer.allocateDirect(HASH160_BYTES);
                        final Random random = new Random(threadSeed);
                        try {
                            for (int i = 0; i < lookupsPerThread && !stop.get(); i++) {
                                lookup.run(key, random);
                                reads.incrementAndGet();
                            }
                        } catch (Throwable t) {
                            errors.add(t);
                        } finally {
                            permits.release();
                        }
                    },
                    "lmdb-churn-" + threadSeed);
            worker.setDaemon(true);
            worker.start();
            threadsSpawned.incrementAndGet();
        }

        stop.set(true);
        // Drain: reacquire every permit → every spawned worker has finished and exited.
        boolean drained = permits.tryAcquire(concurrency, DRAIN_AWAIT_SECONDS, TimeUnit.SECONDS);
        if (!drained) {
            errors.add(new IllegalStateException("churn workers did not drain within " + DRAIN_AWAIT_SECONDS + "s"));
        }
        return new ChurnResult(reads.get(), threadsSpawned.get(), errors);
    }

    /** A single thread-confined lookup: fill {@code key} from the corpus and query LMDB. */
    @FunctionalInterface
    private interface Lookup {
        void run(ByteBuffer key, Random random) throws Exception;
    }

    private static final class ChurnResult {
        final long reads;
        final long threadsSpawned;
        final List<Throwable> errors;

        ChurnResult(long reads, long threadsSpawned, List<Throwable> errors) {
            this.reads = reads;
            this.threadsSpawned = threadsSpawned;
            this.errors = errors;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Fixtures.
    // ------------------------------------------------------------------------------------------

    /**
     * Builds the small read-only test LMDB used by every mode.
     *
     * @param folder the temp directory to build into
     * @param persistenceUtils the persistence helpers bound to the test network
     * @return the LMDB directory
     * @throws Exception if the fixture LMDB cannot be created
     */
    static File buildTestLmdb(Path folder, PersistenceUtils persistenceUtils) throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        return testAddressesLMDB.createTestLMDB(folder, testAddresses, true, false);
    }

    /**
     * Builds a corpus of 20-byte hash160 values (a mix of compressed/uncompressed hashes from
     * distinct private keys). Most are misses against the tiny test LMDB — fine, because every
     * lookup still opens a read transaction and performs a {@code Dbi.get}, which is the crash
     * path under test.
     *
     * @param count number of hash160 entries to generate
     * @return the hash160 corpus
     */
    static byte[][] buildHash160Corpus(int count) {
        byte[][] corpus = new byte[count][];
        for (int i = 0; i < count; i++) {
            PublicKeyBytes publicKeyBytes = PublicKeyBytes.fromPrivate(BigInteger.valueOf(1000L + i));
            corpus[i] = (i % 2 == 0) ? publicKeyBytes.getUncompressedKeyHash() : publicKeyBytes.getCompressedKeyHash();
        }
        return corpus;
    }
}
