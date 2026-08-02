// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.LMDBPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.keyproducer.ConnectionUtils;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Feeds a range in and reads the outcome back out — over one WebSocket, as a browser page would.
 *
 * <p>The pieces are covered separately elsewhere; what only shows up here is whether they line up:
 * the server accepting a base before the producers exist, the grid configuration reaching a client
 * that connected first, and the checked batch coming back on the very same connection.
 */
class FinderWebSocketRoundtripTest {

    /** Upper bound for every wait; exceeding it is a failure, not a slow machine. */
    private static final int AWAIT_SECONDS = 30;

    /** A small grid keeps the CPU producer's single batch fast. */
    private static final int BATCH_SIZE_IN_BITS = 8;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    public Path folder;

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
            // surfaced through a missing expected message
        }

        String awaitMessageOfType(String type, ObjectMapper mapper) throws Exception {
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
            while (System.nanoTime() < deadline) {
                final String raw = messages.poll(1, TimeUnit.SECONDS);
                if (raw == null) {
                    continue;
                }
                final JsonNode node = mapper.readTree(raw);
                if (type.equals(node.path("type").asText())) {
                    return raw;
                }
            }
            return null;
        }
    }

    // <editor-fold defaultstate="collapsed" desc="roundtrip">
    @Test
    void webSocket_baseSentByClient_checkedBatchComesBackOnTheSameConnection() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();

        // arrange
        final int port = freePort();
        final CFinder cFinder = buildConfiguration(port);
        final Finder finder = new Finder(cFinder);
        final RecordingClient client = new RecordingClient(URI.create("ws://127.0.0.1:" + port));
        try {
            finder.startKeyProducer();
            // The server binds on a background thread, so wait for the port rather than racing it.
            ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, AWAIT_SECONDS * 1000);

            // The client is deliberately here before the producers exist, which is the situation the
            // catch-up greeting is for.
            assertThat(client.connectBlocking(AWAIT_SECONDS, TimeUnit.SECONDS), is(true));

            finder.startConsumer();
            finder.configureProducer();
            finder.initProducer();

            // assert: the grid configuration arrives even though the client connected first
            final String configMessage = client.awaitMessageOfType("config", objectMapper);
            assertThat(configMessage, is(notNullValue()));
            final JsonNode config = objectMapper.readTree(configMessage);
            assertThat(config.get("batchSizeInBits").asInt(), is(equalTo(BATCH_SIZE_IN_BITS)));

            // act: send one base, exactly as the page does
            finder.startProducer();
            client.send(secretBaseMessage(BigInteger.valueOf(0x100L)));

            // assert: the checked batch comes back on the same connection
            final String batchMessage = client.awaitMessageOfType("batch", objectMapper);
            assertThat(batchMessage, is(notNullValue()));
            final JsonNode batch = objectMapper.readTree(batchMessage);
            assertThat(batch.get("checkedCount").asInt(), is(equalTo(1 << BATCH_SIZE_IN_BITS)));
            assertThat(batch.get("secretBase"), is(notNullValue()));
        } finally {
            client.closeBlocking();
            // An interactively fed run never exhausts its source, so it ends the way the user ends
            // it: the shutdown hook interrupts, and the awaiting main thread then completes. Calling
            // only the await would wait for a producer that is parked on the next base forever.
            finder.interrupt();
            finder.shutdownAndAwaitTermination();
        }
    }
    // </editor-fold>

    /**
     * Encodes a secret base the way the WebSocket key producer expects it: fixed-width raw bytes.
     *
     * @param secretBase the base to send
     * @return the message payload
     */
    private static ByteBuffer secretBaseMessage(BigInteger secretBase) {
        return ByteBuffer.wrap(ByteBufferUtility.bigIntegerToFixedLengthBytes(
                secretBase, OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES));
    }

    /**
     * Builds a configuration whose only key source is the WebSocket, as the browser-driven mode uses.
     *
     * @param port the port to serve on
     * @return the finder configuration
     * @throws Exception if the test database cannot be created
     */
    private CFinder buildConfiguration(int port) throws Exception {
        final TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        final TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        final File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, false);

        final CFinder cFinder = new CFinder();
        // Bound the wait so a regression shows up as a failing test rather than a killed fork.
        cFinder.awaitTerminateSeconds = AWAIT_SECONDS;

        final CKeyProducerJavaWebSocket webSocket = new CKeyProducerJavaWebSocket();
        webSocket.keyProducerId = "browser";
        webSocket.port = port;
        // Block instead of ending the run when the user pauses; the browser drives the pace.
        webSocket.timeoutMillis = -1;
        webSocket.broadcastResults = true;
        cFinder.keyProducerJavaWebSocket.add(webSocket);

        final CProducerJava producerJava = new CProducerJava();
        producerJava.keyProducerId = "browser";
        producerJava.batchSizeInBits = BATCH_SIZE_IN_BITS;
        producerJava.batchUsePrivateKeyIncrement = true;
        cFinder.producerJava.add(producerJava);

        final CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cFinder.consumerJava = cConsumerJava;

        return cFinder;
    }

    /**
     * Picks a free TCP port, so parallel runs cannot collide on a fixed one.
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
