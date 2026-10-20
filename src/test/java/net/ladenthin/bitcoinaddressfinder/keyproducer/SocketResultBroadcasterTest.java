// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.Hit;
import nl.altindag.log.LogCaptor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link SocketResultBroadcaster} through real TCP clients.
 *
 * <p>Two properties matter here and nowhere else. First, this transport had no notion of several
 * peers at all — the key producer serves exactly one — so "reaches every listener" is new behaviour
 * and is asserted with two clients at once. Second, the outbound direction needs framing that the
 * inbound one does not: secrets arrive as fixed-width records, results are variable-length JSON, so
 * they are newline-delimited and a reader must be able to rely on that.
 */
class SocketResultBroadcasterTest {

    /** Upper bound for every read; exceeding it is a failure, not a slow machine. */
    private static final int AWAIT_MILLIS = 15_000;

    /** Read timeout for a message that, when it is sent at all, is sent at once. */
    private static final int SHORT_READ_MILLIS = 3_000;

    /** How long each registration is held up in the test that makes the registration race certain. */
    private static final int SLOW_REGISTRATION_MILLIS = 300;

    /** Receive buffer of a client that never reads; small so its buffers fill quickly. */
    private static final int STALLED_RECEIVE_BUFFER_BYTES = 4096;

    /** About 10 MB of batch messages: far more than any loopback socket buffers hold. */
    private static final int CALLS_TO_FILL_THE_BUFFERS = 100_000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // <editor-fold defaultstate="collapsed" desc="onBatchChecked">
    @Test
    void onBatchChecked_twoConnectedClients_bothReceiveTheSameLine() throws Exception {
        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        try {
            start(broadcaster);

            try (Socket first = connect(port);
                    Socket second = connect(port)) {
                final BufferedReader firstReader = reader(first);
                final BufferedReader secondReader = reader(second);
                broadcaster.awaitClientCount(2, AWAIT_MILLIS);

                // act
                broadcaster.onBatchChecked(new BatchResult(BigInteger.valueOf(0x4000L), 4096, List.of()));

                // assert
                for (BufferedReader clientReader : List.of(firstReader, secondReader)) {
                    final String line = clientReader.readLine();
                    assertThat(line, is(notNullValue()));
                    final JsonNode message = objectMapper.readTree(line);
                    assertThat(message.get("type").asText(), is(equalTo("batch")));
                    assertThat(message.get("checkedCount").asInt(), is(equalTo(4096)));
                }
            }
        } finally {
            broadcaster.close();
        }
    }

    /**
     * The last batches of a finite range are reported right before the finder shuts down; they are
     * the completion signal a client waits for. Closing must deliver what is already queued for a
     * connected client instead of discarding it with the socket. The writer is held before its
     * first write until the stop has been requested, so the queue is guaranteed to be non-empty
     * when {@code close()} runs.
     */
    @Test
    void close_batchesStillQueued_aConnectedClientReceivesAllOfThem() throws Exception {
        // arrange
        final int port = freePort();
        final int batches = 10;
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        final CountDownLatch stopRequested = new CountDownLatch(1);
        broadcaster.setBeforeWrite(() -> {
            try {
                stopRequested.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        broadcaster.setAfterStopRequested(stopRequested::countDown);
        final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        try {
            start(broadcaster);
            try (Socket client = connect(port)) {
                final BufferedReader clientReader = reader(client);
                broadcaster.awaitClientCount(1, AWAIT_MILLIS);
                final Future<Integer> received = readerExecutor.submit(() -> {
                    int lines = 0;
                    while (readLineOrNull(clientReader) != null) {
                        lines++;
                    }
                    return lines;
                });
                for (int i = 0; i < batches; i++) {
                    broadcaster.onBatchChecked(new BatchResult(BigInteger.valueOf(i), 1, List.of()));
                }

                // act
                broadcaster.close();

                // assert
                assertThat(received.get(AWAIT_MILLIS, TimeUnit.MILLISECONDS), is(equalTo(batches)));
            }
        } finally {
            broadcaster.close();
            readerExecutor.shutdownNow();
        }
    }

    @Test
    void onBatchChecked_batchWithHit_clientReceivesTheKeyMaterial() throws Exception {
        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        try {
            start(broadcaster);

            try (Socket client = connect(port)) {
                final BufferedReader clientReader = reader(client);
                broadcaster.awaitClientCount(1, AWAIT_MILLIS);
                final Hit hit = new Hit(BigInteger.valueOf(73), "aabb", "1TestAddress", true, false);

                // act
                broadcaster.onBatchChecked(new BatchResult(BigInteger.ONE, 1, List.of(hit)));

                // assert
                final JsonNode message = objectMapper.readTree(clientReader.readLine());
                assertThat(message.get("hits").get(0).get("privateKey").asText(), is(equalTo("49")));
            }
        } finally {
            broadcaster.close();
        }
    }

    @Test
    void onBatchChecked_clientDisconnected_remainingClientStillReceives() throws Exception {
        // A dead peer must not take the channel down with it: the write to it fails, and the healthy
        // client must still be served.

        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        try {
            start(broadcaster);

            final Socket leaving = connect(port);
            try (Socket staying = connect(port)) {
                final BufferedReader stayingReader = reader(staying);
                broadcaster.awaitClientCount(2, AWAIT_MILLIS);
                leaving.close();

                // act: the first send may still succeed into the dead socket's buffer
                broadcaster.onBatchChecked(new BatchResult(BigInteger.ONE, 1, List.of()));
                broadcaster.onBatchChecked(new BatchResult(BigInteger.TWO, 2, List.of()));

                // assert
                assertThat(stayingReader.readLine(), is(notNullValue()));
                assertThat(stayingReader.readLine(), is(notNullValue()));
            }
        } finally {
            broadcaster.close();
        }
    }

    @Test
    void onBatchChecked_clientConnectedButNeverReading_doesNotBlockTheCaller() throws Exception {
        // onBatchChecked runs on the consumer's worker threads. A client that connects and never
        // reads fills its TCP buffers; a blocking write to it then stalls every consumer thread,
        // i.e. the scan, for as long as the client stays connected.

        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        final ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            start(broadcaster);
            try (Socket stalled = new Socket()) {
                stalled.setReceiveBufferSize(STALLED_RECEIVE_BUFFER_BYTES);
                stalled.connect(new InetSocketAddress("127.0.0.1", port));
                broadcaster.awaitClientCount(1, AWAIT_MILLIS);

                // act
                final Future<?> calls = caller.submit(() -> {
                    for (int i = 0; i < CALLS_TO_FILL_THE_BUFFERS; i++) {
                        broadcaster.onBatchChecked(new BatchResult(BigInteger.valueOf(i), 1, List.of()));
                    }
                });

                // assert
                calls.get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
            }
        } finally {
            caller.shutdownNow();
            broadcaster.close();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="announceConfiguration">
    @Test
    void announceConfiguration_clientConnectsAfterwards_receivesItOnConnect() throws Exception {
        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        try {
            broadcaster.announceConfiguration(18, true);
            start(broadcaster);

            // act
            try (Socket client = connect(port)) {
                final JsonNode message = objectMapper.readTree(reader(client).readLine());

                // assert
                assertThat(message.get("type").asText(), is(equalTo("config")));
                assertThat(message.get("batchSizeInBits").asInt(), is(equalTo(18)));
            }
        } finally {
            broadcaster.close();
        }
    }

    @Test
    void announceConfiguration_clientConnectedBefore_isCaughtUp() throws Exception {
        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        try {
            start(broadcaster);

            try (Socket client = connect(port)) {
                final BufferedReader clientReader = reader(client);
                broadcaster.awaitClientCount(1, AWAIT_MILLIS);

                // act: the configuration only becomes known now
                broadcaster.announceConfiguration(18, true);

                // assert
                final JsonNode message = objectMapper.readTree(clientReader.readLine());
                assertThat(message.get("type").asText(), is(equalTo("config")));
            }
        } finally {
            broadcaster.close();
        }
    }

    /**
     * {@link SocketResultBroadcaster#awaitClientCount(int, int)} counts every accepted connection, so
     * a test that connects anything besides its own clients -- a port probe, say -- returns from the
     * wait before its last client is registered, and the batch sent next misses that client. That
     * race is what made this class flaky (about one fork in twenty). Here every registration is
     * slowed down, which turns the race into a certainty: the batch must still arrive.
     */
    @Test
    void onBatchChecked_registrationIsSlow_theOnlyClientStillReceivesTheBatch() throws Exception {
        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        broadcaster.setBeforeClientRegistered(() -> {
            try {
                Thread.sleep(SLOW_REGISTRATION_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            start(broadcaster);
            try (Socket client = connect(port)) {
                client.setSoTimeout(SHORT_READ_MILLIS);
                final BufferedReader clientReader = reader(client);
                broadcaster.awaitClientCount(1, AWAIT_MILLIS);

                // act
                broadcaster.onBatchChecked(new BatchResult(BigInteger.ONE, 1, List.of()));

                // assert
                final String line = readLineOrNull(clientReader);
                assertThat(line, is(notNullValue()));
                assertThat(objectMapper.readTree(line).get("type").asText(), is(equalTo("batch")));
            }
        } finally {
            broadcaster.close();
        }
    }

    /**
     * A connection the accept loop took just before {@code close()} must not survive it. Here the
     * whole {@code close()} runs while the accept thread holds an accepted client it has not
     * registered yet, so the client is registered after close swept the client set: it used to stay
     * connected forever (nothing would close its socket), and starting its writer on the shut-down
     * executor threw a {@code RejectedExecutionException} that died unseen in the accept task.
     */
    @Test
    void close_whileAnAcceptedClientIsBeingRegistered_theClientIsDisconnected() throws Exception {
        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        final AtomicReference<@Nullable Throwable> closeFailure = new AtomicReference<>();
        broadcaster.setBeforeClientRegistered(() -> {
            final Thread closer = new Thread(() -> {
                try {
                    broadcaster.close();
                } catch (Throwable t) {
                    closeFailure.set(t);
                }
            });
            closer.start();
            // close() interrupts this accept thread; wait for it to finish all the same
            boolean interrupted = false;
            while (closer.isAlive()) {
                try {
                    closer.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            start(broadcaster);

            // act
            try (Socket client = connect(port)) {
                client.setSoTimeout(SHORT_READ_MILLIS);

                // assert: the server end is closed, so the read ends instead of timing out
                assertThat(readEndOfStream(client), is(true));
            }
            assertThat(closeFailure.get(), is(nullValue()));
        } finally {
            broadcaster.close();
        }
    }

    @Test
    void announceConfiguration_whileAClientIsBeingRegistered_theClientStillReceivesIt() throws Exception {
        // arrange: the announcement runs on another thread exactly while the accept loop has decided
        // the new client's greeting but not yet registered it; the hook lets it run until it either
        // finishes or has to wait for the registration
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        final ExecutorService announcer = Executors.newSingleThreadExecutor();
        try {
            broadcaster.setBeforeClientRegistered(() -> {
                final AtomicReference<@Nullable Thread> announcing = new AtomicReference<>();
                final Future<?> announcement = announcer.submit(() -> {
                    announcing.set(Thread.currentThread());
                    broadcaster.announceConfiguration(18, true);
                });
                final long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
                while (!announcement.isDone() && System.currentTimeMillis() < deadline) {
                    final Thread thread = announcing.get();
                    if (thread != null && thread.getState() == Thread.State.BLOCKED) {
                        return;
                    }
                    Thread.onSpinWait();
                }
            });
            broadcaster.start();

            // act
            try (Socket client = connect(port)) {
                client.setSoTimeout(SHORT_READ_MILLIS);
                final String line = readLineOrNull(reader(client));

                // assert
                assertThat(line, is(notNullValue()));
                assertThat(objectMapper.readTree(line).get("type").asText(), is(equalTo("config")));
            }
        } finally {
            broadcaster.close();
            announcer.shutdownNow();
        }
    }
    /**
     * A batch reported after {@link SocketResultBroadcaster#close()} reaches no client any more. That
     * must be visible in the log, once, instead of the report disappearing without a word.
     */
    @Test
    void onBatchChecked_afterClose_warnsOnceThatTheResultIsNotSent() throws Exception {
        // arrange
        SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(freePort());
        start(broadcaster);
        broadcaster.close();
        try (LogCaptor logCaptor = LogCaptor.forClass(SocketResultBroadcaster.class)) {
            // act
            broadcaster.onBatchChecked(new BatchResult(BigInteger.ONE, 1, List.of()));
            broadcaster.onBatchChecked(new BatchResult(BigInteger.TWO, 1, List.of()));

            // assert
            assertThat(logCaptor.getWarnLogs(), hasSize(1));
            assertThat(logCaptor.getWarnLogs().get(0), containsString("already closed"));
        }
    }

    // </editor-fold>

    /**
     * Starts the broadcaster. {@link SocketResultBroadcaster#start()} binds the server socket before it
     * returns, so the port accepts connections from here on and nothing has to wait for it. Probing the
     * port is not just unnecessary but wrong: the probe is accepted and registered like any client, so
     * {@link SocketResultBroadcaster#awaitClientCount(int, int)} counts it and returns before the test's
     * last client is registered (see
     * {@link #onBatchChecked_registrationIsSlow_theOnlyClientStillReceivesTheBatch()}).
     *
     * @param broadcaster the broadcaster to start
     * @throws Exception if it cannot be started
     */
    private static void start(SocketResultBroadcaster broadcaster) throws Exception {
        broadcaster.start();
    }

    /**
     * Reads until the peer closes the connection.
     *
     * @param socket the connected socket, with a read timeout
     * @return {@code true} if the stream ended, {@code false} if the read timed out
     * @throws Exception if the read fails for another reason
     */
    private static boolean readEndOfStream(Socket socket) throws Exception {
        try {
            while (socket.getInputStream().read() != -1) {
                // skip whatever arrives before the end
            }
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        }
    }

    /**
     * Reads one line, treating a read timeout as "nothing arrived".
     *
     * @param clientReader the reader to read from
     * @return the line, or {@code null} if none arrived in time
     * @throws Exception if the read fails for another reason
     */
    private static @Nullable String readLineOrNull(BufferedReader clientReader) throws Exception {
        try {
            return clientReader.readLine();
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    /**
     * Opens a client connection with a bounded read timeout.
     *
     * @param port the port to connect to
     * @return the connected socket
     * @throws Exception if the connection cannot be established
     */
    private static Socket connect(int port) throws Exception {
        final Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(AWAIT_MILLIS);
        return socket;
    }

    /**
     * Wraps a socket in a line reader, matching the newline framing of the outbound direction.
     *
     * @param socket the connected socket
     * @return a reader over its input
     * @throws Exception if the stream cannot be opened
     */
    private static BufferedReader reader(Socket socket) throws Exception {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Picks a free TCP port, so parallel runs cannot collide on a fixed one.
     *
     * @return a port that was free at the moment of asking
     * @throws Exception if no port could be reserved
     */
    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
