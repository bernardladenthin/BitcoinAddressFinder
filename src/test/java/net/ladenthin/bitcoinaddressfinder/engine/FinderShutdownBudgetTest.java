// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.Objects;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * How long a finder's shutdown can take after {@link Finder#interrupt()} has returned: the consumer's
 * queue drain plus the close bound of every configured result broadcaster.
 *
 * <p>The JVM shutdown hook of {@code Main} waits this long on top of its own margin. Before it did, the
 * hook waited a flat 30 s against a 60 s drain and two 2 s broadcaster closes, and the JVM halted with
 * batches unchecked and reports unsent. The broadcaster bounds below are the shipped
 * {@code SocketResultBroadcaster.CLOSE_DRAIN_MILLIS} and {@code ZmqResultBroadcaster.LINGER_MILLIS}
 * (2000 ms each) in whole seconds, pinned on purpose.
 */
public class FinderShutdownBudgetTest {

    /** A queue drain that is not the default, so the budget provably reads the field and not a constant. */
    private static final long QUEUE_DRAIN_SECONDS = 7L;

    /** {@code SocketResultBroadcaster.CLOSE_DRAIN_MILLIS} (2000 ms) in whole seconds. */
    private static final long SOCKET_CLOSE_DRAIN_SECONDS = 2L;

    /** {@code ZmqResultBroadcaster.LINGER_MILLIS} (2000 ms) in whole seconds. */
    private static final long ZMQ_LINGER_SECONDS = 2L;

    // <editor-fold defaultstate="collapsed" desc="shutdownBudgetSeconds">
    /** Without a broadcaster the drain is the whole budget; the old hook did not know the field at all. */
    @Test
    public void shutdownBudgetSeconds_consumerWithoutBroadcaster_isAwaitQueueEmptySeconds() {
        // arrange
        final CFinder finder = finderWithQueueDrain();

        // act
        final long budgetSeconds = Finder.shutdownBudgetSeconds(finder);

        // assert
        assertThat(budgetSeconds, is(equalTo(QUEUE_DRAIN_SECONDS)));
    }

    /** A socket result server drains its client queues for {@code CLOSE_DRAIN_MILLIS} when it closes. */
    @Test
    public void shutdownBudgetSeconds_socketKeyProducerBroadcasts_addsItsCloseDrain() {
        // arrange
        final CFinder finder = finderWithQueueDrain();
        finder.keyProducerJavaSocket.add(socketKeyProducer(true));

        // act
        final long budgetSeconds = Finder.shutdownBudgetSeconds(finder);

        // assert
        assertThat(budgetSeconds, is(equalTo(QUEUE_DRAIN_SECONDS + SOCKET_CLOSE_DRAIN_SECONDS)));
    }

    /** The broadcasters close one after the other, so every one of them adds its own bound. */
    @Test
    public void shutdownBudgetSeconds_twoSocketKeyProducersBroadcast_addsTheCloseDrainOfEach() {
        // arrange
        final CFinder finder = finderWithQueueDrain();
        finder.keyProducerJavaSocket.add(socketKeyProducer(true));
        finder.keyProducerJavaSocket.add(socketKeyProducer(true));

        // act
        final long budgetSeconds = Finder.shutdownBudgetSeconds(finder);

        // assert
        assertThat(
                budgetSeconds,
                is(equalTo(QUEUE_DRAIN_SECONDS + SOCKET_CLOSE_DRAIN_SECONDS + SOCKET_CLOSE_DRAIN_SECONDS)));
    }

    /** A ZeroMQ publisher's close waits up to its linger for messages not yet written. */
    @Test
    public void shutdownBudgetSeconds_zmqKeyProducerBroadcasts_addsItsLinger() {
        // arrange
        final CFinder finder = finderWithQueueDrain();
        finder.keyProducerJavaZmq.add(zmqKeyProducer(true));

        // act
        final long budgetSeconds = Finder.shutdownBudgetSeconds(finder);

        // assert
        assertThat(budgetSeconds, is(equalTo(QUEUE_DRAIN_SECONDS + ZMQ_LINGER_SECONDS)));
    }

    /**
     * A WebSocket broadcaster stops the key producer's server, whose stop joins the selector thread
     * without a timeout: there is no bound to add, so it deliberately counts nothing.
     */
    @Test
    public void shutdownBudgetSeconds_webSocketKeyProducerBroadcasts_addsNothing() {
        // arrange
        final CFinder finder = finderWithQueueDrain();
        finder.keyProducerJavaWebSocket.add(webSocketKeyProducer(true));

        // act
        final long budgetSeconds = Finder.shutdownBudgetSeconds(finder);

        // assert
        assertThat(budgetSeconds, is(equalTo(QUEUE_DRAIN_SECONDS)));
    }

    /** A key producer that does not broadcast starts no broadcaster, so there is nothing to close. */
    @Test
    public void shutdownBudgetSeconds_keyProducersWithoutBroadcastResults_addNothing() {
        // arrange
        final CFinder finder = finderWithQueueDrain();
        finder.keyProducerJavaSocket.add(socketKeyProducer(false));
        finder.keyProducerJavaZmq.add(zmqKeyProducer(false));
        finder.keyProducerJavaWebSocket.add(webSocketKeyProducer(false));

        // act
        final long budgetSeconds = Finder.shutdownBudgetSeconds(finder);

        // assert
        assertThat(budgetSeconds, is(equalTo(QUEUE_DRAIN_SECONDS)));
    }

    /** Without a consumer section nothing is drained; the broadcaster bounds are the whole budget. */
    @Test
    public void shutdownBudgetSeconds_consumerJavaNull_isTheBroadcasterBoundsAlone() {
        // arrange
        final CFinder finder = new CFinder();
        finder.consumerJava = null;
        finder.keyProducerJavaSocket.add(socketKeyProducer(true));
        finder.keyProducerJavaZmq.add(zmqKeyProducer(true));

        // act
        final long budgetSeconds = Finder.shutdownBudgetSeconds(finder);

        // assert
        assertThat(budgetSeconds, is(equalTo(SOCKET_CLOSE_DRAIN_SECONDS + ZMQ_LINGER_SECONDS)));
    }

    /**
     * Nothing validates {@code awaitQueueEmptySeconds}, and a negative value makes the consumer's own
     * wait return at once; counted as is, it would shorten the hook's wait below the margin for the
     * rest of the shutdown (the database close), so it counts as no drain.
     */
    @Test
    public void shutdownBudgetSeconds_negativeAwaitQueueEmptySeconds_countsAsNoDrain() {
        // arrange
        final CFinder finder = finderWithQueueDrain();
        Objects.requireNonNull(finder.consumerJava).awaitQueueEmptySeconds = -5L;
        finder.keyProducerJavaSocket.add(socketKeyProducer(true));

        // act
        final long budgetSeconds = Finder.shutdownBudgetSeconds(finder);

        // assert
        assertThat(budgetSeconds, is(equalTo(SOCKET_CLOSE_DRAIN_SECONDS)));
    }

    /**
     * "Never give up" may be spelled as an {@code awaitQueueEmptySeconds} near the maximum; the
     * broadcaster bounds added to it must not wrap into a negative budget, which the hook would turn
     * into an immediate halt.
     */
    @Test
    public void shutdownBudgetSeconds_awaitQueueEmptySecondsNearTheMaximum_saturatesInsteadOfWrapping() {
        // arrange
        final CFinder finder = finderWithQueueDrain();
        Objects.requireNonNull(finder.consumerJava).awaitQueueEmptySeconds = Long.MAX_VALUE - 1L;
        finder.keyProducerJavaSocket.add(socketKeyProducer(true));

        // act
        final long budgetSeconds = Finder.shutdownBudgetSeconds(finder);

        // assert
        assertThat(budgetSeconds, is(equalTo(Long.MAX_VALUE)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="wholeSecondsRoundedUp">
    /**
     * The bounds are milliseconds and the hook waits whole seconds; a fraction of a second rounded
     * down would let the hook halt the JVM inside that fraction, so the rounding goes up.
     */
    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "999, 1", "1000, 1", "1001, 2", "2000, 2"})
    public void wholeSecondsRoundedUp_millisGiven_roundsUpToTheNextWholeSecond(long millis, long expectedSeconds) {
        // act
        final long seconds = Finder.wholeSecondsRoundedUp(millis);

        // assert
        assertThat(seconds, is(equalTo(expectedSeconds)));
    }
    // </editor-fold>

    private static CFinder finderWithQueueDrain() {
        final CFinder finder = new CFinder();
        final CConsumerJava consumerJava = new CConsumerJava();
        consumerJava.awaitQueueEmptySeconds = QUEUE_DRAIN_SECONDS;
        finder.consumerJava = consumerJava;
        return finder;
    }

    private static CKeyProducerJavaSocket socketKeyProducer(boolean broadcastResults) {
        final CKeyProducerJavaSocket socket = new CKeyProducerJavaSocket();
        socket.keyProducerId = "socket";
        socket.broadcastResults = broadcastResults;
        return socket;
    }

    private static CKeyProducerJavaZmq zmqKeyProducer(boolean broadcastResults) {
        final CKeyProducerJavaZmq zmq = new CKeyProducerJavaZmq();
        zmq.keyProducerId = "zmq";
        zmq.broadcastResults = broadcastResults;
        return zmq;
    }

    private static CKeyProducerJavaWebSocket webSocketKeyProducer(boolean broadcastResults) {
        final CKeyProducerJavaWebSocket webSocket = new CKeyProducerJavaWebSocket();
        webSocket.keyProducerId = "webSocket";
        webSocket.broadcastResults = broadcastResults;
        return webSocket;
    }
}
