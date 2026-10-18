// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.ExecutorService;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.core.Startable;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Key producer that receives secrets from a TCP socket (client or server mode).
 *
 * <p>The background reader thread is not spawned by the constructor; callers must
 * invoke {@link #start()} after construction. This avoids the JEP&nbsp;410
 * this-escape that would otherwise publish a partially-constructed instance to
 * the worker thread.</p>
 */
// The reader runnable still mutates @Nullable socket / serverSocket / inputStream
// fields and reads them across blocking I/O boundaries that the Checker Framework
// flow analyser cannot bridge; the surviving suppressions cover only those
// in-lambda field reads and the @Nullable e.getMessage() passed to Logger.warn —
// not a this-escape. The constructor no longer publishes this; start() runs after
// construction so the receiver is fully initialised when the worker observes it.
@SuppressWarnings({"nullness:dereference.of.nullable", "nullness:argument"})
@ToString(callSuper = true)
public class KeyProducerJavaSocket extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaSocket>
        implements Startable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyProducerJavaSocket.class);

    private @Nullable ServerSocket serverSocket;
    private @Nullable Socket socket;
    // DataInputStream toString is the default Object identity hash, not useful.
    @ToString.Exclude
    private @Nullable DataInputStream inputStream;
    // Future toString is the default Object identity hash, not useful.
    @ToString.Exclude
    private @Nullable Future<?> readerFuture;

    // ExecutorService toString includes the pool internals — verbose and unhelpful for logs.
    @ToString.Exclude
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new socket-based key producer. The background reader thread is NOT
     * started here; the caller must invoke {@link #start()} afterwards.
     *
     * @param config      the socket configuration
     * @param keyUtility  cryptographic helper
     * @param bitHelper   bit/batch-size helper (unused but kept for symmetry)
     * @throws IllegalArgumentException if {@code config.timeoutMillis} is negative, or if in server
     *     mode {@code config.port} is the {@code resultPort} results are broadcast on
     */
    public KeyProducerJavaSocket(CKeyProducerJavaSocket config, KeyUtility keyUtility, BitHelper bitHelper) {
        super(requireDistinctResultPort(requireNonNegativeTimeout(config)), keyUtility);
    }

    /**
     * Rejects a server whose port is the one results are broadcast on. The result server is started
     * first and takes the port, every accept attempt then fails with "Address already in use", and once
     * {@code timeoutMillis} passed without a secret the run ended as a regular one, without a single key
     * checked. Checked before {@code super(...)} so no partially constructed producer exists when it
     * throws.
     *
     * @param config the socket configuration
     * @return {@code config}, unchanged
     * @throws IllegalArgumentException if the two ports collide
     */
    private static CKeyProducerJavaSocket requireDistinctResultPort(CKeyProducerJavaSocket config) {
        if (config.mode == CKeyProducerJavaSocket.Mode.SERVER
                && config.broadcastResults
                && config.port == config.resultPort) {
            throw new IllegalArgumentException("port and resultPort of the socket key producer "
                    + config.keyProducerId + " must differ in server mode while broadcastResults is set, but"
                    + " both are " + config.port);
        }
        return config;
    }

    /**
     * Rejects a negative {@code timeoutMillis}. {@code java.net} throws {@code IllegalArgumentException}
     * for a negative SO_TIMEOUT, but inside the reader future nothing would see it: the reader would die
     * silently and {@link #createSecrets} would block forever. Checked before {@code super(...)} so no
     * partially constructed producer exists when it throws.
     *
     * @param config the socket configuration
     * @return {@code config}, unchanged
     * @throws IllegalArgumentException if {@code timeoutMillis} is negative
     */
    private static CKeyProducerJavaSocket requireNonNegativeTimeout(CKeyProducerJavaSocket config) {
        if (config.timeoutMillis < 0) {
            throw new IllegalArgumentException(
                    "timeoutMillis must not be negative for the socket key producer, but was " + config.timeoutMillis);
        }
        return config;
    }

    /**
     * Submits the background reader runnable that connects (or accepts) on the
     * configured port and feeds incoming secrets into the queue. Idempotency:
     * calling {@code start()} more than once will submit a second runnable on
     * the same single-thread executor, which will sit queued behind the first.
     * The intended usage is a single invocation right after construction; tests
     * and the production caller (Finder) do exactly one call.
     *
     * <p>In server mode the port is bound here, on the calling thread, before the reader is
     * submitted. A port that cannot be bound (held by another process, or not permitted) is a
     * configuration error, not a transient one, so the start fails like any server's would: retried
     * in the reader, it only produced warnings until {@code timeoutMillis} ended the run as a
     * regular one, without a single key checked.
     *
     * @throws IllegalStateException if the server port cannot be bound
     */
    @Override
    public void start() {
        if (cKeyProducerJava.mode == CKeyProducerJavaSocket.Mode.SERVER) {
            serverSocket = bindServerSocket();
        }
        readerFuture = readerExecutor.submit(() -> {
            int attempts = 0;
            Exception lastException = null;

            while (!shouldStop && attempts < cKeyProducerJava.connectRetryCount) {
                try {
                    if (cKeyProducerJava.mode == CKeyProducerJavaSocket.Mode.SERVER) {
                        if (serverSocket == null) {
                            serverSocket = new ServerSocket(cKeyProducerJava.getPort());
                            serverSocket.setSoTimeout(cKeyProducerJava.timeoutMillis);
                        }
                        socket = serverSocket.accept();
                        LOGGER.info("Accepted client connection at port {}", cKeyProducerJava.getPort());
                    } else {
                        socket = new Socket();
                        socket.connect(
                                new InetSocketAddress(cKeyProducerJava.getHost(), cKeyProducerJava.getPort()),
                                cKeyProducerJava.timeoutMillis);
                        LOGGER.info(
                                "Connected to server at {}:{}", cKeyProducerJava.getHost(), cKeyProducerJava.getPort());
                    }

                    Socket localSocket = Objects.requireNonNull(socket);
                    localSocket.setSoTimeout(cKeyProducerJava.timeoutMillis);
                    inputStream = new DataInputStream(localSocket.getInputStream());
                    // connectRetryCount bounds the attempts to establish one connection, not the
                    // reconnects over the producer's lifetime: a peer that disconnects after a
                    // successful connection gets the full retry budget again.
                    attempts = 0;

                    byte[] buffer = new byte[OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES];
                    while (!shouldStop) {
                        int read = 0;
                        while (read < buffer.length) {
                            int r = inputStream.read(buffer, read, buffer.length - read);
                            if (r == -1) throw new IOException("End of stream");
                            read += r;
                        }
                        addSecret(buffer.clone());
                    }

                } catch (IOException e) {
                    lastException = e;
                    LOGGER.warn("Connection attempt {} failed: {}", attempts + 1, e.getMessage());
                    attempts++;
                    closeConnections();
                    sleep(cKeyProducerJava.connectRetryDelayMillis);
                }
            }

            if (!shouldStop && socket == null) {
                IllegalStateException failure = new IllegalStateException(
                        "Unable to establish socket connection to "
                                + cKeyProducerJava.getHost() + ":" + cKeyProducerJava.getPort()
                                + " after " + attempts + " attempts",
                        lastException);
                // Nobody reads the reader's future, so the failure must reach the log here.
                LOGGER.error(failure.getMessage(), lastException);
                throw failure;
            }
        });
    }

    /**
     * Binds the server port with the configured accept timeout.
     *
     * @return the bound server socket
     * @throws IllegalStateException if the port cannot be bound
     */
    private ServerSocket bindServerSocket() {
        try {
            ServerSocket bound = new ServerSocket(cKeyProducerJava.getPort());
            try {
                bound.setSoTimeout(cKeyProducerJava.timeoutMillis);
            } catch (IOException e) {
                bound.close();
                throw e;
            }
            return bound;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "The socket key producer " + cKeyProducerJava.keyProducerId + " cannot listen on port "
                            + cKeyProducerJava.getPort() + ": " + e.getMessage(),
                    e);
        }
    }

    // Best-effort cleanup of the three socket-side resources. Each close() can
    // throw IOException (broken pipe, already closed by peer, etc.); the producer
    // is already in shutdown so there is nothing useful to do with those failures
    // and propagating them would mask the original reason we are closing. The
    // ignored exception is intentional at every catch site below.
    private void closeConnections() {
        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException ignored) {
            // best-effort: see method comment
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // best-effort: see method comment
        }
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
            // best-effort: see method comment
        }
        inputStream = null;
        socket = null;
        serverSocket = null;
    }

    @Override
    protected int getReadTimeout() {
        return cKeyProducerJava.timeoutMillis;
    }

    @Override
    public void interrupt() {
        signalShutdown(); // wakes any caller blocked in createSecrets()
        if (readerFuture != null) {
            readerFuture.cancel(true);
        }
        readerExecutor.shutdownNow();
        closeConnections();
        try {
            readerExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
