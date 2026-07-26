// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.MockConsumer;
import net.ladenthin.bitcoinaddressfinder.MockKeyProducer;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import nl.altindag.log.LogCaptor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class AbstractProducerTest {

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @Test
    public void initProducer_configurationGiven_stateInitializedAndLogged() throws Exception {
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        verifyInitProducer(abstractProducerTestImpl);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @Test
    public void releaseProducer_configurationGiven_stateInitializedAndLogged() throws Exception {
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        verifyReleaseProducer(abstractProducerTestImpl);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="createSecretBase">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_CREATE_SECRET_BASE_LOGGED)
    public void createSecretBase_secretGiven_alignedDownAndLogged(
            String givenSecret,
            int batchSizeInBits,
            String expectedSecretBase,
            String logInfo0,
            String logTrace0,
            String logTrace1,
            String logTrace2,
            String logTrace3,
            String logTrace4)
            throws Exception, DecoderException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = batchSizeInBits;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        BigInteger secret = new BigInteger(1, Hex.decodeHex(givenSecret));
        boolean logSecretBase = true;

        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractProducer.class)) {
            logCaptor.setLogLevelToTrace();

            // act
            BigInteger secretBase = abstractProducerTestImpl.createSecretBase(secret, logSecretBase);

            // assert
            assertThat(keyUtility.bigIntegerToFixedLengthHex(secretBase), is(equalTo(expectedSecretBase)));

            List<String> infoLogs = logCaptor.getInfoLogs();
            List<String> traceLogs = logCaptor.getTraceLogs();
            assertThat(infoLogs, hasSize(1));
            assertThat(traceLogs, hasSize(5));
            // assert log secret base
            assertThat(infoLogs.get(0), is(equalTo(logInfo0)));
            // assert log trace
            assertThat(traceLogs.get(0), is(equalTo(logTrace0)));
            assertThat(traceLogs.get(1), is(equalTo(logTrace1)));
            assertThat(traceLogs.get(2), is(equalTo(logTrace2)));
            assertThat(traceLogs.get(3), is(equalTo(logTrace3)));
            assertThat(traceLogs.get(4), is(equalTo(logTrace4)));
        }
    }

    @Test
    public void createSecretBase_secretGivenAndLogSecretBaseDisabledTraceEnabled_alignedDownAndLogged()
            throws Exception, DecoderException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 2;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        BigInteger secret = new BigInteger(Hex.decodeHex("ABCDEF"));
        boolean logSecretBase = false;

        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractProducer.class)) {
            logCaptor.setLogLevelToTrace();

            // act
            abstractProducerTestImpl.createSecretBase(secret, logSecretBase);

            // assert log secret base disabled
            assertThat(logCaptor.getInfoLogs(), hasSize(0));
            // assert log trace
            assertThat(logCaptor.getTraceLogs(), hasSize(5));
        }
    }

    @Test
    public void createSecretBase_secretGivenAndLogSecretBaseEnabledTraceDisabled_alignedDownAndLogged()
            throws Exception, DecoderException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 2;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        BigInteger secret = new BigInteger(Hex.decodeHex("ABCDEF"));
        boolean logSecretBase = true;

        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractProducer.class)) {
            logCaptor.setLogLevelToInfo();

            // act
            abstractProducerTestImpl.createSecretBase(secret, logSecretBase);

            // assert log secret base
            assertThat(logCaptor.getInfoLogs(), hasSize(1));
            // assert log trace
            assertThat(logCaptor.getTraceLogs(), hasSize(0));
        }
    }
    // </editor-fold>

    static void verifyReleaseProducer(AbstractProducer abstractProducer) throws Exception {
        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractProducer.class)) {
            abstractProducer.initProducer();

            // act
            abstractProducer.releaseProducer();

            // assert
            assertThat(abstractProducer.state, is(equalTo(ProducerState.INITIALIZED)));

            List<String> infoLogs = logCaptor.getInfoLogs();
            assertThat(infoLogs, hasSize(2));
            // assert log initProducer
            assertThat(infoLogs.get(1), is(equalTo("Release producer.")));
        }
    }

    static void verifyInitProducer(AbstractProducer abstractProducer) throws Exception {
        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractProducer.class)) {
            // pre-assert
            assertThat(abstractProducer.state, is(equalTo(ProducerState.UNINITIALIZED)));

            // act
            abstractProducer.initProducer();

            // assert
            assertThat(abstractProducer.state, is(equalTo(ProducerState.INITIALIZED)));

            assertThat(logCaptor.getInfoLogs(), hasItem(equalTo("Init producer.")));
        }
    }

    // <editor-fold defaultstate="collapsed" desc="run">
    @Test
    public void run_notInitialized_illegalStateExceptionThrown() throws Exception {
        // arrange
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // act
        assertThrows(IllegalStateException.class, () -> abstractProducerTestImpl.run());
    }

    @Test
    public void run_interruptedBeforeStarted_stateSetToNotRunning() throws Exception {
        // arrange
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractProducer.class)) {
            abstractProducerTestImpl.initProducer();
            abstractProducerTestImpl.interrupt();

            // act
            abstractProducerTestImpl.run();

            // assert
            assertThat(abstractProducerTestImpl.state, is(equalTo(ProducerState.NOT_RUNNING)));

            assertThat(
                    logCaptor.getInfoLogs(), hasItem(equalTo("Producer was interrupted before it started running.")));
        }
    }

    @Test
    public void run_exceptionInProduceKeys_exceptionCaughtAndLoggedToError() throws Exception {
        // arrange
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);

        AbstractProducerTestImpl abstractProducerTestImpl =
                new AbstractProducerTestImpl(
                        cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics()) {
                    @Override
                    public void produceKeys() {
                        throw new RuntimeException("Test exception");
                    }
                };

        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractProducer.class)) {
            abstractProducerTestImpl.initProducer();

            // act
            abstractProducerTestImpl.run();

            // assert
            assertThat(abstractProducerTestImpl.state, is(equalTo(ProducerState.NOT_RUNNING)));

            assertThat(logCaptor.getErrorLogs(), hasItem(equalTo("Error in produceKeys")));
        }
    }

    /**
     * A producer that dies mid-run must say so, not just log it.
     *
     * <p>Logging alone leaves every caller blind: {@code run()} swallows the exception on the
     * executor thread, so an orchestrator waiting out a measurement window on another thread sees a
     * producer that simply stopped producing. {@code TuneConfiguration} then reported the partial
     * throughput of a crashed arm as if it were a valid measurement — a real sweep recorded
     * "1,022,288 candidates/s" for an arm whose producer had died of {@code CL_OUT_OF_RESOURCES}
     * seconds in, and printed it next to healthy arms with nothing to distinguish it.
     */
    /**
     * The failure mode that actually occurs on a GPU: {@code ProducerOpenCL.processSecretBase}
     * catches a {@link RuntimeException} per secret, logs it and carries on, so the producer never
     * dies and nothing propagates anywhere. A real Intel Arc sweep threw {@code CL_OUT_OF_RESOURCES}
     * <b>40 times inside a single 20 s arm</b> while the producer stayed alive the whole window —
     * and the arm was reported as "1,022,288 candidates/s", a number describing a device that was
     * failing continuously.
     *
     * <p>Terminal-failure tracking alone cannot see this: the loop never ends. A swallowed failure
     * has to be recorded at the point it is swallowed.
     */
    @Test
    public void getLastFailure_secretFailureSwallowed_isRecordedAlthoughTheProducerKeepsRunning() throws Exception {
        // arrange
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, new Random(1));
        AbstractProducerTestImpl producer = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());
        RuntimeException swallowed = new RuntimeException("CL_OUT_OF_RESOURCES");

        // pre-assert
        assertThat(producer.getLastFailure(), is(nullValue()));

        // act — exactly what ProducerOpenCL.processSecretBase does with a CLException
        producer.logErrorInProduceKeys(swallowed, BigInteger.ONE);

        // assert: recorded, and the producer is still perfectly alive
        assertThat(producer.getLastFailure(), is(sameInstance(swallowed)));
        assertThat(producer.getState(), is(not(equalTo(ProducerState.NOT_RUNNING))));
    }

    @Test
    public void getLastFailure_exceptionInProduceKeys_returnsTheExceptionThatStoppedTheLoop() throws Exception {
        // arrange
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, new Random(1));
        RuntimeException thrown = new RuntimeException("CL_OUT_OF_RESOURCES");

        AbstractProducerTestImpl producer =
                new AbstractProducerTestImpl(
                        cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics()) {
                    @Override
                    public void produceKeys() {
                        throw thrown;
                    }
                };
        producer.initProducer();

        // pre-assert: nothing has gone wrong yet
        assertThat(producer.getLastFailure(), is(nullValue()));

        // act
        producer.run();

        // assert
        assertThat(producer.getLastFailure(), is(sameInstance(thrown)));
    }

    /**
     * The counterpart: a producer that stopped because it was asked to must not look like one that
     * crashed, or every clean shutdown would be reported as a failure.
     */
    @Test
    public void getLastFailure_producerRanWithoutError_returnsNull() throws Exception {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.runOnce = true;
        MockConsumer mockConsumer = new MockConsumer();
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, new Random(1));

        AbstractProducerTestImpl producer = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());
        producer.initProducer();

        // act
        producer.run();

        // assert
        assertThat(producer.getState(), is(equalTo(ProducerState.NOT_RUNNING)));
        assertThat(producer.getLastFailure(), is(nullValue()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="waitTillProducerNotRunning">
    @Test
    public void waitTillProducerNotRunning_stateAlreadyNotRunning_returnsImmediately() throws IOException {
        CProducer cProducer = new CProducer();
        cProducer.shutdownTimeoutSeconds = 60;
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl producer = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());
        producer.state = ProducerState.NOT_RUNNING;

        Instant start = Instant.now();
        producer.waitTillProducerNotRunning();
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(elapsed.toMillis(), is(lessThan(1000L)));
    }

    @Test
    public void waitTillProducerNotRunning_stateBecomesNotRunning_returnsWithinDuration() throws Exception {
        CProducer cProducer = new CProducer();
        cProducer.shutdownTimeoutSeconds = 60;
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl producer = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());
        producer.state = ProducerState.RUNNING;

        CountDownLatch flipped = new CountDownLatch(1);
        Thread flipper = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            producer.signalNotRunning();
            flipped.countDown();
        });
        flipper.start();

        Instant start = Instant.now();
        producer.waitTillProducerNotRunning();
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(flipped.await(1, TimeUnit.SECONDS), is(true));
        assertThat(elapsed.toMillis(), is(lessThan(2000L)));
        flipper.join();
    }

    @Test
    public void waitTillProducerNotRunning_stateStaysRunning_returnsAfterTimeoutAndLogsError() throws IOException {
        CProducer cProducer = new CProducer();
        cProducer.shutdownTimeoutSeconds = 1;
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl producer = new AbstractProducerTestImpl(
                cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());
        producer.state = ProducerState.RUNNING;

        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractProducer.class)) {
            Instant start = Instant.now();
            producer.waitTillProducerNotRunning();
            Duration elapsed = Duration.between(start, Instant.now());

            assertThat(elapsed.toSeconds(), is(equalTo(1L)));
            assertThat(producer.state, is(equalTo(ProducerState.RUNNING)));
            assertThat(
                    logCaptor.getErrorLogs(), hasItem(containsString("waitTillProducerNotRunning timed out after 1s")));
        }
    }
    // </editor-fold>
}
