// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.*;
import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaIncremental;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandomAlgorithm;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import net.ladenthin.bitcoinaddressfinder.core.Startable;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class KeyProducerJavaTest {

    /**
     * A timeout is required to ensure the producer can terminate.
     * Without it, the producer may block indefinitely while waiting for keys.
     */
    public static final int TIMEOUT_FOR_TERMINATE = 3_000;

    private KeyUtility keyUtility;
    private BitHelper bitHelper;

    private final Network network = new NetworkParameterFactory().getNetwork();

    @BeforeEach
    public void setUp() {
        keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        bitHelper = new BitHelper();
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_JAVA_KEY_PRODUCER_AND_BIT_SIZE)
    public void createSecrets_throwsException_whenWorkSizeExceedsMax(
            CommonDataProvider.KeyProducerTypesLocal keyProducerType, int bits)
            throws IOException, InterruptedException {
        // arrange
        final int maxWorkSize = 1 << bits; // 2^bits
        KeyProducerJava keyProducer = createKeyProducer(keyProducerType, maxWorkSize);

        // act, assert
        assertWorkSizeTooLargeThrows(keyProducer, maxWorkSize + 1);
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_KEY_PRODUCER_TYPES)
    public void createSecrets_throwsException_whenWorkSizeTooLess(
            CommonDataProvider.KeyProducerTypesLocal keyProducerType) throws IOException, InterruptedException {
        // arrange
        KeyProducerJava keyProducer = createKeyProducer(keyProducerType, 1);

        // act

        // act, assert
        assertWorkSizeTooLessThrows(keyProducer);
    }

    public KeyProducerJava createKeyProducer(
            CommonDataProvider.KeyProducerTypesLocal keyProducerType, final int maxWorkSize)
            throws IllegalArgumentException {
        final String keyProducerId = "id";
        final KeyProducerJava keyProducer;
        switch (keyProducerType) {
            case KeyProducerJavaRandom:
                CKeyProducerJavaRandom configureKeyProducerJavaRandom =
                        configureKeyProducerJavaRandom(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaRandom(configureKeyProducerJavaRandom, keyUtility, bitHelper);
                break;
            case KeyProducerJavaIncremental:
                CKeyProducerJavaIncremental configureKeyProducerJavaIncremental =
                        configureKeyProducerJavaIncremental(keyProducerId, maxWorkSize);
                keyProducer =
                        new KeyProducerJavaIncremental(configureKeyProducerJavaIncremental, keyUtility, bitHelper);
                break;
            case KeyProducerJavaBip39:
                CKeyProducerJavaBip39 configureKeyProducerJavaBip39 =
                        configureKeyProducerJavaBip39(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaBip39(configureKeyProducerJavaBip39, keyUtility, bitHelper);
                break;
            case KeyProducerJavaSocket:
                CKeyProducerJavaSocket configureKeyProducerJavaSocket =
                        configureKeyProducerJavaSocket(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaSocket(configureKeyProducerJavaSocket, keyUtility, bitHelper);
                break;
            case KeyProducerJavaWebSocket:
                CKeyProducerJavaWebSocket configureKeyProducerJavaWebSocket =
                        configureKeyProducerJavaWebSocket(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaWebSocket(configureKeyProducerJavaWebSocket, keyUtility, bitHelper);
                break;
            case KeyProducerJavaZmq:
                CKeyProducerJavaZmq configureKeyProducerJavaZmq =
                        configureKeyProducerJavaZmq(keyProducerId, maxWorkSize);
                keyProducer = new KeyProducerJavaZmq(configureKeyProducerJavaZmq, keyUtility, bitHelper);
                break;
            default:
                throw new IllegalArgumentException("Unknown KeyProducerType: " + keyProducerType);
        }
        // Mirrors the Finder.processKeyProducers dispatch: producers that implement
        // Startable (Socket, Zmq) have their background reader started here so the
        // test exercises the same lifecycle the production code uses.
        if (keyProducer instanceof Startable startable) {
            startable.start();
        }
        return keyProducer;
    }

    public static <T extends CKeyProducerJava> void assertWorkSizeTooLargeThrows(
            KeyProducerJava<T> producer, int requestedSize) {
        try {
            producer.createSecrets(requestedSize, false);
            fail("Expected NoMoreSecretsAvailableException for oversized work size");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public static <T extends CKeyProducerJava> void assertWorkSizeTooLessThrows(KeyProducerJava<T> producer) {
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
        cKeyProducerJavaRandom.randomAlgorithm = CKeyProducerJavaRandomAlgorithm.RANDOM_CUSTOM_SEED;
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
        socket.timeoutMillis = TIMEOUT_FOR_TERMINATE;
        socket.keyProducerId = keyProducerId;
        socket.maxWorkSize = maxWorkSize;
        return socket;
    }

    private CKeyProducerJavaWebSocket configureKeyProducerJavaWebSocket(String keyProducerId, int maxWorkSize) {
        CKeyProducerJavaWebSocket webSocket = new CKeyProducerJavaWebSocket();
        webSocket.port = KeyProducerJavaSocketTest.findFreePort();
        webSocket.timeoutMillis = TIMEOUT_FOR_TERMINATE;
        webSocket.keyProducerId = keyProducerId;
        webSocket.maxWorkSize = maxWorkSize;
        return webSocket;
    }

    private CKeyProducerJavaZmq configureKeyProducerJavaZmq(String keyProducerId, int maxWorkSize) {
        CKeyProducerJavaZmq zmq = new CKeyProducerJavaZmq();
        zmq.address = KeyProducerJavaZmqTest.findFreeZmqAddress();
        zmq.timeoutMillis = TIMEOUT_FOR_TERMINATE;
        zmq.keyProducerId = keyProducerId;
        zmq.maxWorkSize = maxWorkSize;
        return zmq;
    }
}
