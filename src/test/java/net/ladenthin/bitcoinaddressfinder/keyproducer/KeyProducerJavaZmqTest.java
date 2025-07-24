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

import java.math.BigInteger;
import java.util.concurrent.*;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.zeromq.SocketType;

public class KeyProducerJavaZmqTest {

    private static final int WAIT_TIME = 1000;
    
    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();
    private ExecutorService executorService;

    @Before
    public void setup() {
        executorService = Executors.newCachedThreadPool();
    }

    @After
    public void teardown() {
        executorService.shutdownNow();
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
            KeyProducerJavaZmq keyProducer = new KeyProducerJavaZmq(config, keyUtility, bitHelper);

            // Create dummy key
            byte[] secretBytes = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
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

            keyProducer.close();
            sender.close();
        }
    }

    @Test
    public void createSecrets_success_receivesOneKey() throws Exception {
        String address = findFreeZmqAddress();
        byte[] secretBytes = new byte[32];
        for (int i = 0; i < secretBytes.length; i++) {
            secretBytes[i] = (byte) i;
        }
        BigInteger expected = new BigInteger(1, secretBytes);

        Future<Void> senderFuture = executorService.submit(() -> {
            try (ZContext context = new ZContext(); ZMQ.Socket socket = context.createSocket(ZMQ.PUSH)) {
                socket.connect(address);
                Thread.sleep(WAIT_TIME); // wait for receiver
                socket.send(secretBytes);
            }
            return null;
        });

        CKeyProducerJavaZmq config = createBindConfig(address);
        KeyProducerJavaZmq producer = new KeyProducerJavaZmq(config, keyUtility, bitHelper);

        BigInteger[] secrets = producer.createSecrets(1, true);
        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(expected));

        producer.close();
        senderFuture.get(1, TimeUnit.SECONDS);
    }

    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_timeout_throwsException() throws Exception {
        String address = findFreeZmqAddress();

        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeout = 500;

        KeyProducerJavaZmq producer = new KeyProducerJavaZmq(config, keyUtility, bitHelper);
        producer.createSecrets(1, true);
    }

    @Test
    public void createSecrets_multipleKeys_success() throws Exception {
        String address = findFreeZmqAddress();

        Future<Void> senderFuture = executorService.submit(() -> {
            try (ZContext context = new ZContext(); ZMQ.Socket socket = context.createSocket(ZMQ.PUSH)) {
                socket.connect(address);
                for (int i = 0; i < 3; i++) {
                    byte[] secretBytes = new byte[32];
                    for (int j = 0; j < secretBytes.length; j++) {
                        secretBytes[j] = (byte) (i + 1);
                    }
                    socket.send(secretBytes);
                    Thread.sleep(50);
                }
            }
            return null;
        });

        CKeyProducerJavaZmq config = createBindConfig(address);
        KeyProducerJavaZmq producer = new KeyProducerJavaZmq(config, keyUtility, bitHelper);

        BigInteger[] secrets = producer.createSecrets(3, false);
        assertThat(secrets.length, is(3));

        for (int i = 0; i < secrets.length; i++) {
            byte expected = (byte) (i + 1);
            assertThat(secrets[i].toByteArray()[secrets[i].toByteArray().length - 1], is(expected));
        }

        producer.close();
        senderFuture.get(1, TimeUnit.SECONDS);
    }
    
    @Test
    public void interrupt_duringReceive_stopsCleanly() throws Exception {
        String address = findFreeZmqAddress();

        // Setup ZMQ PULL socket that will wait for messages
        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeout = -1; // block indefinitely
        KeyProducerJavaZmq producer = new KeyProducerJavaZmq(config, keyUtility, bitHelper);

        // Start a thread that will block on createSecrets
        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return producer.createSecrets(1, true);
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        // Let it enter the blocking receive
        Thread.sleep(WAIT_TIME);

        // Now interrupt from another thread (will close socket/context)
        producer.interrupt();

        // Assert the future exits cleanly within timeout
        BigInteger[] result = future.get(2, TimeUnit.SECONDS);
        assertThat("Result should be null due to interruption", result, is(nullValue()));
    }
}
