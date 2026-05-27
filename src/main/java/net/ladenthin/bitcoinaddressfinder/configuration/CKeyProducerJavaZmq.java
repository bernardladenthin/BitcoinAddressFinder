// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Configuration for the ZeroMQ-based key producer.
 */
public class CKeyProducerJavaZmq extends CKeyProducerJavaReceiver {

    /** Creates a new {@link CKeyProducerJavaZmq}. */
    public CKeyProducerJavaZmq() {
    }

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
    public int timeout = 1000;
}
