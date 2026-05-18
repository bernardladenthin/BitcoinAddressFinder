// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

public class CKeyProducerJavaWebSocket extends CKeyProducerJavaReceiver {
    /**
     * Timeout for receiving secrets from the internal queue, in milliseconds.
     *
     * <p>The WebSocket server pushes received messages onto the shared queue;
     * this value controls how long {@code createSecrets()} waits for an entry.
     *
     * <p>Semantics:
     * <ul>
     *   <li>A positive value: the maximum time (in ms) to wait for each receive
     *       operation. A timed-out wait causes
     *       {@code createSecrets()} to throw
     *       {@code NoMoreSecretsAvailableException}.</li>
     *   <li>A negative value (typically {@code -1}): block indefinitely until a
     *       message arrives or the producer is interrupted via
     *       {@code interrupt()}.</li>
     *   <li>{@code 0}: return immediately if no message is queued.</li>
     * </ul>
     *
     * <p>Default: {@code 1000} ms
     */
    public int timeout = 1000;
    public int port = 8080;

    public int getPort() {
        return port;
    }
}
