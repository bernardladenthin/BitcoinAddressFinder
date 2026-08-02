// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaWebSocket;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.core.Startable;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Key producer that receives secrets through a WebSocket server.
 *
 * <p>The server itself lives in {@link WebSocketEndpoint}, which this producer shares with the
 * result broadcaster: one port carries the ranges in and the outcomes back, so a browser page needs
 * a single connection to act as the whole user interface.
 *
 * <p>The endpoint is not started by the constructor; callers must invoke {@link #start()}
 * afterwards. That keeps a partially-constructed instance from being published to the server's
 * callback threads, which read {@code shouldStop} and call {@code addSecret}.
 */
@ToString(callSuper = true)
public class KeyProducerJavaWebSocket extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaWebSocket>
        implements Startable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyProducerJavaWebSocket.class);

    @ToString.Exclude
    private final WebSocketEndpoint endpoint;

    /**
     * Creates a new WebSocket-based key producer. The server is NOT started here; the caller must
     * invoke {@link #start()} afterwards.
     *
     * @param config      the WebSocket configuration
     * @param keyUtility  cryptographic helper
     * @param bitHelper   bit/batch-size helper (unused but kept for symmetry)
     */
    public KeyProducerJavaWebSocket(CKeyProducerJavaWebSocket config, KeyUtility keyUtility, BitHelper bitHelper) {
        this(config, keyUtility, bitHelper, new WebSocketEndpoint(config.getPort()));
    }

    /**
     * Creates a new WebSocket-based key producer on an explicit endpoint.
     *
     * @param config      the WebSocket configuration
     * @param keyUtility  cryptographic helper
     * @param bitHelper   bit/batch-size helper (unused but kept for symmetry)
     * @param endpoint    the server shared with the result broadcaster
     */
    public KeyProducerJavaWebSocket(
            CKeyProducerJavaWebSocket config, KeyUtility keyUtility, BitHelper bitHelper, WebSocketEndpoint endpoint) {
        super(config, keyUtility);
        this.endpoint = endpoint;
    }

    /**
     * Returns the shared server, so a result broadcaster can send on the same port.
     *
     * @return the endpoint backing this producer
     */
    public WebSocketEndpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public void start() {
        endpoint.setMessageHandler(message -> {
            if (shouldStop) {
                return;
            }
            if (message.length == OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES) {
                addSecret(message);
            } else {
                LOGGER.warn("Invalid message length: {}", message.length);
            }
        });
        endpoint.start();
    }

    @Override
    protected int getReadTimeout() {
        return cKeyProducerJava.timeoutMillis;
    }

    @Override
    public void interrupt() {
        signalShutdown(); // wakes any caller blocked in createSecrets()
        endpoint.stop();
    }
}
