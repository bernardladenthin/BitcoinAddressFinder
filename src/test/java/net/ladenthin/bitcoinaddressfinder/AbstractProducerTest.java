// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
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
    public void initProducer_configurationGiven_stateInitializedAndLogged() throws IOException, InterruptedException {
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl =
                new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        verifyInitProducer(abstractProducerTestImpl);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @Test
    public void releaseProducer_configurationGiven_stateInitializedAndLogged()
            throws IOException, InterruptedException {
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl =
                new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        verifyReleaseProducer(abstractProducerTestImpl);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="createSecretBase">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_CREATE_SECRET_BASE_LOGGED)
    public void createSecretBase_secretGiven_bitsKilledAndLogged(
            String givenSecret,
            int batchSizeInBits,
            String expectedSecretBase,
            String logInfo0,
            String logTrace0,
            String logTrace1,
            String logTrace2,
            String logTrace3,
            String logTrace4)
            throws IOException, InterruptedException, DecoderException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = batchSizeInBits;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl =
                new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
    public void createSecretBase_secretGivenAndLogSecretBaseDisabledTraceEnabled_bitsKilledAndLogged()
            throws IOException, InterruptedException, DecoderException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 2;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl =
                new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
    public void createSecretBase_secretGivenAndLogSecretBaseEnabledTraceDisabled_bitsKilledAndLogged()
            throws IOException, InterruptedException, DecoderException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 2;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl =
                new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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

    static void verifyReleaseProducer(AbstractProducer abstractProducer) {
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

    static void verifyInitProducer(AbstractProducer abstractProducer) {
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
    public void run_notInitialized_illegalStateExceptionThrown() throws IOException, InterruptedException {
        // arrange
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl =
                new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        // act
        assertThrows(IllegalStateException.class, () -> abstractProducerTestImpl.run());
    }

    @Test
    public void run_interruptedBeforeStarted_stateSetToNotRunning() throws IOException, InterruptedException {
        // arrange
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl =
                new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
    public void run_exceptionInProduceKeys_exceptionCaughtAndLoggedToError() throws IOException, InterruptedException {
        // arrange
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);

        AbstractProducerTestImpl abstractProducerTestImpl =
                new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper) {
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
    // </editor-fold>
}
