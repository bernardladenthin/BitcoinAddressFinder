// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

public class KeyProducerJavaWebSocket extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaWebSocket> {

    private WebSocketServer webSocketServer;

    public KeyProducerJavaWebSocket(CKeyProducerJavaWebSocket config, KeyUtility keyUtility, BitHelper bitHelper, Logger logger) {
        super(config, keyUtility, logger);
        initWebSocketServer();
    }

    private void initWebSocketServer() {
        webSocketServer = new WebSocketServer(new InetSocketAddress(cKeyProducerJava.getPort())) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                logger.info("WebSocket connection opened from: {}", conn.getRemoteSocketAddress());
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                logger.info("WebSocket closed: {}", conn.getRemoteSocketAddress());
            }

            @Override
            public void onMessage(WebSocket conn, ByteBuffer message) {
                if (shouldStop) return;
                if (message.remaining() == PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES) {
                    byte[] secret = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
                    message.get(secret);
                    addSecret(secret);
                } else {
                    logger.warn("Invalid message length: {}", message.remaining());
                }
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                logger.error("WebSocket error", ex);
            }

            @Override
            public void onStart() {
                logger.info("WebSocket server started on port: {}", getPort());
            }

            @Override
            public void onMessage(WebSocket ws, String string) {
                logger.info("onMessage: {}", string);
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
        shouldStop = true;
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}