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
        final MockStoppable mockStoppable = new MockStoppable(true);
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder, mockStoppable, new MockShutdown());
        // act
        finder.interrupt();
        // assert
    }
    
    @Test
    public void interrupt_producersSetAndNotInitialized_noExceptionThrown() throws IOException {
        // arrange
        final MockStoppable mockStoppable = new MockStoppable(true);
        final MockShutdown mockShutdown = new MockShutdown();
        CFinder cFinder = new CFinder();
        cFinder.producerJava.add(new CProducerJava());
        cFinder.producerJavaSecretsFiles.add(new CProducerJavaSecretsFiles());
        cFinder.producerOpenCL.add(new CProducerOpenCL());
        Finder finder = new Finder(cFinder, mockStoppable, mockShutdown);
        finder.configureProducer();
        // act
        finder.interrupt();
        // assert
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="producerFinished">
    @Test
    public void producerFinished_noProducersSet_shutdownCalled() throws IOException {
        // arrange
        final MockStoppable mockStoppable = new MockStoppable(true);
        final MockShutdown mockShutdown = new MockShutdown();
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder, mockStoppable, mockShutdown);
        // act
        finder.producerFinished();
        // assert
        assertThat(mockShutdown.shutdownCalledCounter.get(), is(equalTo(Integer.valueOf(1))));
    }
    
    @Test
    public void producerFinished_producersSetAndNotInitialized_shutdownCalled() throws IOException {
        // arrange
        final MockStoppable mockStoppable = new MockStoppable(true);
        final MockShutdown mockShutdown = new MockShutdown();
        CFinder cFinder = new CFinder();
        cFinder.producerJava.add(new CProducerJava());
        cFinder.producerJavaSecretsFiles.add(new CProducerJavaSecretsFiles());
        cFinder.producerOpenCL.add(new CProducerOpenCL());
        Finder finder = new Finder(cFinder, mockStoppable, mockShutdown);
        finder.configureProducer();
        // act
        finder.producerFinished();
        // assert
        assertThat(mockShutdown.shutdownCalledCounter.get(), is(equalTo(Integer.valueOf(1))));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getAllProducers">
    @Test
    public void getAllProducers_noProducersSet_returnEmptyList() throws IOException {
        // arrange
        final MockStoppable mockStoppable = new MockStoppable(true);
        final MockShutdown mockShutdown = new MockShutdown();
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder, mockStoppable, mockShutdown);
        // act
        List<Producer> allProducers = finder.getAllProducers();
        // assert
        assertThat(allProducers, is(empty()));
    }
    
    @Test
    public void getAllProducers_producersSetAndNotInitialized_returnList() throws IOException {
        // arrange
        final MockStoppable mockStoppable = new MockStoppable(true);
        final MockShutdown mockShutdown = new MockShutdown();
        CFinder cFinder = new CFinder();
        cFinder.producerJava.add(new CProducerJava());
        cFinder.producerJavaSecretsFiles.add(new CProducerJavaSecretsFiles());
        cFinder.producerOpenCL.add(new CProducerOpenCL());
        Finder finder = new Finder(cFinder, mockStoppable, mockShutdown);
        finder.configureProducer();
        // act
        List<Producer> allProducers = finder.getAllProducers();
        // assert
        assertThat(allProducers, hasSize(3));
    }
    // </editor-fold>
    
    
    // <editor-fold defaultstate="collapsed" desc="testFullCycle">
    @Test
    public void testFullCycle_producerJavaSetAndInitialized_XYT() throws IOException, InterruptedException {
        // arrange
        boolean compressed = false;
        boolean useStaticAmount = true;
        final MockStoppable mockStoppable = new MockStoppable(true);
        final MockShutdown mockShutdown = new MockShutdown();
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.runOnce = false;
        cFinder.producerJava.add(cProducerJava);
        
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();

        TestAddressesFiles testAddresses = new TestAddressesFiles(compressed);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, false);
        
        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        
        cFinder.consumerJava = cConsumerJava;
        Finder finder = new Finder(cFinder, mockStoppable, mockShutdown);
        // act
        finder.startConsumer();
        finder.configureProducer();
        finder.initProducer();
        finder.startProducer();
        Thread.sleep(Duration.ofSeconds(1L));
        
        // Attention, this is the correct order for a shutdown
        // TODO: This cant work because shouldRun blocks
        mockStoppable.shouldRun.set(false);
        finder.interrupt();
        
        // assert
        //assertThat(allProducers, hasSize(3));
    }
    // </editor-fold>

}
