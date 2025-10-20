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
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import org.bitcoinj.base.Network;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

import java.math.BigInteger;
import java.util.concurrent.*;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import org.slf4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZMQException;

public class KeyProducerJavaZmqTest {
    
    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();
    private ExecutorService executorService;
    private Logger mockLogger;

    @Before
    public void setup() {
        executorService = Executors.newCachedThreadPool();
        mockLogger = mock(Logger.class);
    }

    @After
    public void teardown() {
        executorService.shutdownNow();
    }

    private KeyProducerJavaZmq createKeyProducerJavaZmq(CKeyProducerJavaZmq config) {
        return new KeyProducerJavaZmq(config, keyUtility, bitHelper, mockLogger);
    }
    
    public static String findFreeZmqAddress() {
        return "tcp://127.0.0.1:" + KeyProducerJavaSocketTest.findFreePort();
    }

    private CKeyProducerJavaZmq createBindConfig(String address) {
        CKeyProducerJavaZmq config = new CKeyProducerJavaZmq();
        config.address = address;
        config.mode = CKeyProducerJavaZmq.Mode.BIND;
        return config;
    }

    private CKeyProducerJavaZmq createConnectConfig(String address) {
        CKeyProducerJavaZmq config = new CKeyProducerJavaZmq();
        config.address = address;
        config.mode = CKeyProducerJavaZmq.Mode.CONNECT;
        return config;
    }
    
    @Test
    public void createSecrets_connectMode_receivesSecret() throws Exception {
        try (ZContext context = new ZContext()) {
            String address = findFreeZmqAddress();

            // Server socket (push) binds
            ZMQ.Socket sender = context.createSocket(SocketType.PUSH);
            sender.bind(address);

            // Client config connects to that address
            CKeyProducerJavaZmq config = createConnectConfig(address);
            KeyProducerJavaZmq keyProducer = createKeyProducerJavaZmq(config);

            // Create dummy key
            byte[] secretBytes = new KeyProducerTestUtility().createZeroedSecret();
            for (int i = 0; i < secretBytes.length; i++) {
                secretBytes[i] = (byte) (i + 1);
            }
            BigInteger expected = new BigInteger(1, secretBytes);

            // Send secret
            sender.send(secretBytes, 0);

            // Receive
            BigInteger[] secrets = keyProducer.createSecrets(1, true);

            assertThat(secrets.length, is(1));
            assertThat(secrets[0], equalTo(expected));

            keyProducer.interrupt();
            sender.close();
        }
    }

    @Test
    public void createSecrets_success_receivesOneKey() throws Exception {
        String address = findFreeZmqAddress();

        byte[] secretBytes = new KeyProducerTestUtility().createZeroedSecret();
        BigInteger expected = new BigInteger(1, secretBytes);

        // Create the BIND receiver first (so it’s ready to accept)
        CKeyProducerJavaZmq config = createBindConfig(address);
        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        // Now spawn the sender thread
        Future<Void> senderFuture = executorService.submit(() -> {
            try (ZContext context = new ZContext(); ZMQ.Socket socket = context.createSocket(ZMQ.PUSH)) {
                Thread.sleep(TestTimeProvider.DEFAULT_SETTLE_DELAY); // let the receiver settle
                socket.connect(address);
                Thread.sleep(TestTimeProvider.DEFAULT_ESTABLISH_DELAY); // let connection establish
                socket.send(secretBytes);
            }
            return null;
        });

        BigInteger[] secrets = producer.createSecrets(1, true);
        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(expected));

        producer.interrupt();
        senderFuture.get(TestTimeProvider.DEFAULT_SEND_WAIT, TestTimeProvider.TIME_UNIT);
    }

    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_timeout_throwsException() throws Exception {
        String address = findFreeZmqAddress();

        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeout = TestTimeProvider.DEFAULT_TIMEOUT;

        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);
        producer.createSecrets(1, true);
    }
    
    @Test
    public void createSecrets_multipleKeys_success() throws Exception {
        String address = findFreeZmqAddress();
        final int numberOfSecrets = 3;

        // Bind receiver first (small but important ordering)
        CKeyProducerJavaZmq config = createBindConfig(address);
        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        // Latch to ensure all sends actually happened
        final CountDownLatch sentLatch = new CountDownLatch(numberOfSecrets);

        Future<Void> senderFuture = executorService.submit(() -> {
            try (ZContext context = new ZContext(); ZMQ.Socket socket = context.createSocket(ZMQ.PUSH)) {
                socket.connect(address);
                for (int i = 0; i < numberOfSecrets; i++) {
                    byte[] secretBytes = new KeyProducerTestUtility().createIncrementedSecret((byte) i);
                    socket.send(secretBytes);
                    sentLatch.countDown();
                    Thread.sleep(TestTimeProvider.DEFAULT_SEND_DELAY);
                }
            }
            return null;
        });

        BigInteger[] secrets = producer.createSecrets(numberOfSecrets, false);
        assertThat(secrets.length, is(numberOfSecrets));
        new KeyProducerTestUtility().assertIncrementedSecrets(secrets);

        // Wait until the sender really pushed all messages
        boolean allSent = sentLatch.await(
            (long) numberOfSecrets * TestTimeProvider.DEFAULT_SEND_DELAY + TestTimeProvider.DEFAULT_DELAY, TimeUnit.MILLISECONDS
        );
        assertThat("Sender did not send all secrets in time", allSent, is(true));

        producer.interrupt();
        senderFuture.get(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);
    }
    
    @Test
    public void interrupt_duringReceive_stopsCleanly() throws Exception {
        String address = findFreeZmqAddress();

        // Setup ZMQ PULL socket that will wait for messages
        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeout = -1; // block indefinitely
        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        // Start a thread that will block on createSecrets
        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return producer.createSecrets(1, true);
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        // Let it enter the blocking receive
        Thread.sleep(TestTimeProvider.DEFAULT_SEND_WAIT);

        // Now interrupt from another thread (will close socket/context)
        producer.interrupt();

        // Assert the future exits cleanly within timeout
        BigInteger[] result = future.get(2, TimeUnit.SECONDS);
        assertThat("Result should be null due to interruption", result, is(nullValue()));

        // Verify no unexpected ZMQ errors were logged
        verify(mockLogger, never()).error(eq("ZMQ error"), any(ZMQException.class));
    }

    @Test
    public void createSecrets_invalidSecretLength_errorLogged() throws Exception {
        try (ZContext context = new ZContext()) {
            String address = findFreeZmqAddress();

            // Server socket (push) binds
            ZMQ.Socket sender = context.createSocket(SocketType.PUSH);
            sender.bind(address);

            // Client config connects to that address
            CKeyProducerJavaZmq config = createConnectConfig(address);
            KeyProducerJavaZmq keyProducer = createKeyProducerJavaZmq(config);

            // Create invalid secret with wrong length (e.g., 16 bytes instead of 32)
            byte[] invalidSecretBytes = new byte[16];
            for (int i = 0; i < invalidSecretBytes.length; i++) {
                invalidSecretBytes[i] = (byte) (i + 1);
            }

            // Send invalid secret
            sender.send(invalidSecretBytes, 0);

            // Wait a bit to ensure the receiver thread processes the message
            Thread.sleep(TestTimeProvider.DEFAULT_SEND_WAIT);

            // Now send a valid secret so createSecrets can return
            byte[] validSecretBytes = new KeyProducerTestUtility().createZeroedSecret();
            sender.send(validSecretBytes, 0);

            // Receive - should get only the valid secret
            BigInteger[] secrets = keyProducer.createSecrets(1, true);
            assertThat(secrets.length, is(1));

            // Verify logger was called with error message for invalid length
            verify(mockLogger).error("Received invalid secret length: 16");

            keyProducer.interrupt();
            sender.close();
        }
    }

}
