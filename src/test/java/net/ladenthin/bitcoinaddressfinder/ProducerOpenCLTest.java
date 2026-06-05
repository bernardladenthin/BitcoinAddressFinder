// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
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
        ProducerOpenCL producerOpenCL =
                new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, injectedReaderPool);

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
        ProducerOpenCL producerOpenCL =
                new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
        ProducerOpenCL producerOpenCL =
                new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
        ProducerOpenCL producerOpenCL =
                new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
        ProducerOpenCL producerOpenCL =
                new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
        ProducerOpenCL producerOpenCL =
                new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        producerOpenCL.initProducer();

        // act
        int freeThreads = producerOpenCL.getFreeThreads();

        // assert
        assertThat(freeThreads, is(equalTo(Integer.valueOf(cProducerOpenCL.maxResultReaderThreads))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="waitTillFreeThreadsInPool">
    @Test
    public void waitTillFreeThreadsInPool_notInitialized_returnImmediately() throws Exception {
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL =
                new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        // act
        producerOpenCL.waitTillFreeThreadsInPool();

        // assert
    }

    @Test
    @OpenCLTest
    public void waitTillFreeThreadsInPool_initialized_returnImmediately() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL =
                new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        producerOpenCL.initProducer();

        // act
        producerOpenCL.waitTillFreeThreadsInPool();

        // assert
    }

    @Test
    @OpenCLTest
    public void waitTillFreeThreadsInPool_initializedAndThreadPoolFull_doNotReturn() throws Exception {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ThreadPoolExecutor injectedReaderPool =
                (ThreadPoolExecutor) Executors.newFixedThreadPool(cProducerOpenCL.maxResultReaderThreads);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper, injectedReaderPool);

        producerOpenCL.initProducer();

        Duration sleepDuration = Duration.ofSeconds(5L);

        for (int i = 0; i < cProducerOpenCL.maxResultReaderThreads; i++) {
            injectedReaderPool.submit(() -> {
                try {
                    Thread.sleep(sleepDuration);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
        // act
        final long before = System.currentTimeMillis();
        producerOpenCL.waitTillFreeThreadsInPool();
        final long after = System.currentTimeMillis();

        Duration durationOfWait = Duration.ofMillis(after - before);
        // assert
        // expect at least the half of the sleep duration
        assertThat(durationOfWait, is(greaterThan(sleepDuration.dividedBy(2))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="produceKeys">
    @Test
    public void produceKeys_notInitialized_illegalStateExceptionThrown() throws Exception {
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL =
                new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
        ProducerOpenCL producerOpenCL =
                new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        producerOpenCL.initProducer();

        // act
        producerOpenCL.produceKeys();

        // it takes some time to consume keys
        Thread.sleep(Duration.ofSeconds(10L));

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(greaterThan(0)));
    }
    // </editor-fold>
}
