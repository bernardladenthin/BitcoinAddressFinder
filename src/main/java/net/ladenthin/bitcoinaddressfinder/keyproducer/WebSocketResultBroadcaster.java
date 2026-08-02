// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.ResultListener;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends the outcome of every checked batch to all connected WebSocket clients.
 *
 * <p>Every batch is reported, hit or not. That is what lets a client sweeping a range tell "checked,
 * nothing there" from "never checked" — without it, a range dropped when the process stopped looks
 * exactly like a clean one, and an automated sweep would move past ground it never covered.
 *
 * <p><b>The payload contains private keys.</b> Delivery is a broadcast: everyone connected receives
 * every hit, including ones from ranges somebody else submitted. Expose this port accordingly.
 *
 * <p>Delivery is best-effort. A client that is not connected at the moment a batch completes misses
 * that message and it is not replayed; the log remains the authoritative record of a hit.
 */
@ToString
public class WebSocketResultBroadcaster implements ResultListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketResultBroadcaster.class);

    @ToString.Exclude
    private final WebSocketEndpoint endpoint;

    private final BatchResultJsonFormatter formatter;

    /** The greeting, once the producer configuration it describes is known. */
    @ToString.Exclude
    private volatile @Nullable String configurationMessage;

    /**
     * Creates a broadcaster sending on the given endpoint.
     *
     * @param endpoint the server shared with the key producer
     */
    public WebSocketResultBroadcaster(WebSocketEndpoint endpoint) {
        this(endpoint, new BatchResultJsonFormatter());
    }

    /**
     * Creates a broadcaster on an explicit formatter.
     *
     * @param endpoint  the server shared with the key producer
     * @param formatter renders the wire messages
     */
    public WebSocketResultBroadcaster(WebSocketEndpoint endpoint, BatchResultJsonFormatter formatter) {
        this.endpoint = endpoint;
        this.formatter = formatter;
        endpoint.setGreetingSupplier(() -> configurationMessage);
    }

    /**
     * Publishes the grid configuration a client needs in order to send usable ranges.
     *
     * <p>Called once the producers are configured, which happens after the server has already begun
     * accepting connections. Clients that connected earlier are caught up immediately; later ones
     * receive it on connect.
     *
     * <p>Without this a client cannot know the step it must advance by. The server aligns every
     * incoming base down to a multiple of {@code 2^batchSizeInBits}, so a client stepping by less
     * would resubmit the same block over and over while leaving the rest of its range untouched —
     * silently, because nothing rejects a misaligned base.
     *
     * @param batchSizeInBits              the grid size each base is expanded to
     * @param batchUsePrivateKeyIncrement  whether one base yields a whole grid; when {@code false}
     *                                     the producer wants one secret per candidate instead
     */
    public void announceConfiguration(int batchSizeInBits, boolean batchUsePrivateKeyIncrement) {
        try {
            configurationMessage = formatter.formatConfiguration(batchSizeInBits, batchUsePrivateKeyIncrement);
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not serialise the grid configuration; clients will not receive it.", e);
            return;
        }
        endpoint.greetAll();
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
        endpoint.broadcast(message);
    }
}
