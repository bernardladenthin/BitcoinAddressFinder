// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.consumer;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import net.ladenthin.bitcoinaddressfinder.AwaitTimeTest;
import net.ladenthin.bitcoinaddressfinder.AwaitTimeTests;
import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.LMDBPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.ManualDebugConstants;
import net.ladenthin.bitcoinaddressfinder.MockKeyProducer;
import net.ladenthin.bitcoinaddressfinder.ToStringTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerJava;
import net.ladenthin.bitcoinaddressfinder.secret.BIP39Wordlist;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses1337;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses42;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import nl.altindag.log.LogCaptor;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ConsumerJavaTest {

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);
    private final BitHelper bitHelper = new BitHelper();

    /**
     * Returns an example key. <a href="https://privatekeys.pw/key/0000000000000000000000000000000000000000000000000000000000000049">Example key</a>
     * @return an example key.
     */
    public static PublicKeyBytes[] createExamplePublicKeyBytesfromPrivateKey73() {
        PublicKeyBytes publicKeyBytes = PublicKeyBytes.fromPrivate(BigInteger.valueOf(73));
        PublicKeyBytes[] publicKeyBytesArray = new PublicKeyBytes[] {publicKeyBytes};
        return publicKeyBytesArray;
    }

    @Test
    public void initLMDB_lmdbNotExisting_noExceptionThrown() throws Exception {
        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory =
                Files.createTempDirectory(folder, "junit").toFile().getAbsolutePath();

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        assertThrows(org.lmdbjava.LmdbNativeException.class, () -> consumerJava.initLMDB());
    }

    @Test
    public void initLMDB_lmdbOnlyBackend_keepsEnvOpenForDirectLookups() throws Exception {
        // Regression: LMDB_ONLY uses the LMDBPersistence itself as the lookup. initLMDB must
        // NOT close its env (it would if LMDBPersistence.requiresBackend() returned false),
        // otherwise every containsAddress throws Env$AlreadyClosedException. See the
        // consumer-thread spin that floods the log when the env is closed under live readers.
        new LMDBPlatformAssume().assumeLMDBExecution();
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.lmdbConfigurationReadOnly.addressLookupBackend =
                net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend.LMDB_ONLY;
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        try {
            consumerJava.initLMDB();

            // env must stay open: persistence is retained (not nulled) and a lookup succeeds
            // through the live env rather than throwing Env$AlreadyClosedException.
            assertThat(consumerJava.persistence, is(notNullValue()));
            ByteBuffer buffer = ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES);
            consumerJava.consumeKeys(createExamplePublicKeyBytesfromPrivateKey73());
            consumerJava.consumeOneCycle(buffer);
        } finally {
            consumerJava.interrupt();
        }
    }

    @Test
    public void initLMDB_binaryFuse8Backend_keepsEnvOpenToVerifyFilterHits() throws Exception {
        // BINARY_FUSE_8 is a probabilistic filter (~0.4% FPR), so it is wrapped by
        // BinaryFuseAccelerator and must keep LMDB open to verify filter hits. initLMDB must
        // therefore NOT close the env (regression: it previously closed it, which would turn
        // ~0.4% of all scanned addresses into unverified false hits).
        new LMDBPlatformAssume().assumeLMDBExecution();
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.lmdbConfigurationReadOnly.addressLookupBackend =
                net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend.BINARY_FUSE_8;
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        try {
            consumerJava.initLMDB();

            // env must stay open: persistence is retained (not nulled) and a lookup succeeds
            // through the live env rather than throwing Env$AlreadyClosedException.
            assertThat(consumerJava.persistence, is(notNullValue()));
            ByteBuffer buffer = ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES);
            consumerJava.consumeKeys(createExamplePublicKeyBytesfromPrivateKey73());
            consumerJava.consumeOneCycle(buffer);
        } finally {
            consumerJava.interrupt();
        }
    }

    // <editor-fold defaultstate="collapsed" desc="GPU pre-filter payload (built in initLMDB, decoupled from CPU
    // lookup)">
    @Test
    public void gpuFilter_binaryFuse8Backend_reusesCpuFilter() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.lmdbConfigurationReadOnly.addressLookupBackend =
                net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend.BINARY_FUSE_8;
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        try {
            consumerJava.setGpuFilterRequested(true);
            consumerJava.initLMDB();
            // the Binary Fuse 8 backend already built a filter, so it is reused for GPU upload
            assertThat(consumerJava.getGpuFilterData().isPresent(), is(true));
            assertThat(consumerJava.getGpuFilterData().get().fingerprints().length > 0, is(true));
        } finally {
            consumerJava.interrupt();
        }
    }

    @Test
    public void gpuFilter_lmdbOnlyBackend_buildsFilterFromOpenLmdb() throws Exception {
        // Decoupling: the GPU pre-filter must be available even when the CPU lookup is LMDB_ONLY
        // (no CPU-side filter). initLMDB builds a transient Fuse-8 filter from the open LMDB purely
        // for VRAM upload; the CPU lookup stays LMDB-only (no double filtering).
        new LMDBPlatformAssume().assumeLMDBExecution();
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.lmdbConfigurationReadOnly.addressLookupBackend =
                net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend.LMDB_ONLY;
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        try {
            consumerJava.setGpuFilterRequested(true);
            consumerJava.initLMDB();
            assertThat(consumerJava.getGpuFilterData().isPresent(), is(true));
            assertThat(consumerJava.getGpuFilterData().get().fingerprints().length > 0, is(true));
        } finally {
            consumerJava.interrupt();
        }
    }

    @Test
    public void gpuFilter_selfContainedBackend_builtBeforeLmdbClosed() throws Exception {
        // Regression: a self-contained snapshot (HASHSET) closes LMDB at the end of initLMDB().
        // Because the GPU filter is requested up-front, initLMDB builds it from the open LMDB
        // BEFORE the close -> the payload is present even though the env is afterwards released.
        // (Previously the build happened after the close, so the data was already gone.)
        new LMDBPlatformAssume().assumeLMDBExecution();
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.lmdbConfigurationReadOnly.addressLookupBackend =
                net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend.HASHSET;
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        try {
            consumerJava.setGpuFilterRequested(true);
            consumerJava.initLMDB();
            // payload built before the close
            assertThat(consumerJava.getGpuFilterData().isPresent(), is(true));
            assertThat(consumerJava.getGpuFilterData().get().fingerprints().length > 0, is(true));
            // ...and the self-contained backend still released LMDB afterwards
            assertThat(consumerJava.persistence, is(nullValue()));
            // discarding frees the host copy
            consumerJava.discardGpuFilterData();
            assertThat(consumerJava.getGpuFilterData().isPresent(), is(false));
        } finally {
            consumerJava.interrupt();
        }
    }

    @Test
    public void gpuFilter_notRequested_returnsEmpty() throws Exception {
        // When no producer needs the GPU filter, initLMDB does not build it.
        new LMDBPlatformAssume().assumeLMDBExecution();
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.lmdbConfigurationReadOnly.addressLookupBackend =
                net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend.LMDB_ONLY;
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        try {
            // gpuFilterRequested defaults to false
            consumerJava.initLMDB();
            assertThat(consumerJava.getGpuFilterData().isPresent(), is(false));
        } finally {
            consumerJava.interrupt();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="runtime health counters">
    @Test
    public void consumeOneCycle_emptyQueue_marksReadyAndIncrementsReadyCount() throws Exception {
        CConsumerJava cConsumerJava = new CConsumerJava();
        // keep the idle wait window tiny so the ready (empty-queue) cycle returns fast
        cConsumerJava.queuePollTimeoutMillis = 1;
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        ByteBuffer buffer = ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES);

        // act: nothing was ever enqueued, so the cycle drains nothing and the timed poll times out
        boolean readyForWork = consumerJava.consumeOneCycle(buffer);

        // assert
        assertThat(readyForWork, is(equalTo(true)));
        assertThat(consumerJava.consumerReadyCount.get(), is(equalTo(1L)));
        assertThat(consumerJava.producerBlockedCount.get(), is(equalTo(0L)));
    }

    @Test
    public void consumeOneCycle_queueHasBatch_processesBatchAndIsNotReady() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.queuePollTimeoutMillis = 1;
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        consumerJava.initLMDB();
        try {
            // enqueue one batch the cycle must drain and process
            consumerJava.consumeKeys(createExamplePublicKeyBytesfromPrivateKey73());
            ByteBuffer buffer = ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES);

            // act
            boolean readyForWork = consumerJava.consumeOneCycle(buffer);

            // assert: work was done, so the cycle is not a ready (idle) cycle and the queue is drained
            assertThat(readyForWork, is(equalTo(false)));
            assertThat(consumerJava.consumerReadyCount.get(), is(equalTo(0L)));
            assertThat(consumerJava.keysQueueSize(), is(equalTo(0)));
        } finally {
            // close the LMDB env so the memory-mapped file handle is released; on Windows an
            // open env blocks @TempDir deletion ("JUnit Failed to close extension context").
            consumerJava.interrupt();
        }
    }

    @Test
    public void consumeKeys_queueFull_incrementsProducerBlockedCount() throws Exception {
        CConsumerJava cConsumerJava = new CConsumerJava();
        // single-slot queue so the second enqueue finds it full
        cConsumerJava.queueSize = 1;
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);

        // first enqueue fills the only slot; remaining capacity was 1 beforehand -> not counted
        consumerJava.consumeKeys(createExamplePublicKeyBytesfromPrivateKey73());
        assertThat(consumerJava.producerBlockedCount.get(), is(equalTo(0L)));

        // a second producer finds the queue full -> counts the block, then parks in put()
        ExecutorService producer = Executors.newSingleThreadExecutor();
        try {
            producer.submit(() -> {
                consumerJava.consumeKeys(createExamplePublicKeyBytesfromPrivateKey73());
                return null;
            });

            // the block is counted before put() parks, so await the increment
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
            while (consumerJava.producerBlockedCount.get() == 0L && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            assertThat(consumerJava.producerBlockedCount.get(), is(equalTo(1L)));

            // release the parked producer so the executor can terminate cleanly
            consumerJava.keysQueue.poll();
        } finally {
            producer.shutdownNow();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="toString">
    @ToStringTest
    @Test
    public void toString_whenCalled_containsClassNameAndConfig() throws Exception {
        // After migrating ConsumerJava to Lombok @ToString the output becomes
        // "ConsumerJava(keyUtility=..., checkedKeys=0, ..., consumerJava=CConsumerJava(...), ...)".
        // The old identity-style "ConsumerJava@<hex>" form is gone — replaced by a
        // structured state snapshot. We assert on the class name + the consumerJava
        // configuration carrier since identity is no longer in the contract.
        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory =
                Files.createTempDirectory(folder, "junit").toFile().getAbsolutePath();

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);

        String toStringOutput = consumerJava.toString();

        assertThat(toStringOutput, not(emptyOrNullString()));
        assertThat(toStringOutput, containsString("ConsumerJava("));
        assertThat(toStringOutput, containsString("consumerJava=CConsumerJava("));
    }
    // </editor-fold>

    @Test
    public void startStatisticsTimer_noExceptionThrown() throws Exception {
        final int runTimes = 3;

        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.printStatisticsEveryNSeconds = 1;
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        try (LogCaptor logCaptor = LogCaptor.forClass(ConsumerJava.class)) {
            // act
            consumerJava.startStatisticsTimer();

            // sleep close to runTimes+1 cycles to let the tasks run runTimes times
            Thread.sleep(((long) cConsumerJava.printStatisticsEveryNSeconds * (runTimes + 1) * 1000) - 300);

            // assert
            consumerJava.interrupt();

            List<String> arguments = logCaptor.getInfoLogs();
            assertThat(arguments.size(), is(greaterThanOrEqualTo(runTimes)));
            assertThat(
                    arguments,
                    hasItem(
                            equalTo(
                                    "Statistics: [Checked 0 M keys in 0 minutes] [0 k keys/second] [0 M keys/minute] [Batches per producer: none] [Producers running: 0] [Consumers running: 0] [Consumer ready for work (queue empty): 0] [Producer blocked (queue full): 0] [Average contains time: 0 ms] [keys queue size: 0] [Hits: 0]")));
        }
    }

    @Test
    public void startStatisticsTimer_invalidparameter_throwsException() throws Exception {
        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.printStatisticsEveryNSeconds = 0;

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        assertThrows(IllegalArgumentException.class, () -> consumerJava.startStatisticsTimer());
    }

    @AwaitTimeTest
    @Test
    public void interrupt_keysQueueNotEmpty_consumerNotRunningWaitedInternallyForTheDuration() throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        // Shorten the queue-empty await from the production default (60 s) so the test's
        // wait-then-time-out branch fires in seconds rather than a minute; injected via
        // config, no static mutation, no test-order coupling.
        cConsumerJava.awaitQueueEmptySeconds = AwaitTimeTests.AWAIT_DURATION.toSeconds();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        ExecutorService consumeKeysExecutor = Executors.newFixedThreadPool(cConsumerJava.threads);
        ConsumerJava consumerJava = new ConsumerJava(
                cConsumerJava,
                keyUtility,
                persistenceUtils,
                new RuntimeStatistics(),
                Executors.newSingleThreadScheduledExecutor(),
                consumeKeysExecutor);
        consumerJava.initLMDB();

        // add keys
        consumerJava.consumeKeys(createExamplePublicKeyBytesfromPrivateKey73());

        // pre-assert, assert the keys queue is not empty
        assertThat(consumerJava.keysQueueSize(), is(equalTo(1)));
        assertThat(consumerJava.shouldRun(), is(equalTo(Boolean.TRUE)));

        // add a pseudo thread to the executor to test its eecution duration
        consumeKeysExecutor.submit(() -> {
            try {
                Thread.sleep(AwaitTimeTests.AWAIT_DURATION);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        // act
        long beforeAct = System.currentTimeMillis();
        // the consume is not running and the interrupt must wait and release nevertheless
        consumerJava.interrupt();

        // assert
        assertThat(consumerJava.shouldRun(), is(equalTo(Boolean.FALSE)));

        long afterAct = System.currentTimeMillis();
        Duration waitTime = Duration.ofMillis(afterAct - beforeAct);

        // assert the waiting time is over, substract imprecision
        assertThat(waitTime, is(greaterThan(AwaitTimeTests.AWAIT_DURATION.minus(AwaitTimeTests.IMPRECISION))));
    }

    @Test
    public void interrupt_statisticsTimerStarted_executerServiceShutdown() throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.printStatisticsEveryNSeconds = 1;
        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        ExecutorService consumeKeysExecutor = Executors.newFixedThreadPool(cConsumerJava.threads);
        ConsumerJava consumerJava = new ConsumerJava(
                cConsumerJava,
                keyUtility,
                persistenceUtils,
                new RuntimeStatistics(),
                scheduledExecutor,
                consumeKeysExecutor);

        consumerJava.initLMDB();
        // pre-assert
        assertThat(scheduledExecutor.isShutdown(), is(equalTo(Boolean.FALSE)));

        consumerJava.startStatisticsTimer();
        // wait till the scheduled TimerTask is completed
        Thread.sleep(Duration.ofSeconds(1L));

        // pre-assert
        assertThat(scheduledExecutor.isShutdown(), is(equalTo(Boolean.FALSE)));
        assertThat(consumeKeysExecutor.isShutdown(), is(equalTo(Boolean.FALSE)));

        // act
        consumerJava.interrupt();

        // assert
        assertThat(scheduledExecutor.isShutdown(), is(equalTo(Boolean.TRUE)));
        assertThat(consumeKeysExecutor.isShutdown(), is(equalTo(Boolean.TRUE)));
    }

    @Test
    public void initLMDB_initialize_databaseOpened() throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);

        // pre-assert
        assertThat(consumerJava.persistence, is(nullValue()));

        // act
        consumerJava.initLMDB();

        // assert
        Persistence persistence = Objects.requireNonNull(consumerJava.persistence);
        assertThat(persistence.isClosed(), is(equalTo(Boolean.FALSE)));
        consumerJava.interrupt();
    }

    @Test
    public void interrupt_consumerInitialized_databaseClosed() throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        consumerJava.initLMDB();
        Persistence persistence = Objects.requireNonNull(consumerJava.persistence);

        // pre-assert
        assertThat(persistence.isClosed(), is(equalTo(Boolean.FALSE)));

        // act
        consumerJava.interrupt();

        // assert
        assertThat(persistence.isClosed(), is(equalTo(Boolean.TRUE)));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT)
    public void runProber_testAddressGiven_hitExpected(boolean compressed, boolean useStaticAmount) throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(compressed);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, false);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.runtimePublicKeyCalculationCheck =
                ManualDebugConstants.ENABLE_RUNTIME_PUBLIC_KEY_CALCULATION_CHECK;

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        Random randomForProducer = new Random(TestAddresses42.RANDOM_SEED);

        CProducerJava cProducerJava = new CProducerJava();
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, randomForProducer);
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, consumerJava, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        try (LogCaptor logCaptor = LogCaptor.forClass(ConsumerJava.class)) {
            producerJava.produceKeys();
            consumerJava.consumeKeys(createHash160ByteBuffer());

            // assert
            assertThat(consumerJava.hits.get(), is(equalTo(1L)));
            assertThat(consumerJava.vanityHits.get(), is(equalTo(0L)));

            List<String> arguments = logCaptor.getInfoLogs();
            assertThat(arguments, hasSize(6));

            ECKey key = new TestAddresses42(1, compressed).getECKeys().get(0);

            PublicKeyBytes publicKeyBytes = PublicKeyBytes.fromPrivate(key.getPrivKey());

            // to prevent any exception in further hit message creation and a possible missing hit message, log the
            // secret
            // alone first that a recovery is possible
            String hitMessageSecretKey =
                    ConsumerJava.HIT_SAFE_PREFIX + "publicKeyBytes.getSecretKey(): " + key.getPrivKey();
            assertThat(arguments.get(0), is(equalTo(hitMessageSecretKey)));

            String hitMessagePublicKeyBytesUncompressed = ConsumerJava.HIT_SAFE_PREFIX
                    + "publicKeyBytes.getUncompressed(): " + Hex.encodeHexString(publicKeyBytes.getUncompressed());
            assertThat(arguments.get(1), is(equalTo(hitMessagePublicKeyBytesUncompressed)));

            String hitMessagePublicKeyBytesCompressed = ConsumerJava.HIT_SAFE_PREFIX
                    + "publicKeyBytes.getCompressed(): " + Hex.encodeHexString(publicKeyBytes.getCompressed());
            assertThat(arguments.get(2), is(equalTo(hitMessagePublicKeyBytesCompressed)));

            String hitMessageHash160Uncompressed = ConsumerJava.HIT_SAFE_PREFIX + "hash160Uncompressed: "
                    + Hex.encodeHexString(publicKeyBytes.getUncompressedKeyHash());
            assertThat(arguments.get(3), is(equalTo(hitMessageHash160Uncompressed)));

            String hitMessageHash160Compressed = ConsumerJava.HIT_SAFE_PREFIX + "hash160Compressed: "
                    + Hex.encodeHexString(publicKeyBytes.getCompressedKeyHash());
            assertThat(arguments.get(4), is(equalTo(hitMessageHash160Compressed)));

            String hitMessageFull = ConsumerJava.HIT_PREFIX + keyUtility.createKeyDetails(key);
            assertThat(arguments.get(5), is(equalTo(hitMessageFull)));
        }
        consumerJava.interrupt();
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT)
    public void runProber_unknownAddressGiven_missExpectedAndLogMessagesInDebugAndTrace(
            boolean compressed, boolean useStaticAmount) throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(compressed);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, false);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.runtimePublicKeyCalculationCheck =
                ManualDebugConstants.ENABLE_RUNTIME_PUBLIC_KEY_CALCULATION_CHECK;

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        Random randomForProducer = new Random(TestAddresses1337.RANDOM_SEED);

        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.batchSizeInBits = 0;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, randomForProducer);
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, consumerJava, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        try (LogCaptor logCaptor = LogCaptor.forClass(ConsumerJava.class)) {
            logCaptor.setLogLevelToTrace();
            producerJava.produceKeys();

            consumerJava.consumeKeys(createHash160ByteBuffer());

            // assert
            assertThat(consumerJava.hits.get(), is(equalTo(0L)));
            assertThat(consumerJava.vanityHits.get(), is(equalTo(0L)));

            List<String> argumentsDebug = logCaptor.getDebugLogs();
            List<String> argumentsTrace = logCaptor.getTraceLogs();
            assertThat(argumentsDebug, hasSize(2));
            assertThat(argumentsTrace, hasSize(9));

            ECKey unknownKeyUncompressed =
                    new TestAddresses1337(1, false).getECKeys().get(0);
            ECKey unknownKeyCompressed =
                    new TestAddresses1337(1, true).getECKeys().get(0);
            String missMessageUncompressed =
                    ConsumerJava.MISS_PREFIX + keyUtility.createKeyDetails(unknownKeyUncompressed);
            String missMessageCompressed = ConsumerJava.MISS_PREFIX + keyUtility.createKeyDetails(unknownKeyCompressed);

            assertThat(argumentsDebug.get(0), startsWith("keysQueue.put(publicKeyBytes) with length: 1"));
            assertThat(argumentsDebug.get(1), startsWith("keysQueue.size(): 1"));

            assertThat(argumentsTrace.get(0), startsWith("consumeKeys"));
            assertThat(argumentsTrace.get(1), startsWith("Time before lookup.containsAddress: "));
            assertThat(argumentsTrace.get(2), startsWith("Time after lookup.containsAddress: "));
            assertThat(argumentsTrace.get(3), startsWith("Time delta: "));
            assertThat(argumentsTrace.get(4), startsWith("Time before lookup.containsAddress: "));
            assertThat(argumentsTrace.get(5), startsWith("Time after lookup.containsAddress: "));
            assertThat(argumentsTrace.get(6), startsWith("Time delta: "));

            // assert for expected miss messages
            assertThat(argumentsTrace.get(7), is(equalTo(missMessageUncompressed)));
            assertThat(argumentsTrace.get(8), is(equalTo(missMessageCompressed)));
        }
        consumerJava.interrupt();
    }

    @Test
    public void consumeKeys_invalidSecretGiven_continueExpectedAndNoExceptionThrown() throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.runtimePublicKeyCalculationCheck =
                ManualDebugConstants.ENABLE_RUNTIME_PUBLIC_KEY_CALCULATION_CHECK;

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        PublicKeyBytes invalidPublicKeyBytes = PublicKeyBytes.INVALID_KEY_ONE;
        PublicKeyBytes[] publicKeyBytesArray = new PublicKeyBytes[] {invalidPublicKeyBytes};
        consumerJava.consumeKeys(publicKeyBytesArray);
        consumerJava.consumeKeys(createHash160ByteBuffer());
        consumerJava.interrupt();
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_COMPRESSED)
    public void consumeKeys_withRuntimeKeyCalculationEnabled_logsError_whenPublicKeyHashIsInvalid(boolean compressed)
            throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.runtimePublicKeyCalculationCheck = true;

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        PublicKeyBytes invalidPublicKeyBytes = PublicKeyBytes.fromPrivate(BigInteger.valueOf(1337));
        // invalidate compressed or uncompressed
        if (compressed) {
            invalidPublicKeyBytes.getCompressed()[7] = 0;
        } else {
            invalidPublicKeyBytes.getUncompressed()[7] = 0;
        }
        PublicKeyBytes[] publicKeyBytesArray = new PublicKeyBytes[] {invalidPublicKeyBytes};

        try (LogCaptor logCaptor = LogCaptor.forClass(PublicKeyBytes.class)) {
            consumerJava.consumeKeys(publicKeyBytesArray);
            consumerJava.consumeKeys(createHash160ByteBuffer());

            // assert
            assertThat(consumerJava.hits.get(), is(equalTo(0L)));
            assertThat(consumerJava.vanityHits.get(), is(equalTo(0L)));

            List<String> arguments = logCaptor.getErrorLogs();
            assertThat(arguments, hasSize(6));

            if (compressed) {
                assertThat(arguments.get(0), is(equalTo("fromPrivateCompressed.getPubKeyHash() != hash160Compressed")));
                assertThat(arguments.get(1), is(equalTo("getSecretKey: 1337")));
                assertThat(
                        arguments.get(2),
                        is(
                                equalTo(
                                        "pubKeyCompressed: 02db0c51cc634a0096374b0b895584a3ca2fb3bea4fd0ee2361f8db63a650fcee6")));
                assertThat(
                        arguments.get(3),
                        is(
                                equalTo(
                                        "pubKeyCompressedFromEcKey: 02db0c51cc634a4096374b0b895584a3ca2fb3bea4fd0ee2361f8db63a650fcee6")));
                assertThat(
                        arguments.get(4), is(equalTo("hash160Compressed: a1039a5001eaccd75abb339b446b83b1ecf54ef7")));
                assertThat(
                        arguments.get(5),
                        is(equalTo("hash160CompressedFromEcKey: 879f5696d90c1c280fa3c7d77723ebc59d7ac108")));
            } else {
                assertThat(
                        arguments.get(0),
                        is(equalTo("fromPrivateUncompressed.getPubKeyHash() != hash160Uncompressed")));
                assertThat(arguments.get(1), is(equalTo("getSecretKey: 1337")));
                assertThat(
                        arguments.get(2),
                        is(
                                equalTo(
                                        "pubKeyUncompressed: 04db0c51cc634a0096374b0b895584a3ca2fb3bea4fd0ee2361f8db63a650fcee67ec0bd2baea1ae184bd16fd397b0e64d5d28257f85836486367fe33cc5b6e6a0")));
                assertThat(
                        arguments.get(3),
                        is(
                                equalTo(
                                        "pubKeyUncompressedFromEcKey: 04db0c51cc634a4096374b0b895584a3ca2fb3bea4fd0ee2361f8db63a650fcee67ec0bd2baea1ae184bd16fd397b0e64d5d28257f85836486367fe33cc5b6e6a0")));
                assertThat(
                        arguments.get(4), is(equalTo("hash160Uncompressed: 1a69285cb42032d77801a15a30357d510b247100")));
                assertThat(
                        arguments.get(5),
                        is(equalTo("hash160UncompressedFromEcKey: e02e1cae178d3a2f84a5d897ee8b7ed6c0e2bbc4")));
            }
        }
        consumerJava.interrupt();
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_COMPRESSED)
    public void consumeKeys_testVanityPattern_patternMatches(boolean compressed) throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.enableVanity = true;
        if (compressed) {
            // 1JYHzX3ndZEcnjrWSQ9VC7324TJ9BAoGy4
            cConsumerJava.vanityPattern = "1JYH.*";
        } else {
            // 14sNbmEhgiGX6BZe9Q5PCgTQT3576mniZt
            cConsumerJava.vanityPattern = "14sN.*";
        }

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        try (LogCaptor logCaptor = LogCaptor.forClass(ConsumerJava.class)) {
            consumerJava.consumeKeys(createExamplePublicKeyBytesfromPrivateKey73());
            consumerJava.consumeKeys(createHash160ByteBuffer());

            // assert
            assertThat(consumerJava.hits.get(), is(equalTo(0L)));
            assertThat(consumerJava.vanityHits.get(), is(equalTo(1L)));

            List<String> arguments = logCaptor.getInfoLogs();
            assertThat(arguments, hasSize(6));

            BigInteger secret = BigInteger.valueOf(73);
            ECKey ecKey = keyUtility.createECKey(secret, true);
            String mnemonics = keyUtility.createMnemonics(ecKey.getPrivKeyBytes());

            Map<BIP39Wordlist, String> map = new HashMap<>();
            map.put(BIP39Wordlist.CHINESE_SIMPLIFIED, "的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 这 铁");
            map.put(BIP39Wordlist.CHINESE_TRADITIONAL, "的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 的 這 鐵");
            map.put(
                    BIP39Wordlist.CZECH,
                    "abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace abdikace ananas internet");
            map.put(
                    BIP39Wordlist.ENGLISH,
                    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abuse differ");
            map.put(
                    BIP39Wordlist.FRENCH,
                    "abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abaisser abreuver cylindre");
            map.put(
                    BIP39Wordlist.ITALIAN,
                    "abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco abaco accenno disposto");
            // attention: japanese has a special separator
            map.put(
                    BIP39Wordlist.JAPANESE,
                    "あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あいこくしん　あさい　くなん");
            map.put(
                    BIP39Wordlist.KOREAN,
                    "가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가격 가슴 목걸이");
            map.put(
                    BIP39Wordlist.PORTUGUESE,
                    "abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abacate abranger conectar");
            map.put(
                    BIP39Wordlist.RUSSIAN,
                    "абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац абзац агитация завтра");
            map.put(
                    BIP39Wordlist.SPANISH,
                    "ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco ábaco abuelo cuota");
            map.put(
                    BIP39Wordlist.TURKISH,
                    "abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur abajur absürt fason");

            assertThat(map.size(), is(BIP39Wordlist.values().length));

            for (Map.Entry<BIP39Wordlist, String> entry : map.entrySet()) {
                BIP39Wordlist bip39Wordlist = entry.getKey();
                String expectedMnemonic = entry.getValue();
                assertThat(mnemonics, containsString(expectedMnemonic));
            }

            assertThat(arguments.get(0), is(equalTo("hit: safe log: publicKeyBytes.getSecretKey(): 73")));
            assertThat(
                    arguments.get(1),
                    is(
                            equalTo(
                                    "hit: safe log: publicKeyBytes.getUncompressed(): 04af3c423a95d9f5b3054754efa150ac39cd29552fe360257362dfdecef4053b45f98a3fd831eb2b749a93b0e6f35cfb40c8cd5aa667a15581bc2feded498fd9c6")));
            assertThat(
                    arguments.get(2),
                    is(
                            equalTo(
                                    "hit: safe log: publicKeyBytes.getCompressed(): 02af3c423a95d9f5b3054754efa150ac39cd29552fe360257362dfdecef4053b45")));
            assertThat(
                    arguments.get(3),
                    is(equalTo("hit: safe log: hash160Uncompressed: 2a6f34a72c181bdd4e6d91ffa69e84fd6c49b207")));
            assertThat(
                    arguments.get(4),
                    is(equalTo("hit: safe log: hash160Compressed: c065379323a549fc3547bcb1937d5dcb48df2396")));

            final String privateKeyBytes =
                    "[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 73]";
            final String privateKeyHex = "0000000000000000000000000000000000000000000000000000000000000049";
            final String wif;
            final String publicKeyAsHex;
            final String publicKeyHash160Hex;
            final String publicKeyHash160Base58;

            if (compressed) {
                wif = "KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU7fj3itoEY";
                publicKeyAsHex = "02af3c423a95d9f5b3054754efa150ac39cd29552fe360257362dfdecef4053b45";
                publicKeyHash160Hex = "c065379323a549fc3547bcb1937d5dcb48df2396";
                publicKeyHash160Base58 = "1JYHzX3ndZEcnjrWSQ9VC7324TJ9BAoGy4";
            } else {
                wif = "5HpHagT65TZzG1PH3CSu63k8DbpvD8s5ip4nEB3kEsreJwwNRRr";
                publicKeyAsHex =
                        "04af3c423a95d9f5b3054754efa150ac39cd29552fe360257362dfdecef4053b45f98a3fd831eb2b749a93b0e6f35cfb40c8cd5aa667a15581bc2feded498fd9c6";
                publicKeyHash160Hex = "2a6f34a72c181bdd4e6d91ffa69e84fd6c49b207";
                publicKeyHash160Base58 = "14sNbmEhgiGX6BZe9Q5PCgTQT3576mniZt";
            }

            String expectedMessage = "vanity pattern match: privateKeyBigInteger: [73] privateKeyBytes: ["
                    + privateKeyBytes
                    + "] privateKeyHex: [" + privateKeyHex + "] WiF: [" + wif + "] publicKeyAsHex: [" + publicKeyAsHex
                    + "] publicKeyHash160Hex: [" + publicKeyHash160Hex + "] publicKeyHash160Base58: ["
                    + publicKeyHash160Base58 + "] Compressed: [" + compressed + "] " + mnemonics;
            assertThat(arguments.get(5), is(equalTo(expectedMessage)));
        }
        consumerJava.interrupt();
    }

    @Test
    public void interrupt_persistenceCloseThrowsException_runtimeExceptionThrown() throws Exception {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        // Mock the persistence to throw an exception on close
        Persistence realPersistence = Objects.requireNonNull(consumerJava.persistence);
        Persistence mockPersistence = mock(Persistence.class);
        when(mockPersistence.isClosed()).thenReturn(false);
        doThrow(new RuntimeException("Simulated close failure"))
                .when(mockPersistence)
                .close();
        consumerJava.persistence = mockPersistence;

        // act - should throw RuntimeException
        try {
            assertThrows(RuntimeException.class, () -> consumerJava.interrupt());
        } finally {
            realPersistence.close();
        }
    }

    private ByteBuffer createHash160ByteBuffer() {
        ByteBuffer threadLocalReuseableByteBuffer =
                ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES);
        return threadLocalReuseableByteBuffer;
    }
}
