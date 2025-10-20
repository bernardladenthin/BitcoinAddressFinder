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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Duration;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import org.bitcoinj.base.Network;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

public class ProducerOpenCLTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();
    

    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @OpenCLTest
    @Test
    public void initProducer_configurationGiven_stateInitializedAndLogged() throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        AbstractProducerTest.verifyInitProducer(producerOpenCL);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="releaseProducer">
    @OpenCLTest
    @Test
    public void releaseProducer_configurationGiven_stateInitializedAndLoggedAndExecuterServiceShutdown() throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        AbstractProducerTest.verifyReleaseProducer(producerOpenCL);
        assertThat(producerOpenCL.resultReaderThreadPoolExecutor.isShutdown(), is(equalTo(Boolean.TRUE)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @OpenCLTest
    @Test
    public void initProducer_configurationGiven_stateInitializedAndOpenCLContextSet() throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        // pre-assert
        assertThat(producerOpenCL.openCLContext, nullValue());
        assertThat(producerOpenCL.state, is(equalTo(ProducerState.UNINITIALIZED)));
        
        // act
        producerOpenCL.initProducer();

        // assert
        assertThat(producerOpenCL.openCLContext, notNullValue());
        assertThat(producerOpenCL.state, is(equalTo(ProducerState.INITIALIZED)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="releaseProducers">
    @Test
    public void releaseProducers_notInitialized_noExceptionThrown() throws IOException, InterruptedException {
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);
        
        // act
        producerOpenCL.releaseProducer();
    }
    
    @Test
    @OpenCLTest
    public void releaseProducers_initialized_noExceptionThrownAndOpenCLContextFreed() throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);
        
        producerOpenCL.initProducer();
        
        // pre-assert
        assertThat(producerOpenCL.openCLContext, notNullValue());
        
        // act
        producerOpenCL.releaseProducer();
        
        // assert
        assertThat(producerOpenCL.openCLContext, nullValue());
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getFreeThreads">
    @Test
    public void getFreeThreads_notInitialized_numberOfFreeThreadsReturned() throws IOException, InterruptedException {
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);
        
        // act
        int freeThreads = producerOpenCL.getFreeThreads();
        
        // assert
        assertThat(freeThreads, is(equalTo(Integer.valueOf(cProducerOpenCL.maxResultReaderThreads))));
    }
    
    @Test
    @OpenCLTest
    public void getFreeThreads_initialized_numberOfFreeThreadsReturned() throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);
        
        producerOpenCL.initProducer();
        
        // act
        int freeThreads = producerOpenCL.getFreeThreads();
        
        // assert
        assertThat(freeThreads, is(equalTo(Integer.valueOf(cProducerOpenCL.maxResultReaderThreads))));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="waitTillFreeThreadsInPool">
    @Test
    public void waitTillFreeThreadsInPool_notInitialized_returnImmediately() throws IOException, InterruptedException {
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);
        
        // act
        producerOpenCL.waitTillFreeThreadsInPool();
        
        // assert
    }
    
    @Test
    @OpenCLTest
    public void waitTillFreeThreadsInPool_initialized_returnImmediately() throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);
        
        producerOpenCL.initProducer();
        
        // act
        producerOpenCL.waitTillFreeThreadsInPool();
        
        // assert
    }
    
    @Test
    @OpenCLTest
    public void waitTillFreeThreadsInPool_initializedAndThreadPoolFull_doNotReturn() throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);
        
        producerOpenCL.initProducer();
        
        Duration sleepDuration = Duration.ofSeconds(5L);
        
        for (int i = 0; i < cProducerOpenCL.maxResultReaderThreads; i++) {
            producerOpenCL.resultReaderThreadPoolExecutor.submit(() -> {
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
        
        Duration durationOfWait = Duration.ofMillis(after-before);
        // assert
        // expect at least the half of the sleep duration
        assertThat(durationOfWait, is(greaterThan(sleepDuration.dividedBy(2))));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="produceKeys">
    @Test(expected = IllegalStateException.class)
    public void produceKeys_notInitialized_illegalStateExceptionThrown() throws Exception {
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);
        
        // act
        producerOpenCL.produceKeys();
        
        // assert
    }
    
    @Test
    @OpenCLTest
    public void produceKeys_initialized_keysInConsumer() throws Exception {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerOpenCL producerOpenCL = new ProducerOpenCL(cProducerOpenCL, mockConsumer, keyUtility, mockKeyProducer, bitHelper);
        
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
