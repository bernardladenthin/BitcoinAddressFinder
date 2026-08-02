// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.Hit;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link SocketResultBroadcaster} through real TCP clients.
 *
 * <p>Two properties matter here and nowhere else. First, this transport had no notion of several
 * peers at all — the key producer serves exactly one — so "reaches every listener" is new behaviour
 * and is asserted with two clients at once. Second, the outbound direction needs framing that the
 * inbound one does not: secrets arrive as fixed-width records, results are variable-length JSON, so
 * they are newline-delimited and a reader must be able to rely on that.
 */
class SocketResultBroadcasterTest {

    /** Upper bound for every read; exceeding it is a failure, not a slow machine. */
    private static final int AWAIT_MILLIS = 15_000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // <editor-fold defaultstate="collapsed" desc="onBatchChecked">
    @Test
    void onBatchChecked_twoConnectedClients_bothReceiveTheSameLine() throws Exception {
        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        try {
            broadcaster.start();
            ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, AWAIT_MILLIS);

            try (Socket first = connect(port);
                    Socket second = connect(port)) {
                final BufferedReader firstReader = reader(first);
                final BufferedReader secondReader = reader(second);
                broadcaster.awaitClientCount(2, AWAIT_MILLIS);

                // act
                broadcaster.onBatchChecked(new BatchResult(BigInteger.valueOf(0x4000L), 4096, List.of()));

                // assert
                for (BufferedReader clientReader : List.of(firstReader, secondReader)) {
                    final String line = clientReader.readLine();
                    assertThat(line, is(notNullValue()));
                    final JsonNode message = objectMapper.readTree(line);
                    assertThat(message.get("type").asText(), is(equalTo("batch")));
                    assertThat(message.get("checkedCount").asInt(), is(equalTo(4096)));
                }
            }
        } finally {
            broadcaster.close();
        }
    }

    @Test
    void onBatchChecked_batchWithHit_clientReceivesTheKeyMaterial() throws Exception {
        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        try {
            broadcaster.start();
            ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, AWAIT_MILLIS);

            try (Socket client = connect(port)) {
                final BufferedReader clientReader = reader(client);
                broadcaster.awaitClientCount(1, AWAIT_MILLIS);
                final Hit hit = new Hit(BigInteger.valueOf(73), "aabb", "1TestAddress", true, false);

                // act
                broadcaster.onBatchChecked(new BatchResult(BigInteger.ONE, 1, List.of(hit)));

                // assert
                final JsonNode message = objectMapper.readTree(clientReader.readLine());
                assertThat(message.get("hits").get(0).get("privateKey").asText(), is(equalTo("49")));
            }
        } finally {
            broadcaster.close();
        }
    }

    @Test
    void onBatchChecked_clientDisconnected_remainingClientStillReceives() throws Exception {
        // A dead peer must not take the channel down with it: the write to it fails, and the healthy
        // client must still be served.

        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        try {
            broadcaster.start();
            ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, AWAIT_MILLIS);

            final Socket leaving = connect(port);
            try (Socket staying = connect(port)) {
                final BufferedReader stayingReader = reader(staying);
                broadcaster.awaitClientCount(2, AWAIT_MILLIS);
                leaving.close();

                // act: the first send may still succeed into the dead socket's buffer
                broadcaster.onBatchChecked(new BatchResult(BigInteger.ONE, 1, List.of()));
                broadcaster.onBatchChecked(new BatchResult(BigInteger.TWO, 2, List.of()));

                // assert
                assertThat(stayingReader.readLine(), is(notNullValue()));
                assertThat(stayingReader.readLine(), is(notNullValue()));
            }
        } finally {
            broadcaster.close();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="announceConfiguration">
    @Test
    void announceConfiguration_clientConnectsAfterwards_receivesItOnConnect() throws Exception {
        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        try {
            broadcaster.announceConfiguration(18, true);
            broadcaster.start();
            ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, AWAIT_MILLIS);

            // act
            try (Socket client = connect(port)) {
                final JsonNode message = objectMapper.readTree(reader(client).readLine());

                // assert
                assertThat(message.get("type").asText(), is(equalTo("config")));
                assertThat(message.get("batchSizeInBits").asInt(), is(equalTo(18)));
            }
        } finally {
            broadcaster.close();
        }
    }

    @Test
    void announceConfiguration_clientConnectedBefore_isCaughtUp() throws Exception {
        // arrange
        final int port = freePort();
        final SocketResultBroadcaster broadcaster = new SocketResultBroadcaster(port);
        try {
            broadcaster.start();
            ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, AWAIT_MILLIS);

            try (Socket client = connect(port)) {
                final BufferedReader clientReader = reader(client);
                broadcaster.awaitClientCount(1, AWAIT_MILLIS);

                // act: the configuration only becomes known now
                broadcaster.announceConfiguration(18, true);

                // assert
                final JsonNode message = objectMapper.readTree(clientReader.readLine());
                assertThat(message.get("type").asText(), is(equalTo("config")));
            }
        } finally {
            broadcaster.close();
        }
    }
    // </editor-fold>

    /**
     * Opens a client connection with a bounded read timeout.
     *
     * @param port the port to connect to
     * @return the connected socket
     * @throws Exception if the connection cannot be established
     */
    private static Socket connect(int port) throws Exception {
        final Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(AWAIT_MILLIS);
        return socket;
    }

    /**
     * Wraps a socket in a line reader, matching the newline framing of the outbound direction.
     *
     * @param socket the connected socket
     * @return a reader over its input
     * @throws Exception if the stream cannot be opened
     */
    private static BufferedReader reader(Socket socket) throws Exception {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
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
