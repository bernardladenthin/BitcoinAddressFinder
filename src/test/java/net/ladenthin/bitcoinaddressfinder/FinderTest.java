// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaIncremental;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandomInstance;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.keyproducer.*;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaSocketTest;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaZmqTest;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import org.slf4j.Logger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;

public class FinderTest {

    @TempDir
    public java.nio.file.Path folder;

    // <editor-fold defaultstate="collapsed" desc="interrupt">
    @Test
    public void interrupt_noProducersSet_noExceptionThrown() throws IOException {
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder);
        finder.interrupt();
    }
    
    @Test
    public void interrupt_producersSetAndNotInitialized_noExceptionThrown() throws IOException {
        CFinder cFinder = new CFinder();
        configureProducerWithExamples(cFinder);
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.startConsumer();
        finder.configureProducer();
        finder.interrupt();
    }
    
    @Test
    public void interrupt_consumerStarted_consumerNotStopped() throws IOException {
        CFinder cFinder = new CFinder();
        configureProducerWithExamples(cFinder);
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startConsumer();
        finder.interrupt();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="shutdownAndAwaitTermination">
    @Test
    public void shutdownAndAwaitTermination_noProducersSet_shutdownCalled() throws IOException {
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder);
        assertThat(finder.producerExecutorService.isTerminated(), is(equalTo(Boolean.FALSE)));
        finder.shutdownAndAwaitTermination();
        assertThat(finder.producerExecutorService.isTerminated(), is(equalTo(Boolean.TRUE)));
    }
    
    @Test
    public void shutdownAndAwaitTermination_producersSetAndNotInitialized_shutdownCalled() throws IOException {
        CFinder cFinder = new CFinder();
        configureProducerWithExamples(cFinder);
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.startConsumer();
        finder.configureProducer();
        finder.shutdownAndAwaitTermination();
        assertThat(finder.producerExecutorService.isTerminated(), is(equalTo(Boolean.TRUE)));
    }
    
    @AwaitTimeTest
    @Test
    public void shutdownAndAwaitTermination_producersSetAndInitialized_shutdownCalledAndAwaitTermination() throws IOException {
        Finder.AWAIT_DURATION_TERMINATE = AwaitTimeTests.AWAIT_DURATION;
        new LogLevelChange().turnOff();
        
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

        long beforeAct = System.currentTimeMillis();
        finder.shutdownAndAwaitTermination();
        
        long afterAct = System.currentTimeMillis();
        Duration waitTime = Duration.ofMillis(afterAct-beforeAct);
        
        assertThat(waitTime, is(greaterThan(Finder.AWAIT_DURATION_TERMINATE.minus(AwaitTimeTests.IMPRECISION))));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getAllProducers">
    @Test
    public void getAllProducers_noProducersSet_returnEmptyList() throws IOException {
        CFinder cFinder = new CFinder();
        Finder finder = new Finder(cFinder);
        List<Producer> allProducers = finder.getAllProducers();
        assertThat(allProducers, is(empty()));
    }
    
    @Test
    public void getAllProducers_producersSetAndNotInitialized_returnList() throws IOException {
        CFinder cFinder = new CFinder();
        configureProducerWithExamples(cFinder);
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();
        finder.startConsumer();
        finder.configureProducer();
        List<Producer> allProducers = finder.getAllProducers();
        assertThat(allProducers, hasSize(3));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="startKeyProducer">
    @Test
    public void startKeyProducer_keyProducerIdIsNull_ExceptionThrown() throws IOException, InterruptedException {
        org.junit.jupiter.api.Assertions.assertThrows(KeyProducerIdNullException.class, () -> {
            CFinder cFinder = new CFinder();
            configureKeyProducerJavaRandom(null, cFinder);
            configureConsumerJava(cFinder);
            Finder finder = new Finder(cFinder);
            finder.startKeyProducer();
        });
    }
    
    @Test
    public void startKeyProducer_keyProducerIdIsNotUnique_ExceptionThrown() throws IOException, InterruptedException {
        org.junit.jupiter.api.Assertions.assertThrows(KeyProducerIdIsNotUniqueException.class, () -> {
            CFinder cFinder = new CFinder();
            String sameIdTwice = "123";
            configureKeyProducerJavaRandom(sameIdTwice, cFinder);
            configureKeyProducerJavaRandom(sameIdTwice, cFinder);
            configureConsumerJava(cFinder);
            Finder finder = new Finder(cFinder);
            finder.startKeyProducer();
        });
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="configureProducer">
    @Test
    public void configureProducer_keyProducerIdIsUnknown_ExceptionThrown() throws IOException, InterruptedException {
        org.junit.jupiter.api.Assertions.assertThrows(KeyProducerIdUnknownException.class, () -> {
            CFinder cFinder = new CFinder();
            final CProducerJava cProducerJava = new CProducerJava();
            cProducerJava.runOnce = false;
            cProducerJava.keyProducerId = null;
            cFinder.producerJava.add(cProducerJava);
            configureKeyProducerJavaRandom("unknownId", cFinder);
            configureConsumerJava(cFinder);
    
            Finder finder = new Finder(cFinder);
    
            finder.startConsumer();
            finder.startKeyProducer();
            finder.configureProducer();
        });
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="testFullCycle">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_KEY_PRODUCER_TYPES)
    public void testFullCycle_keyProducerJavaSetAndInitialized_statesCorrect(CommonDataProvider.KeyProducerTypesLocal keyProducerType) throws IOException, InterruptedException {
        CFinder cFinder = new CFinder();
        String keyProducerId = "exampleId";
        final CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = keyProducerId;
        cProducerJava.runOnce = false;
        cFinder.producerJava.add(cProducerJava);
        final Class keyProducerClass;
        switch (keyProducerType) {
            case KeyProducerJavaRandom:
                keyProducerClass = KeyProducerJavaRandom.class;
                configureKeyProducerJavaRandom(keyProducerId, cFinder);
                break;
            case KeyProducerJavaIncremental:
                keyProducerClass = KeyProducerJavaIncremental.class;
                configureKeyProducerJavaIncremental(keyProducerId, cFinder);
                break;
            case KeyProducerJavaBip39:
                keyProducerClass = KeyProducerJavaBip39.class;
                configureKeyProducerJavaBip39(keyProducerId, cFinder);
                break;
            case KeyProducerJavaSocket:
                keyProducerClass = KeyProducerJavaSocket.class;
                configureKeyProducerJavaSocket(keyProducerId, cFinder);
                break;
            case KeyProducerJavaWebSocket:
                keyProducerClass = KeyProducerJavaWebSocket.class;
                configureKeyProducerJavaWebSocket(keyProducerId, cFinder);
                break;
            case KeyProducerJavaZmq:
                keyProducerClass = KeyProducerJavaZmq.class;
                configureKeyProducerJavaZmq(keyProducerId, cFinder);
                break;
            default:
                throw new IllegalArgumentException("Unknown KeyProducerType: " + keyProducerType);
        }
        
        configureConsumerJava(cFinder);
        Finder finder = new Finder(cFinder);
        {
            assertThat(finder.getKeyProducers().keySet(), hasSize(0));
            finder.startKeyProducer();
            assertThat(finder.getKeyProducers().keySet(), hasSize(1));
        }
        {
            KeyProducer keyProducer = Objects.requireNonNull(finder.getKeyProducers().get(keyProducerId));
            Logger logger = keyProducer.getLogger();
            assertThat(logger.getName(), is(keyProducerClass.getCanonicalName()));
        }
        {
            assertThat(finder.getAllConsumers(), hasSize(0));
            finder.startConsumer();
            assertThat(finder.getAllConsumers(), hasSize(1));
        }
        
        {
            assertThat(finder.getAllProducers(), hasSize(0));
            finder.configureProducer();
            assertThat(finder.getAllProducers(), hasSize(1));
            assertProducerState(finder.getAllProducers(), ProducerState.UNINITIALIZED);
        }
        
        {
            finder.initProducer();
            assertProducerState(finder.getAllProducers(), ProducerState.INITIALIZED);
        }
        final List<Producer> allProducers = finder.getAllProducers();
        {
            finder.startProducer();
            Thread.sleep(Duration.ofSeconds(1L));
            assertProducerState(allProducers, ProducerState.RUNNING);
        }
        {
            finder.interrupt();
            assertThat(finder.getAllProducers(), hasSize(0));
            assertProducerState(allProducers, ProducerState.NOT_RUNNING);
        }
        
        {
            assertThat(finder.getAllConsumers(), hasSize(1));
            finder.shutdownAndAwaitTermination();
            assertThat(finder.getAllConsumers(), hasSize(0));
        }
    }
    // </editor-fold>
    
    @Test
    public void startKeyProducer_allConfiguredKeyProducerTypes_haveInstances() throws IOException {
        CFinder cFinder = new CFinder();

        CKeyProducerJavaRandom javaRandom = new CKeyProducerJavaRandom();
        javaRandom.keyProducerId = "randomId";
        javaRandom.keyProducerJavaRandomInstance = CKeyProducerJavaRandomInstance.RANDOM_CUSTOM_SEED;
        javaRandom.customSeed = 123L;
        cFinder.keyProducerJavaRandom.add(javaRandom);

        CKeyProducerJavaBip39 bip39 = new CKeyProducerJavaBip39();
        bip39.keyProducerId = "bip39Id";
        bip39.mnemonic = CKeyProducerJavaBip39.DEFAULT_MNEMONIC;
        cFinder.keyProducerJavaBip39.add(bip39);

        CKeyProducerJavaIncremental incremental = new CKeyProducerJavaIncremental();
        incremental.keyProducerId = "incrementalId";
        cFinder.keyProducerJavaIncremental.add(incremental);

        CKeyProducerJavaSocket socket = new CKeyProducerJavaSocket();
        socket.keyProducerId = "socketId";
        socket.port = KeyProducerJavaSocketTest.findFreePort();
        cFinder.keyProducerJavaSocket.add(socket);

        CKeyProducerJavaWebSocket webSocket = new CKeyProducerJavaWebSocket();
        webSocket.keyProducerId = "webSocketId";
        webSocket.port = KeyProducerJavaSocketTest.findFreePort();
        cFinder.keyProducerJavaWebSocket.add(webSocket);

        CKeyProducerJavaZmq zmq = new CKeyProducerJavaZmq();
        zmq.keyProducerId = "zmqId";
        zmq.address = KeyProducerJavaZmqTest.findFreeZmqAddress();
        cFinder.keyProducerJavaZmq.add(zmq);

        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();

        assertThat(finder.getKeyProducers().keySet(), hasItems("randomId", "bip39Id", "incrementalId", "socketId", "webSocketId", "zmqId"));

        assertThat(finder.getKeyProducers().get("randomId"), instanceOf(KeyProducerJavaRandom.class));
        assertThat(finder.getKeyProducers().get("bip39Id"), instanceOf(KeyProducerJavaBip39.class));
        assertThat(finder.getKeyProducers().get("incrementalId"), instanceOf(KeyProducerJavaIncremental.class));
        assertThat(finder.getKeyProducers().get("socketId"), instanceOf(KeyProducerJavaSocket.class));
        assertThat(finder.getKeyProducers().get("webSocketId"), instanceOf(KeyProducerJavaWebSocket.class));
        assertThat(finder.getKeyProducers().get("zmqId"), instanceOf(KeyProducerJavaZmq.class));
        
        finder.interrupt();
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
    
    private void configureKeyProducerJavaRandom(@Nullable String keyProducerId, CFinder cFinder) {
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
        bip39.mnemonic = CKeyProducerJavaBip39.DEFAULT_MNEMONIC;
        cFinder.keyProducerJavaBip39.add(bip39);
    }
    
    private void configureKeyProducerJavaSocket(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaSocket socket = new CKeyProducerJavaSocket();
        socket.port = KeyProducerJavaSocketTest.findFreePort();
        socket.timeout = KeyProducerJavaTest.TIMEOUT_FOR_TERMINATE;
        socket.keyProducerId = keyProducerId;
        cFinder.keyProducerJavaSocket.add(socket);
    }
    
    private void configureKeyProducerJavaWebSocket(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaWebSocket socket = new CKeyProducerJavaWebSocket();
        socket.port = KeyProducerJavaSocketTest.findFreePort();
        socket.timeout = KeyProducerJavaTest.TIMEOUT_FOR_TERMINATE;
        socket.keyProducerId = keyProducerId;
        cFinder.keyProducerJavaWebSocket.add(socket);
    }
    
    private void configureKeyProducerJavaZmq(String keyProducerId, CFinder cFinder) {
        CKeyProducerJavaZmq zmq = new CKeyProducerJavaZmq();
        zmq.address = KeyProducerJavaZmqTest.findFreeZmqAddress();
        zmq.timeout = KeyProducerJavaTest.TIMEOUT_FOR_TERMINATE;
        zmq.keyProducerId = keyProducerId;
        cFinder.keyProducerJavaZmq.add(zmq);
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
