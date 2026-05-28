// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Configuration for the TCP socket-based key producer (client or server).
 */
public class CKeyProducerJavaSocket extends CKeyProducerJavaReceiver {

    /** Creates a new {@link CKeyProducerJavaSocket}. */
    public CKeyProducerJavaSocket() {}

    /**
     * Whether the socket producer acts as a client (connecting out) or as a server (accepting connections).
     */
    public enum Mode {
        /** Connect to a remote endpoint as a TCP client. */
        CLIENT,
        /** Bind to a local port and accept TCP connections. */
        SERVER
    }

    /** Hostname or IP address for client mode; ignored in server mode */
    public String host = "localhost";

    /** Port number to connect to or listen on */
    public int port = 12345;

    /** Operating mode: client or server */
    public Mode mode = Mode.SERVER;

    /**
     * Socket-level timeout in milliseconds. This field is overloaded and applied
     * to multiple operations:
     * <ul>
     *   <li>{@code ServerSocket.setSoTimeout} &#x2014; accept timeout (server mode)</li>
     *   <li>{@code Socket.connect(addr, timeout)} &#x2014; connect timeout (client mode)</li>
     *   <li>{@code Socket.setSoTimeout} &#x2014; per-read timeout on the connected stream</li>
     *   <li>The internal secret queue polling inside {@code createSecrets()}</li>
     * </ul>
     *
     * <p>Must be a non-negative value. Unlike
     * {@code CKeyProducerJavaZmq.timeout} and {@code CKeyProducerJavaWebSocket.timeout},
     * negative values (e.g. {@code -1} for &quot;block forever&quot;) are NOT
     * supported here: the underlying {@code java.net} APIs throw
     * {@code IllegalArgumentException} on negative SO_TIMEOUT, so any negative
     * value would prevent the producer from initialising. Use a large positive
     * value if you need a near-unbounded wait.
     */
    public int timeout = 3000;

    /** Number of attempts to reconnect if connection fails */
    public int connectionRetryCount = 5;

    /** Delay in milliseconds between retry attempts */
    public int retryDelayMillisConnect = 1000;

    /**
     * Returns the configured host name.
     *
     * @return the configured host name
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the configured port number.
     *
     * @return the configured port number
     */
    public int getPort() {
        return port;
    }
}
