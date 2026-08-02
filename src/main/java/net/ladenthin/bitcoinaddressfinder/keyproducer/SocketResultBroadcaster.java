// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.FireAndForget;
import net.ladenthin.bitcoinaddressfinder.core.ResultListener;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves the outcome of every checked batch to every connected TCP client.
 *
 * <p>Every batch is reported, hit or not, so a client sweeping a range can tell "checked, nothing
 * there" from "never checked".
 *
 * <p><b>The payload contains private keys</b>, and every connected client receives every result —
 * including hits from ranges somebody else submitted.
 *
 * <h2>Why this is a server of its own</h2>
 * The plain-socket key producer serves exactly one peer: it accepts a single connection and reads a
 * stream of fixed-width secrets from it. "Reach every listener" has no counterpart there, so this
 * class keeps its own accept loop and its own client list rather than extending that one.
 *
 * <h2>Framing differs by direction</h2>
 * Inbound secrets need no framing — every record is exactly
 * {@code PRIVATE_KEY_MAX_NUM_BYTES} long. Results are variable-length JSON, so they are terminated
 * by a newline and a reader can use {@code readLine()}. The formatter guarantees single-line output;
 * an embedded newline would split one message into two.
 *
 * <h2>What it does not promise</h2>
 * Delivery is best-effort: a client that is not connected when a batch completes misses that message
 * and nothing replays it. The log remains the authoritative record of a hit. A client whose write
 * fails is dropped, and one slow client cannot be allowed to stall the scan — writes happen on the
 * consumer's worker thread, so a peer that stops reading will eventually fill its buffer and be
 * dropped on the resulting error rather than blocking forever.
 */
@ToString
public class SocketResultBroadcaster implements ResultListener, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketResultBroadcaster.class);

    /** Terminates every outbound message; see the framing note in the class comment. */
    private static final String MESSAGE_TERMINATOR = "\n";

    /** How long the accept loop waits before checking whether it should stop. */
    private static final int ACCEPT_TIMEOUT_MILLIS = 250;

    /** Poll interval used while waiting for an expected client count. */
    private static final int CLIENT_WAIT_POLL_MILLIS = 20;

    private final int port;
    private final BatchResultJsonFormatter formatter;

    @ToString.Exclude
    private final List<Socket> clients = new CopyOnWriteArrayList<>();

    @ToString.Exclude
    private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor();

    private volatile boolean shouldStop = false;

    @ToString.Exclude
    private volatile @Nullable ServerSocket serverSocket;

    /** The greeting, once the producer configuration it describes is known. */
    @ToString.Exclude
    private volatile @Nullable String configurationMessage;

    /**
     * Creates a broadcaster serving on the given port. The server is not started here.
     *
     * @param port the TCP port to serve on
     */
    public SocketResultBroadcaster(int port) {
        this(port, new BatchResultJsonFormatter());
    }

    /**
     * Creates a broadcaster on an explicit formatter.
     *
     * @param port      the TCP port to serve on
     * @param formatter renders the wire messages
     */
    public SocketResultBroadcaster(int port, BatchResultJsonFormatter formatter) {
        this.port = port;
        this.formatter = formatter;
    }

    /**
     * Starts accepting clients on a background thread.
     *
     * @throws IOException if the port cannot be bound
     */
    public void start() throws IOException {
        final ServerSocket localServerSocket = new ServerSocket(port);
        localServerSocket.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);
        serverSocket = localServerSocket;
        LOGGER.info("Serving results on port {}", port);

        @FireAndForget("lifecycle via close()")
        @SuppressWarnings("FutureReturnValueIgnored")
        Object unused = acceptExecutor.submit(() -> {
            while (!shouldStop) {
                try {
                    final Socket client = localServerSocket.accept();
                    clients.add(client);
                    LOGGER.info("Result listener connected: {}", String.valueOf(client.getRemoteSocketAddress()));
                    // A client that arrives after the grid is known must not have to wait for the
                    // next republication to learn it.
                    final String greeting = configurationMessage;
                    if (greeting != null) {
                        writeTo(client, greeting);
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // expected: the timeout exists so the loop can observe shouldStop
                } catch (IOException e) {
                    if (!shouldStop) {
                        LOGGER.warn("Accepting a result listener failed; continuing.", e);
                    }
                }
            }
        });
    }

    /**
     * Records the grid configuration and sends it to everyone already connected.
     *
     * @param batchSizeInBits             the grid size each base is expanded to
     * @param batchUsePrivateKeyIncrement whether one base yields a whole grid
     */
    public void announceConfiguration(int batchSizeInBits, boolean batchUsePrivateKeyIncrement) {
        final String message;
        try {
            message = formatter.formatConfiguration(batchSizeInBits, batchUsePrivateKeyIncrement);
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not serialise the grid configuration; clients will not receive it.", e);
            return;
        }
        configurationMessage = message;
        broadcast(message);
    }

    @Override
    public void onBatchChecked(BatchResult batchResult) {
        final String message;
        try {
            message = formatter.formatBatch(batchResult);
        } catch (JsonProcessingException e) {
            // Never let a serialisation problem reach the consumer thread that is draining keys.
            LOGGER.error("Could not serialise a batch result; skipping this message.", e);
            return;
        }
        broadcast(message);
    }

    /**
     * Blocks until the expected number of clients is connected.
     *
     * <p>Exists for tests: a client's socket is connected before the accept loop has registered it,
     * so sending immediately after connecting would race the registration.
     *
     * @param expected     the number of clients to wait for
     * @param timeoutMillis how long to wait
     * @throws InterruptedException if the wait is interrupted
     */
    public void awaitClientCount(int expected, int timeoutMillis) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (clients.size() >= expected) {
                return;
            }
            Thread.sleep(CLIENT_WAIT_POLL_MILLIS);
        }
        throw new IllegalStateException(
                "Expected " + expected + " result listeners but only " + clients.size() + " connected");
    }

    /**
     * Sends one message to every connected client, dropping the ones that fail.
     *
     * @param message the message to send, without its terminator
     */
    private void broadcast(String message) {
        // Collect first, remove after: mutating the list while walking it is legal on a
        // copy-on-write list but reads as a bug at every later glance, and the intent here is
        // simply "drop the ones that failed".
        final List<Socket> dead = new ArrayList<>();
        for (Socket client : clients) {
            if (!writeTo(client, message)) {
                dead.add(client);
            }
        }
        for (Socket client : dead) {
            clients.remove(client);
            closeQuietly(client);
        }
    }

    /**
     * Writes one terminated message to one client.
     *
     * @param client  the client to write to
     * @param message the message to send
     * @return {@code true} if the write succeeded
     */
    private boolean writeTo(Socket client, String message) {
        try {
            final OutputStream out = client.getOutputStream();
            out.write((message + MESSAGE_TERMINATOR).getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException e) {
            LOGGER.info("Result listener {} went away; dropping it.", String.valueOf(client.getRemoteSocketAddress()));
            return false;
        }
    }

    /**
     * Closes a socket, ignoring failures.
     *
     * @param socket the resource to close
     */
    private static void closeQuietly(Closeable socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // best-effort: the peer is already gone
        }
    }

    @Override
    public void close() {
        shouldStop = true;
        final ServerSocket localServerSocket = serverSocket;
        if (localServerSocket != null) {
            try {
                localServerSocket.close();
            } catch (IOException e) {
                // best-effort during shutdown
            }
        }
        for (Socket client : clients) {
            closeQuietly(client);
        }
        clients.clear();
        acceptExecutor.shutdownNow();
    }
}
