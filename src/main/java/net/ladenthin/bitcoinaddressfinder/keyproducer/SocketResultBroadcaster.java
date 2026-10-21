// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * fails is dropped, and one slow client cannot be allowed to stall the scan. A blocking socket write
 * does not fail when the peer stops reading, it waits forever once the buffers are full — so each
 * client has its own writer thread and a bounded queue, and a client that falls
 * {@link #CLIENT_QUEUE_CAPACITY} messages behind is dropped instead of blocking the consumer. What is
 * already queued for a connected client when the broadcaster closes is still delivered, for at most
 * {@link #CLOSE_DRAIN_MILLIS}: the last batches of a finite run are reported right before the
 * shutdown and are the completion signal a client waits for.
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

    /**
     * Messages buffered per client before it counts as fallen behind and is dropped. Writes happen
     * on a per-client writer thread, so a client that stops reading costs at most this many queued
     * messages and never blocks the consumer threads that call {@link #onBatchChecked(BatchResult)}.
     */
    private static final int CLIENT_QUEUE_CAPACITY = 4096;

    /**
     * How long {@link #close()} lets the writers deliver what is already queued. Bounded, so a client
     * that stopped reading cannot hold the shutdown up.
     */
    private static final int CLOSE_DRAIN_MILLIS = 2_000;

    /** How often an idle writer looks at the stop flag, so it can leave once its queue is empty. */
    private static final int WRITER_POLL_MILLIS = 50;

    private final int port;
    private final BatchResultJsonFormatter formatter;

    @ToString.Exclude
    private final Set<Client> clients = new CopyOnWriteArraySet<>();

    /** Orders an announcement against the registration of a new client; see the accept loop. */
    @ToString.Exclude
    private final Object registrationLock = new Object();

    @ToString.Exclude
    private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor();

    /** Runs one writer per connected client; see {@link #CLIENT_QUEUE_CAPACITY}. */
    @ToString.Exclude
    private final ExecutorService writerExecutor = Executors.newCachedThreadPool();

    private volatile boolean shouldStop = false;

    /** Set at the end of {@link #close()}, once no client is connected any more. */
    private volatile boolean closed;

    /** Set by the first message after {@link #close()}, so that message is logged once and not per batch. */
    @ToString.Exclude
    private final AtomicBoolean broadcastAfterCloseReported = new AtomicBoolean();

    @ToString.Exclude
    private volatile @Nullable ServerSocket serverSocket;

    /** The greeting, once the producer configuration it describes is known. */
    @ToString.Exclude
    private volatile @Nullable String configurationMessage;

    /**
     * Runs on the accept thread after a new client's greeting is decided and before the client is
     * registered. A no-op outside tests, which use it to interleave an announcement with a connect.
     */
    @ToString.Exclude
    private volatile Runnable beforeClientRegistered = () -> {};

    /** Test seam: runs in a writer thread right before a queued message is written. */
    @ToString.Exclude
    private volatile Runnable beforeWrite = () -> {};

    /** Test seam: runs in {@link #close()} right after the stop has been requested. */
    @ToString.Exclude
    private volatile Runnable afterStopRequested = () -> {};

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
    @FireAndForget("lifecycle via close()")
    @SuppressWarnings("FutureReturnValueIgnored")
    public void start() throws IOException {
        final ServerSocket localServerSocket = new ServerSocket(port);
        localServerSocket.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);
        serverSocket = localServerSocket;
        LOGGER.info("Serving results on port {}", port);

        acceptExecutor.submit(() -> {
            while (!shouldStop) {
                try {
                    final Socket socket = localServerSocket.accept();
                    final Client client = new Client(socket);
                    LOGGER.info("Result listener connected: {}", String.valueOf(socket.getRemoteSocketAddress()));
                    // A client that arrives after the grid is known must not have to wait for the
                    // next republication to learn it. Queued before the client is registered, so the
                    // greeting is the first line it receives. Under the lock an announcement either
                    // happened before (the greeting carries it) or happens after the registration
                    // (its broadcast reaches the client); without it, one in between reached neither.
                    synchronized (registrationLock) {
                        final String greeting = configurationMessage;
                        if (greeting != null) {
                            // the queue is still empty, so this cannot exceed its capacity
                            client.queue().add(greeting);
                        }
                        beforeClientRegistered.run();
                        clients.add(client);
                    }
                    // close() sets shouldStop before it sweeps the clients, so a client registered
                    // after the sweep sees the flag here; one registered before it is closed by it.
                    if (shouldStop) {
                        drop(client);
                        continue;
                    }
                    try {
                        startWriter(client);
                    } catch (RejectedExecutionException e) {
                        // close() shut the writers down between the check above and here
                        drop(client);
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
        synchronized (registrationLock) {
            configurationMessage = message;
            broadcast(message);
        }
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
     * Installs the hook that runs between deciding a new client's greeting and registering it.
     *
     * @param hook the code to run on the accept thread
     */
    @VisibleForTesting
    void setBeforeClientRegistered(Runnable hook) {
        this.beforeClientRegistered = hook;
    }

    /**
     * Installs the hook that runs in a writer thread right before a queued message is written.
     *
     * @param hook the code to run on the writer thread
     */
    @VisibleForTesting
    void setBeforeWrite(Runnable hook) {
        this.beforeWrite = hook;
    }

    /**
     * Installs the hook that runs in {@link #close()} right after the stop has been requested.
     *
     * @param hook the code to run on the closing thread
     */
    @VisibleForTesting
    void setAfterStopRequested(Runnable hook) {
        this.afterStopRequested = hook;
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
        if (closed) {
            if (broadcastAfterCloseReported.compareAndSet(false, true)) {
                LOGGER.warn(
                        "The result server on port {} is already closed; results reported from now on are not sent.",
                        port);
            }
            return;
        }
        for (Client client : clients) {
            if (!client.queue().offer(message)) {
                LOGGER.warn(
                        "Result listener {} fell behind by {} messages; dropping it.",
                        String.valueOf(client.socket().getRemoteSocketAddress()),
                        CLIENT_QUEUE_CAPACITY);
                drop(client);
            }
        }
    }

    /**
     * Starts the writer that drains one client's queue onto its socket until the client is dropped, or
     * until a stop has been requested and the queue is empty.
     *
     * @param client the client to serve
     */
    @FireAndForget("lifecycle via drop() and close()")
    @SuppressWarnings("FutureReturnValueIgnored")
    private void startWriter(Client client) {
        writerExecutor.submit(() -> {
            try {
                // After a stop the writer keeps going until its queue is empty: the last batches of a
                // run are its completion signal, and close() waits for exactly this drain.
                while (clients.contains(client)) {
                    final String message = client.queue().poll(WRITER_POLL_MILLIS, TimeUnit.MILLISECONDS);
                    if (message == null) {
                        if (shouldStop) {
                            return;
                        }
                        continue;
                    }
                    beforeWrite.run();
                    if (!writeTo(client.socket(), message)) {
                        drop(client);
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Unregisters a client and closes its socket, which also unblocks a write in progress.
     *
     * @param client the client to drop
     */
    private void drop(Client client) {
        clients.remove(client);
        closeQuietly(client.socket());
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

    /**
     * Stops accepting, lets every writer deliver what is already queued (for at most
     * {@link #CLOSE_DRAIN_MILLIS}), then closes the client connections.
     */
    @Override
    public void close() {
        shouldStop = true;
        afterStopRequested.run();
        final ServerSocket localServerSocket = serverSocket;
        if (localServerSocket != null) {
            try {
                localServerSocket.close();
            } catch (IOException e) {
                // best-effort during shutdown
            }
        }
        acceptExecutor.shutdownNow();
        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(CLOSE_DRAIN_MILLIS, TimeUnit.MILLISECONDS)) {
                LOGGER.warn("Result listeners did not take all queued results within {} ms.", CLOSE_DRAIN_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Client client : clients) {
            closeQuietly(client.socket());
        }
        clients.clear();
        closed = true;
        writerExecutor.shutdownNow();
    }

    /** One connected client: its socket and the messages not yet written to it. */
    private static final class Client {

        private final Socket socket;

        private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(CLIENT_QUEUE_CAPACITY);

        private Client(Socket socket) {
            this.socket = socket;
        }

        private Socket socket() {
            return socket;
        }

        private BlockingQueue<String> queue() {
            return queue;
        }
    }
}
