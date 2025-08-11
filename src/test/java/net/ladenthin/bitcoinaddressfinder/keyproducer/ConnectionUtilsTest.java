// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.ServerSocket;
import java.util.concurrent.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ConnectionUtilsTest {

    private ExecutorService executorService;

    @Before
    public void setup() {
        executorService = Executors.newCachedThreadPool();
    }

    @After
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

    @Test(expected = IllegalStateException.class)
    public void waitUntilTcpPortOpen_whenPortNeverOpens_throwsException() throws Exception {
        int unusedPort = findFreePort();
        // Do not bind anything to this port

        ConnectionUtils.waitUntilTcpPortOpen("localhost", unusedPort, 1000);
    }
}
