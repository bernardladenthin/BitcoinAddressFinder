// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.FireAndForget;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.Startable;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Key producer that receives secrets through a WebSocket server.
 *
 * <p>The embedded {@link WebSocketServer} is not constructed by the constructor;
 * callers must invoke {@link #start()} after construction. This matches the
 * sibling {@link KeyProducerJavaSocket} and {@link KeyProducerJavaZmq} producers
 * and removes the same partial-construction publication hazard (the anonymous
 * {@code WebSocketServer} subclass captures the outer-class {@code this} and its
 * callbacks call back into {@code addSecret} / read {@code shouldStop}).</p>
 */
public class KeyProducerJavaWebSocket extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaWebSocket>
        implements Startable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyProducerJavaWebSocket.class);

    private @Nullable WebSocketServer webSocketServer;

    /**
     * Creates a new WebSocket-based key producer. The embedded server is NOT
     * constructed here; the caller must invoke {@link #start()} afterwards.
     *
     * @param config      the WebSocket configuration
     * @param keyUtility  cryptographic helper
     * @param bitHelper   bit/batch-size helper (unused but kept for symmetry)
     */
    public KeyProducerJavaWebSocket(CKeyProducerJavaWebSocket config, KeyUtility keyUtility, BitHelper bitHelper) {
        super(config, keyUtility);
    }

    /**
     * Builds the embedded {@link WebSocketServer} and submits its blocking
     * {@code start()} call to a single-thread executor so the server runs in the
     * background. Idempotency: calling {@code start()} more than once will
     * replace the server reference; the intended usage is a single invocation
     * right after construction.
     */
    @Override
    public void start() {
        final WebSocketServer localServer = new WebSocketServer(new InetSocketAddress(cKeyProducerJava.getPort())) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                LOGGER.info("WebSocket connection opened from: {}", conn.getRemoteSocketAddress());
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                LOGGER.info("WebSocket closed: {}", conn.getRemoteSocketAddress());
            }

            @Override
            public void onMessage(WebSocket conn, ByteBuffer message) {
                if (shouldStop) return;
                if (message.remaining() == PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES) {
                    byte[] secret = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
                    message.get(secret);
                    addSecret(secret);
                } else {
                    LOGGER.warn("Invalid message length: {}", message.remaining());
                }
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                LOGGER.error("WebSocket error", ex);
            }

            @Override
            public void onStart() {
                LOGGER.info("WebSocket server started on port: {}", getPort());
            }

            @Override
            public void onMessage(WebSocket ws, String string) {
                LOGGER.info("onMessage: {}", string);
            }
        };
        webSocketServer = localServer;

        @FireAndForget("lifecycle via WebSocketServer.stop() in interrupt()")
        @SuppressWarnings("FutureReturnValueIgnored")
        Object unused = Executors.newSingleThreadExecutor().submit(localServer::start);
    }

    @Override
    protected int getReadTimeout() {
        return cKeyProducerJava.timeout;
    }

    @Override
    public void interrupt() {
        signalShutdown(); // wakes any caller blocked in createSecrets()
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
