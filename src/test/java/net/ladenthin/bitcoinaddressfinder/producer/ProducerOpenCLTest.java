// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import net.ladenthin.bitcoinaddressfinder.MockConsumer;
import net.ladenthin.bitcoinaddressfinder.MockKeyProducer;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import net.ladenthin.bitcoinaddressfinder.util.PrivateKeyTooLargeException;
import nl.altindag.log.LogCaptor;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProducerOpenCLTest {

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

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
