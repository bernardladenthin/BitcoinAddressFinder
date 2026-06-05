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
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.Startable;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
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
     */
    public KeyProducerJavaSocket(CKeyProducerJavaSocket config, KeyUtility keyUtility, BitHelper bitHelper) {
        super(config, keyUtility);
    }

    /**
     * Submits the background reader runnable that connects (or accepts) on the
     * configured port and feeds incoming secrets into the queue. Idempotency:
     * calling {@code start()} more than once will submit a second runnable on
     * the same single-thread executor, which will sit queued behind the first.
     * The intended usage is a single invocation right after construction; tests
     * and the production caller (Finder) do exactly one call.
     */
    @Override
    public void start() {
        readerFuture = readerExecutor.submit(() -> {
            int attempts = 0;
            Exception lastException = null;

            while (!shouldStop && attempts < cKeyProducerJava.connectionRetryCount) {
                try {
                    if (cKeyProducerJava.mode == CKeyProducerJavaSocket.Mode.SERVER) {
                        if (serverSocket == null) {
                            serverSocket = new ServerSocket(cKeyProducerJava.getPort());
                            serverSocket.setSoTimeout(cKeyProducerJava.timeout);
                        }
                        socket = serverSocket.accept();
                        LOGGER.info("Accepted client connection at port {}", cKeyProducerJava.getPort());
                    } else {
                        socket = new Socket();
                        socket.connect(
                                new InetSocketAddress(cKeyProducerJava.getHost(), cKeyProducerJava.getPort()),
                                cKeyProducerJava.timeout);
                        LOGGER.info(
                                "Connected to server at {}:{}", cKeyProducerJava.getHost(), cKeyProducerJava.getPort());
                    }

                    Socket localSocket = Objects.requireNonNull(socket);
                    localSocket.setSoTimeout(cKeyProducerJava.timeout);
                    inputStream = new DataInputStream(localSocket.getInputStream());

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
                    sleep(cKeyProducerJava.retryDelayMillisConnect);
                }
            }

            if (!shouldStop && socket == null) {
                throw new RuntimeException("Unable to establish socket connection", lastException);
            }
        });
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
        return cKeyProducerJava.timeout;
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
