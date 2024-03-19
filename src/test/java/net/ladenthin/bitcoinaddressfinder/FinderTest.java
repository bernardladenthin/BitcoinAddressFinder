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

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;

public class FinderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // <editor-fold defaultstate="collapsed" desc="interrupt">
    @Test
    public void interrupt_noProducersSet_noExceptionThrown() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder);
        // act
        finder.interrupt();
        // assert
    }
    
    @Test
    public void interrupt_producersSetAndNotInitialized_noExceptionThrown() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cFinder.producerJava.add(cProducerJava);
        cFinder.producerJavaSecretsFiles.add(new CProducerJavaSecretsFiles());
        cFinder.producerOpenCL.add(new CProducerOpenCL());
        Finder finder = new Finder(cFinder);
        finder.configureProducer();
        // act
        finder.interrupt();
        // assert
    }
    
    @Test
    public void interrupt_consumerStarted_consumerNotStopped() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cFinder.producerJava.add(cProducerJava);
        cFinder.producerJavaSecretsFiles.add(new CProducerJavaSecretsFiles());
        cFinder.producerOpenCL.add(new CProducerOpenCL());
        
        // consumer java configuration
        configureConsumerJava(cFinder);
        
        Finder finder = new Finder(cFinder);
        finder.startConsumer();
        // act
        finder.interrupt();
        // assert
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="shutdownAndAwaitTermination">
    @Test
    public void shutdownAndAwaitTermination_noProducersSet_shutdownCalled() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder);
        // pre-assert
        assertThat(finder.producerExecutorService.isTerminated(), is(equalTo(Boolean.FALSE)));
        // act
        finder.shutdownAndAwaitTermination();
        // assert
        assertThat(finder.producerExecutorService.isTerminated(), is(equalTo(Boolean.TRUE)));
    }
    
    @Test
    public void shutdownAndAwaitTermination_producersSetAndNotInitialized_shutdownCalled() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cFinder.producerJava.add(cProducerJava);
        cFinder.producerJavaSecretsFiles.add(new CProducerJavaSecretsFiles());
        cFinder.producerOpenCL.add(new CProducerOpenCL());
        Finder finder = new Finder(cFinder);
        finder.configureProducer();
        // act
        finder.shutdownAndAwaitTermination();
        // assert
        assertThat(finder.producerExecutorService.isTerminated(), is(equalTo(Boolean.TRUE)));
    }
    
    /**
     * Attention, this is an await time test. This tests changes {@link Finder#AWAIT_DURATION_TERMINATE}.
     */
    @Test
    public void shutdownAndAwaitTermination_producersSetAndInitialized_shutdownCalledAndAwaitTermination() throws IOException {
        // Attention: Change the duration.
        Finder.AWAIT_DURATION_TERMINATE = AwaitTimeTests.AWAIT_DURATION;
        
        // arrange
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.runOnce = false;
        cFinder.producerJava.add(cProducerJava);
        
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startConsumer();
        finder.configureProducer();
        finder.initProducer();
        finder.startProducer();

        // act
        long beforeAct = System.currentTimeMillis();
        finder.shutdownAndAwaitTermination();
        
        // assert
        long afterAct = System.currentTimeMillis();
        Duration waitTime = Duration.ofMillis(afterAct-beforeAct);
        
        // assert the waiting time is over, substract imprecision
        assertThat(waitTime, is(greaterThan(Finder.AWAIT_DURATION_TERMINATE.minus(AwaitTimeTests.IMPRECISION))));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getAllProducers">
    @Test
    public void getAllProducers_noProducersSet_returnEmptyList() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder);
        // act
        List<Producer> allProducers = finder.getAllProducers();
        // assert
        assertThat(allProducers, is(empty()));
    }
    
    @Test
    public void getAllProducers_producersSetAndNotInitialized_returnList() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cFinder.producerJava.add(cProducerJava);
        cFinder.producerJavaSecretsFiles.add(new CProducerJavaSecretsFiles());
        cFinder.producerOpenCL.add(new CProducerOpenCL());
        Finder finder = new Finder(cFinder);
        finder.configureProducer();
        // act
        List<Producer> allProducers = finder.getAllProducers();
        // assert
        assertThat(allProducers, hasSize(3));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="testFullCycle">
    @Test
    public void testFullCycle_producerJavaSetAndInitialized_statesCorrect() throws IOException, InterruptedException {
        // arrange
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.runOnce = false;
        cFinder.producerJava.add(cProducerJava);
        
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        // act and assert the full cycle
        {
            // pre-assert
            assertThat(finder.getAllConsumers(), hasSize(0));
            // act
            finder.startConsumer();
            // assert
            assertThat(finder.getAllConsumers(), hasSize(1));
        }
        
        {
            // pre-assert
            assertThat(finder.getAllProducers(), hasSize(0));
            // act
            finder.configureProducer();
            // assert
            assertThat(finder.getAllProducers(), hasSize(1));
            assertProducerState(finder.getAllProducers(), ProducerState.UNINITIALIZED);
        }
        
        {
            // act
            finder.initProducer();
            // assert
            assertProducerState(finder.getAllProducers(), ProducerState.INITIALIZED);
        }
        // catch the reference, it is not possible to get the reference afterwards the interrupt
        final List<Producer> allProducers = finder.getAllProducers();
        {
            // act
            finder.startProducer();
            // wait
            Thread.sleep(Duration.ofSeconds(1L));
        
            // assert
            assertProducerState(allProducers, ProducerState.RUNNING);
        }
        {
            // act
            finder.interrupt();
            // assert
            assertThat(finder.getAllProducers(), hasSize(0));
            assertProducerState(allProducers, ProducerState.NOT_RUNNING);
        }
        
        {
            // pre-assert
            assertThat(finder.getAllConsumers(), hasSize(1));
            // act
            finder.shutdownAndAwaitTermination();
            // assert
            assertThat(finder.getAllConsumers(), hasSize(0));
        }
    }
    // </editor-fold>

    private void configureConsumerJava(CFinder cFinder) throws IOException {
        boolean compressed = false;
        boolean useStaticAmount = true;
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        
        TestAddressesFiles testAddresses = new TestAddressesFiles(compressed);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, false);
        
        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        
        cFinder.consumerJava = cConsumerJava;
    }

    private static void assertProducerState(List<Producer> producerStateProviders, ProducerState expectedProducerState) {
        for (ProducerStateProvider producerStateProvider : producerStateProviders) {
            assertThat(producerStateProvider.getState(), is(equalTo(expectedProducerState)));
        }
    }
}
