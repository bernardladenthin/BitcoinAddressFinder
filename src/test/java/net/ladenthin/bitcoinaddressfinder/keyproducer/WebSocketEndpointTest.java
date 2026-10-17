// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Lifecycle tests for {@link WebSocketEndpoint}. */
class WebSocketEndpointTest {

    /** Upper bound for the endpoint's threads to wind down after {@link WebSocketEndpoint#stop()}. */
    private static final long AWAIT_MILLIS = 15_000L;

    /** Poll interval while waiting for the threads to end. */
    private static final long POLL_MILLIS = 50L;

    // <editor-fold defaultstate="collapsed" desc="stop">
    @Test
    void stop_afterStart_leavesNoNonDaemonThreadBehind() throws Exception {
        // A non-daemon thread left running after stop() keeps the JVM alive: BAF's Main never calls
        // System.exit, so a Find run whose WebSocket key producer has ended would never terminate.

        // arrange
        final Set<Thread> before = liveNonDaemonThreads();
        final int port = freePort();
        final WebSocketEndpoint endpoint = new WebSocketEndpoint(port);
        endpoint.start();
        ConnectionUtils.waitUntilTcpPortOpen("127.0.0.1", port, (int) AWAIT_MILLIS);

        // act
        endpoint.stop();

        // assert
        final long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        Set<Thread> leftOver = leftOver(before);
        while (!leftOver.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS);
            leftOver = leftOver(before);
        }
        assertThat(leftOver, is(empty()));
    }
    // </editor-fold>

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Set<Thread> leftOver(Set<Thread> before) {
        final Set<Thread> now = liveNonDaemonThreads();
        now.removeAll(before);
        return now;
    }

    private static Set<Thread> liveNonDaemonThreads() {
        final Set<Thread> threads = new HashSet<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && !thread.isDaemon()) {
                threads.add(thread);
            }
        }
        return threads;
    }
}
