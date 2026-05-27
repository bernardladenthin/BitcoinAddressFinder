// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Helpers for working with TCP connections used by socket-based key producers.
 */
public class ConnectionUtils {

    /** Creates a new {@link ConnectionUtils}. */
    public ConnectionUtils() {}

    /**
     * Waits until the given TCP port accepts connections.
     *
     * @param host          the target host name or address
     * @param port          the target port
     * @param timeoutMillis the maximum time to wait, in milliseconds
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static void waitUntilTcpPortOpen(String host, int port, int timeoutMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMillis) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 100);
                return;
            } catch (Exception e) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("TCP port " + host + ":" + port + " not open after " + timeoutMillis + "ms");
    }
}
