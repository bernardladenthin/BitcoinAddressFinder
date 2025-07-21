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

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
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
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaIncremental;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandomInstance;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
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
        configureProducerWithExamples(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.configureProducer();
        // act
        finder.interrupt();
        // assert
    }
    
    @Test
    public void interrupt_consumerStarted_consumerNotStopped() throws IOException {
        // arrange
        CFinder cFinder = new CFinder();
        configureProducerWithExamples(cFinder);
        
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
        configureProducerWithExamples(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.configureProducer();
        // act
        finder.shutdownAndAwaitTermination();
        // assert
        assertThat(finder.producerExecutorService.isTerminated(), is(equalTo(Boolean.TRUE)));
    }
    
    @AwaitTimeTest
    @Test
    public void shutdownAndAwaitTermination_producersSetAndInitialized_shutdownCalledAndAwaitTermination() throws IOException {
        // Change await duration
        Finder.AWAIT_DURATION_TERMINATE = AwaitTimeTests.AWAIT_DURATION;
        
        // Attention: During the long duration, this test produce a lot of debug and warn output, prevent it by set the log details
        new LogLevelChange().turnOff();
        
        // arrange
        CFinder cFinder = new CFinder();
        String keyProducerId = "exampleId";
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = keyProducerId;
        cProducerJava.runOnce = false;
        cFinder.producerJava.add(cProducerJava);
        configureKeyProducerJavaRandom(keyProducerId, cFinder);
        
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
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
        configureProducerWithExamples(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.configureProducer();
        // act
        List<Producer> allProducers = finder.getAllProducers();
        // assert
        assertThat(allProducers, hasSize(3));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="startKeyProducer">
    @Test(expected = KeyProducerIdNullException.class)
    public void startKeyProducer_keyProducerIdIsNull_ExceptionThrown() throws IOException, InterruptedException {
        // arrange
        CFinder cFinder = new CFinder();
        configureKeyProducerJavaRandom(null, cFinder);
        
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        // act
        finder.startKeyProducer();
    }
    
    @Test(expected = KeyProducerIdIsNotUniqueException.class)
    public void startKeyProducer_keyProducerIdIsNotUnique_ExceptionThrown() throws IOException, InterruptedException {
        // arrange
        CFinder cFinder = new CFinder();
        String sameIdTwice = "123";
        configureKeyProducerJavaRandom(sameIdTwice, cFinder);
        configureKeyProducerJavaRandom(sameIdTwice, cFinder);
        
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        // act
        finder.startKeyProducer();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="configureProducer">
    @Test(expected = KeyProducerIdUnknownException.class)
    public void configureProducer_keyProducerIdIsUnknown_ExceptionThrown() throws IOException, InterruptedException {
        // arrange
        CFinder cFinder = new CFinder();
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.runOnce = false;
        // null is not valid or will find any other id
        cProducerJava.keyProducerId = null;
        cFinder.producerJava.add(cProducerJava);
        configureKeyProducerJavaRandom("unknownId", cFinder);
        
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        
        finder.startKeyProducer();
        
        // act
        finder.configureProducer();
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="testFullCycle">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_LOCAL_JAVA_KEY_PRODUCER, location = CommonDataProvider.class)
    public void testFullCycle_keyProducerJavaRandomSetAndInitialized_statesCorrect(CommonDataProvider.KeyProducerTypesLocal keyProducerType) throws IOException, InterruptedException {
        // arrange
        CFinder cFinder = new CFinder();
        String keyProducerId = "exampleId";
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = keyProducerId;
        cProducerJava.runOnce = false;
        cFinder.producerJava.add(cProducerJava);
        switch (keyProducerType) {
            case KeyProducerJavaRandom:
                configureKeyProducerJavaRandom(keyProducerId, cFinder);
                break;
            case KeyProducerJavaIncremental:
                configureKeyProducerJavaIncremental(keyProducerId, cFinder);
                break;
            case KeyProducerJavaBip39:
                configureKeyProducerJavaBip39(keyProducerId, cFinder);
                break;
            case KeyProducerJavaSocket:
                configureKeyProducerJavaSocket(keyProducerId, cFinder);
                break;
            default:
                throw new IllegalArgumentException("Unknown KeyProducerType: " + keyProducerType);
        }
        
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        // act and assert the full cycle
        {
            // pre-assert
            assertThat(finder.getKeyProducers().keySet(), hasSize(0));
            // act
            finder.startKeyProducer();
            // assert
            assertThat(finder.getKeyProducers().keySet(), hasSize(1));
        }
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
    
    @Test
    public void startKeyProducer_allConfiguredKeyProducerTypes_haveInstances() throws IOException {
        // Arrange
        CFinder cFinder = new CFinder();

        // Configure one instance for each KeyProducer type
        // 1. JavaRandom
        CKeyProducerJavaRandom javaRandom = new CKeyProducerJavaRandom();
        javaRandom.keyProducerId = "randomId";
        javaRandom.keyProducerJavaRandomInstance = CKeyProducerJavaRandomInstance.RANDOM_CUSTOM_SEED;
        javaRandom.customSeed = 123L;
        cFinder.keyProducerJavaRandom.add(javaRandom);

        // 2. JavaBip39
        CKeyProducerJavaBip39 bip39 = new CKeyProducerJavaBip39();
        bip39.keyProducerId = "bip39Id";
        bip39.mnemonic = KeyProducerJavaBip39Test.MNEMONIC;
        cFinder.keyProducerJavaBip39.add(bip39);

        // 3. JavaIncremental
        CKeyProducerJavaIncremental incremental = new CKeyProducerJavaIncremental();
        incremental.keyProducerId = "incrementalId";
        cFinder.keyProducerJavaIncremental.add(incremental);

        // 4. JavaSocket
        CKeyProducerJavaSocket socket = new CKeyProducerJavaSocket();
        socket.keyProducerId = "socketId";
        socket.host = "localhost";
        socket.port = 12345;
        socket.mode = net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket.Mode.CLIENT;
        cFinder.keyProducerJavaSocket.add(socket);

        Finder finder = new Finder(cFinder);

        // Act
        finder.startKeyProducer();

        // Assert
        assertThat(finder.getKeyProducers().keySet(), hasItems("randomId", "bip39Id", "incrementalId", "socketId"));

        // Additionally assert each instance is of expected class type
        assertThat(finder.getKeyProducers().get("randomId"), instanceOf(KeyProducerJavaRandom.class));
        assertThat(finder.getKeyProducers().get("bip39Id"), instanceOf(KeyProducerJavaBip39.class));
        assertThat(finder.getKeyProducers().get("incrementalId"), instanceOf(KeyProducerJavaIncremental.class));
        assertThat(finder.getKeyProducers().get("socketId"), instanceOf(KeyProducerJavaSocket.class));
        
        // Interrupt and free producers
        finder.interrupt();

        // Assert keyProducers map is empty after interrupt
        assertThat(finder.getKeyProducers().keySet(), is(empty()));
    }
    
    private void configureProducerWithExamples(CFinder cFinder) {
        String keyProducerId_producerJava = "exampleId_producerJava";
        String keyProducerId_producerJavaSecretsFiles = "exampleId_producerJavaSecretsFiles";
        String keyProducerId_producerOpenCL = "exampleId_producerOpenCL";
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = keyProducerId_producerJava;
        cFinder.producerJava.add(cProducerJava);
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();
        cProducerJavaSecretsFiles.keyProducerId = keyProducerId_producerJavaSecretsFiles;
        cFinder.producerJavaSecretsFiles.add(cProducerJavaSecretsFiles);
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.keyProducerId = keyProducerId_producerOpenCL;
        cFinder.producerOpenCL.add(cProducerOpenCL);
        configureKeyProducerJavaRandom(keyProducerId_producerJava, cFinder);
        configureKeyProducerJavaRandom(keyProducerId_producerJavaSecretsFiles, cFinder);
        configureKeyProducerJavaRandom(keyProducerId_producerOpenCL, cFinder);
    }
    
    private void configureKeyProducerJavaRandom(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = keyProducerId;
        cKeyProducerJavaRandom.keyProducerJavaRandomInstance = CKeyProducerJavaRandomInstance.RANDOM_CUSTOM_SEED;
        cKeyProducerJavaRandom.customSeed = 0L;
        cFinder.keyProducerJavaRandom.add(cKeyProducerJavaRandom);
    }
    
    private void configureKeyProducerJavaIncremental(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaIncremental incremental = new CKeyProducerJavaIncremental();
        incremental.keyProducerId = keyProducerId;
        cFinder.keyProducerJavaIncremental.add(incremental);
    }
    
    private void configureKeyProducerJavaBip39(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaBip39 bip39 = new CKeyProducerJavaBip39();
        bip39.keyProducerId = keyProducerId;
        bip39.mnemonic = KeyProducerJavaBip39Test.MNEMONIC;
        cFinder.keyProducerJavaBip39.add(bip39);
    }
    
    private void configureKeyProducerJavaSocket(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaSocket socket = new CKeyProducerJavaSocket();
        socket.keyProducerId = keyProducerId;
        socket.host = "localhost"; // or any dummy valid host for testing
        socket.port = 12345;       // dummy port
        socket.mode = CKeyProducerJavaSocket.Mode.CLIENT;
        cFinder.keyProducerJavaSocket.add(socket);
    }
    
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
