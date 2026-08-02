// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Configuration for the ZeroMQ-based key producer.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CKeyProducerJavaZmq extends CKeyProducerJavaReceiver {

    /** Creates a new {@link CKeyProducerJavaZmq}. */
    public CKeyProducerJavaZmq() {}

    /**
     * Whether the ZMQ socket binds to or connects to the configured address.
     */
    public enum Mode {
        /** Connect to a remote ZMQ endpoint. */
        CONNECT,
        /** Bind to the configured local address. */
        BIND
    }

    /** The ZMQ address, e.g., tcp://localhost:5555 */
    public String address = "tcp://localhost:5555";

    /** Whether this side binds to the address or connects */
    public Mode mode = Mode.BIND;

    /**
     * Send the outcome of every checked batch to ZeroMQ subscribers.
     *
     * <p><b>The messages contain private keys</b>, and every subscriber receives every result —
     * including hits from ranges somebody else submitted. Off by default.
     */
    public boolean broadcastResults = false;

    /**
     * Address the result publisher binds to, used only when {@link #broadcastResults} is set.
     *
     * <p>A second address is unavoidable: results go out over a {@code PUB} socket while secrets
     * arrive on a {@code PULL} socket, and two socket types cannot share one endpoint. Unlike the
     * WebSocket, which serves both directions on one port, ZeroMQ needs both configured.
     *
     * <p>Note for subscribers: {@code PUB}/{@code SUB} drops what is published before a subscriber
     * has finished connecting (the "slow joiner" problem), and the socket cannot tell when someone
     * subscribes. The grid configuration is therefore republished periodically rather than on
     * connect - see the publisher for the interval.
     */
    public String publishAddress = "tcp://127.0.0.1:5558";

    /**
     * Timeout for receiving secrets, in milliseconds.
     *
     * <p>This timeout is applied consistently to:
     * <ul>
     *   <li>the underlying ZMQ socket receive operation ({@code RCVTIMEO})</li>
     *   <li>the internal secret queue polling inside {@code createSecrets()}</li>
     * </ul>
     *
     * <p>Semantics:
     * <ul>
     *   <li>A positive value: the maximum time (in ms) to wait for each receive
     *       operation. A timed-out wait causes
     *       {@code createSecrets()} to throw
     *       {@code NoMoreSecretsAvailableException}.</li>
     *   <li>A negative value (typically {@code -1}): block indefinitely until a
     *       message arrives or the producer is interrupted via
     *       {@code interrupt()}. Matches ZMQ's "infinite wait"
     *       {@code RCVTIMEO} semantics.</li>
     *   <li>{@code 0}: return immediately if no message is queued (primarily useful
     *       for testing).</li>
     * </ul>
     *
     * <p>This value maps directly to the ZMQ socket option {@code RCVTIMEO}.
     *
     * <p>Default: {@code 1000} ms
     */
    public int timeoutMillis = 1000;
}
