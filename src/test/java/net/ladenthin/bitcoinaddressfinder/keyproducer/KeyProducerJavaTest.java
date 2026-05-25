// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

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
import static org.junit.jupiter.api.Assertions.fail;

import org.bitcoinj.base.Network;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.slf4j.Logger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
    
    @BeforeEach
    public void setUp() {
        keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        bitHelper = new BitHelper();
        mockLogger = mock(Logger.class);
    }
    
    @ParameterizedTest
    @MethodSource("net.ladenthin.bitcoinaddressfinder.CommonDataProvider#keyProducerTypeAndBitSize")
    public void createSecrets_throwsException_whenWorkSizeExceedsMax(CommonDataProvider.KeyProducerTypesLocal keyProducerType, int bits) throws IOException, InterruptedException {
        // arrange
        final int maxWorkSize = 1 << bits; // 2^bits
        KeyProducerJava keyProducer = createKeyProducer(keyProducerType, maxWorkSize);
        
        // act, assert
        assertWorkSizeTooLargeThrows(keyProducer, maxWorkSize + 1);
    }
    
    @ParameterizedTest
    @MethodSource("net.ladenthin.bitcoinaddressfinder.CommonDataProvider#keyProducerTypes")
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
