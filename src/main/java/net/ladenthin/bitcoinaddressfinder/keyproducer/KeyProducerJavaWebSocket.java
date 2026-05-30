// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Key producer that receives secrets through a WebSocket server.
 */
public class KeyProducerJavaWebSocket extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaWebSocket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyProducerJavaWebSocket.class);

    private WebSocketServer webSocketServer;

    /**
     * Creates a new WebSocket-based key producer and starts the embedded server.
     *
     * @param config      the WebSocket configuration
     * @param keyUtility  cryptographic helper
     * @param bitHelper   bit/batch-size helper (unused but kept for symmetry)
     */
    public KeyProducerJavaWebSocket(CKeyProducerJavaWebSocket config, KeyUtility keyUtility, BitHelper bitHelper) {
        super(config, keyUtility);
        initWebSocketServer();
    }

    private void initWebSocketServer() {
        webSocketServer = new WebSocketServer(new InetSocketAddress(cKeyProducerJava.getPort())) {
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

        Executors.newSingleThreadExecutor().submit(webSocketServer::start);
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
