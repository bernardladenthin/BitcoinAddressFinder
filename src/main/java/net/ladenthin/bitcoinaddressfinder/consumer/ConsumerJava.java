// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.consumer;

import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import net.ladenthin.bitcoinaddressfinder.core.FireAndForget;
import net.ladenthin.bitcoinaddressfinder.core.InterruptedRuntimeException;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.bloom.BloomFilterAccelerator;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuseAccelerator;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.HashSetAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.TruncatedLong64SortedArrayPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.statistics.Statistics;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
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
     * InterruptedException swallow. Cancellation is via {@link #shouldRun}, not via
     * {@link Thread#interrupt()}. Restoring the flag would make the next
     * {@link LinkedBlockingQueue#poll(long, TimeUnit)} call immediately re-throw,
     * producing a tight CPU loop until {@code shouldRun} also flips. The constant
     * is {@code false} so the if-branch is dead code (eliminated by the JIT) but
     * kept in source for readers and IDE navigation.
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
    /**
     * Number of consume cycles in which the consumer found no work and was therefore ready
     * for more — the keys queue was empty for the entire wait window (nothing drained and
     * the timed {@code poll} timed out).
     *
     * <p><b>A rising value is normal and healthy</b>, not a problem: an empty queue means
     * the CPU drains everything the producers generate and has spare capacity, ready to
     * consume the next batch immediately. For GPU scanning this counter climbing (with
     * {@code keysQueue} near empty) is the expected operating point. It is only worth
     * attention if you expected the producers to saturate the CPU and they do not, or if
     * <b>nothing</b> is producing (a stalled or misconfigured producer also shows up as a
     * consistently ready consumer, alongside zero throughput). The genuine bottleneck
     * warning is {@link #producerBlockedCount}.
     *
     * <p>Counted per consumer thread; with multiple threads this is a heuristic gauge,
     * not exact accounting.
     */
    protected final AtomicLong consumerReadyCount = new AtomicLong();
    /**
     * Number of times a producer reached a <b>full</b> keys queue when enqueuing a batch
     * (the bounded queue had no remaining capacity, so {@code put} must block until a
     * slot frees). A rising value means the consumer/CPU is too slow to drain what the
     * producers generate (CPU-bound). Stays near zero when the consumer keeps up. Sampled
     * at enqueue time across all producer threads; a heuristic gauge, not exact accounting.
     */
    protected final AtomicLong producerBlockedCount = new AtomicLong();
    /**
     * Shared runtime metrics sink. Producers write per-producer batch counts here and the
     * statistics line reads them; also exposes the live running-producer gauge. Excluded
     * from {@link ToString} — shared aggregate, not part of this consumer's identity.
     */
    @ToString.Exclude
    protected final RuntimeStatistics runtimeStatistics;
    /** Total number of address hits found so far. */
    protected final AtomicLong hits = new AtomicLong();
    /**
     * Wall-clock start time (epoch ms) of the consumer for statistics. Volatile so the
     * single write in {@link #startStatisticsTimer()} (caller thread) publishes to the
     * scheduled-executor task that reads it on every tick (fb-contrib AT_NONATOMIC_64BIT_PRIMITIVE).
     */
    protected volatile long startTime = 0;

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
    public ConsumerJava(CConsumerJava consumerJava, KeyUtility keyUtility, PersistenceUtils persistenceUtils) {
        this(consumerJava, keyUtility, persistenceUtils, new RuntimeStatistics());
    }

    /**
     * Production constructor that injects the shared {@link RuntimeStatistics} so the
     * statistics line can report per-producer batch counts and the running-producer gauge.
     *
     * @param consumerJava     consumer configuration
     * @param keyUtility       cryptographic helper
     * @param persistenceUtils persistence helper used to construct the LMDB layer
     * @param runtimeStatistics shared runtime metrics sink (also written by the producers)
     */
    public ConsumerJava(
            CConsumerJava consumerJava,
            KeyUtility keyUtility,
            PersistenceUtils persistenceUtils,
            RuntimeStatistics runtimeStatistics) {
        this(
                consumerJava,
                keyUtility,
                persistenceUtils,
                runtimeStatistics,
                Executors.newSingleThreadScheduledExecutor(),
                Executors.newFixedThreadPool(consumerJava.threads));
    }

    /**
     * Test-friendly constructor that injects the runtime metrics and both executor services.
     *
     * <p>Production callers should use the 3- or 4-arg constructors above; this overload
     * exists so tests can substitute their own executors and assert on post-shutdown state
     * without reaching into the consumer's internal fields.
     *
     * @param consumerJava              consumer configuration
     * @param keyUtility                cryptographic helper
     * @param persistenceUtils          persistence helper used to construct the LMDB layer
     * @param runtimeStatistics         shared runtime metrics sink
     * @param scheduledExecutorService  scheduler used for the periodic stats logger
     * @param consumeKeysExecutorService pool used for the worker threads that drain the keys queue
     */
    @VisibleForTesting
    ConsumerJava(
            CConsumerJava consumerJava,
            KeyUtility keyUtility,
            PersistenceUtils persistenceUtils,
            RuntimeStatistics runtimeStatistics,
            ScheduledExecutorService scheduledExecutorService,
            ExecutorService consumeKeysExecutorService) {
        this.consumerJava = consumerJava;
        this.keysQueue = new LinkedBlockingQueue<>(consumerJava.queueSize);
        this.keyUtility = keyUtility;
        this.persistenceUtils = persistenceUtils;
        this.runtimeStatistics = runtimeStatistics;
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
    public void initLMDB() throws Exception {
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

    /**
     * Returns the GPU-upload payload for the active Binary Fuse 8 filter, if the configured
     * address-lookup backend built one.
     *
     * <p>Exposed so the engine ({@code Finder}) can read the filter the consumer built and route
     * it to the OpenCL producers for VRAM upload (the consumer never touches the OpenCL layer
     * directly). Returns {@link Optional#empty()} for every backend other than
     * {@code BINARY_FUSE_8} and before {@link #initLMDB()} has run.
     *
     * @return the Binary Fuse 8 GPU-upload payload, or empty if the active lookup is not a
     *     {@link BinaryFuseAccelerator} wrapping a {@link BinaryFuse8AddressPresence}
     */
    public Optional<BinaryFuse8GpuFilterData> getGpuFilterData() {
        AddressPresence localLookup = lookup;
        if (localLookup instanceof BinaryFuseAccelerator accelerator) {
            return accelerator.getGpuFilterData();
        }
        return Optional.empty();
    }

    private static AddressPresence buildLookupChain(
            LMDBPersistence lmdb, AddressLookupBackend choice, double bloomFpp) {
        return switch (choice) {
            case LMDB_ONLY -> lmdb;
            case BLOOM -> BloomFilterAccelerator.populateFrom(lmdb, lmdb, bloomFpp);
            case HASHSET -> HashSetAddressPresence.populateFrom(lmdb);
            case TRUNCATED_LONG_64 -> TruncatedLong64SortedArrayPresence.populateFrom(lmdb);
            case BINARY_FUSE_8 -> new BinaryFuseAccelerator(BinaryFuse8AddressPresence.populateFrom(lmdb), lmdb);
            case BINARY_FUSE_16 -> new BinaryFuseAccelerator(BinaryFuse16AddressPresence.populateFrom(lmdb), lmdb);
        };
    }

    /**
     * Starts the periodic statistics-printing scheduler.
     */
    public void startStatisticsTimer() {
        long period = consumerJava.printStatisticsEveryNSeconds;
        if (period <= 0) {
            throw new IllegalArgumentException(
                    "consumerJava.printStatisticsEveryNSeconds must be > 0 but was " + period);
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
                                    runtimeStatistics.batchesByProducerSnapshot(),
                                    runtimeStatistics.getRunningProducers(),
                                    runningConsumerCount(),
                                    consumerReadyCount.get(),
                                    producerBlockedCount.get(),
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
            try {
                consumeOneCycle(threadLocalReuseableByteBuffer);
            } catch (InterruptedException e) {
                LOGGER.warn("Consumer poll loop interrupted; relying on shouldRun for shutdown.", e);
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

    /**
     * Runs a single drain-and-wait cycle of {@link #consumeKeysRunner()}: first drains
     * every batch already queued, then waits up to {@code queuePollTimeoutMillis} for one
     * more. A cycle is counted as <b>ready</b> (see {@link #consumerReadyCount}) when it
     * did no work at all — nothing was drained and the timed wait returned nothing — which
     * means the consumer emptied the queue and is ready for more work (normal/healthy; the
     * CPU is keeping up with the producers).
     *
     * <p>Extracted from the run loop so the ready/idle accounting can be unit-tested
     * directly with a pre-filled or empty queue, without spawning the consumer threads.
     *
     * @param threadLocalReuseableByteBuffer thread-local buffer reused across address lookups
     * @return {@code true} if this cycle was ready for work (queue empty, no work done),
     *     {@code false} otherwise
     * @throws InterruptedException if the timed wait is interrupted (shutdown)
     */
    @VisibleForTesting
    boolean consumeOneCycle(ByteBuffer threadLocalReuseableByteBuffer) throws InterruptedException {
        long drained = consumeKeys(threadLocalReuseableByteBuffer);
        // Wait for the next batch instead of an unconditional sleep — wakes the instant a
        // producer enqueues, so queuePollTimeoutMillis is the max idle wait window per
        // cycle, not a fixed back-off.
        PublicKeyBytes[] next = keysQueue.poll(consumerJava.queuePollTimeoutMillis, TimeUnit.MILLISECONDS);
        if (next != null) {
            processBatch(next, threadLocalReuseableByteBuffer);
        }
        boolean readyForWork = drained == 0 && next == null;
        if (readyForWork) {
            consumerReadyCount.incrementAndGet();
        }
        return readyForWork;
    }

    /**
     * Drains and processes every batch currently in the keys queue using non-blocking
     * polls, returning once the queue is momentarily empty.
     *
     * @param threadLocalReuseableByteBuffer thread-local buffer reused across address lookups
     * @return the number of batches drained and processed in this call
     */
    long consumeKeys(ByteBuffer threadLocalReuseableByteBuffer) {
        LOGGER.trace("consumeKeys");
        long drained = 0;
        PublicKeyBytes[] publicKeyBytesArray = keysQueue.poll();
        while (publicKeyBytesArray != null) {
            processBatch(publicKeyBytesArray, threadLocalReuseableByteBuffer);
            drained++;
            publicKeyBytesArray = keysQueue.poll();
        }
        return drained;
    }

    /**
     * Processes a single batch of public keys: address lookup, hit logging, optional
     * vanity matching. Extracted so {@link #consumeKeysRunner()} can dispatch the
     * batch returned by its timed wait between drain cycles without re-entering the
     * non-blocking drain loop in {@link #consumeKeys(ByteBuffer)}.
     *
     * @param publicKeyBytesArray              the batch to process
     * @param threadLocalReuseableByteBuffer   thread-local buffer reused across address lookups
     */
    private void processBatch(PublicKeyBytes[] publicKeyBytesArray, ByteBuffer threadLocalReuseableByteBuffer) {
        for (PublicKeyBytes publicKeyBytes : publicKeyBytesArray) {
            if (publicKeyBytes.isOutsidePrivateKeyRange()) {
                continue;
            }

            byte[] hash160Uncompressed = publicKeyBytes.getUncompressedKeyHash();
            boolean containsAddressUncompressed = containsAddress(threadLocalReuseableByteBuffer, hash160Uncompressed);

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
                Matcher uncompressedKeyHashAsBase58Matcher = localVanityPattern.matcher(uncompressedKeyHashAsBase58);
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

        if (keysQueue.remainingCapacity() == 0) {
            // The bounded queue is full, so the following put() will block this producer
            // until the consumer frees a slot. A rising count means the consumer/CPU is
            // the bottleneck (cannot drain as fast as producers generate).
            producerBlockedCount.incrementAndGet();
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
            throw new InterruptedRuntimeException(
                    "Interrupted while awaiting consumer termination on thread "
                            + Thread.currentThread().getName(),
                    e);
        }
        try {
            if (persistence != null) {
                Persistence localPersistence = Objects.requireNonNull(persistence);
                localPersistence.close();
                persistence = null;
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to close persistence during ConsumerJava shutdown ("
                            + e.getClass().getSimpleName() + ")",
                    e);
        }
        LOGGER.debug("Interrupt complete: resources released and persistence closed.");
    }

    @VisibleForTesting
    int keysQueueSize() {
        return keysQueue.size();
    }

    /**
     * Counts the consumer worker threads that are still running (their {@link Future} has
     * not completed). Surfaced in the statistics line as "Consumers running".
     *
     * @return the number of consumer worker threads currently running
     */
    @VisibleForTesting
    long runningConsumerCount() {
        return consumers.stream().filter(future -> !future.isDone()).count();
    }
}
