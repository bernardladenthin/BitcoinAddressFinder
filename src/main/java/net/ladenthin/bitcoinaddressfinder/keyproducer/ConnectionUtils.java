// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.net.InetSocketAddress;
import java.net.Socket;

public class ConnectionUtils {

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
