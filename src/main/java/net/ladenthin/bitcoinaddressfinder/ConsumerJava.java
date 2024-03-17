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
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.MnemonicException;

public class ConsumerJava implements Consumer, Interruptable {

    /**
     * We assume a queue might be empty after this amount of time.
     * If not, some keys in the queue are not checked before shutdow.
     */
    static final Duration DURATION_WAIT_QUEUE_EMPTY = Duration.ofMinutes(1);
    
    /**
     * The duration for a cyclic check to test the keys queue is empty.
     */
    private static final Duration DURATION_CYCLIC_CHECK_KEYS_QUEUE_EMPTY  = Duration.ofMillis(100L);
    
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
    protected final Timer timer = new Timer();

    protected Persistence persistence;
    private final PersistenceUtils persistenceUtils;
    
    private final List<Future<Void>> consumers = new ArrayList<>();
    protected final LinkedBlockingQueue<PublicKeyBytes[]> keysQueue;
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
    
    protected final AtomicLong vanityHits = new AtomicLong();
    private final Pattern vanityPattern;
    
    private final AtomicBoolean shouldRun = new AtomicBoolean(true);

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
        long period = consumerJava.printStatisticsEveryNSeconds * Statistics.ONE_SECOND_IN_MILLISECONDS;
        if (period <= 0) {
            throw new IllegalArgumentException("period must be greater than 0.");
        }

        startTime = System.currentTimeMillis();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // get transient information
                long uptime = Math.max(System.currentTimeMillis() - startTime, 1);

                String message = new Statistics().createStatisticsMessage(uptime, checkedKeys.get(), checkedKeysSumOfTimeToCheckContains.get(), emptyConsumer.get(), keysQueue.size(), hits.get());

                // log the information
                logger.info(message);
            }
        }, period, period);
    }

    @Override
    public void startConsumer() {
        ExecutorService executor = Executors.newFixedThreadPool(consumerJava.threads);
        for (int i = 0; i < consumerJava.threads; i++) {
            consumers.add(executor.submit(
                    () -> {
                        consumeKeysRunner();
                        return null;
                    }));
        }
    }
    
    /**
     * This method runs in multiple threads.
     */
    private void consumeKeysRunner() {
        logger.trace("Start consumeKeysRunner.");
        
        ByteBuffer threadLocalReuseableByteBuffer = ByteBuffer.allocateDirect(PublicKeyBytes.HASH160_SIZE);
        
        while (shouldRun.get()) {
            if (keysQueue.size() >= consumerJava.queueSize) {
                logger.warn("Attention, queue is full. Please increase queue size.");
            }
            try {
                consumeKeys(threadLocalReuseableByteBuffer);
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
        }
    }
    
    void consumeKeys(ByteBuffer threadLocalReuseableByteBuffer) throws MnemonicException.MnemonicLengthException {
        PublicKeyBytes[] publicKeyBytesArray = keysQueue.poll();
        while (publicKeyBytesArray != null) {
            for (PublicKeyBytes publicKeyBytes : publicKeyBytesArray) {
                if (publicKeyBytes.isInvalid()) {
                    continue;
                }
                byte[] hash160Uncompressed = publicKeyBytes.getUncompressedKeyHash();

                threadLocalReuseableByteBuffer.rewind();
                threadLocalReuseableByteBuffer.put(hash160Uncompressed);
                threadLocalReuseableByteBuffer.flip();

                boolean containsAddressUncompressed = containsAddress(threadLocalReuseableByteBuffer);

                byte[] hash160Compressed = publicKeyBytes.getCompressedKeyHash();
                threadLocalReuseableByteBuffer.rewind();
                threadLocalReuseableByteBuffer.put(hash160Compressed);
                threadLocalReuseableByteBuffer.flip();

                boolean containsAddressCompressed = containsAddress(threadLocalReuseableByteBuffer);

                if (consumerJava.runtimePublicKeyCalculationCheck) {
                    
                    ECKey fromPrivateUncompressed = ECKey.fromPrivate(publicKeyBytes.getSecretKey(), false);
                    ECKey fromPrivateCompressed = ECKey.fromPrivate(publicKeyBytes.getSecretKey(), true);
                    
                    final byte[] pubKeyUncompressedFromEcKey = fromPrivateUncompressed.getPubKey();
                    final byte[] pubKeyCompressedFromEcKey = fromPrivateCompressed.getPubKey();
                    
                    final byte[] hash160UncompressedFromEcKey = fromPrivateUncompressed.getPubKeyHash();
                    final byte[] hash160CompressedFromEcKey = fromPrivateCompressed.getPubKeyHash();
                    
                    if (!Arrays.equals(hash160UncompressedFromEcKey, hash160Uncompressed)) {
                        logger.error("fromPrivateUncompressed.getPubKeyHash() != hash160Uncompressed");
                        logger.error("getSecretKey: " + publicKeyBytes.getSecretKey());
                        logger.error("pubKeyUncompressed: " + Hex.encodeHexString(publicKeyBytes.getUncompressed()));
                        logger.error("pubKeyUncompressedFromEcKey: " + Hex.encodeHexString(pubKeyUncompressedFromEcKey));
                        logger.error("hash160Uncompressed: " + Hex.encodeHexString(hash160Uncompressed));
                        logger.error("hash160UncompressedFromEcKey: " + Hex.encodeHexString(hash160UncompressedFromEcKey));
                    }
                    
                    if (!Arrays.equals(hash160CompressedFromEcKey, hash160Compressed)) {
                        logger.error("fromPrivateCompressed.getPubKeyHash() != hash160Compressed");
                        logger.error("getSecretKey: " + publicKeyBytes.getSecretKey());
                        logger.error("pubKeyCompressed: " + Hex.encodeHexString(publicKeyBytes.getCompressed()));
                        logger.error("pubKeyCompressedFromEcKey: " + Hex.encodeHexString(pubKeyCompressedFromEcKey));
                        logger.error("hash160Compressed: " + Hex.encodeHexString(hash160Compressed));
                        logger.error("hash160CompressedFromEcKey: " + Hex.encodeHexString(hash160CompressedFromEcKey));
                    }
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
                    String uncompressedKeyHashAsBase58 = publicKeyBytes.getUncompressedKeyHashAsBase58(keyUtility);
                    Matcher uncompressedKeyHashAsBase58Matcher = vanityPattern.matcher(uncompressedKeyHashAsBase58);
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
    
    /**
     * Try to log safe informations which may not thrown an exception.
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
        if (logger.isDebugEnabled()) {
            logger.debug("Time before persistence.containsAddress: " + timeBefore);
        }
        boolean containsAddress = persistence.containsAddress(hash160AsByteBuffer);
        long timeAfter = System.currentTimeMillis();
        long timeDelta = timeAfter - timeBefore;
        checkedKeys.incrementAndGet();
        checkedKeysSumOfTimeToCheckContains.addAndGet(timeDelta);
        if (logger.isDebugEnabled()) {
            logger.debug("Time after persistence.containsAddress: " + timeAfter);
            logger.debug("Time delta: " + timeDelta);
        }
        return containsAddress;
    }

    @Override
    public void consumeKeys(PublicKeyBytes[] publicKeyBytes) throws InterruptedException {
        keysQueue.put(publicKeyBytes);
    }
    
    /**
     * Returns if the consume was finished or.
     * @return {@code true} if the keys queue is empty, otherwise {@code false}.
     */
    @VisibleForTesting
    boolean waitTillKeysQueueEmpty(Duration maxWait) throws InterruptedException {
        final long startTime = System.currentTimeMillis();
        do {
            if (keysQueue.isEmpty()) {
                return true;
            }
            Thread.sleep(DURATION_CYCLIC_CHECK_KEYS_QUEUE_EMPTY);
        } while (maxWait.minusMillis(System.currentTimeMillis() - startTime).isPositive());
        return false;
    }

    @Override
    public void interrupt() {
        try {
            // the result does not matter, just try to wait some seconds to empty the queue
            waitTillKeysQueueEmpty(DURATION_WAIT_QUEUE_EMPTY);
        } catch (InterruptedException ex) {
            // do nothing, it is no problem
        }
        shouldRun.set(false);
        timer.cancel();
    }
}
