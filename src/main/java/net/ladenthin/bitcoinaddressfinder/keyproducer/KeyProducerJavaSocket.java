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
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Key producer that receives secrets from a TCP socket (client or server mode).
 */
// The constructor publishes the runnable that mutates @Nullable socket / serverSocket /
// inputStream fields; CF flags this-escape on setupSocket and possibly-null dereferences
// inside the runnable that survive the assignment. Tracked in CLAUDE.md as a TODO
// ("KeyProducerJavaSocket this-escape") to refactor with a start() method later.
@SuppressWarnings({"nullness:method.invocation", "nullness:dereference.of.nullable", "nullness:argument"})
public class KeyProducerJavaSocket extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaSocket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyProducerJavaSocket.class);

    private @Nullable ServerSocket serverSocket;
    private @Nullable Socket socket;
    private @Nullable DataInputStream inputStream;
    private @Nullable Future<?> readerFuture;

    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new socket-based key producer and starts the background reader thread.
     *
     * @param config      the socket configuration
     * @param keyUtility  cryptographic helper
     * @param bitHelper   bit/batch-size helper (unused but kept for symmetry)
     */
    public KeyProducerJavaSocket(CKeyProducerJavaSocket config, KeyUtility keyUtility, BitHelper bitHelper) {
        super(config, keyUtility);
        setupSocket();
    }

    private void setupSocket() {
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

                    byte[] buffer = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
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

    private void closeConnections() {
        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
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
