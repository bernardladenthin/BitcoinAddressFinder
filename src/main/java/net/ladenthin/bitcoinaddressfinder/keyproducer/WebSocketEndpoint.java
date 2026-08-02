// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.core.FireAndForget;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One embedded WebSocket server, usable in both directions.
 *
 * <p>Extracted so incoming secrets and outgoing results can share a single server on a single port.
 * That is what makes a browser page a complete user interface with one connection: it feeds ranges in
 * and watches the outcome come back, without a second endpoint to configure.
 *
 * <p>The binding type {@link WebSocket} deliberately does not appear in this class's public API.
 * Callers register a handler for inbound messages and a greeting for new connections; nothing above
 * has to know which library moves the bytes.
 */
@ToString
public class WebSocketEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketEndpoint.class);

    /** Receives binary messages arriving from a connected client. */
    @FunctionalInterface
    public interface MessageHandler {

        /**
         * Called for every binary message.
         *
         * @param message the message payload
         */
        void onBinaryMessage(byte[] message);
    }

    private final int port;

    @ToString.Exclude
    private @Nullable WebSocketServer webSocketServer;

    @ToString.Exclude
    private @Nullable MessageHandler messageHandler;

    @ToString.Exclude
    private @Nullable Supplier<@Nullable String> greetingSupplier;

    /**
     * Creates an endpoint bound to the given port. The server is not started here.
     *
     * @param port the TCP port to serve on
     */
    public WebSocketEndpoint(int port) {
        this.port = port;
    }

    /**
     * Registers the handler for inbound binary messages.
     *
     * @param messageHandler the handler, replacing any previous one
     */
    public void setMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    /**
     * Registers a greeting sent to every client right after it connects.
     *
     * <p>A supplier rather than a fixed string because the greeting describes the running
     * configuration, which is only fully known once the producers have been configured — later than
     * the moment this server starts accepting connections. Clients that connect before then simply
     * receive nothing; {@link #greetAll()} catches them up.
     *
     * @param greetingSupplier produces the greeting; it may itself return {@code null} while the
     *                         greeting is not yet known, in which case nothing is sent
     */
    public void setGreetingSupplier(@Nullable Supplier<@Nullable String> greetingSupplier) {
        this.greetingSupplier = greetingSupplier;
    }

    /**
     * Sends the current greeting to every connected client.
     *
     * <p>Used once the greeting becomes available, so clients that connected earlier are not left
     * without it.
     */
    public void greetAll() {
        final String greeting = currentGreeting();
        if (greeting != null) {
            broadcast(greeting);
        }
    }

    /**
     * Returns the greeting to send right now.
     *
     * @return the greeting, or {@code null} while none is available
     */
    private @Nullable String currentGreeting() {
        final Supplier<@Nullable String> localSupplier = greetingSupplier;
        if (localSupplier == null) {
            return null;
        }
        final String greeting = localSupplier.get();
        // An empty greeting is treated as absent: a client must never have to parse a blank message
        // just because it connected before the configuration was known.
        return greeting == null || greeting.isEmpty() ? null : greeting;
    }

    /**
     * Starts the server on a background thread. Calling this more than once replaces the server;
     * the intended usage is a single invocation.
     */
    public void start() {
        final WebSocketServer localServer = new WebSocketServer(new InetSocketAddress(port)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                LOGGER.info("WebSocket connection opened from: {}", conn.getRemoteSocketAddress());
                final String greeting = currentGreeting();
                if (greeting != null) {
                    conn.send(greeting);
                }
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                LOGGER.info("WebSocket closed: {}", conn.getRemoteSocketAddress());
            }

            @Override
            public void onMessage(WebSocket conn, ByteBuffer message) {
                final MessageHandler localHandler = messageHandler;
                if (localHandler == null) {
                    return;
                }
                final byte[] payload = new byte[message.remaining()];
                message.get(payload);
                localHandler.onBinaryMessage(payload);
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

        @FireAndForget("lifecycle via stop()")
        @SuppressWarnings("FutureReturnValueIgnored")
        Object unused = Executors.newSingleThreadExecutor().submit(localServer::start);
    }

    /**
     * Sends a text message to every connected client.
     *
     * <p>Never throws: this is called from the consumer's worker threads, where a transport failure
     * must not disturb the scan.
     *
     * @param message the message to send
     */
    public void broadcast(String message) {
        final WebSocketServer localServer = webSocketServer;
        if (localServer == null) {
            return;
        }
        try {
            localServer.broadcast(message);
        } catch (RuntimeException e) {
            LOGGER.warn("Broadcast failed; continuing.", e);
        }
    }

    /** Stops the server, if it was started. */
    public void stop() {
        final WebSocketServer localServer = webSocketServer;
        if (localServer != null) {
            try {
                localServer.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
