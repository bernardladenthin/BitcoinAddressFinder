// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.io.IOException;

import net.ladenthin.bitcoinaddressfinder.*;

import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaIncremental;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandomInstance;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import static org.junit.Assert.fail;

import org.bitcoinj.base.Network;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.mock;
import org.slf4j.Logger;

@RunWith(DataProviderRunner.class)
public class KeyProducerJavaTest {
    
    /**
     * A timeout is required to ensure the producer can terminate.
     * Without it, the producer may block indefinitely while waiting for keys.
     */
    public final static int TIMEOUT_FOR_TERMINATE = 3_000;
    
    private KeyUtility keyUtility;
    private BitHelper bitHelper;
    private Logger mockLogger;

    private final Network network = new NetworkParameterFactory().getNetwork();
    
    @Before
    public void setUp() {
        keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        bitHelper = new BitHelper();
        mockLogger = mock(Logger.class);
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_JAVA_KEY_PRODUCER_AND_BIT_SIZE, location = CommonDataProvider.class)
    public void createSecrets_throwsException_whenWorkSizeExceedsMax(CommonDataProvider.KeyProducerTypesLocal keyProducerType, int bits) throws IOException, InterruptedException {
        // arrange
        final int maxWorkSize = 1 << bits; // 2^bits
        KeyProducerJava keyProducer = createKeyProducer(keyProducerType, maxWorkSize);
        
        // act, assert
        assertWorkSizeTooLargeThrows(keyProducer, maxWorkSize + 1);
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_KEY_PRODUCER_TYPES, location = CommonDataProvider.class)
    public void createSecrets_throwsException_whenWorkSizeTooLess(CommonDataProvider.KeyProducerTypesLocal keyProducerType) throws IOException, InterruptedException {
        // arrange
        KeyProducerJava keyProducer = createKeyProducer(keyProducerType, 1);
        
        // act
        
        // act, assert
        assertWorkSizeTooLessThrows(keyProducer);
    }

    public KeyProducerJava createKeyProducer(CommonDataProvider.KeyProducerTypesLocal keyProducerType, final int maxWorkSize) throws IllegalArgumentException {
        final String keyProducerId = "id";
        final KeyProducerJava keyProducer;
        switch (keyProducerType) {
            case KeyProducerJavaRandom:
                CKeyProducerJavaRandom configureKeyProducerJavaRandom = configureKeyProducerJavaRandom(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaRandom(configureKeyProducerJavaRandom, keyUtility, bitHelper, mockLogger);
                break;
            case KeyProducerJavaIncremental:
                CKeyProducerJavaIncremental configureKeyProducerJavaIncremental = configureKeyProducerJavaIncremental(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaIncremental(configureKeyProducerJavaIncremental, keyUtility, bitHelper, mockLogger);
                break;
            case KeyProducerJavaBip39:
                CKeyProducerJavaBip39 configureKeyProducerJavaBip39 = configureKeyProducerJavaBip39(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaBip39(configureKeyProducerJavaBip39, keyUtility, bitHelper, mockLogger);
                break;
            case KeyProducerJavaSocket:
                CKeyProducerJavaSocket configureKeyProducerJavaSocket = configureKeyProducerJavaSocket(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaSocket(configureKeyProducerJavaSocket, keyUtility, bitHelper, mockLogger);
                break;
            case KeyProducerJavaWebSocket:
                CKeyProducerJavaWebSocket configureKeyProducerJavaWebSocket = configureKeyProducerJavaWebSocket(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaWebSocket(configureKeyProducerJavaWebSocket, keyUtility, bitHelper, mockLogger);
                break;
            case KeyProducerJavaZmq:
                CKeyProducerJavaZmq configureKeyProducerJavaZmq = configureKeyProducerJavaZmq(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaZmq(configureKeyProducerJavaZmq, keyUtility, bitHelper, mockLogger);
                break;
            default:
                throw new IllegalArgumentException("Unknown KeyProducerType: " + keyProducerType);
        }
        return keyProducer;
    }
    
    public static <T extends CKeyProducerJava> void assertWorkSizeTooLargeThrows(
        KeyProducerJava<T> producer, int requestedSize
    ) {
        try {
            producer.createSecrets(requestedSize, false);
            fail("Expected NoMoreSecretsAvailableException for oversized work size");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
    
    public static <T extends CKeyProducerJava> void assertWorkSizeTooLessThrows(
        KeyProducerJava<T> producer
    ) {
        try {
            producer.createSecrets(-1, false);
            fail("Expected NoMoreSecretsAvailableException for oversized work size");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
    
    private CKeyProducerJavaRandom configureKeyProducerJavaRandom(String keyProducerId, int maxWorkSize) {
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = keyProducerId;
        cKeyProducerJavaRandom.keyProducerJavaRandomInstance = CKeyProducerJavaRandomInstance.RANDOM_CUSTOM_SEED;
        cKeyProducerJavaRandom.customSeed = 0L;
        cKeyProducerJavaRandom.maxWorkSize = maxWorkSize;
        return cKeyProducerJavaRandom;
    }
    
    private CKeyProducerJavaIncremental configureKeyProducerJavaIncremental(String keyProducerId, int maxWorkSize) {
        CKeyProducerJavaIncremental incremental = new CKeyProducerJavaIncremental();
        incremental.keyProducerId = keyProducerId;
        incremental.maxWorkSize = maxWorkSize;
        return incremental;
    }
    
    private CKeyProducerJavaBip39 configureKeyProducerJavaBip39(String keyProducerId, int maxWorkSize) {
        CKeyProducerJavaBip39 bip39 = new CKeyProducerJavaBip39();
        bip39.keyProducerId = keyProducerId;
        bip39.mnemonic = CKeyProducerJavaBip39.DEFAULT_MNEMONIC;
        bip39.maxWorkSize = maxWorkSize;
        return bip39;
    }
    
    private CKeyProducerJavaSocket configureKeyProducerJavaSocket(String keyProducerId, int maxWorkSize) {
        CKeyProducerJavaSocket socket = new CKeyProducerJavaSocket();
        socket.port = KeyProducerJavaSocketTest.findFreePort();
        socket.timeout = TIMEOUT_FOR_TERMINATE;
        socket.keyProducerId = keyProducerId;
        socket.maxWorkSize = maxWorkSize;
        return socket;
    }
    
    private CKeyProducerJavaWebSocket configureKeyProducerJavaWebSocket(String keyProducerId, int maxWorkSize) {
        CKeyProducerJavaWebSocket webSocket = new CKeyProducerJavaWebSocket();
        webSocket.port = KeyProducerJavaSocketTest.findFreePort();
        webSocket.timeout = TIMEOUT_FOR_TERMINATE;
        webSocket.keyProducerId = keyProducerId;
        webSocket.maxWorkSize = maxWorkSize;
        return webSocket;
    }
    
    private CKeyProducerJavaZmq configureKeyProducerJavaZmq(String keyProducerId, int maxWorkSize) {
        CKeyProducerJavaZmq zmq = new CKeyProducerJavaZmq();
        zmq.address = KeyProducerJavaZmqTest.findFreeZmqAddress();
        zmq.timeout = TIMEOUT_FOR_TERMINATE;
        zmq.keyProducerId = keyProducerId;
        zmq.maxWorkSize = maxWorkSize;
        return zmq;
    }
}
