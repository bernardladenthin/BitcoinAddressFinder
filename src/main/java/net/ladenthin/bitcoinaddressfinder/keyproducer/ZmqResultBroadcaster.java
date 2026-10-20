// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.ResultListener;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Publishes the outcome of every checked batch to ZeroMQ subscribers.
 *
 * <p>Every batch is reported, hit or not, so a subscriber sweeping a range can tell "checked,
 * nothing there" from "never checked".
 *
 * <p><b>The payload contains private keys</b>, and every subscriber receives every result —
 * including hits from ranges somebody else submitted.
 *
 * <h2>Why this needs its own address and its own socket</h2>
 * Secrets arrive on a {@code PULL} socket, which cannot send. Results therefore go out over a
 * separate {@code PUB} socket, and two socket types cannot share one endpoint — so unlike the
 * WebSocket, which serves both directions on one port, ZeroMQ needs a second address configured.
 *
 * <h2>What {@code PUB}/{@code SUB} cannot promise</h2>
 * <ul>
 *   <li>A message published before a subscriber has finished connecting is dropped — the "slow
 *       joiner" problem. There is no replay.
 *   <li>The socket cannot tell when someone subscribes, so there is no connect event to greet on.
 *       This is the concrete difference from the WebSocket, which catches up a client that arrived
 *       early. Here the grid configuration is instead <b>republished</b> alongside batch results
 *       (see {@link #republishConfiguration()}), which is the only way a late subscriber can ever
 *       receive it.
 *   <li>Delivery is best-effort throughout; the log remains the authoritative record of a hit.
 * </ul>
 */
@ToString
public class ZmqResultBroadcaster implements ResultListener, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZmqResultBroadcaster.class);

    /**
     * Number of batch messages between two republications of the grid configuration.
     *
     * <p>A compromise: often enough that a subscriber joining mid-run learns the grid within a few
     * seconds, rare enough that it does not measurably share the channel with the results.
     */
    private static final int CONFIG_REPUBLISH_EVERY_N_BATCHES = 64;

    /**
     * How long {@link #close()} may wait for messages the {@code PUB} socket has not written yet.
     *
     * <p>The last batch messages of a run are its completion signal; a linger of zero discarded them
     * whenever a subscriber was behind at shutdown. Bounded to the same 2 s the socket broadcaster
     * drains for, so a subscriber that has stopped reading delays the shutdown by at most that much.
     */
    private static final int LINGER_MILLIS = 2_000;

    private final BatchResultJsonFormatter formatter;

    @ToString.Exclude
    private final ZContext context;

    @ToString.Exclude
    private final ZMQ.Socket publisher;

    /** The greeting, once the producer configuration it describes is known. */
    @ToString.Exclude
    private volatile @Nullable String configurationMessage;

    /**
     * Counts batches so the configuration can be slipped in periodically.
     *
     * <p>Atomic because {@link #onBatchChecked} runs on every consumer worker thread at once: a
     * plain int would lose increments and could let two threads republish in the same round.
     */
    private final AtomicInteger batchesSinceConfigRepublish = new AtomicInteger();

    /**
     * Serialises every use of {@link #publisher}. A ZeroMQ socket is not thread-safe, and
     * {@link #onBatchChecked(BatchResult)} is called from all of the consumer's worker threads
     * while {@link #close()} comes from the shutdown path: unserialised, concurrent sends corrupted
     * jeromq's pipe state and left the sending consumer threads spinning forever.
     */
    @ToString.Exclude
    private final Object socketLock = new Object();

    /** Set by {@link #close()} under {@link #socketLock}; a send after it is skipped. */
    @ToString.Exclude
    private boolean closed;

    /** Set by the first send after {@link #close()}, so that send is logged once and not per batch. */
    @ToString.Exclude
    private final AtomicBoolean sendAfterCloseReported = new AtomicBoolean();

    /**
     * Creates a publisher bound to the given address.
     *
     * @param publishAddress the ZeroMQ address to bind the {@code PUB} socket to
     */
    public ZmqResultBroadcaster(String publishAddress) {
        this(publishAddress, new BatchResultJsonFormatter());
    }

    /**
     * Creates a publisher on an explicit formatter.
     *
     * @param publishAddress the ZeroMQ address to bind the {@code PUB} socket to
     * @param formatter      renders the wire messages
     */
    public ZmqResultBroadcaster(String publishAddress, BatchResultJsonFormatter formatter) {
        this(publishAddress, formatter, LINGER_MILLIS);
    }

    /**
     * Creates a publisher with an explicit linger.
     *
     * <p>Only for tests: a test that proves {@link #close()} keeps pending messages must not race the
     * shipped {@link #LINGER_MILLIS} against the speed of the machine it runs on.
     *
     * @param publishAddress the ZeroMQ address to bind the {@code PUB} socket to
     * @param formatter      renders the wire messages
     * @param lingerMillis   how long {@link #close()} may wait for messages not yet written
     */
    @VisibleForTesting
    ZmqResultBroadcaster(String publishAddress, BatchResultJsonFormatter formatter, int lingerMillis) {
        this.formatter = formatter;
        this.context = new ZContext();
        this.publisher = context.createSocket(SocketType.PUB);
        this.publisher.setLinger(lingerMillis);
        this.publisher.bind(publishAddress);
        LOGGER.info("Publishing results on {}", publishAddress);
    }

    /**
     * Records the grid configuration and publishes it once.
     *
     * <p>Subscribers connected at this moment receive it; later ones depend on
     * {@link #republishConfiguration()}, because a {@code PUB} socket has no connect event to react
     * to.
     *
     * @param batchSizeInBits             the grid size each base is expanded to
     * @param batchUsePrivateKeyIncrement whether one base yields a whole grid
     */
    public void announceConfiguration(int batchSizeInBits, boolean batchUsePrivateKeyIncrement) {
        try {
            configurationMessage = formatter.formatConfiguration(batchSizeInBits, batchUsePrivateKeyIncrement);
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not serialise the grid configuration; subscribers will not receive it.", e);
            return;
        }
        republishConfiguration();
    }

    /**
     * Publishes the recorded grid configuration again, for subscribers that joined since.
     *
     * <p>Does nothing while no configuration is known.
     */
    public void republishConfiguration() {
        final String message = configurationMessage;
        if (message != null) {
            send(message);
        }
    }

    @Override
    public void onBatchChecked(BatchResult batchResult) {
        final String message;
        try {
            message = formatter.formatBatch(batchResult);
        } catch (JsonProcessingException e) {
            // Never let a serialisation problem reach the consumer thread that is draining keys.
            LOGGER.error("Could not serialise a batch result; skipping this message.", e);
            return;
        }
        send(message);

        if (batchesSinceConfigRepublish.incrementAndGet() >= CONFIG_REPUBLISH_EVERY_N_BATCHES) {
            batchesSinceConfigRepublish.set(0);
            republishConfiguration();
        }
    }

    /**
     * Sends one message, absorbing any transport failure.
     *
     * @param message the message to publish
     */
    private void send(String message) {
        final byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        try {
            synchronized (socketLock) {
                if (closed) {
                    reportSendAfterClose();
                    return;
                }
                publisher.send(bytes, ZMQ.DONTWAIT);
            }
        } catch (RuntimeException e) {
            // Called from the consumer's worker threads: a transport failure must not disturb the scan.
            LOGGER.warn("Publishing a result failed; continuing.", e);
        }
    }

    /**
     * Logs, once, that a message arrived after {@link #close()}: it is not published, and a subscriber
     * waiting for the batch it reports would otherwise never learn why it did not arrive.
     */
    private void reportSendAfterClose() {
        if (sendAfterCloseReported.compareAndSet(false, true)) {
            LOGGER.warn("The result publisher is already closed; results reported from now on are not published.");
        }
    }

    /**
     * Closes the socket and the context. The context's close waits up to the socket's linger for a
     * subscriber to take what is queued, and it runs under the socket lock, so a consumer thread that
     * publishes at the same time (the finder closes the broadcasters after the consumer, but a consumer
     * whose drain timed out may still be running) waits as long; it then sees the broadcaster closed,
     * logs that once and returns.
     */
    @Override
    public void close() {
        synchronized (socketLock) {
            if (closed) {
                return;
            }
            closed = true;
            publisher.close();
            context.close();
        }
    }
}
