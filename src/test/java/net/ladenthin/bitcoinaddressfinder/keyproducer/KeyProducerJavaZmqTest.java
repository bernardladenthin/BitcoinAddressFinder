// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.junit.jupiter.api.Timeout;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Drives {@link KeyProducerJavaZmq} through real ZeroMQ sockets.
 *
 * <p>Two guards keep a stalled connection from running into Surefire's whole-fork timeout (240 s),
 * which reported this class on CI as "There was a timeout in the fork" (Windows, PR #389) and, in the
 * branch review, hung on Linux under load with {@code main} parked in {@code createSecrets}:
 *
 * <ul>
 *   <li>Every test-side socket that connects goes through {@link #connectSender(ZContext, String)},
 *       which caps the ZMTP handshake at {@link #HANDSHAKE_IVL_MILLIS}. jeromq 0.6.0's poller can lose
 *       the registration of a connecting socket's stream engine (about one connection in a hundred, see
 *       {@link JeromqPollerHandOffRaceTest} and {@code ZmqResultBroadcasterTest}); the engine never
 *       sends its greeting, and only the handshake timer, 30 s by default, recovers it.
 *       {@link #connectSender_peerNeverAnswersTheHandshake_reconnectsWithinTheCap()} pins that the cap
 *       reaches the engine. The fix of the race is submitted as zeromq/jeromq#1022 (open as of
 *       2026-10-21); {@code TODO.md} says what to change here once a release carries it.</li>
 *   <li>Every test has a finite {@link Timeout} in its own thread, so a wedge ends as a failure of that
 *       test, with a thread dump ({@code junit-platform.properties}), instead of taking the fork down.
 *       The two tests in which the producer connects get {@link #PRODUCER_CONNECTS_TIMEOUT_SECONDS}: the
 *       producer's socket is production code with jeromq's 30 s handshake timer, which reconnects a
 *       stalled connection by itself.</li>
 *   <li>In those two tests the bound test sender sends only after a completed handshake ({@link
 *       #monitorHandshakes(ZContext, ZMQ.Socket)}): jeromq queues a bound socket's message on a
 *       connection before its handshake, and a stalled connection takes the message with it when it is
 *       dropped. Both reproduced hangs were these tests ({@code main} in {@code createSecrets} after both
 *       sends had returned, the producer never logging the invalid secret).</li>
 * </ul>
 */
@Timeout(value = KeyProducerJavaZmqTest.TEST_TIMEOUT_SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class KeyProducerJavaZmqTest {

    /** Budget of a test whose sockets are all capped; each currently takes about two seconds. */
    static final int TEST_TIMEOUT_SECONDS = 15;

    /** Budget of a test in which the producer connects: above {@link #HANDSHAKE_AWAIT_MILLIS}. */
    static final int PRODUCER_CONNECTS_TIMEOUT_SECONDS = 50;

    /**
     * How long {@link #connectSender_peerNeverAnswersTheHandshake_reconnectsWithinTheCap()} waits for
     * the reconnect: well above {@link #HANDSHAKE_IVL_MILLIS}, well below jeromq's 30 s default.
     */
    private static final int RECONNECT_AWAIT_MILLIS = 10_000;

    /** Maximum time a test-side sender allows the ZMTP handshake; jeromq's default is 30 s. */
    static final int HANDSHAKE_IVL_MILLIS = 1_000;

    /**
     * How long a bound test sender waits for the producer's handshake: one self-healing stall of the
     * producer's connecting socket (jeromq's 30 s handshake timer, then a reconnect) plus margin.
     */
    private static final int HANDSHAKE_AWAIT_MILLIS = 40_000;

    /** Makes every handshake monitor's {@code inproc} address unique within the JVM. */
    private static final AtomicInteger MONITOR_SEQUENCE = new AtomicInteger();

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

    /**
     * Creates a {@code PUSH} socket connected to {@code address}, the sending side of the tests in
     * which the producer binds, with the ZMTP handshake capped at {@link #HANDSHAKE_IVL_MILLIS}.
     *
     * <p>Only a connecting socket can hit jeromq 0.6.0's poller race (see the class comment); a stalled
     * sender then drops the connection and reconnects after about a second instead of after the 30 s
     * default. The option must be set before {@code connect}; later calls do not reach the engine.
     *
     * @param context the context that owns the socket (closing it closes the socket)
     * @param address the producer's address
     * @return the connected socket
     */
    static ZMQ.Socket connectSender(ZContext context, String address) {
        final ZMQ.Socket sender = context.createSocket(SocketType.PUSH);
        sender.setHandshakeIvl(HANDSHAKE_IVL_MILLIS);
        sender.connect(address);
        return sender;
    }

    /**
     * Reports the completed ZMTP handshakes of a bound {@code sender} on a {@code PAIR} socket; call it
     * before the peer connects, since an event sent before this socket is connected is dropped.
     *
     * <p>Needed where the producer connects to a bound test sender. jeromq 0.6.0 creates the bound
     * side's pipe when the TCP connection is accepted, before the handshake, so a {@code send} returns
     * at once and the message waits in that connection's pipe. When the producer's connecting socket hits
     * the poller race, the handshake never completes, both handshake timers end that connection after
     * 30 s, and the messages in its pipe go with it; the producer's reconnect opens a new, empty pipe.
     * Sending only after {@link #awaitHandshake(ZMQ.Socket)} puts the message on a connection that
     * works; a stall then costs up to 30 s instead of the message.
     *
     * @param context the context that owns the monitor socket
     * @param sender the bound socket to monitor
     * @return the socket the handshake events arrive on, with a receive timeout of {@link
     *     #HANDSHAKE_AWAIT_MILLIS}
     */
    private static ZMQ.Socket monitorHandshakes(ZContext context, ZMQ.Socket sender) {
        final String monitorAddress = "inproc://handshake-monitor-" + MONITOR_SEQUENCE.incrementAndGet();
        assertThat(sender.monitor(monitorAddress, ZMQ.EVENT_HANDSHAKE_PROTOCOL), is(true));
        final ZMQ.Socket handshakes = context.createSocket(SocketType.PAIR);
        handshakes.setReceiveTimeOut(HANDSHAKE_AWAIT_MILLIS);
        handshakes.connect(monitorAddress);
        return handshakes;
    }

    /**
     * Waits for a completed ZMTP handshake reported by {@link #monitorHandshakes(ZContext, ZMQ.Socket)}.
     *
     * @param handshakes the monitor socket
     */
    private static void awaitHandshake(ZMQ.Socket handshakes) {
        final ZMQ.Event handshake = ZMQ.Event.recv(handshakes);
        assertThat(
                "no completed ZMTP handshake with the producer within " + HANDSHAKE_AWAIT_MILLIS + " ms",
                handshake,
                is(notNullValue()));
    }

    public static String findFreeZmqAddress() {
        return "tcp://127.0.0.1:" + KeyProducerJavaSocketTest.findFreePort();
    }

    private CKeyProducerJavaZmq createBindConfig(String address) {
        CKeyProducerJavaZmq config = new CKeyProducerJavaZmq();
        config.address = address;
        config.mode = CKeyProducerJavaZmq.Mode.BIND;
        // Block until a message arrives (or signalShutdown via interrupt() wakes us).
        // The class-level @Timeout bounds each test (see the class comment), and the
        // @AfterEach teardown interrupts every producer, so a wedged receive cannot
        // outlive its own test.
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
    @Timeout(value = PRODUCER_CONNECTS_TIMEOUT_SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void createSecrets_connectMode_receivesSecret() throws Exception {
        // arrange
        try (ZContext context = new ZContext()) {
            String address = findFreeZmqAddress();

            // Server socket (push) binds
            ZMQ.Socket sender = context.createSocket(SocketType.PUSH);
            sender.bind(address);
            ZMQ.Socket handshakes = monitorHandshakes(context, sender);

            // Client config connects to that address
            CKeyProducerJavaZmq config = createConnectConfig(address);
            KeyProducerJavaZmq keyProducer = createKeyProducerJavaZmq(config);
            awaitHandshake(handshakes);

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
                ZMQ.Socket socket = connectSender(context, address)) {
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
                ZMQ.Socket socket = connectSender(context, address)) {
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
                ZMQ.Socket socket = connectSender(context, address)) {
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
    @Timeout(value = PRODUCER_CONNECTS_TIMEOUT_SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void createSecrets_invalidSecretLength_errorLogged() throws Exception {
        // arrange
        try (ZContext context = new ZContext();
                LogCaptor logCaptor = LogCaptor.forClass(KeyProducerJavaZmq.class)) {
            String address = findFreeZmqAddress();

            // Server socket (push) binds
            ZMQ.Socket sender = context.createSocket(SocketType.PUSH);
            sender.bind(address);
            ZMQ.Socket handshakes = monitorHandshakes(context, sender);

            // Client config connects to that address
            CKeyProducerJavaZmq config = createConnectConfig(address);
            KeyProducerJavaZmq keyProducer = createKeyProducerJavaZmq(config);
            awaitHandshake(handshakes);

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

    // <editor-fold defaultstate="collapsed" desc="connectSender">
    @Test
    public void connectSender_peerNeverAnswersTheHandshake_reconnectsWithinTheCap() throws Exception {
        // arrange: a TCP peer that accepts and never sends a ZMTP greeting -- the state a sender is in
        // when jeromq's poller race swallows its engine's registration (see the class comment)
        try (ServerSocket silentPeer = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
                ZContext context = new ZContext()) {
            silentPeer.setSoTimeout(RECONNECT_AWAIT_MILLIS);
            final String address = "tcp://127.0.0.1:" + silentPeer.getLocalPort();
            connectSender(context, address);

            // act
            try (Socket first = silentPeer.accept()) {
                // assert: the cap drops the stalled connection and jeromq connects again; without it
                // the handshake timer (30 s) outlasts the wait and accept() times out
                try (Socket second = silentPeer.accept()) {
                    assertThat(second.isConnected(), is(true));
                }
            }
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
