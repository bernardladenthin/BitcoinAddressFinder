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
import java.math.BigInteger;
import java.net.ServerSocket;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.Hit;
import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Drives {@link ZmqResultBroadcaster} through a real ZeroMQ subscriber.
 *
 * <p>Both tests fight the same property of {@code PUB}/{@code SUB}: a message published before the
 * subscriber has finished connecting is dropped, and the publisher has no way to learn that anyone
 * subscribed. There is no handshake to wait on, so the tests publish repeatedly until a message
 * arrives. That is not test scaffolding around a bug — it is how a real subscriber has to behave,
 * and the reason the grid configuration is republished rather than sent once.
 */
class ZmqResultBroadcasterTest {

    /** Upper bound for the retry loop; exceeding it is a failure, not a slow machine. */
    private static final int AWAIT_MILLIS = 15_000;

    /** How long a single receive attempt waits before the next publish. */
    private static final int RECEIVE_TIMEOUT_MILLIS = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // <editor-fold defaultstate="collapsed" desc="onBatchChecked">
    @Test
    void onBatchChecked_batchWithoutHit_subscriberReceivesTheCheckedRange() throws Exception {
        // arrange
        final String address = "tcp://127.0.0.1:" + freePort();
        final ZmqResultBroadcaster broadcaster = new ZmqResultBroadcaster(address);
        try (ZContext context = new ZContext()) {
            final ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect(address);
            subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
            subscriber.setReceiveTimeOut(RECEIVE_TIMEOUT_MILLIS);

            // act, assert
            final String received = receiveWhilePublishing(
                    subscriber,
                    () -> broadcaster.onBatchChecked(new BatchResult(BigInteger.valueOf(0x4000L), 4096, List.of())));
            assertThat(received, is(notNullValue()));

            final JsonNode message = objectMapper.readTree(received);
            assertThat(message.get("type").asText(), is(equalTo("batch")));
            assertThat(message.get("secretBase").asText(), is(equalTo("4000")));
            assertThat(message.get("checkedCount").asInt(), is(equalTo(4096)));
        } finally {
            broadcaster.close();
        }
    }

    @Test
    void onBatchChecked_batchWithHit_subscriberReceivesTheKeyMaterial() throws Exception {
        // arrange
        final String address = "tcp://127.0.0.1:" + freePort();
        final ZmqResultBroadcaster broadcaster = new ZmqResultBroadcaster(address);
        try (ZContext context = new ZContext()) {
            final ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect(address);
            subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
            subscriber.setReceiveTimeOut(RECEIVE_TIMEOUT_MILLIS);
            final Hit hit = new Hit(BigInteger.valueOf(73), "aabb", "1TestAddress", true, false);

            // act, assert
            final String received = receiveWhilePublishing(
                    subscriber, () -> broadcaster.onBatchChecked(new BatchResult(BigInteger.ONE, 1, List.of(hit))));
            assertThat(received, is(notNullValue()));

            final JsonNode message = objectMapper.readTree(received);
            assertThat(message.get("hits").get(0).get("privateKey").asText(), is(equalTo("49")));
        } finally {
            broadcaster.close();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="announceConfiguration">
    @Test
    void announceConfiguration_subscriberJoinsLate_stillReceivesItThroughRepublishing() throws Exception {
        // The publisher cannot detect a subscriber, so a configuration sent once would be lost to
        // anyone who was not already connected. Republishing is what makes it reachable at all.

        // arrange
        final String address = "tcp://127.0.0.1:" + freePort();
        final ZmqResultBroadcaster broadcaster = new ZmqResultBroadcaster(address);
        try (ZContext context = new ZContext()) {
            broadcaster.announceConfiguration(18, true);

            // act: only now does the subscriber appear
            final ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect(address);
            subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
            subscriber.setReceiveTimeOut(RECEIVE_TIMEOUT_MILLIS);

            final String received = receiveWhilePublishing(subscriber, broadcaster::republishConfiguration);

            // assert
            assertThat(received, is(notNullValue()));
            final JsonNode message = objectMapper.readTree(received);
            assertThat(message.get("type").asText(), is(equalTo("config")));
            assertThat(message.get("batchSizeInBits").asInt(), is(equalTo(18)));
        } finally {
            broadcaster.close();
        }
    }
    // </editor-fold>

    /**
     * Publishes repeatedly until the subscriber receives something or the budget runs out.
     *
     * @param subscriber the connected subscriber
     * @param publish    the action that publishes one message
     * @return the received message, or {@code null} if none arrived in time
     */
    private static String receiveWhilePublishing(ZMQ.Socket subscriber, Runnable publish) {
        final long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            publish.run();
            final byte[] received = subscriber.recv(0);
            if (received != null) {
                return new String(received, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
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
