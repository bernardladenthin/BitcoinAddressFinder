// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.Hit;
import nl.altindag.log.LogCaptor;
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
 *
 * <p>The subscriber is created through {@link #connectSubscriber(ZContext, String)}, which caps the
 * ZMTP handshake at {@link #HANDSHAKE_IVL_MILLIS}. Without that cap the tests failed about once in
 * twenty runs (fresh Maven forks on Linux; the ubuntu CI jobs went red the same way), always with
 * the full {@link #AWAIT_MILLIS} elapsed and nothing received. See the helper for what stalls and
 * why the cap is the fix; the deterministic reproduction and the full write-up are in
 * {@link JeromqPollerHandOffRaceTest}, which goes red once a jeromq build fixes the race — that is
 * the moment to delete the cap.
 */
class ZmqResultBroadcasterTest {

    /** Upper bound for the retry loop; exceeding it is a failure, not a slow machine. */
    private static final int AWAIT_MILLIS = 15_000;

    /** How long a single receive attempt waits before the next publish. */
    private static final int RECEIVE_TIMEOUT_MILLIS = 100;

    /**
     * Maximum time the subscriber allows the ZMTP handshake before it drops the connection and
     * reconnects; jeromq's default is 30 s, twice {@link #AWAIT_MILLIS}.
     */
    private static final int HANDSHAKE_IVL_MILLIS = 1_000;

    /**
     * Batches of the burst in {@link #close_whileTheSubscriberIsBehind_subscriberStillReceivesTheLastBatch()};
     * below the publisher's send high-water mark of 1000, so none is dropped before {@code close()}.
     */
    private static final int BURST_BATCHES = 800;

    /** Hits per burst batch: the burst (about 20 MB) must exceed what the TCP buffers can hold. */
    private static final int BURST_HITS_PER_BATCH = 150;

    /** Kernel receive buffer of the lagging subscriber; small, so TCP pushes back early. */
    private static final int SMALL_RECEIVE_BUFFER_BYTES = 4096;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // <editor-fold defaultstate="collapsed" desc="onBatchChecked">
    @Test
    void onBatchChecked_batchWithoutHit_subscriberReceivesTheCheckedRange() throws Exception {
        // arrange
        final String address = "tcp://127.0.0.1:" + freePort();
        final ZmqResultBroadcaster broadcaster = new ZmqResultBroadcaster(address);
        try (ZContext context = new ZContext()) {
            final ZMQ.Socket subscriber = connectSubscriber(context, address);

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
    void onBatchChecked_calledFromSeveralConsumerThreads_everyCallReturns() throws Exception {
        // arrange: ConsumerJava notifies its listeners from consumerJava.threads worker threads,
        // and a ZeroMQ socket is not thread-safe. Unserialised, concurrent sends corrupted jeromq's
        // pipe state (IndexOutOfBoundsException inside the send path) and left the senders spinning
        // forever, i.e. the consumer threads stopped checking keys.
        final String address = "tcp://127.0.0.1:" + freePort();
        final ZmqResultBroadcaster broadcaster = new ZmqResultBroadcaster(address);
        final int threads = 8;
        final int callsPerThread = 200;
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        try (ZContext context = new ZContext()) {
            final ZMQ.Socket subscriber = connectSubscriber(context, address);
            assertThat(
                    receiveWhilePublishing(
                            subscriber,
                            () -> broadcaster.onBatchChecked(new BatchResult(BigInteger.ONE, 1, List.of()))),
                    is(notNullValue()));
            final CyclicBarrier start = new CyclicBarrier(threads);

            // act
            for (int t = 0; t < threads; t++) {
                executor.execute(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < callsPerThread; i++) {
                        broadcaster.onBatchChecked(new BatchResult(BigInteger.valueOf(i), 1, List.of()));
                    }
                });
            }
            executor.shutdown();

            // assert
            assertThat(executor.awaitTermination(AWAIT_MILLIS, TimeUnit.MILLISECONDS), is(true));
        } finally {
            executor.shutdownNow();
            broadcaster.close();
        }
    }

    @Test
    void onBatchChecked_batchWithHit_subscriberReceivesTheKeyMaterial() throws Exception {
        // arrange
        final String address = "tcp://127.0.0.1:" + freePort();
        final ZmqResultBroadcaster broadcaster = new ZmqResultBroadcaster(address);
        try (ZContext context = new ZContext()) {
            final ZMQ.Socket subscriber = connectSubscriber(context, address);
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

    @Test
    void close_whileTheSubscriberIsBehind_subscriberStillReceivesTheLastBatch() throws Exception {
        // The last batch messages are the completion signal of a run. With a linger of zero,
        // closing the socket discarded whatever the PUB socket had not written yet, so a subscriber
        // that was behind missed exactly the end of the run. The socket broadcaster drains for up
        // to 2 s (F130); the ZeroMQ one now lingers as long.
        //
        // To make "behind" deterministic, the subscriber does not read during the burst and holds
        // at most one message (receive high-water mark 1, small kernel buffer): its I/O thread stops
        // reading, TCP pushes back, and the burst stays queued at the publisher. close() then runs
        // on another thread while this one drains.

        // arrange
        final String address = "tcp://127.0.0.1:" + freePort();
        final ZmqResultBroadcaster broadcaster = new ZmqResultBroadcaster(address);
        final List<Hit> manyHits = new ArrayList<>();
        for (int i = 0; i < BURST_HITS_PER_BATCH; i++) {
            manyHits.add(new Hit(BigInteger.valueOf(i), "aabb".repeat(16), "1TestAddress" + i, true, false));
        }
        final ExecutorService closer = Executors.newSingleThreadExecutor();
        try (ZContext context = new ZContext()) {
            final ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.setHandshakeIvl(HANDSHAKE_IVL_MILLIS);
            subscriber.setRcvHWM(1);
            subscriber.setReceiveBufferSize(SMALL_RECEIVE_BUFFER_BYTES);
            subscriber.connect(address);
            subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
            subscriber.setReceiveTimeOut(RECEIVE_TIMEOUT_MILLIS);
            assertThat(
                    receiveWhilePublishing(
                            subscriber,
                            () -> broadcaster.onBatchChecked(new BatchResult(BigInteger.ZERO, 1, List.of()))),
                    is(notNullValue()));
            for (long base = 1; base <= BURST_BATCHES; base++) {
                broadcaster.onBatchChecked(new BatchResult(BigInteger.valueOf(base), 1, manyHits));
            }

            // act
            final Future<?> closed = closer.submit(broadcaster::close);

            // assert
            final String lastBaseHex = BigInteger.valueOf(BURST_BATCHES).toString(16);
            boolean lastSeen = false;
            final long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
            while (!lastSeen && System.currentTimeMillis() < deadline) {
                final byte[] received = subscriber.recv(0);
                if (received == null) {
                    continue;
                }
                final JsonNode message = objectMapper.readTree(new String(received, StandardCharsets.UTF_8));
                lastSeen = lastBaseHex.equals(message.path("secretBase").asText());
            }
            assertThat(lastSeen, is(true));
            closed.get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        } finally {
            closer.shutdownNow();
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
            final ZMQ.Socket subscriber = connectSubscriber(context, address);

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
    /**
     * A batch reported after {@link ZmqResultBroadcaster#close()} cannot be published any more. That
     * must be visible in the log, once, instead of the report disappearing without a word.
     */
    @Test
    void onBatchChecked_afterClose_warnsOnceThatTheResultIsNotPublished() {
        // arrange
        ZmqResultBroadcaster broadcaster = new ZmqResultBroadcaster("inproc://zmq-result-broadcaster-closed");
        broadcaster.close();
        try (LogCaptor logCaptor = LogCaptor.forClass(ZmqResultBroadcaster.class)) {
            // act
            broadcaster.onBatchChecked(new BatchResult(BigInteger.ONE, 1, List.of()));
            broadcaster.onBatchChecked(new BatchResult(BigInteger.TWO, 1, List.of()));

            // assert
            assertThat(logCaptor.getWarnLogs(), hasSize(1));
            assertThat(logCaptor.getWarnLogs().get(0), containsString("already closed"));
        }
    }

    // </editor-fold>

    /**
     * Creates a subscriber to everything on the given address, with a short handshake cap.
     *
     * <p>The cap works around a race in jeromq 0.6.0's poller: when a connect completes, the
     * connecter retires its poll handle and the new stream engine registers a handle for the same
     * channel. If both land in the same registration pass, the poller finds the old, just-cancelled
     * selector key for the new handle and registers nothing — the engine never sends its greeting,
     * the ZMTP handshake never happens, and the publisher, seeing no subscription, drops every
     * message. Measured on this pattern: roughly one connection in a hundred, with the subscriber's
     * engine handle in the poller's table but without a selector key. jeromq only recovers through
     * its handshake timer, which defaults to 30 s and thus outlasts {@link #AWAIT_MILLIS}. Capping
     * it at {@link #HANDSHAKE_IVL_MILLIS} makes a stalled connection drop and reconnect in about a
     * second; the option must be set before {@code connect}, later calls do not reach the engine.
     *
     * @param context the context that owns the socket (closing it closes the socket)
     * @param address the publisher's address
     * @return a connected, subscribed socket with {@link #RECEIVE_TIMEOUT_MILLIS} receive timeout
     */
    private static ZMQ.Socket connectSubscriber(ZContext context, String address) {
        final ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
        subscriber.setHandshakeIvl(HANDSHAKE_IVL_MILLIS);
        subscriber.connect(address);
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
        subscriber.setReceiveTimeOut(RECEIVE_TIMEOUT_MILLIS);
        return subscriber;
    }

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
