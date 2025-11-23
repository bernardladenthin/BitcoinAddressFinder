// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder;

import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteBuffer;
import java.time.Duration;
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
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.MnemonicException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerJava implements Consumer {

    /**
     * We assume a queue might be empty after this amount of time.
     * If not, some keys in the queue are not checked before shutdow.
     */
    @VisibleForTesting
    static Duration AWAIT_DURATION_QUEUE_EMPTY = Duration.ofMinutes(1);
    
    public static final String MISS_PREFIX = "miss: Could not find the address: ";
    public static final String HIT_PREFIX = "hit: Found the address: ";
    public static final String VANITY_HIT_PREFIX = "vanity pattern match: ";
    public static final String HIT_SAFE_PREFIX = "hit: safe log: ";

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final KeyUtility keyUtility;
    protected final AtomicLong checkedKeys = new AtomicLong();
    protected final AtomicLong checkedKeysSumOfTimeToCheckContains = new AtomicLong();
    protected final AtomicLong emptyConsumer = new AtomicLong();
    protected final AtomicLong hits = new AtomicLong();
    protected long startTime = 0;

    protected final CConsumerJava consumerJava;
    @VisibleForTesting
    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    protected @Nullable Persistence persistence;
    private final PersistenceUtils persistenceUtils;
    
    private final List<Future<Void>> consumers = new ArrayList<>();
    protected final LinkedBlockingQueue<PublicKeyBytes[]> keysQueue;
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
    
    protected final AtomicLong vanityHits = new AtomicLong();
    private final @Nullable Pattern vanityPattern;
    
    @VisibleForTesting
    final AtomicBoolean shouldRun = new AtomicBoolean(true);
    
    @VisibleForTesting
    final ExecutorService consumeKeysExecutorService;

    protected ConsumerJava(CConsumerJava consumerJava, KeyUtility keyUtility, PersistenceUtils persistenceUtils) {
        this.consumerJava = consumerJava;
        this.keysQueue = new LinkedBlockingQueue<>(consumerJava.queueSize);
        this.keyUtility = keyUtility;
        this.persistenceUtils = persistenceUtils;
        if (consumerJava.enableVanity) {
            this.vanityPattern = Pattern.compile(consumerJava.vanityPattern);
        } else {
            vanityPattern = null;
        }
        consumeKeysExecutorService = Executors.newFixedThreadPool(consumerJava.threads);
    }

    Logger getLogger() {
        return logger;
    }
    
    void setLogger(Logger logger) {
        this.logger = logger;
    }

    protected void initLMDB() {
        persistence = new LMDBPersistence(consumerJava.lmdbConfigurationReadOnly, persistenceUtils);
        persistence.init();
    }

    protected void startStatisticsTimer() {
        long period = consumerJava.printStatisticsEveryNSeconds;
        if (period <= 0) {
            throw new IllegalArgumentException("period must be greater than 0.");
        }

        startTime = System.currentTimeMillis();

        scheduledExecutorService.scheduleAtFixedRate(() -> {
            // get transient information
            long uptime = Math.max(System.currentTimeMillis() - startTime, 1);

            String message = new Statistics().createStatisticsMessage(uptime, checkedKeys.get(), checkedKeysSumOfTimeToCheckContains.get(), emptyConsumer.get(), keysQueue.size(), hits.get());

            // log the information
            logger.info(message);
        }, period, period, TimeUnit.SECONDS);
    }

    @Override
    public void startConsumer() {
        logger.debug("Starting {} consumer threads...", consumerJava.threads);
        for (int i = 0; i < consumerJava.threads; i++) {
            consumers.add(consumeKeysExecutorService.submit(
                    () -> {
                        consumeKeysRunner();
                        return null;
                    }));
        }
        logger.debug("Successfully started {} consumer threads.", consumers.size());
    }
    
    /**
     * This method runs in multiple threads.
     */
    private void consumeKeysRunner() {
        logger.info("start consumeKeysRunner");
        ByteBuffer threadLocalReuseableByteBuffer = ByteBuffer.allocateDirect(PublicKeyBytes.RIPEMD160_HASH_NUM_BYTES);
        
        while (shouldRun.get()) {
            if (keysQueue.size() >= consumerJava.queueSize) {
                logger.warn("Attention, queue is full. Please increase queue size.");
            }
            try {
                consumeKeys(threadLocalReuseableByteBuffer);
                // the consumeKeys method is looped inside, if the method returns it means the queue is empty
                emptyConsumer.incrementAndGet();
                Thread.sleep(consumerJava.delayEmptyConsumer);
            } catch (InterruptedException e) {
                // we need to catch the exception to not break the thread
                logger.error("Ignore InterruptedException during Thread.sleep.", e);
            } catch (Exception e) {
                // log every Exception because it's hard to debug and we do not break down the thread loop
                logger.error("Error in consumeKeysRunner()." , e);
                e.printStackTrace();
            }
        }

        if (threadLocalReuseableByteBuffer != null) {
            byteBufferUtility.freeByteBuffer(threadLocalReuseableByteBuffer);
            threadLocalReuseableByteBuffer = null;
        }
        logger.info("end consumeKeysRunner");
    }
    
    void consumeKeys(ByteBuffer threadLocalReuseableByteBuffer) throws MnemonicException.MnemonicLengthException {
        logger.trace("consumeKeys");
        PublicKeyBytes[] publicKeyBytesArray = keysQueue.poll();
        while (publicKeyBytesArray != null) {
            for (PublicKeyBytes publicKeyBytes : publicKeyBytesArray) {
                if (publicKeyBytes.isOutsidePrivateKeyRange()) {
                    continue;
                }
                
                byte[] hash160Uncompressed = publicKeyBytes.getUncompressedKeyHash();
                boolean containsAddressUncompressed = containsAddress(threadLocalReuseableByteBuffer, hash160Uncompressed);
                
                byte[] hash160Compressed = publicKeyBytes.getCompressedKeyHash();
                boolean containsAddressCompressed = containsAddress(threadLocalReuseableByteBuffer, hash160Compressed);
                
                if (consumerJava.runtimePublicKeyCalculationCheck) {
                    publicKeyBytes.runtimePublicKeyCalculationCheck(logger);
                }
                
                if (containsAddressUncompressed) {
                    // immediately log the secret
                    safeLog(publicKeyBytes, hash160Uncompressed, hash160Compressed);
                    hits.incrementAndGet();
                    ECKey ecKeyUncompressed = ECKey.fromPrivateAndPrecalculatedPublic(publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getUncompressed());
                    String hitMessageUncompressed = HIT_PREFIX + keyUtility.createKeyDetails(ecKeyUncompressed);
                    logger.info(hitMessageUncompressed);
                }

                if (containsAddressCompressed) {
                    // immediately log the secret
                    safeLog(publicKeyBytes, hash160Uncompressed, hash160Compressed);
                    hits.incrementAndGet();
                    ECKey ecKeyCompressed = ECKey.fromPrivateAndPrecalculatedPublic(publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getCompressed());
                    String hitMessageCompressed = HIT_PREFIX + keyUtility.createKeyDetails(ecKeyCompressed);
                    logger.info(hitMessageCompressed);
                }

                if (consumerJava.enableVanity) {
                    var localVanityPattern = Objects.requireNonNull(vanityPattern);
                    String uncompressedKeyHashAsBase58 = publicKeyBytes.getUncompressedKeyHashAsBase58(keyUtility);
                    Matcher uncompressedKeyHashAsBase58Matcher = localVanityPattern.matcher(uncompressedKeyHashAsBase58);
                    if (uncompressedKeyHashAsBase58Matcher.matches()) {
                        // immediately log the secret
                        safeLog(publicKeyBytes, hash160Uncompressed, hash160Compressed);
                        vanityHits.incrementAndGet();
                        ECKey ecKeyUncompressed = ECKey.fromPrivateAndPrecalculatedPublic(publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getUncompressed());
                        String vanityHitMessageUncompressed = VANITY_HIT_PREFIX + keyUtility.createKeyDetails(ecKeyUncompressed);
                        logger.info(vanityHitMessageUncompressed);
                    }

                    String compressedKeyHashAsBase58 = publicKeyBytes.getCompressedKeyHashAsBase58(keyUtility);
                    Matcher compressedKeyHashAsBase58Matcher = vanityPattern.matcher(compressedKeyHashAsBase58);
                    if (compressedKeyHashAsBase58Matcher.matches()) {
                        // immediately log the secret
                        safeLog(publicKeyBytes, hash160Uncompressed, hash160Compressed);
                        vanityHits.incrementAndGet();
                        ECKey ecKeyCompressed = ECKey.fromPrivateAndPrecalculatedPublic(publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getCompressed());
                        String vanityHitMessageCompressed = VANITY_HIT_PREFIX + keyUtility.createKeyDetails(ecKeyCompressed);
                        logger.info(vanityHitMessageCompressed);
                    }
                }

                if (!containsAddressUncompressed && !containsAddressCompressed) {
                    if (logger.isTraceEnabled()) {
                        ECKey ecKeyUncompressed = ECKey.fromPrivateAndPrecalculatedPublic(publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getUncompressed());
                        String missMessageUncompressed = MISS_PREFIX + keyUtility.createKeyDetails(ecKeyUncompressed);
                        logger.trace(missMessageUncompressed);

                        ECKey ecKeyCompressed = ECKey.fromPrivateAndPrecalculatedPublic(publicKeyBytes.getSecretKey().toByteArray(), publicKeyBytes.getCompressed());
                        String missMessageCompressed = MISS_PREFIX + keyUtility.createKeyDetails(ecKeyCompressed);
                        logger.trace(missMessageCompressed);
                    }
                }
            }
            publicKeyBytesArray = keysQueue.poll();
        }
    }

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
        logger.info(HIT_SAFE_PREFIX +"publicKeyBytes.getSecretKey(): " + publicKeyBytes.getSecretKey());
        logger.info(HIT_SAFE_PREFIX +"publicKeyBytes.getUncompressed(): " + Hex.encodeHexString(publicKeyBytes.getUncompressed()));
        logger.info(HIT_SAFE_PREFIX +"publicKeyBytes.getCompressed(): " + Hex.encodeHexString(publicKeyBytes.getCompressed()));
        logger.info(HIT_SAFE_PREFIX +"hash160Uncompressed: " + Hex.encodeHexString(hash160Uncompressed));
        logger.info(HIT_SAFE_PREFIX +"hash160Compressed: " + Hex.encodeHexString(hash160Compressed));
    }

    private boolean containsAddress(ByteBuffer hash160AsByteBuffer) {
        long timeBefore = System.currentTimeMillis();
        if (logger.isTraceEnabled()) {
            logger.trace("Time before persistence.containsAddress: " + timeBefore);
        }
        Persistence localPersistence = Objects.requireNonNull(persistence);
        boolean containsAddress = localPersistence.containsAddress(hash160AsByteBuffer);
        long timeAfter = System.currentTimeMillis();
        long timeDelta = timeAfter - timeBefore;
        checkedKeys.incrementAndGet();
        checkedKeysSumOfTimeToCheckContains.addAndGet(timeDelta);
        if (logger.isTraceEnabled()) {
            logger.trace("Time after persistence.containsAddress: " + timeAfter);
            logger.trace("Time delta: " + timeDelta);
        }
        return containsAddress;
    }

    @Override
    public void consumeKeys(PublicKeyBytes[] publicKeyBytes) throws InterruptedException {
        if(logger.isDebugEnabled()){
            logger.debug("keysQueue.put(publicKeyBytes) with length: " + publicKeyBytes.length);
        }
        
        keysQueue.put(publicKeyBytes);
        
        if(logger.isDebugEnabled()){
            logger.debug("keysQueue.size(): " + keysQueue.size());
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
        logger.info("Interrupt initiated: stopping consumer execution...");
        shouldRun.set(false);
        scheduledExecutorService.shutdown();
        consumeKeysExecutorService.shutdown();
        logger.info("Waiting for termination of {} consumer threads (timeout: {} seconds)...", consumers.size(), AWAIT_DURATION_QUEUE_EMPTY.getSeconds());
        try {
            boolean terminated = consumeKeysExecutorService.awaitTermination(AWAIT_DURATION_QUEUE_EMPTY.getSeconds(), TimeUnit.SECONDS);
            if (!terminated) {
                logger.warn("Timeout reached. Some consumer threads may not have terminated cleanly.");
            }
        } catch (InterruptedException ex) {
            logger.error("Interrupted while awaiting consumer termination.", ex);
            throw new RuntimeException(ex);
        }
        try {
            if(persistence != null){
                Persistence localPersistence = Objects.requireNonNull(persistence);
                localPersistence.close();
                persistence = null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        logger.debug("Interrupt complete: resources released and persistence closed.");
    }
    
    @VisibleForTesting
    int keysQueueSize() {
        return keysQueue.size();
    }
    
    @Override
    public String toString() {
        return "ConsumerJava@" + Integer.toHexString(System.identityHashCode(this));
    }
}
