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

import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import org.bitcoinj.base.Network;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.net.URI;
import java.util.concurrent.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;

public class KeyProducerJavaWebSocketTest {

    private static final int TIMEOUT_MS = 2000;

    private KeyUtility keyUtility;
    private BitHelper bitHelper;
    private Logger mockLogger;

    private ExecutorService executorService;

    @Before
    public void setUp() {
        Network network = new NetworkParameterFactory().getNetwork();
        keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        bitHelper = new BitHelper();
        mockLogger = mock(Logger.class);
        executorService = Executors.newCachedThreadPool();
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    private CKeyProducerJavaWebSocket createConfig() {
        CKeyProducerJavaWebSocket config = new CKeyProducerJavaWebSocket();
        config.port = KeyProducerJavaSocketTest.findFreePort();
        config.timeout = TIMEOUT_MS;
        return config;
    }

    @Test
    public void createSecrets_receivesValidSecret() throws Exception {
        CKeyProducerJavaWebSocket config = createConfig();
        KeyProducerJavaWebSocket producer = new KeyProducerJavaWebSocket(config, keyUtility, bitHelper, mockLogger);

        // WebSocket client to send a valid 32-byte secret
        byte[] secret = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) 0x42;
        }
        BigInteger expected = new BigInteger(1, secret);

        CountDownLatch connected = new CountDownLatch(1);

        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:" + config.port)) {
            @Override public void onOpen(ServerHandshake handshakedata) { connected.countDown(); }
            @Override public void onMessage(String message) {}
            @Override public void onClose(int code, String reason, boolean remote) {}
            @Override public void onError(Exception ex) { ex.printStackTrace(); }
        };

        client.connectBlocking();
        connected.await(1, TimeUnit.SECONDS);

        client.send(secret);

        BigInteger[] secrets = producer.createSecrets(1, true);
        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(expected));

        producer.interrupt();
        client.close();
    }

    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_timeoutWithoutMessage_throwsException() throws Exception {
        CKeyProducerJavaWebSocket config = createConfig();
        config.timeout = 500;
        KeyProducerJavaWebSocket producer = new KeyProducerJavaWebSocket(config, keyUtility, bitHelper, mockLogger);

        producer.createSecrets(1, true);
    }

    @Test
    public void interrupt_stopsReceiverAndCausesNoMoreSecretsAvailableException() throws Exception {
        CKeyProducerJavaWebSocket config = createConfig();
        KeyProducerJavaWebSocket producer = new KeyProducerJavaWebSocket(config, keyUtility, bitHelper, mockLogger);

        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return producer.createSecrets(1, true);
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        Thread.sleep(200); // Give time to enter blocking poll
        producer.interrupt();

        BigInteger[] result = future.get(2, TimeUnit.SECONDS);
        assertThat("Receiver thread should have exited due to interrupt", result, is(nullValue()));
    }

    @Test
    public void createSecrets_invalidMessageLength_ignoredByServer() throws Exception {
        CKeyProducerJavaWebSocket config = createConfig();
        KeyProducerJavaWebSocket producer = new KeyProducerJavaWebSocket(config, keyUtility, bitHelper, mockLogger);

        byte[] invalid = new byte[16]; // Invalid size
        CountDownLatch connected = new CountDownLatch(1);

        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:" + config.port)) {
            @Override public void onOpen(ServerHandshake handshakedata) { connected.countDown(); }
            @Override public void onMessage(String message) {}
            @Override public void onClose(int code, String reason, boolean remote) {}
            @Override public void onError(Exception ex) { ex.printStackTrace(); }
        };

        client.connectBlocking(); // blocks at socket level
        waitForConnectionOrFail(client, connected, 2000);

        client.send(invalid);

        try {
            producer.createSecrets(1, true);
        } catch (NoMoreSecretsAvailableException e) {
            assertThat(e.getMessage(), containsString("Timeout while waiting for secret"));
        }

        producer.interrupt();
        client.close();
    }
    
    public static void waitForConnectionOrFail(WebSocketClient client, CountDownLatch latch, int timeoutMillis) throws InterruptedException {
        boolean opened = latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        if (!opened || !client.isOpen()) {
            throw new IllegalStateException("WebSocket not open after " + timeoutMillis + "ms");
        }
    }
}
