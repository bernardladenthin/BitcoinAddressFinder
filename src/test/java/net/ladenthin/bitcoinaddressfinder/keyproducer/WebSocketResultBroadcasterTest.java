// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.Hit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link WebSocketResultBroadcaster} through a real WebSocket connection.
 *
 * <p>A real client rather than a mocked transport, because the parts that actually break live in the
 * transport: whether a client that connects before the configuration is known still receives it, and
 * whether a batch without a hit is reported at all. Both are the difference between a browser page
 * that can sweep a range safely and one that silently loses ground.
 */
class WebSocketResultBroadcasterTest {

    /** Upper bound for every wait on a message; exceeding it is a failure, not a slow machine. */
    private static final int AWAIT_SECONDS = 15;

    /** Grid size announced in the greeting, mirroring a producer's batchSizeInBits. */
    private static final int BATCH_SIZE_IN_BITS = 12;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Collects everything the server sends, in order. */
    private static final class RecordingClient extends WebSocketClient {

        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

        RecordingClient(URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            // nothing to do
        }

        @Override
        public void onMessage(String message) {
            messages.add(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            // nothing to do
        }

        @Override
        public void onError(Exception ex) {
            // recorded through the absence of expected messages
        }

        String awaitMessage() throws InterruptedException {
            return messages.poll(AWAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="onBatchChecked">
    @Test
    void onBatchChecked_batchWithoutHit_clientReceivesTheCheckedRangeAnyway() throws Exception {
        // arrange
        final int port = freePort();
        final WebSocketEndpoint endpoint = new WebSocketEndpoint(port);
        final WebSocketResultBroadcaster broadcaster = new WebSocketResultBroadcaster(endpoint);
        endpoint.start();
        // The server binds on a background thread, so wait for the port rather than racing it.
        ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, AWAIT_SECONDS * 1000);

        final RecordingClient client = new RecordingClient(URI.create("ws://127.0.0.1:" + port));
        try {
            assertThat(client.connectBlocking(AWAIT_SECONDS, TimeUnit.SECONDS), is(true));

            // act
            broadcaster.onBatchChecked(new BatchResult(BigInteger.valueOf(0x4000L), 4096, List.of()));

            // assert
            final JsonNode message = objectMapper.readTree(client.awaitMessage());
            assertThat(message.get("type").asText(), is(equalTo("batch")));
            assertThat(message.get("secretBase").asText(), is(equalTo("4000")));
            assertThat(message.get("checkedCount").asInt(), is(equalTo(4096)));
            assertThat(message.get("hits").size(), is(equalTo(0)));
        } finally {
            client.closeBlocking();
            endpoint.stop();
        }
    }

    @Test
    void onBatchChecked_batchWithHit_clientReceivesTheKeyMaterial() throws Exception {
        // arrange
        final int port = freePort();
        final WebSocketEndpoint endpoint = new WebSocketEndpoint(port);
        final WebSocketResultBroadcaster broadcaster = new WebSocketResultBroadcaster(endpoint);
        endpoint.start();
        // The server binds on a background thread, so wait for the port rather than racing it.
        ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, AWAIT_SECONDS * 1000);

        final RecordingClient client = new RecordingClient(URI.create("ws://127.0.0.1:" + port));
        try {
            assertThat(client.connectBlocking(AWAIT_SECONDS, TimeUnit.SECONDS), is(true));
            final Hit hit = new Hit(BigInteger.valueOf(73), "aabb", "1TestAddress", true, false);

            // act
            broadcaster.onBatchChecked(new BatchResult(BigInteger.valueOf(0x4000L), 4096, List.of(hit)));

            // assert
            final JsonNode message = objectMapper.readTree(client.awaitMessage());
            final JsonNode reportedHit = message.get("hits").get(0);
            assertThat(reportedHit.get("privateKey").asText(), is(equalTo("49")));
            assertThat(reportedHit.get("address").asText(), is(equalTo("1TestAddress")));
            assertThat(reportedHit.get("hash160Hex").asText(), is(equalTo("aabb")));
            assertThat(reportedHit.get("compressed").asBoolean(), is(true));
        } finally {
            client.closeBlocking();
            endpoint.stop();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="greeting">
    @Test
    void greeting_clientConnectedBeforeConfigurationKnown_stillReceivesIt() throws Exception {
        // The server accepts connections before the producers are configured, so a client can be
        // there first. Without the catch-up it would never learn the grid size and could only guess
        // its step, silently rescanning one block over and over.

        // arrange
        final int port = freePort();
        final WebSocketEndpoint endpoint = new WebSocketEndpoint(port);
        final WebSocketResultBroadcaster broadcaster = new WebSocketResultBroadcaster(endpoint);
        endpoint.start();
        // The server binds on a background thread, so wait for the port rather than racing it.
        ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, AWAIT_SECONDS * 1000);

        final RecordingClient client = new RecordingClient(URI.create("ws://127.0.0.1:" + port));
        try {
            assertThat(client.connectBlocking(AWAIT_SECONDS, TimeUnit.SECONDS), is(true));

            // act: the configuration only becomes known now
            broadcaster.announceConfiguration(BATCH_SIZE_IN_BITS, true);

            // assert
            final String raw = client.awaitMessage();
            assertThat(raw, is(notNullValue()));
            final JsonNode greeting = objectMapper.readTree(raw);
            assertThat(greeting.get("type").asText(), is(equalTo("config")));
            assertThat(greeting.get("batchSizeInBits").asInt(), is(equalTo(BATCH_SIZE_IN_BITS)));
            assertThat(greeting.get("batchUsePrivateKeyIncrement").asBoolean(), is(true));
            assertThat(greeting.get("secretByteLength").asInt(), is(equalTo(32)));
        } finally {
            client.closeBlocking();
            endpoint.stop();
        }
    }

    @Test
    void greeting_clientConnectsAfterConfigurationKnown_receivesItOnConnect() throws Exception {
        // arrange
        final int port = freePort();
        final WebSocketEndpoint endpoint = new WebSocketEndpoint(port);
        final WebSocketResultBroadcaster broadcaster = new WebSocketResultBroadcaster(endpoint);
        broadcaster.announceConfiguration(BATCH_SIZE_IN_BITS, true);
        endpoint.start();
        // The server binds on a background thread, so wait for the port rather than racing it.
        ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, AWAIT_SECONDS * 1000);

        final RecordingClient client = new RecordingClient(URI.create("ws://127.0.0.1:" + port));
        try {
            // act
            assertThat(client.connectBlocking(AWAIT_SECONDS, TimeUnit.SECONDS), is(true));

            // assert
            assertThat(client.awaitMessage(), containsString("\"type\":\"config\""));
        } finally {
            client.closeBlocking();
            endpoint.stop();
        }
    }
    // </editor-fold>

    /**
     * Picks a free TCP port, so parallel test runs cannot collide on a fixed one.
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
