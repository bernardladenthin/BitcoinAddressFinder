// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import ch.qos.logback.classic.Level;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class LMDBPersistencePerformanceTest {

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);

    private static final int ARRAY_SIZE = 1024 * 8;
    private static final BigInteger PRIVATE_KEY = BigInteger.valueOf(1337);
    private static final int CONSUMER_THREADS = 32;
    private static final int TEST_TIME_IN_SECONDS = 4;

    private static final int KEYS_QUEUE_SIZE = CONSUMER_THREADS * 2;
    private static final int PRODUCER_THREADS = KEYS_QUEUE_SIZE;

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BLOOM_FILTER_ENABLED)
    public void runProber_performanceTest(boolean useBloomFilter) throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();

        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, false);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.threads = CONSUMER_THREADS;
        cConsumerJava.queueSize = KEYS_QUEUE_SIZE;
        cConsumerJava.printStatisticsEveryNSeconds = 1;
        cConsumerJava.delayEmptyConsumer = 1;
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.lmdbConfigurationReadOnly.addressLookupBackend = useBloomFilter
                ? net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend.BLOOM
                : net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend.LMDB_ONLY;
        cConsumerJava.runtimePublicKeyCalculationCheck =
                ManualDebugConstants.ENABLE_RUNTIME_PUBLIC_KEY_CALCULATION_CHECK;

        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        ExecutorService consumeKeysExecutor = Executors.newFixedThreadPool(cConsumerJava.threads);
        ConsumerJava consumerJava = new ConsumerJava(
                cConsumerJava, keyUtility, persistenceUtils, scheduledExecutor, consumeKeysExecutor);
        // Quiet ConsumerJava's class-level logger down to INFO for this perf test.
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ConsumerJava.class)).setLevel(Level.INFO);

        consumerJava.initLMDB();

        // create producer
        PublicKeyBytes[] publicKeyByteses = createPublicKeyBytesArray();
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(PRODUCER_THREADS);
        AtomicBoolean producerShouldRun = new AtomicBoolean(true);
        createProducerThreads(threadPoolExecutor, consumerJava, publicKeyByteses, producerShouldRun);

        // act
        consumerJava.startConsumer();
        consumerJava.startStatisticsTimer();

        Thread.sleep(TEST_TIME_IN_SECONDS * Statistics.ONE_SECOND_IN_MILLISECONDS);
        // Signal all producer threads to stop by setting the shared flag to false.
        // Each producer thread will check this flag in its loop and exit cleanly.
        producerShouldRun.set(false);
        // Gracefully stop consumer threads and release all resources
        consumerJava.interrupt();

        assertThat(consumerJava.persistence, is(nullValue()));
        assertThat(consumerJava.shouldRun(), is(false));
        assertThat(scheduledExecutor.isShutdown(), is(true));
        assertThat(scheduledExecutor.isTerminated(), is(true));
        assertThat(consumeKeysExecutor.isShutdown(), is(true));
        assertThat(consumeKeysExecutor.isTerminated(), is(true));
    }

    private void createProducerThreads(
            ThreadPoolExecutor threadPoolExecutor,
            ConsumerJava consumerJava,
            PublicKeyBytes[] publicKeyByteses,
            AtomicBoolean producerShouldRun) {
        for (int i = 0; i < PRODUCER_THREADS; i++) {
            threadPoolExecutor.submit(() -> {
                while (producerShouldRun.get()) {
                    try {
                        consumerJava.consumeKeys(publicKeyByteses);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    private PublicKeyBytes[] createPublicKeyBytesArray() {
        PublicKeyBytes publicKeyBytes = PublicKeyBytes.fromPrivate(PRIVATE_KEY);
        PublicKeyBytes[] publicKeyByteses = new PublicKeyBytes[ARRAY_SIZE];
        for (int i = 0; i < publicKeyByteses.length; i++) {
            publicKeyByteses[i] = publicKeyBytes;
        }
        return publicKeyByteses;
    }
}
