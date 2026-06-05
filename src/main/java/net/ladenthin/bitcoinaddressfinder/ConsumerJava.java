// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.bloom.BloomFilterAccelerator;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.HashSetAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.TruncatedLong64SortedArrayPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.crypto.ECKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-process consumer that pulls candidate public keys from a queue and matches them
 * against the LMDB persistence layer.
 */
@ToString
public class ConsumerJava implements Consumer {

    /**
     * Marker for the {@link #consumeKeysRunner} poll loop's intentional
     * InterruptedException swallow.
     *
     * <p>Cancellation of this consumer is signalled via {@link #shouldRun}, not via
     * {@link Thread#interrupt()}. {@link #interrupt()} sets {@code shouldRun = false}
     * BEFORE calling {@code shutdown()} on the executor service that interrupts the
     * worker threads; the worker's outer {@code while (shouldRun.get())} therefore
     * exits cleanly on the next iteration.
     *
     * <p>Restoring the interrupt flag here would make the next
     * {@link Thread#sleep(long)} call immediately re-throw, producing a tight CPU
     * loop until {@code shouldRun} also flips. The constant is {@code false} so the
     * if-branch is dead code (eliminated by the JIT), but the {@code interrupt()}
     * call site is preserved in source for readers and IDE navigation.
     */
    private static final boolean POLL_LOOP_RESTORES_INTERRUPT_FLAG = false;

    /** Log prefix for misses in trace-level logging. */
    public static final String MISS_PREFIX = "miss: Could not find the address: ";
    /** Log prefix for confirmed address hits. */
    public static final String HIT_PREFIX = "hit: Found the address: ";
    /** Log prefix for vanity-pattern matches. */
    public static final String VANITY_HIT_PREFIX = "vanity pattern match: ";
    /** Log prefix used by {@code safeLog} to record key details immediately on a hit. */
    public static final String HIT_SAFE_PREFIX = "hit: safe log: ";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerJava.class);

    private final KeyUtility keyUtility;
    /** Total number of address lookups performed. */
    protected final AtomicLong checkedKeys = new AtomicLong();
    /** Cumulative time spent inside the persistence's {@code containsAddress} calls (ms). */
    protected final AtomicLong checkedKeysSumOfTimeToCheckContains = new AtomicLong();
    /** Number of consumer iterations that found the queue empty. */
    protected final AtomicLong emptyConsumer = new AtomicLong();
    /** Total number of address hits found so far. */
    protected final AtomicLong hits = new AtomicLong();
    /** Wall-clock start time (epoch ms) of the consumer for statistics. */
    protected long startTime = 0;

    /** Consumer configuration. */
    protected final CConsumerJava consumerJava;

    // ScheduledExecutorService toString is verbose pool internals — not useful in aggregate logs.
    @ToString.Exclude
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * The persistence implementation; initialised in {@link #initLMDB()}. May be set to
     * {@code null} after population when the chosen accelerator does not require the
     * backing storage to stay open (see
     * {@link AddressPresence#requiresBackend()}).
     */
    protected @Nullable Persistence persistence;

    /**
     * Read-only presence chain the scan hot path queries through. Always non-null after
     * {@link #initLMDB()} returns; may be the LMDB instance itself ({@code LMDB_ONLY}),
     * a {@code BloomFilterAccelerator} wrapping it ({@code BLOOM}), or a self-contained
     * in-memory snapshot ({@code HASHSET}, {@code TRUNCATED_LONG_64}).
     */
    protected @Nullable AddressPresence lookup;

    private final PersistenceUtils persistenceUtils;

    // List of Future<Void> for the in-flight consumer iterations; toString is identity-style noise.
    @ToString.Exclude
    private final List<Future<Void>> consumers = new ArrayList<>();

    /**
     * Queue of pending public-key batches; bounded by {@code consumerJava.queueSize}.
     *
     * <p>Excluded from {@link ToString} — dumping every queued {@code PublicKeyBytes[]}
     * batch would be log-killing.
     */
    @ToString.Exclude
    protected final LinkedBlockingQueue<PublicKeyBytes[]> keysQueue;

    /** Total number of vanity-pattern hits found so far. */
    protected final AtomicLong vanityHits = new AtomicLong();

    private final @Nullable Pattern vanityPattern;

    // Lifecycle flag — uninformative in aggregate toString.
    @ToString.Exclude
    private final AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * Returns the current value of the cancellation flag — {@code true} as long as no
     * one has asked this consumer to stop, {@code false} after {@link #interrupt()}.
     *
     * <p>This is the read side of the cancellation signal documented on the class
     * (the worker loop body is {@code while (shouldRun()) { ... }}). The return value
     * is the request/intent state of the flag, NOT a thread-liveness observation:
     * returning {@code true} does not guarantee the worker thread is currently
     * executing (it may not be started yet), and returning {@code false} does not
     * guarantee the worker thread has finished (it may still be mid-iteration). The
     * flag only reflects whether cancellation has been requested.
     *
     * <p>Exposed so tests can assert the pre-/post-{@code interrupt()} flag state.
     *
     * @return {@code true} while the cancellation flag is still set to "keep
     *     running", {@code false} once {@link #interrupt()} has flipped it
     */
    public boolean shouldRun() {
        return shouldRun.get();
    }

    // ExecutorService toString is verbose pool internals — not useful in aggregate logs.
    @ToString.Exclude
    private final ExecutorService consumeKeysExecutorService;

    /**
     * Creates a new consumer with default executors: a single-thread scheduler for the
     * stats logger and a fixed-size pool sized by {@code consumerJava.threads} for the
     * key-consumer workers.
     *
     * @param consumerJava     consumer configuration
     * @param keyUtility       cryptographic helper
     * @param persistenceUtils persistence helper used to construct the LMDB layer
     */
    protected ConsumerJava(CConsumerJava consumerJava, KeyUtility keyUtility, PersistenceUtils persistenceUtils) {
        this(
                consumerJava,
                keyUtility,
                persistenceUtils,
                Executors.newSingleThreadScheduledExecutor(),
                Executors.newFixedThreadPool(consumerJava.threads));
    }

    /**
     * Test-friendly constructor that injects both executor services.
     *
     * <p>Production callers should use the 3-arg constructor above; this overload exists
     * so tests can substitute their own executors and assert on post-shutdown state
     * without reaching into the consumer's internal fields.
     *
     * @param consumerJava              consumer configuration
     * @param keyUtility                cryptographic helper
     * @param persistenceUtils          persistence helper used to construct the LMDB layer
     * @param scheduledExecutorService  scheduler used for the periodic stats logger
     * @param consumeKeysExecutorService pool used for the worker threads that drain the keys queue
     */
    @VisibleForTesting
    ConsumerJava(
            CConsumerJava consumerJava,
            KeyUtility keyUtility,
            PersistenceUtils persistenceUtils,
            ScheduledExecutorService scheduledExecutorService,
            ExecutorService consumeKeysExecutorService) {
        this.consumerJava = consumerJava;
        this.keysQueue = new LinkedBlockingQueue<>(consumerJava.queueSize);
        this.keyUtility = keyUtility;
        this.persistenceUtils = persistenceUtils;
        this.scheduledExecutorService = scheduledExecutorService;
        this.consumeKeysExecutorService = consumeKeysExecutorService;
        if (consumerJava.enableVanity && consumerJava.vanityPattern != null) {
            this.vanityPattern = Pattern.compile(consumerJava.vanityPattern);
        } else {
            vanityPattern = null;
        }
    }

    /**
     * Initialises the LMDB persistence layer and builds the address-lookup chain selected
     * by {@code consumerJava.lmdbConfigurationReadOnly.addressLookupBackend}.
     *
     * <p>If the chosen chain is self-contained
     * ({@link AddressPresence#requiresBackend()} returns {@code false}), the LMDB env is
     * closed and the {@code persistence} reference is dropped after the in-memory
     * snapshot is built; the on-disk store becomes eligible for garbage collection.
     *
     * @throws Exception if the LMDB env cannot be opened, the snapshot population fails,
     *     or the LMDB cannot be closed after population for self-contained backends
     */
    protected void initLMDB() throws Exception {
        var cfg = consumerJava.lmdbConfigurationReadOnly;
        LMDBPersistence lmdb = new LMDBPersistence(cfg, persistenceUtils);
        lmdb.init();
        persistence = lmdb;

        AddressPresence chain = buildLookupChain(lmdb, cfg.addressLookupBackend, cfg.bloomFilterFpp);
        lookup = chain;

        if (!chain.requiresBackend()) {
            LOGGER.info(
                    "Address lookup backend {} is self-contained; closing LMDB env to release on-disk resources.",
                    cfg.addressLookupBackend);
            lmdb.close();
            persistence = null;
        }
    }

    private static AddressPresence buildLookupChain(
            LMDBPersistence lmdb, AddressLookupBackend choice, double bloomFpp) {
        return switch (choice) {
            case LMDB_ONLY -> lmdb;
            case BLOOM -> BloomFilterAccelerator.populateFrom(lmdb, lmdb, bloomFpp);
            case HASHSET -> HashSetAddressPresence.populateFrom(lmdb);
            case TRUNCATED_LONG_64 -> TruncatedLong64SortedArrayPresence.populateFrom(lmdb);
        };
    }

    /**
     * Starts the periodic statistics-printing scheduler.
     */
    protected void startStatisticsTimer() {
        long period = consumerJava.printStatisticsEveryNSeconds;
        if (period <= 0) {
            throw new IllegalArgumentException("period must be greater than 0.");
        }

        startTime = System.currentTimeMillis();

        @FireAndForget("scheduler shutdown via interrupt() drives the task's stop")
        @SuppressWarnings("FutureReturnValueIgnored")
        Object unused = scheduledExecutorService.scheduleAtFixedRate(
                () -> {
                    // get transient information
                    long uptime = Math.max(System.currentTimeMillis() - startTime, 1);

                    String message = new Statistics()
                            .createStatisticsMessage(
                                    uptime,
                                    checkedKeys.get(),
                                    checkedKeysSumOfTimeToCheckContains.get(),
                                    emptyConsumer.get(),
                                    keysQueue.size(),
                                    hits.get());

                    // log the information
                    LOGGER.info(message);
                },
                period,
                period,
                TimeUnit.SECONDS);
    }

    @Override
    public void startConsumer() {
        LOGGER.debug("Starting {} consumer threads...", consumerJava.threads);
        for (int i = 0; i < consumerJava.threads; i++) {
            consumers.add(consumeKeysExecutorService.submit(() -> {
                consumeKeysRunner();
                return null;
            }));
        }
        LOGGER.debug("Successfully started {} consumer threads.", consumers.size());
    }

    /**
     * This method runs in multiple threads.
     */
    private void consumeKeysRunner() {
        LOGGER.info("start consumeKeysRunner");
        final ByteBuffer threadLocalReuseableByteBuffer =
                ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES);

        while (shouldRun.get()) {
            if (keysQueue.size() >= consumerJava.queueSize) {
                LOGGER.warn("Attention, queue is full. Please increase queue size.");
            }
            try {
                consumeKeys(threadLocalReuseableByteBuffer);
                // the consumeKeys method is looped inside, if the method returns it means the queue is empty
                emptyConsumer.incrementAndGet();
                Thread.sleep(consumerJava.delayEmptyConsumer);
            } catch (InterruptedException e) {
                // Cancellation is via shouldRun, not Thread.interrupt; see
                // POLL_LOOP_RESTORES_INTERRUPT_FLAG.
                LOGGER.warn("Consumer poll loop sleep interrupted; relying on shouldRun for shutdown.", e);
                if (POLL_LOOP_RESTORES_INTERRUPT_FLAG) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                // log every Exception because it's hard to debug and we do not break down the thread loop
                LOGGER.error("Error in consumeKeysRunner().", e);
            }
        }

        LOGGER.info("end consumeKeysRunner");
    }

    void consumeKeys(ByteBuffer threadLocalReuseableByteBuffer) {
        LOGGER.trace("consumeKeys");
        PublicKeyBytes[] publicKeyBytesArray = keysQueue.poll();
        while (publicKeyBytesArray != null) {
            for (PublicKeyBytes publicKeyBytes : publicKeyBytesArray) {
                if (publicKeyBytes.isOutsidePrivateKeyRange()) {
                    continue;
                }

                byte[] hash160Uncompressed = publicKeyBytes.getUncompressedKeyHash();
                boolean containsAddressUncompressed =
                        containsAddress(threadLocalReuseableByteBuffer, hash160Uncompressed);

                byte[] hash160Compressed = publicKeyBytes.getCompressedKeyHash();
                boolean containsAddressCompressed = containsAddress(threadLocalReuseableByteBuffer, hash160Compressed);

                if (consumerJava.runtimePublicKeyCalculationCheck) {
                    publicKeyBytes.runtimePublicKeyCalculationCheck();
                }

                if (containsAddressUncompressed) {
                    // immediately log the secret
                    safeLog(publicKeyBytes, hash160Uncompressed, hash160Compressed);
                    hits.incrementAndGet();
                    ECKey ecKeyUncompressed = ECKey.fromPrivateAndPrecalculatedPublic(
                            publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getUncompressed());
                    String hitMessageUncompressed = HIT_PREFIX + keyUtility.createKeyDetails(ecKeyUncompressed);
                    LOGGER.info(hitMessageUncompressed);
                }

                if (containsAddressCompressed) {
                    // immediately log the secret
                    safeLog(publicKeyBytes, hash160Uncompressed, hash160Compressed);
                    hits.incrementAndGet();
                    ECKey ecKeyCompressed = ECKey.fromPrivateAndPrecalculatedPublic(
                            publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getCompressed());
                    String hitMessageCompressed = HIT_PREFIX + keyUtility.createKeyDetails(ecKeyCompressed);
                    LOGGER.info(hitMessageCompressed);
                }

                if (consumerJava.enableVanity) {
                    var localVanityPattern = Objects.requireNonNull(vanityPattern);
                    String uncompressedKeyHashAsBase58 = publicKeyBytes.getUncompressedKeyHashAsBase58(keyUtility);
                    Matcher uncompressedKeyHashAsBase58Matcher =
                            localVanityPattern.matcher(uncompressedKeyHashAsBase58);
                    if (uncompressedKeyHashAsBase58Matcher.matches()) {
                        // immediately log the secret
                        safeLog(publicKeyBytes, hash160Uncompressed, hash160Compressed);
                        vanityHits.incrementAndGet();
                        ECKey ecKeyUncompressed = ECKey.fromPrivateAndPrecalculatedPublic(
                                publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getUncompressed());
                        String vanityHitMessageUncompressed =
                                VANITY_HIT_PREFIX + keyUtility.createKeyDetails(ecKeyUncompressed);
                        LOGGER.info(vanityHitMessageUncompressed);
                    }

                    String compressedKeyHashAsBase58 = publicKeyBytes.getCompressedKeyHashAsBase58(keyUtility);
                    Matcher compressedKeyHashAsBase58Matcher = vanityPattern.matcher(compressedKeyHashAsBase58);
                    if (compressedKeyHashAsBase58Matcher.matches()) {
                        // immediately log the secret
                        safeLog(publicKeyBytes, hash160Uncompressed, hash160Compressed);
                        vanityHits.incrementAndGet();
                        ECKey ecKeyCompressed = ECKey.fromPrivateAndPrecalculatedPublic(
                                publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getCompressed());
                        String vanityHitMessageCompressed =
                                VANITY_HIT_PREFIX + keyUtility.createKeyDetails(ecKeyCompressed);
                        LOGGER.info(vanityHitMessageCompressed);
                    }
                }

                if (!containsAddressUncompressed && !containsAddressCompressed) {
                    if (LOGGER.isTraceEnabled()) {
                        ECKey ecKeyUncompressed = ECKey.fromPrivateAndPrecalculatedPublic(
                                publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getUncompressed());
                        String missMessageUncompressed = MISS_PREFIX + keyUtility.createKeyDetails(ecKeyUncompressed);
                        LOGGER.trace(missMessageUncompressed);

                        ECKey ecKeyCompressed = ECKey.fromPrivateAndPrecalculatedPublic(
                                publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getCompressed());
                        String missMessageCompressed = MISS_PREFIX + keyUtility.createKeyDetails(ecKeyCompressed);
                        LOGGER.trace(missMessageCompressed);
                    }
                }
            }
            publicKeyBytesArray = keysQueue.poll();
        }
    }

    /**
     * Looks up the given hash160 in the persistence layer, reusing the supplied buffer.
     *
     * @param threadLocalReuseableByteBuffer a thread-local buffer to avoid allocation per lookup
     * @param hash160                        the 20-byte address hash to look up
     * @return {@code true} if the address is present in the database
     */
    public boolean containsAddress(ByteBuffer threadLocalReuseableByteBuffer, byte[] hash160) {
        threadLocalReuseableByteBuffer.rewind();
        threadLocalReuseableByteBuffer.put(hash160);
        threadLocalReuseableByteBuffer.flip();
        return containsAddress(threadLocalReuseableByteBuffer);
    }

    /**
     * Logs key information in a safe and robust way to avoid losing critical data
     * in case of a runtime exception.
     * <p>
     * The primary goal of this method is to ensure that if a valid secret key (i.e., a hit)
     * is found, its corresponding BigInteger value is immediately logged. Since logging a
     * BigInteger is unlikely to fail, this is the first and most essential piece of information.
     * <p>
     * Logging additional details such as the uncompressed/compressed public keys and their
     * hash160 values may theoretically trigger runtime exceptions (e.g., due to malformed data
     * or encoding issues). To mitigate the risk of losing the crucial secret value in such rare
     * cases, it is logged first.
     * <p>
     * All logs are prefixed consistently with {@code HIT_SAFE_PREFIX} to make hits easily searchable.
     *
     * @param publicKeyBytes         the public key bytes wrapper
     * @param hash160Uncompressed    the hash160 of the uncompressed public key
     * @param hash160Compressed      the hash160 of the compressed public key
     */
    private void safeLog(PublicKeyBytes publicKeyBytes, byte[] hash160Uncompressed, byte[] hash160Compressed) {
        LOGGER.info(HIT_SAFE_PREFIX + "publicKeyBytes.getSecretKey(): " + publicKeyBytes.getSecretKey());
        LOGGER.info(HIT_SAFE_PREFIX + "publicKeyBytes.getUncompressed(): "
                + Hex.encodeHexString(publicKeyBytes.getUncompressed()));
        LOGGER.info(HIT_SAFE_PREFIX + "publicKeyBytes.getCompressed(): "
                + Hex.encodeHexString(publicKeyBytes.getCompressed()));
        LOGGER.info(HIT_SAFE_PREFIX + "hash160Uncompressed: " + Hex.encodeHexString(hash160Uncompressed));
        LOGGER.info(HIT_SAFE_PREFIX + "hash160Compressed: " + Hex.encodeHexString(hash160Compressed));
    }

    private boolean containsAddress(ByteBuffer hash160AsByteBuffer) {
        long timeBefore = System.currentTimeMillis();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Time before lookup.containsAddress: " + timeBefore);
        }
        AddressPresence localLookup = Objects.requireNonNull(lookup);
        boolean containsAddress = localLookup.containsAddress(hash160AsByteBuffer);
        long timeAfter = System.currentTimeMillis();
        long timeDelta = timeAfter - timeBefore;
        checkedKeys.incrementAndGet();
        checkedKeysSumOfTimeToCheckContains.addAndGet(timeDelta);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Time after lookup.containsAddress: " + timeAfter);
            LOGGER.trace("Time delta: " + timeDelta);
        }
        return containsAddress;
    }

    @Override
    public void consumeKeys(PublicKeyBytes[] publicKeyBytes) throws InterruptedException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("keysQueue.put(publicKeyBytes) with length: " + publicKeyBytes.length);
        }

        keysQueue.put(publicKeyBytes);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("keysQueue.size(): " + keysQueue.size());
        }
    }

    /**
     * Initiates a graceful shutdown of the consumer:
     * <ul>
     *   <li>Stops internal execution by setting the control flag</li>
     *   <li>Shuts down scheduled tasks and consumer thread pool</li>
     *   <li>Waits for all consumer threads to finish within a defined timeout</li>
     *   <li>Logs any unclean terminations</li>
     *   <li>Closes LMDB persistence and releases resources</li>
     * </ul>
     *
     * This method ensures a clean and deterministic shutdown without relying on thread interruption signals.
     */
    @Override
    public void interrupt() {
        LOGGER.info("Interrupt initiated: stopping consumer execution...");
        shouldRun.set(false);
        scheduledExecutorService.shutdown();
        consumeKeysExecutorService.shutdown();
        LOGGER.info(
                "Waiting for termination of {} consumer threads (timeout: {} seconds)...",
                consumers.size(),
                consumerJava.awaitQueueEmptySeconds);
        try {
            boolean terminated =
                    consumeKeysExecutorService.awaitTermination(consumerJava.awaitQueueEmptySeconds, TimeUnit.SECONDS);
            if (!terminated) {
                LOGGER.warn("Timeout reached. Some consumer threads may not have terminated cleanly.");
            }
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while awaiting consumer termination.", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        try {
            if (persistence != null) {
                Persistence localPersistence = Objects.requireNonNull(persistence);
                localPersistence.close();
                persistence = null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        LOGGER.debug("Interrupt complete: resources released and persistence closed.");
    }

    @VisibleForTesting
    int keysQueueSize() {
        return keysQueue.size();
    }
}
