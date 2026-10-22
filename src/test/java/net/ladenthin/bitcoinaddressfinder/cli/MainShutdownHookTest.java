// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import net.ladenthin.bitcoinaddressfinder.configuration.CCommand;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaZmq;
import org.junit.jupiter.api.Test;

/**
 * How long the JVM shutdown hook of {@link Main} waits for the run to end before the JVM halts.
 *
 * <p>The hook used to wait a flat 30 s, while a {@code Find} run interrupted by a {@code Ctrl+C} lets
 * the consumer drain the keys queue for up to {@code awaitQueueEmptySeconds} (60 s by default) and then
 * closes the result broadcasters, which drain for 2 s each: the JVM halted in the middle of the drain,
 * the remaining batches stayed unchecked and the reports queued for the clients never left. The values
 * below are the shipped ones, pinned on purpose: a change of any of them changes the longest possible
 * shutdown and should be a deliberate one.
 */
public class MainShutdownHookTest {

    /** The shipped margin for everything the main thread does besides the finder's own shutdown. */
    private static final long MARGIN_SECONDS = 30L;

    /** The shipped default of {@code CConsumerJava.awaitQueueEmptySeconds}. */
    private static final long DEFAULT_QUEUE_DRAIN_SECONDS = 60L;

    /** {@code SocketResultBroadcaster.CLOSE_DRAIN_MILLIS} (2000 ms) in whole seconds. */
    private static final long SOCKET_CLOSE_DRAIN_SECONDS = 2L;

    /** {@code ZmqResultBroadcaster.LINGER_MILLIS} (2000 ms) in whole seconds. */
    private static final long ZMQ_LINGER_SECONDS = 2L;

    /**
     * The headline case of the defect: a consumer with the default queue drain and one broadcaster of
     * each bounded kind. The old hook waited the margin alone and halted the JVM up to 64 s before the
     * drain could have ended.
     */
    @Test
    public void shutdownHookTimeoutSeconds_findWithDrainSocketAndZmqBroadcasters_isMarginPlusDrainPlusBothBounds() {
        // arrange
        final CConfiguration configuration = findConfiguration(finderWithDrainAndBothBroadcasters());

        // act
        final long timeoutSeconds = Main.shutdownHookTimeoutSeconds(configuration);

        // assert
        assertThat(
                timeoutSeconds,
                is(equalTo(MARGIN_SECONDS
                        + DEFAULT_QUEUE_DRAIN_SECONDS
                        + SOCKET_CLOSE_DRAIN_SECONDS
                        + ZMQ_LINGER_SECONDS)));
    }

    /**
     * Only a {@code Find} run drains a queue and closes broadcasters; every other command has nothing
     * to wait for beyond the margin, even when the file carries a finder section too.
     */
    @Test
    public void shutdownHookTimeoutSeconds_nonFindCommand_isTheMarginAlone() {
        // arrange
        final CConfiguration configuration = findConfiguration(finderWithDrainAndBothBroadcasters());
        configuration.command = CCommand.AddressFilesToLMDB;

        // act
        final long timeoutSeconds = Main.shutdownHookTimeoutSeconds(configuration);

        // assert
        assertThat(timeoutSeconds, is(equalTo(MARGIN_SECONDS)));
    }

    /**
     * The plain case every CPU-only configuration is in: the default consumer and no broadcaster. The
     * old hook cut the default 60 s drain off at 30 s.
     */
    @Test
    public void shutdownHookTimeoutSeconds_findWithDefaultConsumerAndNoBroadcaster_isMarginPlusQueueDrain() {
        // arrange
        final CFinder finder = new CFinder();
        finder.consumerJava = new CConsumerJava();
        final CConfiguration configuration = findConfiguration(finder);

        // act
        final long timeoutSeconds = Main.shutdownHookTimeoutSeconds(configuration);

        // assert
        assertThat(timeoutSeconds, is(equalTo(MARGIN_SECONDS + DEFAULT_QUEUE_DRAIN_SECONDS)));
    }

    /**
     * {@code awaitQueueEmptySeconds} is a plain {@code long} nobody validates, and "never give up" may
     * be spelled as a value near its maximum; the margin added to it must not wrap into a negative
     * wait, which would halt the JVM at once instead of never.
     */
    @Test
    public void shutdownHookTimeoutSeconds_awaitQueueEmptySecondsNearTheMaximum_saturatesInsteadOfWrapping() {
        // arrange
        final CFinder finder = new CFinder();
        final CConsumerJava consumer = new CConsumerJava();
        consumer.awaitQueueEmptySeconds = Long.MAX_VALUE - 1L;
        finder.consumerJava = consumer;
        final CConfiguration configuration = findConfiguration(finder);

        // act
        final long timeoutSeconds = Main.shutdownHookTimeoutSeconds(configuration);

        // assert
        assertThat(timeoutSeconds, is(equalTo(Long.MAX_VALUE)));
    }

    /**
     * The hook is registered before {@code run()} rejects a {@code Find} without a finder section, so
     * the computation must not fail on it; there is nothing to drain, so the margin is the whole wait.
     */
    @Test
    public void shutdownHookTimeoutSeconds_findWithoutFinderConfiguration_isTheMarginAlone() {
        // arrange
        final CConfiguration configuration = new CConfiguration();
        configuration.command = CCommand.Find;
        configuration.finder = null;

        // act
        final long timeoutSeconds = Main.shutdownHookTimeoutSeconds(configuration);

        // assert
        assertThat(timeoutSeconds, is(equalTo(MARGIN_SECONDS)));
    }

    private static CConfiguration findConfiguration(CFinder finder) {
        final CConfiguration configuration = new CConfiguration();
        configuration.command = CCommand.Find;
        configuration.finder = finder;
        return configuration;
    }

    private static CFinder finderWithDrainAndBothBroadcasters() {
        final CFinder finder = new CFinder();
        final CConsumerJava consumerJava = new CConsumerJava();
        consumerJava.awaitQueueEmptySeconds = DEFAULT_QUEUE_DRAIN_SECONDS;
        finder.consumerJava = consumerJava;
        final CKeyProducerJavaSocket socket = new CKeyProducerJavaSocket();
        socket.keyProducerId = "socket";
        socket.broadcastResults = true;
        finder.keyProducerJavaSocket.add(socket);
        final CKeyProducerJavaZmq zmq = new CKeyProducerJavaZmq();
        zmq.keyProducerId = "zmq";
        zmq.broadcastResults = true;
        finder.keyProducerJavaZmq.add(zmq);
        return finder;
    }
}
