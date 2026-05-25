// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.concurrent.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConnectionUtilsTest {

    private ExecutorService executorService;

    @BeforeEach
    public void setup() {
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    public void teardown() {
        executorService.shutdownNow();
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void waitUntilTcpPortOpen_whenServerAlreadyListening_returnsImmediately() throws Exception {
        int port = findFreePort();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            long start = System.currentTimeMillis();
            ConnectionUtils.waitUntilTcpPortOpen("localhost", port, 2000);
            long duration = System.currentTimeMillis() - start;

            // Should return very quickly (< 500ms)
            assertThat("waitUntilTcpPortOpen should return quickly when port is open", duration, lessThan(500L));
        }
    }

    @Test
    public void waitUntilTcpPortOpen_whenServerStartsLate_returnsAfterStart() throws Exception {
        int port = findFreePort();

        // Delay server start by 1 second
        executorService.submit(() -> {
            Thread.sleep(1000);
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                Thread.sleep(1000); // Keep it open for a bit
            }
            return null;
        });

        long start = System.currentTimeMillis();
        ConnectionUtils.waitUntilTcpPortOpen("localhost", port, 3000);
        long duration = System.currentTimeMillis() - start;

        // Should return after ~1 second
        assertThat("waitUntilTcpPortOpen should return after port becomes available", duration, greaterThanOrEqualTo(900L));
        assertThat(duration, lessThan(2500L)); // with some margin
    }

    @Test
    public void waitUntilTcpPortOpen_whenPortNeverOpens_throwsException() throws Exception {
        int unusedPort = findFreePort();
        // Do not bind anything to this port

        assertThrows(IllegalStateException.class, () -> ConnectionUtils.waitUntilTcpPortOpen("localhost", unusedPort, 1000));
    }
}
