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

import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import org.bitcoinj.base.Network;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    // <editor-fold defaultstate="collapsed" desc="createSecrets">
    @Test
    public void createSecrets_connectMode_receivesSecret() throws Exception {
        // arrange
        try (ZContext context = new ZContext()) {
            String address = findFreeZmqAddress();

            ZMQ.Socket sender = context.createSocket(SocketType.PUSH);
            sender.bind(address);

            CKeyProducerJavaZmq config = createConnectConfig(address);
            KeyProducerJavaZmq keyProducer = createKeyProducerJavaZmq(config);

            byte[] secretBytes = new KeyProducerTestUtility().createZeroedSecret();
            for (int i = 0; i < secretBytes.length; i++) {
                secretBytes[i] = (byte) (i + 1);
            }
            BigInteger expected = new BigInteger(1, secretBytes);

            sender.send(secretBytes, 0);

            // act
            BigInteger[] secrets = keyProducer.createSecrets(1, true);

            // assert
            assertThat(secrets.length, is(1));
            assertThat(secrets[0], is(equalTo(expected)));

            keyProducer.interrupt();
            sender.close();
        }
    }

    @Test
    public void createSecrets_success_receivesOneKey() throws Exception {
        // arrange
        String address = findFreeZmqAddress();

        byte[] secretBytes = new KeyProducerTestUtility().createZeroedSecret();
        BigInteger expected = new BigInteger(1, secretBytes);

        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeout = TestTimeProvider.DEFAULT_SOCKET_TIMEOUT;
        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        final CountDownLatch senderReady = new CountDownLatch(1);

        Future<Void> senderFuture = executorService.submit(() -> {
            try (ZContext context = new ZContext(); ZMQ.Socket socket = context.createSocket(ZMQ.PUSH)) {
                socket.connect(address);
                Thread.sleep(TestTimeProvider.DEFAULT_ESTABLISH_DELAY);
                senderReady.countDown();
                socket.send(secretBytes);
            }
            return null;
        });

        senderReady.await(TestTimeProvider.LONG_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);

        // act
        BigInteger[] secrets = producer.createSecrets(1, true);

        // assert
        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(equalTo(expected)));

        producer.interrupt();
        senderFuture.get(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_timeout_throwsException() throws Exception {
        // arrange
        String address = findFreeZmqAddress();

        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeout = TestTimeProvider.DEFAULT_TIMEOUT;

        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        // act
        producer.createSecrets(1, true);
    }

    @Test
    public void createSecrets_multipleKeys_success() throws Exception {
        // arrange
        String address = findFreeZmqAddress();
        final int numberOfSecrets = 3;

        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeout = TestTimeProvider.DEFAULT_SOCKET_TIMEOUT;
        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        final CountDownLatch senderReady = new CountDownLatch(1);
        final CountDownLatch sentLatch = new CountDownLatch(numberOfSecrets);

        Future<Void> senderFuture = executorService.submit(() -> {
            try (ZContext context = new ZContext(); ZMQ.Socket socket = context.createSocket(ZMQ.PUSH)) {
                socket.connect(address);
                Thread.sleep(TestTimeProvider.DEFAULT_ESTABLISH_DELAY);
                senderReady.countDown();

                for (int i = 0; i < numberOfSecrets; i++) {
                    byte[] secretBytes = new KeyProducerTestUtility().createIncrementedSecret((byte) i);
                    socket.send(secretBytes);
                    sentLatch.countDown();
                    Thread.sleep(TestTimeProvider.DEFAULT_SEND_DELAY);
                }
            }
            return null;
        });

        senderReady.await(TestTimeProvider.LONG_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);

        // act
        BigInteger[] secrets = producer.createSecrets(numberOfSecrets, false);

        // assert
        assertThat(secrets.length, is(numberOfSecrets));
        new KeyProducerTestUtility().assertIncrementedSecrets(secrets);

        boolean allSent = sentLatch.await(
            (long) numberOfSecrets * TestTimeProvider.DEFAULT_SEND_DELAY + TestTimeProvider.DEFAULT_DELAY, TimeUnit.MILLISECONDS
        );
        assertThat("Sender did not send all secrets in time", allSent, is(true));

        producer.interrupt();
        senderFuture.get(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    @Test
    public void createSecrets_invalidSecretLength_errorLogged() throws Exception {
        // arrange
        try (ZContext context = new ZContext()) {
            String address = findFreeZmqAddress();

            ZMQ.Socket sender = context.createSocket(SocketType.PUSH);
            sender.bind(address);

            CKeyProducerJavaZmq config = createConnectConfig(address);
            KeyProducerJavaZmq keyProducer = createKeyProducerJavaZmq(config);

            byte[] invalidSecretBytes = new byte[16];
            for (int i = 0; i < invalidSecretBytes.length; i++) {
                invalidSecretBytes[i] = (byte) (i + 1);
            }

            sender.send(invalidSecretBytes, 0);

            Thread.sleep(TestTimeProvider.DEFAULT_SEND_WAIT);

            byte[] validSecretBytes = new KeyProducerTestUtility().createZeroedSecret();
            sender.send(validSecretBytes, 0);

            // act
            BigInteger[] secrets = keyProducer.createSecrets(1, true);

            // assert
            assertThat(secrets.length, is(1));
            verify(mockLogger).error("Received invalid secret length: 16");

            keyProducer.interrupt();
            sender.close();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="interrupt">
    @Test
    public void interrupt_duringReceive_stopsCleanly() throws Exception {
        // arrange
        String address = findFreeZmqAddress();

        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeout = -1;
        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return producer.createSecrets(1, true);
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        Thread.sleep(TestTimeProvider.DEFAULT_SEND_WAIT);

        // act
        producer.interrupt();

        // assert
        BigInteger[] result = future.get(2, TimeUnit.SECONDS);
        assertThat(result, is(nullValue()));
        verify(mockLogger, never()).error(eq("ZMQ error"), any(ZMQException.class));
    }
    // </editor-fold>

}
