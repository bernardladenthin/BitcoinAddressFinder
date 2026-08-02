// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
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
     * Linger of zero, so closing the context can never stall on undelivered messages. Results are
     * best-effort by nature; blocking a shutdown to flush them would be the wrong trade.
     */
    private static final int LINGER_MILLIS = 0;

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
        this.formatter = formatter;
        this.context = new ZContext();
        this.publisher = context.createSocket(SocketType.PUB);
        this.publisher.setLinger(LINGER_MILLIS);
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
        try {
            publisher.send(message.getBytes(StandardCharsets.UTF_8), ZMQ.DONTWAIT);
        } catch (RuntimeException e) {
            // Called from the consumer's worker threads: a transport failure must not disturb the scan.
            LOGGER.warn("Publishing a result failed; continuing.", e);
        }
    }

    @Override
    public void close() {
        publisher.close();
        context.close();
    }
}
