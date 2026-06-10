// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import net.ladenthin.bitcoinaddressfinder.secret.NoMoreSecretsAvailableException;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import nl.altindag.log.LogCaptor;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class KeyProducerJavaZmqTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();
    private ExecutorService executorService;
    private List<KeyProducerJavaZmq> createdProducers;

    @BeforeEach
    public void setup() {
        executorService = Executors.newCachedThreadPool();
        createdProducers = new ArrayList<>();
    }

    @AfterEach
    public void teardown() {
        executorService.shutdownNow();
        // Interrupt every producer created by this test — including those a test
        // deliberately leaves un-interrupted (e.g. the timeout scenario). A leaked
        // ZContext keeps its internal I/O threads and a bound TCP port alive, which
        // can stall the forked JVM's shutdown and trip the Surefire fork timeout on
        // loaded CI runners. Double-interrupting producers a test already interrupted
        // is safe: jeromq guards Socket.close()/ZContext.close() with closed-state
        // flags, and signalShutdown()/join() are no-ops the second time.
        for (KeyProducerJavaZmq producer : createdProducers) {
            producer.interrupt();
        }
    }

    private KeyProducerJavaZmq createKeyProducerJavaZmq(CKeyProducerJavaZmq config) {
        KeyProducerJavaZmq producer = new KeyProducerJavaZmq(config, keyUtility, bitHelper);
        createdProducers.add(producer);
        producer.start();
        return producer;
    }

    public static String findFreeZmqAddress() {
        return "tcp://127.0.0.1:" + KeyProducerJavaSocketTest.findFreePort();
    }

    private CKeyProducerJavaZmq createBindConfig(String address) {
        CKeyProducerJavaZmq config = new CKeyProducerJavaZmq();
        config.address = address;
        config.mode = CKeyProducerJavaZmq.Mode.BIND;
        // Block until a message arrives (or signalShutdown via interrupt() wakes us).
        // There is no JUnit per-test timeout in this project; the only hard upper
        // bound is the Surefire whole-fork timeout (forkedProcessTimeoutInSeconds in
        // pom.xml), which must cover JVM startup + ALL methods of this class +
        // shutdown. The @AfterEach teardown interrupts every producer, so a wedged
        // receive cannot outlive its own test.
        config.timeoutMillis = -1;
        return config;
    }

    private CKeyProducerJavaZmq createConnectConfig(String address) {
        CKeyProducerJavaZmq config = new CKeyProducerJavaZmq();
        config.address = address;
        config.mode = CKeyProducerJavaZmq.Mode.CONNECT;
        config.timeoutMillis = -1;
        return config;
    }

    // <editor-fold defaultstate="collapsed" desc="createSecrets">
    @Test
    public void createSecrets_connectMode_receivesSecret() throws Exception {
        // arrange
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

        // Producer BINDs first (so it's ready to accept incoming connections).
        CKeyProducerJavaZmq config = createBindConfig(address);
        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        // Sender CONNECTs from the main thread. Keeping the sender's ZContext alive for the entire
        // duration of createSecrets() avoids the jeromq default LINGER=0 which would otherwise drop
        // any message still buffered in the PUSH socket's outbound queue when the context is closed.
        try (ZContext context = new ZContext();
                ZMQ.Socket socket = context.createSocket(SocketType.PUSH)) {
            socket.connect(address);
            boolean sent = socket.send(secretBytes);
            assertThat("ZMQ send returned false", sent, is(true));

            // act
            BigInteger[] secrets = producer.createSecrets(1, true);

            // assert
            assertThat(secrets.length, is(1));
            assertThat(secrets[0], is(equalTo(expected)));
        }

        producer.interrupt();
    }

    @Test
    public void createSecrets_timeout_throwsException() throws Exception {
        // arrange
        String address = findFreeZmqAddress();

        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeoutMillis = TestTimeProvider.DEFAULT_TIMEOUT;

        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        // act
        assertThrows(NoMoreSecretsAvailableException.class, () -> producer.createSecrets(1, true));
    }

    @Test
    public void createSecrets_negativeTimeout_blocksUntilMessageArrives() throws Exception {
        // arrange
        String address = findFreeZmqAddress();
        byte[] secretBytes = new KeyProducerTestUtility().createZeroedSecret();
        BigInteger expected = new BigInteger(1, secretBytes);

        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeoutMillis = -1; // explicit: block indefinitely
        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        // act
        // Start createSecrets in a background thread; it must block (no message yet).
        Future<BigInteger[]> future = executorService.submit(() -> producer.createSecrets(1, true));

        // Give the consumer time to actually park in take(); a quick spin would
        // not exercise the blocking path.
        Thread.sleep(TestTimeProvider.DEFAULT_DELAY);
        assertThat("createSecrets must block when timeout < 0 and queue is empty", future.isDone(), is(false));

        // Now publish a message; the consumer should wake and return it.
        try (ZContext context = new ZContext();
                ZMQ.Socket socket = context.createSocket(SocketType.PUSH)) {
            socket.connect(address);
            boolean sent = socket.send(secretBytes);
            assertThat("ZMQ send returned false", sent, is(true));

            BigInteger[] secrets = future.get(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TestTimeProvider.TIME_UNIT);

            // assert
            assertThat(secrets.length, is(1));
            assertThat(secrets[0], is(equalTo(expected)));
        }

        producer.interrupt();
    }

    @Test
    public void createSecrets_multipleKeys_success() throws Exception {
        // arrange
        String address = findFreeZmqAddress();
        final int numberOfSecrets = 3;

        // Producer BINDs first (so it's ready to accept incoming connections).
        CKeyProducerJavaZmq config = createBindConfig(address);
        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        // Sender CONNECTs from the main thread. Keeping the sender's ZContext alive for the entire
        // duration of createSecrets() avoids the jeromq default LINGER=0 which would otherwise drop
        // any message still buffered in the PUSH socket's outbound queue when the context is closed.
        try (ZContext context = new ZContext();
                ZMQ.Socket socket = context.createSocket(SocketType.PUSH)) {
            socket.connect(address);
            for (int i = 0; i < numberOfSecrets; i++) {
                byte[] secretBytes = new KeyProducerTestUtility().createIncrementedSecret((byte) i);
                boolean sent = socket.send(secretBytes);
                assertThat("ZMQ send for secret " + i + " returned false", sent, is(true));
            }

            // act
            BigInteger[] secrets = producer.createSecrets(numberOfSecrets, false);

            // assert
            assertThat(secrets.length, is(numberOfSecrets));
            new KeyProducerTestUtility().assertIncrementedSecrets(secrets);
        }

        producer.interrupt();
    }

    @Test
    public void createSecrets_invalidSecretLength_errorLogged() throws Exception {
        // arrange
        try (ZContext context = new ZContext();
                LogCaptor logCaptor = LogCaptor.forClass(KeyProducerJavaZmq.class)) {
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

            // act
            BigInteger[] secrets = keyProducer.createSecrets(1, true);

            // assert
            assertThat(secrets.length, is(1));

            // Verify logger was called with error message for invalid length
            assertThat(logCaptor.getErrorLogs(), hasItem("Received invalid secret length: 16"));

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

        // Setup ZMQ PULL socket that will wait for messages
        CKeyProducerJavaZmq config = createBindConfig(address);
        config.timeoutMillis = -1; // block indefinitely
        KeyProducerJavaZmq producer = createKeyProducerJavaZmq(config);

        try (LogCaptor logCaptor = LogCaptor.forClass(KeyProducerJavaZmq.class)) {
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

            // Sanity check: with timeout=-1 the consumer must still be parked, otherwise
            // the test is exercising a different code path than its name claims.
            assertThat("createSecrets must be blocked before interrupt()", future.isDone(), is(false));

            // act
            // Now interrupt from another thread (will close socket/context)
            producer.interrupt();

            // assert
            // Assert the future exits cleanly within timeout
            BigInteger[] result = future.get(2, TimeUnit.SECONDS);
            assertThat(result, is(nullValue()));

            // Verify no unexpected ZMQ errors were logged
            assertThat(logCaptor.getErrorLogs(), not(hasItem("ZMQ error")));
        }
    }
    // </editor-fold>

}
