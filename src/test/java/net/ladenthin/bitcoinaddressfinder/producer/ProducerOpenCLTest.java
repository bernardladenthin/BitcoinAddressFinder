// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.ladenthin.bitcoinaddressfinder.AwaitTimeTest;
import net.ladenthin.bitcoinaddressfinder.MockConsumer;
import net.ladenthin.bitcoinaddressfinder.MockKeyProducer;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLGridResult;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLGridResults;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import net.ladenthin.bitcoinaddressfinder.util.PrivateKeyTooLargeException;
import nl.altindag.log.LogCaptor;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProducerOpenCLTest {

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    /** How long the slow consumer holds a result reader up; far longer than the kernel needs. */
    private static final long SLOW_CONSUMER_MILLIS = 1_000L;

    /** Upper bound for the consumer that never takes its batch; the test must end well before it. */
    private static final long STUCK_CONSUMER_MAX_SECONDS = 30L;

    /** Result readers of the shutdown-deadline test: one, so a single stuck reader holds every slot. */
    private static final int ONE_RESULT_READER = 1;

    /** Shutdown timeout of the shutdown-deadline test; short, because the test waits it out. */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 2;

    /** Time the producer spends on its grid in progress after the interrupt, before it waits for the readers. */
    private static final Duration GRID_AFTER_INTERRUPT = Duration.ofSeconds(1);

    /** The wait for the readers has to end at the shutdown deadline; this much scheduling slack either way. */
    private static final Duration TIMING_MARGIN = Duration.ofMillis(500);

    /** Upper bound for the stuck reader to reach the consumer. */
    private static final Duration READER_START_TIMEOUT = Duration.ofSeconds(10);

    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @OpenCLTest
    @Test
    public void initProducer_configurationGiven_stateInitializedAndLogged() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        AbstractProducerTest.verifyInitProducer(producerOpenCL);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="releaseProducer">
    @OpenCLTest
    @Test
    public void releaseProducer_configurationGiven_stateInitializedAndLoggedAndExecuterServiceShutdown()
            throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ThreadPoolExecutor injectedReaderPool =
                (ThreadPoolExecutor) Executors.newFixedThreadPool(cProducerOpenCL.maxResultReaderThreads);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL,
                mockConsumer,
                keyUtility,
                mockKeyProducer,
                bitHelper,
                new RuntimeStatistics(),
                injectedReaderPool);

        AbstractProducerTest.verifyReleaseProducer(producerOpenCL);
        assertThat(injectedReaderPool.isShutdown(), is(equalTo(Boolean.TRUE)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @OpenCLTest
    @Test
    public void initProducer_configurationGiven_stateInitializedAndOpenCLContextSet() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // pre-assert
        assertThat(producerOpenCL.isInitialized(), is(false));
        assertThat(producerOpenCL.state, is(equalTo(ProducerState.UNINITIALIZED)));

        // act
        producerOpenCL.initProducer();

        // assert
        assertThat(producerOpenCL.isInitialized(), is(true));
        assertThat(producerOpenCL.state, is(equalTo(ProducerState.INITIALIZED)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="releaseProducers">
    @Test
    public void releaseProducers_notInitialized_noExceptionThrown() throws Exception {
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // act
        producerOpenCL.releaseProducer();
    }

    @Test
    @OpenCLTest
    public void releaseProducers_initialized_noExceptionThrownAndOpenCLContextFreed() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        producerOpenCL.initProducer();

        // pre-assert
        assertThat(producerOpenCL.isInitialized(), is(true));

        // act
        producerOpenCL.releaseProducer();

        // assert
        assertThat(producerOpenCL.isInitialized(), is(false));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="constructor">
    @Test
    public void constructor_noResultReaderThread_isRejectedNamingTheField() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.maxResultReaderThreads = 0;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, new Random(1));

        // act
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ProducerOpenCL(
                        cProducerOpenCL,
                        new MockConsumer(),
                        keyUtility,
                        mockKeyProducer,
                        bitHelper,
                        new RuntimeStatistics()));

        // assert
        assertThat(
                exception.getMessage(),
                is(equalTo("producerOpenCL.maxResultReaderThreads must be at least 1 but was 0")));
    }

    @Test
    public void constructor_injectedPoolAndNegativeResultReaderThreads_isRejectedNamingTheField() {
        // arrange — the injected pool has threads of its own; the field still sizes the submit slots,
        // and a semaphore without permits would block the first grid forever.
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.maxResultReaderThreads = -1;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, new Random(1));
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        try {
            // act
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ProducerOpenCL(
                            cProducerOpenCL,
                            new MockConsumer(),
                            keyUtility,
                            mockKeyProducer,
                            bitHelper,
                            new RuntimeStatistics(),
                            pool));

            // assert
            assertThat(
                    exception.getMessage(),
                    is(equalTo("producerOpenCL.maxResultReaderThreads must be at least 1 but was -1")));
        } finally {
            pool.shutdownNow();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getFreeThreads">
    @Test
    public void getFreeThreads_notInitialized_numberOfFreeThreadsReturned() throws Exception {
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // act
        int freeThreads = producerOpenCL.getFreeThreads();

        // assert
        assertThat(freeThreads, is(equalTo(Integer.valueOf(cProducerOpenCL.maxResultReaderThreads))));
    }

    @Test
    @OpenCLTest
    public void getFreeThreads_initialized_numberOfFreeThreadsReturned() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        producerOpenCL.initProducer();

        // act
        int freeThreads = producerOpenCL.getFreeThreads();

        // assert
        assertThat(freeThreads, is(equalTo(Integer.valueOf(cProducerOpenCL.maxResultReaderThreads))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="setGpuFilter">
    @Test
    public void setGpuFilter_validParameters_noExceptionThrownAndProducerNotYetInitialized() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // act (no exception)
        producerOpenCL.setGpuFilter(new byte[] {1, 2, 3}, 0xABCD, 0x1234, 4, 3, 12);

        // assert: staging the filter does not initialise the context
        assertThat(producerOpenCL.isInitialized(), is(false));
    }

    @OpenCLTest
    @Test
    public void setGpuFilter_thenInitProducer_filterUploadedAndContextInitialized() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.enableGpuFilter = true;
        cProducerOpenCL.transferAll = true; // avoid OCL 2.0 compact-mode check; test only the upload path
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // Stage an empty filter (segCountLen=0; kernel sees no entries and never hits)
        producerOpenCL.setGpuFilter(new byte[0], 0, 0, 2, 1, 0);

        // act
        producerOpenCL.initProducer();

        // assert
        assertThat(producerOpenCL.isInitialized(), is(true));

        // cleanup
        producerOpenCL.releaseProducer();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="produceKeys">
    @Test
    public void produceKeys_notInitialized_illegalStateExceptionThrown() throws Exception {
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // act
        assertThrows(IllegalStateException.class, () -> producerOpenCL.produceKeys());

        // assert
    }

    @Test
    @OpenCLTest
    public void produceKeys_initialized_keysInConsumer() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        producerOpenCL.initProducer();

        // act
        producerOpenCL.produceKeys();

        // it takes some time to consume keys
        Thread.sleep(Duration.ofSeconds(10L));

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(greaterThan(0)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="run: the last batch">
    /**
     * {@code processSecretBase} only hands a computed grid to a result-reader thread; the reader puts
     * the keys into the consumer afterwards. A finite run must not report itself finished before
     * that has happened: the finder releases the consumer as soon as every producer is finished, so a
     * batch a reader enqueues after that is never checked — a hit in it is lost without a word
     * (F131's regression, reproduced through the finder on pocl). The reader here is held up by a
     * slow consumer, which makes the race a certainty.
     */
    @Test
    @OpenCLTest
    public void run_readerStillBusyAfterTheLastGrid_runReturnsOnlyAfterTheBatchReachedTheConsumer() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.batchSizeInBits = 8;
        cProducerOpenCL.runOnce = true;
        final AtomicInteger consumedBatches = new AtomicInteger();
        MockConsumer slowConsumer = new MockConsumer() {
            @Override
            public void consumeKeys(PublicKeyBytes[] publicKeyBytes, @Nullable BigInteger secretBase)
                    throws InterruptedException {
                Thread.sleep(SLOW_CONSUMER_MILLIS);
                consumedBatches.incrementAndGet();
            }
        };
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL,
                slowConsumer,
                keyUtility,
                new MockKeyProducer(keyUtility, new Random(1)),
                bitHelper,
                new RuntimeStatistics());
        producerOpenCL.initProducer();
        try {
            // act
            producerOpenCL.run();

            // assert
            assertThat(producerOpenCL.getState(), is(equalTo(ProducerState.NOT_RUNNING)));
            assertThat(consumedBatches.get(), is(equalTo(1)));
        } finally {
            producerOpenCL.releaseProducer();
        }
    }

    /**
     * The wait for the readers is bounded by {@code shutdownTimeoutSeconds}: a consumer that does not
     * take the batch in time must not hold the producer forever. The producer ends anyway and says that
     * a batch may not have arrived. A run that ends by itself (no interrupt) waits the whole timeout.
     */
    @Test
    @OpenCLTest
    public void run_readerBusyLongerThanTheShutdownTimeout_runEndsAndLogsTheBusyReader() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.batchSizeInBits = 8;
        cProducerOpenCL.runOnce = true;
        cProducerOpenCL.shutdownTimeoutSeconds = 1;
        final CountDownLatch release = new CountDownLatch(1);
        MockConsumer stuckConsumer = new MockConsumer() {
            @Override
            public void consumeKeys(PublicKeyBytes[] publicKeyBytes, @Nullable BigInteger secretBase)
                    throws InterruptedException {
                release.await(STUCK_CONSUMER_MAX_SECONDS, TimeUnit.SECONDS);
            }
        };
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL,
                stuckConsumer,
                keyUtility,
                new MockKeyProducer(keyUtility, new Random(1)),
                bitHelper,
                new RuntimeStatistics());
        producerOpenCL.initProducer();
        try (LogCaptor logCaptor = LogCaptor.forClass(ProducerOpenCL.class)) {
            // act
            final long start = System.nanoTime();
            producerOpenCL.run();
            final long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);

            // assert
            assertThat(producerOpenCL.getState(), is(equalTo(ProducerState.NOT_RUNNING)));
            assertThat(elapsedSeconds < STUCK_CONSUMER_MAX_SECONDS, is(true));
            assertThat(logCaptor.getErrorLogs(), hasSize(1));
            assertThat(
                    logCaptor.getErrorLogs().get(0),
                    containsString("1 of 4 result reader(s) still busy after 1.0s (no interrupt was issued,"
                            + " shutdownTimeoutSeconds 1)"));
        } finally {
            release.countDown();
            producerOpenCL.releaseProducer();
        }
    }

    /**
     * {@code shutdownTimeoutSeconds} is measured from the interrupt, and the wait for the result readers
     * gets what is left of it. The releaser waits the same timeout from the interrupt; a reader wait that
     * started the full timeout afresh at the end of the grid in progress ended after the releaser had given
     * the producer up, so the releaser logged a producer still running and leaked its OpenCL context.
     * No device: the slot is held by a reader stuck in the consumer, the way a grid's reader holds it, and
     * the wait is called the way the run loop calls it, after the grid.
     */
    @AwaitTimeTest
    @Test
    public void awaitPendingBatches_interruptedDuringTheGrid_endsAtTheShutdownDeadlineOfTheInterrupt()
            throws Exception {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.maxResultReaderThreads = ONE_RESULT_READER;
        cProducerOpenCL.shutdownTimeoutSeconds = SHUTDOWN_TIMEOUT_SECONDS;
        final CountDownLatch readerInConsumer = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        MockConsumer stuckConsumer = new MockConsumer() {
            @Override
            public void consumeKeys(PublicKeyBytes[] publicKeyBytes, @Nullable BigInteger secretBase)
                    throws InterruptedException {
                readerInConsumer.countDown();
                release.await(STUCK_CONSUMER_MAX_SECONDS, TimeUnit.SECONDS);
            }
        };
        ThreadPoolExecutor readerPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(ONE_RESULT_READER);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL,
                stuckConsumer,
                keyUtility,
                new MockKeyProducer(keyUtility, new Random(1)),
                bitHelper,
                new RuntimeStatistics(),
                readerPool);
        OpenCLGridResult gridResult = new OpenCLGridResults().empty(BigInteger.ONE);
        try (LogCaptor logCaptor = LogCaptor.forClass(ProducerOpenCL.class)) {
            // the slot a grid's reader holds until its batch is in the consumer
            Semaphore submitSlot = producerOpenCL.getSubmitSlot();
            submitSlot.acquire();
            readerPool.execute(new ProducerOpenCL.ResultReaderRunnable(
                    gridResult, stuckConsumer, BigInteger.ONE, producerOpenCL, submitSlot));

            // pre-assert
            assertThat(readerInConsumer.await(READER_START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));

            // act -- the interrupt, the grid in progress, then the wait the run loop makes
            final long interruptNanos = System.nanoTime();
            producerOpenCL.interrupt();
            Thread.sleep(GRID_AFTER_INTERRUPT);
            producerOpenCL.awaitPendingBatches();
            final Duration sinceInterrupt = Duration.ofNanos(System.nanoTime() - interruptNanos);

            // assert
            final Duration deadline = Duration.ofSeconds(SHUTDOWN_TIMEOUT_SECONDS);
            assertThat(sinceInterrupt, is(lessThan(deadline.plus(TIMING_MARGIN))));
            assertThat(sinceInterrupt, is(greaterThanOrEqualTo(deadline.minus(TIMING_MARGIN))));
            assertThat(logCaptor.getErrorLogs(), hasSize(1));
            assertThat(logCaptor.getErrorLogs().get(0), containsString("1 of 1 result reader(s) still busy after"));
            assertThat(logCaptor.getErrorLogs().get(0), containsString("after the interrupt"));
        } finally {
            // the reader ends on its own once released; an interrupt would reach its wait first and be logged
            release.countDown();
            readerPool.shutdown();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="processSecretBase at the top of the key space">
    /**
     * The last aligned grid below the group order contains n, which the kernel cannot compute. Skipping
     * it is expected at the end of the key space, so it is a single WARN naming the grid, not an ERROR
     * with a stack trace; it is still recorded as the producer's last failure, so an orchestrator
     * reading {@code getLastFailure()} sees that this grid was not computed.
     */
    @Test
    @OpenCLTest
    public void processSecretBase_gridContainingTheGroupOrder_isSkippedWithAWarningAndRecorded() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.batchSizeInBits = 8;
        MockConsumer mockConsumer = new MockConsumer();
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL,
                mockConsumer,
                keyUtility,
                new MockKeyProducer(keyUtility, new Random(1)),
                bitHelper,
                new RuntimeStatistics());
        producerOpenCL.initProducer();
        // ...CD0364100, the grid that contains n = ...CD0364141
        BigInteger topGrid = Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.valueOf(0x41));

        try (LogCaptor producerLog = LogCaptor.forClass(ProducerOpenCL.class);
                LogCaptor abstractProducerLog = LogCaptor.forClass(AbstractProducer.class)) {
            producerOpenCL.processSecretBase(topGrid);

            assertThat(producerLog.getWarnLogs(), hasSize(1));
            assertThat(producerLog.getWarnLogs().get(0), containsString("skipped"));
            assertThat(
                    producerLog.getWarnLogs().get(0),
                    containsString(topGrid.toString(16).toUpperCase(Locale.ROOT)));
            assertThat(producerLog.getErrorLogs(), is(empty()));
            assertThat(abstractProducerLog.getErrorLogs(), is(empty()));
        } finally {
            producerOpenCL.releaseProducer();
        }
        assertThat(producerOpenCL.getLastFailure(), is(instanceOf(PrivateKeyTooLargeException.class)));
        assertThat(mockConsumer.publicKeyBytesArrayList, is(empty()));
    }

    /**
     * An unaligned base is a defect of the caller, not the expected end of the key space: it stays an
     * ERROR, and the grid is not computed.
     */
    @Test
    @OpenCLTest
    public void processSecretBase_unalignedBase_isAnErrorAndNotComputed() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.batchSizeInBits = 8;
        MockConsumer mockConsumer = new MockConsumer();
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL,
                mockConsumer,
                keyUtility,
                new MockKeyProducer(keyUtility, new Random(1)),
                bitHelper,
                new RuntimeStatistics());
        producerOpenCL.initProducer();

        try (LogCaptor producerLog = LogCaptor.forClass(ProducerOpenCL.class);
                LogCaptor abstractProducerLog = LogCaptor.forClass(AbstractProducer.class)) {
            producerOpenCL.processSecretBase(BigInteger.valueOf(0x1_0001));

            assertThat(producerLog.getWarnLogs(), is(empty()));
            assertThat(abstractProducerLog.getErrorLogs(), hasSize(1));
        } finally {
            producerOpenCL.releaseProducer();
        }
        assertThat(producerOpenCL.getLastFailure(), is(instanceOf(IllegalArgumentException.class)));
        assertThat(producerOpenCL.getLastFailure(), is(not(instanceOf(PrivateKeyTooLargeException.class))));
        assertThat(mockConsumer.publicKeyBytesArrayList, is(empty()));
    }
    // </editor-fold>
}
