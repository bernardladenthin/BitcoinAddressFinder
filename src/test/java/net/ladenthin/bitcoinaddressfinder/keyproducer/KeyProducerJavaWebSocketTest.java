// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.net.URI;
import java.util.concurrent.*;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import org.bitcoinj.base.Network;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KeyProducerJavaWebSocketTest {

    private KeyUtility keyUtility;
    private BitHelper bitHelper;

    private ExecutorService executorService;

    @BeforeEach
    public void setUp() {
        Network network = new NetworkParameterFactory().getNetwork();
        keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        bitHelper = new BitHelper();
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    public void tearDown() {
        executorService.shutdownNow();
    }

    private CKeyProducerJavaWebSocket createConfig() {
        CKeyProducerJavaWebSocket config = new CKeyProducerJavaWebSocket();
        config.port = KeyProducerJavaSocketTest.findFreePort();
        // Block until a message arrives (or signalShutdown via interrupt() wakes us).
        // The JUnit per-test timeout (60s) is the upper bound when something is
        // genuinely wrong &#x2014; far more reliable than racing against a fixed receiver
        // timeout under loaded CI conditions.
        config.timeout = -1;
        return config;
    }

    @Test
    public void createSecrets_receivesValidSecret() throws Exception {
        CKeyProducerJavaWebSocket config = createConfig();
        KeyProducerJavaWebSocket producer = new KeyProducerJavaWebSocket(config, keyUtility, bitHelper);

        // WebSocket client to send a valid 32-byte secret
        byte[] secret = new KeyProducerTestUtility().createZeroedSecret();
        BigInteger expected = new BigInteger(1, secret);

        CountDownLatch connected = new CountDownLatch(1);

        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:" + config.port)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                connected.countDown();
            }

            @Override
            public void onMessage(String message) {}

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };

        client.connectBlocking();
        connected.await(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TestTimeProvider.TIME_UNIT);

        client.send(secret);

        BigInteger[] secrets = producer.createSecrets(1, true);
        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(expected));

        producer.interrupt();
        client.close();
    }

    @Test
    public void createSecrets_timeoutWithoutMessage_throwsException() throws Exception {
        CKeyProducerJavaWebSocket config = createConfig();
        config.timeout = TestTimeProvider.DEFAULT_TIMEOUT;
        KeyProducerJavaWebSocket producer = new KeyProducerJavaWebSocket(config, keyUtility, bitHelper);

        assertThrows(NoMoreSecretsAvailableException.class, () -> producer.createSecrets(1, true));
    }

    @Test
    public void createSecrets_negativeTimeout_blocksUntilMessageArrives() throws Exception {
        // arrange
        CKeyProducerJavaWebSocket config = createConfig();
        config.timeout = -1; // explicit: block indefinitely
        KeyProducerJavaWebSocket producer = new KeyProducerJavaWebSocket(config, keyUtility, bitHelper);

        byte[] secret = new KeyProducerTestUtility().createZeroedSecret();
        BigInteger expected = new BigInteger(1, secret);

        ConnectionUtils.waitUntilTcpPortOpen("localhost", config.port, TestTimeProvider.DEFAULT_SOCKET_TIMEOUT);

        // act
        // Start createSecrets in a background thread; it must block (no message yet).
        Future<BigInteger[]> future = executorService.submit(() -> producer.createSecrets(1, true));

        // Give the consumer time to actually park in take(); a quick spin would
        // not exercise the blocking path.
        Thread.sleep(TestTimeProvider.DEFAULT_DELAY);
        assertThat("createSecrets must block when timeout < 0 and queue is empty", future.isDone(), is(false));

        // Now publish a message; the consumer should wake and return it.
        CountDownLatch connected = new CountDownLatch(1);
        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:" + config.port)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                connected.countDown();
            }

            @Override
            public void onMessage(String message) {}

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };
        client.connectBlocking();
        waitForConnectionOrFail(client, connected, TestTimeProvider.DEFAULT_SOCKET_TIMEOUT);

        client.send(secret);

        BigInteger[] secrets = future.get(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);

        // assert
        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(expected));

        producer.interrupt();
        client.close();
    }

    @Test
    public void interrupt_stopsReceiverAndCausesNoMoreSecretsAvailableException() throws Exception {
        // arrange
        CKeyProducerJavaWebSocket config = createConfig();
        KeyProducerJavaWebSocket producer = new KeyProducerJavaWebSocket(config, keyUtility, bitHelper);

        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return producer.createSecrets(1, true);
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        Thread.sleep(TestTimeProvider.DEFAULT_DELAY);

        // Sanity check: with timeout=-1 the consumer must still be parked, otherwise
        // the test is exercising a different code path than its name claims.
        assertThat("createSecrets must be blocked before interrupt()", future.isDone(), is(false));

        // act
        producer.interrupt();

        // assert
        BigInteger[] result = future.get(2, TimeUnit.SECONDS);
        assertThat("Receiver thread should have exited due to interrupt", result, is(nullValue()));
    }

    @Test
    public void createSecrets_invalidMessageLength_ignoredByServer() throws Exception {
        // arrange
        CKeyProducerJavaWebSocket config = createConfig();
        KeyProducerJavaWebSocket producer = new KeyProducerJavaWebSocket(config, keyUtility, bitHelper);

        ConnectionUtils.waitUntilTcpPortOpen("localhost", config.port, TestTimeProvider.DEFAULT_SOCKET_TIMEOUT);

        byte[] invalidSecret = new KeyProducerTestUtility().createInvalidSecret();
        byte[] validSecret = new KeyProducerTestUtility().createZeroedSecret();
        BigInteger expected = new BigInteger(1, validSecret);

        CountDownLatch connected = new CountDownLatch(1);
        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:" + config.port)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                connected.countDown();
            }

            @Override
            public void onMessage(String message) {}

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };

        client.connectBlocking();
        waitForConnectionOrFail(client, connected, TestTimeProvider.DEFAULT_SOCKET_TIMEOUT);

        // act
        // Send invalid then valid; only the valid one should reach the queue.
        // If the invalid had not been ignored, createSecrets would either return it
        // (length check would fail with NoMoreSecretsAvailable "Invalid secret length")
        // or block forever waiting for a second message.
        client.send(invalidSecret);
        client.send(validSecret);

        BigInteger[] secrets = producer.createSecrets(1, true);

        // assert
        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(expected));

        producer.interrupt();
        client.close();
    }

    public static void waitForConnectionOrFail(WebSocketClient client, CountDownLatch latch, int timeoutMillis)
            throws InterruptedException {
        boolean opened = latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        if (!opened || !client.isOpen()) {
            throw new IllegalStateException("WebSocket not open after " + timeoutMillis + "ms");
        }
    }
}
