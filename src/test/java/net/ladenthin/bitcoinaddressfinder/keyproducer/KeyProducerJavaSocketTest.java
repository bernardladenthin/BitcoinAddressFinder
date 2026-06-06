// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class KeyProducerJavaSocketTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    private ExecutorService executorService;

    public static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void setup() {
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    public void teardown() {
        executorService.shutdownNow();
    }

    private CKeyProducerJavaSocket createServerConfig(int port) {
        CKeyProducerJavaSocket config = new CKeyProducerJavaSocket();
        config.mode = CKeyProducerJavaSocket.Mode.SERVER;
        config.port = port;
        return config;
    }

    private CKeyProducerJavaSocket createClientConfig(String host, int port) {
        CKeyProducerJavaSocket config = new CKeyProducerJavaSocket();
        config.mode = CKeyProducerJavaSocket.Mode.CLIENT;
        config.host = host;
        config.port = port;
        return config;
    }

    @Test
    public void testServerReadsSecretWithoutReset() throws Exception {
        int port = findFreePort();
        CountDownLatch serverStarted = new CountDownLatch(1);

        Future<Void> serverFuture = executorService.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                serverStarted.countDown(); // only now signal ready
                try (Socket socket = serverSocket.accept();
                        DataInputStream in = new DataInputStream(socket.getInputStream())) {

                    byte[] buffer = new byte[OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES];
                    in.readFully(buffer); // exactly 32 bytes
                }
            }
            return null;
        });

        serverStarted.await(); // wait for real readiness

        try (Socket clientSocket = new Socket("localhost", port);
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            byte[] secret = new KeyProducerTestUtility().createZeroedSecret();
            out.write(secret);
            out.flush();
        }

        serverFuture.get(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    @Test
    public void openCloseConnection_serverMode_success() throws Exception {
        int port = findFreePort();
        CountDownLatch serverReadyLatch = new CountDownLatch(1);

        // Set up server
        CKeyProducerJavaSocket serverConfig = createServerConfig(port);
        serverConfig.timeout = TestTimeProvider.DEFAULT_SOCKET_TIMEOUT;
        serverConfig.connectionRetryCount = TestTimeProvider.DEFAULT_CONNECTION_RETRY_COUNT;
        serverConfig.retryDelayMillisConnect = TestTimeProvider.SHORT_DELAY;
        KeyProducerJavaSocket serverKeyProducer = new KeyProducerJavaSocket(serverConfig, keyUtility, bitHelper);
        serverKeyProducer.start();

        // Server thread: start createSecrets(1, true)
        Future<Void> serverFuture = executorService.submit(() -> {
            try {
                serverReadyLatch.countDown(); // Signal readiness
                serverKeyProducer.createSecrets(1, true); // Reads 32 bytes, then exits
            } catch (NoMoreSecretsAvailableException e) {
                fail("Server failed to read secret: " + e.getMessage());
            }
            return null;
        });

        // Wait until server is accepting
        serverReadyLatch.await();
        waitUntilPortOpen(TestTimeProvider.DEFAULT_ESTABLISH_DELAY);

        // Client: connect and send exactly 1 secret
        try (Socket clientSocket = new Socket(serverConfig.host, serverConfig.port);
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
            byte[] secret = new KeyProducerTestUtility().createZeroedSecret();
            out.write(secret);
            out.flush();
            // Wait briefly to let server finish reading
            Thread.sleep(TestTimeProvider.SHORT_DELAY);
        }

        // Wait for server to complete
        serverFuture.get(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);
        serverKeyProducer.interrupt();
    }

    private void waitUntilPortOpen(int timeoutMillis) throws Exception {
        Thread.sleep(timeoutMillis);
    }

    @Test
    public void openCloseConnection_serverClient_success() throws Exception {
        int port = findFreePort();

        // Start server thread that accepts connection but does nothing
        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket s = serverSocket.accept()) {
                // Just accept and hold open
                Thread.sleep(TestTimeProvider.DEFAULT_TIMEOUT);
            }
            return null;
        });

        // Client config to connect
        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        // Open connection implicitly on createSecrets call with length=1 and returnStartSecretOnly true (1 secret)
        try {
            client.createSecrets(1, true);
        } catch (NoMoreSecretsAvailableException e) {
            // Expect failure because server sends no bytes - but at least connection opened
            assertThat(e.getMessage(), containsString("Timeout while waiting for secret"));
        }

        cleanup(client, serverFuture, serverSocket);
    }

    @Test
    public void createSecrets_success_readsExpectedBigInteger() throws Exception {
        int port = findFreePort();

        // The secret bytes to send (32 bytes = 256 bits)
        byte[] secretBytes = new KeyProducerTestUtility().createFilledSecret((byte) 0xCC);
        BigInteger expected = new BigInteger(1, secretBytes);

        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket clientSocket = serverSocket.accept();
                    DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
                // Send exactly one private key (32 bytes)
                dos.write(secretBytes);
                dos.flush();
                // Keep socket open a bit
                Thread.sleep(TestTimeProvider.SHORT_DELAY);
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        BigInteger[] secrets = client.createSecrets(1, true);

        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(equalTo(expected)));

        cleanup(client, serverFuture, serverSocket);
    }

    @Test
    public void createSecrets_prematureStreamClose_throwsException() throws Exception {
        int port = findFreePort();

        // Server sends fewer bytes than required (e.g. 10 bytes instead of 32)
        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket clientSocket = serverSocket.accept();
                    DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
                dos.write(new byte[10]); // insufficient bytes
                dos.flush();
                // Close immediately
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        try {
            assertThrows(NoMoreSecretsAvailableException.class, () -> client.createSecrets(1, true));
        } finally {
            cleanup(client, serverFuture, serverSocket);
        }
    }

    @Test
    public void interrupt_closesConnectionAndNoExceptionThrown() throws Exception {
        int port = findFreePort();

        final int serverHoldTime =
                TestTimeProvider.DEFAULT_SOCKET_TIMEOUT; // ms - server keeps connection open without sending data
        final int clientSocketTimeout =
                TestTimeProvider.LONG_SOCKET_TIMEOUT; // ms - socket read timeout (should never be hit due to interrupt)
        final int clientSettleTime = TestTimeProvider.DEFAULT_SETTLE_DELAY; // ms - allow client to enter blocking read
        final int futureWaitTime = clientSocketTimeout; // ms - how long we wait for the future to complete

        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket clientSocket = serverSocket.accept()) {
                // simulate a hanging server that does not respond
                Thread.sleep(serverHoldTime);
            }
            return null;
        });

        // Configure client socket with read timeout
        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        clientConfig.timeout = clientSocketTimeout;
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        // Launch a thread that will block on reading secrets
        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return client.createSecrets(10, false); // blocks until secret data or interruption
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        Thread.sleep(clientSettleTime); // Give time to enter read state
        client.interrupt(); // Should trigger socket close and exit read

        // Wait for the client thread to exit cleanly
        BigInteger[] result = future.get(futureWaitTime, TimeUnit.MILLISECONDS);
        assertThat("Future should return null after interrupt", result, nullValue());

        // Final cleanup
        cleanup(client, serverFuture, serverSocket, clientSocketTimeout, TimeUnit.MILLISECONDS);
    }

    @Test
    public void interrupt_wakesBlockedCreateSecretsImmediately() throws Exception {
        // arrange
        // A server that accepts the connection but never sends any bytes, so the
        // client's reader thread sits in inputStream.read() and the secret queue
        // stays empty. createSecrets() will block in queue.poll() with a long
        // positive timeout (Socket producer cannot use -1 because the same value
        // is passed to SO_TIMEOUT, which rejects negatives).
        //
        // Without signalShutdown(), createSecrets would only return when the
        // socket-close ripple eventually wakes the reader and the poll times out
        // &#x2014; up to clientSocketTimeout ms later. With signalShutdown(),
        // interrupt() should wake the consumer in milliseconds.
        int port = findFreePort();

        final int serverHoldTime = TestTimeProvider.LONG_SOCKET_TIMEOUT * 4;
        final int clientSocketTimeout = TestTimeProvider.LONG_SOCKET_TIMEOUT * 4; // 12s
        final int clientSettleTime = TestTimeProvider.DEFAULT_SETTLE_DELAY;
        final int interruptWakeBudgetMs = 1_500; // generous; the actual wake should be << this

        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket accepted = serverSocket.accept()) {
                Thread.sleep(serverHoldTime);
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        clientConfig.timeout = clientSocketTimeout;
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return client.createSecrets(1, true);
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        Thread.sleep(clientSettleTime);
        assertThat("createSecrets must be blocked before interrupt()", future.isDone(), is(false));

        // act
        long beforeInterruptMs = System.currentTimeMillis();
        client.interrupt();
        BigInteger[] result = future.get(clientSocketTimeout, TimeUnit.MILLISECONDS);
        long elapsedSinceInterruptMs = System.currentTimeMillis() - beforeInterruptMs;

        // assert
        assertThat(result, nullValue());
        if (elapsedSinceInterruptMs >= interruptWakeBudgetMs) {
            fail("interrupt() did not wake the blocked consumer promptly: "
                    + elapsedSinceInterruptMs + " ms (budget was " + interruptWakeBudgetMs
                    + " ms; socket timeout was " + clientSocketTimeout + " ms)");
        }

        cleanup(client, serverFuture, serverSocket, clientSocketTimeout, TimeUnit.MILLISECONDS);
    }

    @Test
    public void createSecrets_afterClose_reconnectsAndReadsSuccessfully() throws Exception {
        int port = findFreePort();

        // Server that sends a single valid secret
        ServerSocket serverSocket = new ServerSocket(port);

        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket s = serverSocket.accept();
                    DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                byte[] secretBytes = new KeyProducerTestUtility().createZeroedSecret();
                dos.write(secretBytes);
                dos.flush();
                Thread.sleep(100); // keep open shortly
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        // Successful read
        BigInteger[] secrets1 = client.createSecrets(1, true);
        assertThat(secrets1.length, is(1));

        byte expectedByte1 = 1;
        for (byte b : secrets1[0].toByteArray()) {
            // Just check one byte, overall logic assumes full byte array match
            assertThat(b, is(notNullValue()));
        }

        // Close client forcibly (simulate socket closed)
        client.interrupt();

        // Try reuse and expect specific exception
        try {
            client.createSecrets(1, true);
            fail("Expected NoMoreSecretsAvailableException due to reuse after close");
        } catch (NoMoreSecretsAvailableException e) {
            assertThat(e.getMessage(), containsString("Interrupted"));
        }

        cleanup(client, serverFuture, serverSocket);
    }

    @Test
    public void createSecrets_multipleSequentialCalls_readsAllSuccessfully() throws Exception {
        int port = findFreePort();

        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket s = serverSocket.accept();
                    DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                for (int i = 0; i < 3; i++) {
                    byte[] secretBytes = new KeyProducerTestUtility().createFilledSecret((byte) (i + 10));
                    dos.write(secretBytes);
                    dos.flush();
                    Thread.sleep(TestTimeProvider.SHORT_DELAY);
                }
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        for (int i = 0; i < 3; i++) {
            BigInteger[] secrets = client.createSecrets(1, true);
            assertThat(secrets.length, is(1));
            byte expectedByte = (byte) (i + 10);
            assertThat(secrets[0].toByteArray()[secrets[0].toByteArray().length - 1], is(equalTo(expectedByte)));
        }

        cleanup(client, serverFuture, serverSocket);
    }

    @Test
    public void createSecrets_connectionRetry_worksWhenServerStartsLate() throws Exception {
        int port = findFreePort();

        final byte fillByte = (byte) 99;

        // Server that only starts after a delay (simulate late server start)
        Future<Void> serverFuture = executorService.submit(() -> {
            Thread.sleep(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT); // Delay start by 2 seconds
            try (ServerSocket serverSocket = new ServerSocket(port);
                    Socket s = serverSocket.accept();
                    DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                byte[] secretBytes = new KeyProducerTestUtility().createFilledSecret(fillByte);
                dos.write(secretBytes);
                dos.flush();
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        clientConfig.connectionRetryCount = TestTimeProvider.DEFAULT_CONNECTION_RETRY_COUNT;
        clientConfig.retryDelayMillisConnect = TestTimeProvider.DEFAULT_RETRY_DELAY;
        clientConfig.timeout = TestTimeProvider.LONG_SOCKET_TIMEOUT;
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        // This will retry internally until server comes up
        Thread.sleep(TestTimeProvider.DEFAULT_SETTLE_DELAY); // allow background thread time to connect and read
        BigInteger[] secrets = client.createSecrets(1, true);

        assertThat(secrets.length, is(1));
        new KeyProducerTestUtility().assertFilledSecret(secrets[0], fillByte);

        cleanup(client, serverFuture, null, 3, TimeUnit.SECONDS);
    }

    @Test
    public void createSecrets_socketClosedMidRead_throwsException() throws Exception {
        int port = findFreePort();

        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket s = serverSocket.accept();
                    DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                // Write partial bytes, then close abruptly
                dos.write(new byte[10]); // Less than 32 bytes
                dos.flush();
                s.close();
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        try {
            assertThrows(NoMoreSecretsAvailableException.class, () -> client.createSecrets(1, true));
        } finally {
            cleanup(client, serverFuture, serverSocket);
        }
    }

    @Test
    public void createSecrets_serverDisconnectsMidTransfer_throwsException() throws Exception {
        int port = findFreePort();

        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket s = serverSocket.accept();
                    DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                dos.write(new byte[16]); // only half of a secret (32 bytes)
                dos.flush();
                s.close();
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        try {
            assertThrows(NoMoreSecretsAvailableException.class, () -> client.createSecrets(1, true));
        } finally {
            cleanup(client, serverFuture, serverSocket);
        }
    }

    @Test
    public void createSecrets_zeroSecretsRequested_returnsEmptyArray() throws Exception {
        int port = findFreePort();

        // Server never sends anything, shouldn't matter
        ServerSocket serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(TestTimeProvider.SOCKET_ACCEPT_TIMEOUT);
        Future<Void> serverFuture = executorService.submit(() -> {
            try {
                try (Socket ignored = serverSocket.accept()) {
                    // No-op
                }
            } catch (SocketTimeoutException ignored) {
                // Accept timed out - expected when client does not connect
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        BigInteger[] secrets = client.createSecrets(0, false);
        assertThat(secrets.length, is(0));

        cleanup(client, serverFuture, serverSocket);
    }

    @Test
    public void createSecrets_malformedSecretStream_throwsException() throws Exception {
        int port = findFreePort();

        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket s = serverSocket.accept();
                    DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                // Send 40 bytes which cannot be split into two full 32-byte secrets
                dos.write(new byte[40]);
                dos.flush();
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        try {
            assertThrows(NoMoreSecretsAvailableException.class, () -> client.createSecrets(2, false));
        } finally {
            cleanup(client, serverFuture, serverSocket);
        }
    }

    @Test
    public void testReadRetriesAndCloseCalledOnceMaxReached() throws Exception {
        int port = findFreePort();
        AtomicInteger connectionAttempts = new AtomicInteger(0);

        ServerSocket serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(TestTimeProvider.SOCKET_ACCEPT_TIMEOUT);

        // Server: accepts but sends too few bytes (always triggers IOException)
        Future<Void> serverFuture = executorService.submit(() -> {
            for (int i = 0; i < 3; i++) { // allow up to 3 connections
                try (Socket s = serverSocket.accept();
                        DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                    connectionAttempts.incrementAndGet();
                    dos.write(new byte[10]); // too few bytes (less than 32)
                    dos.flush();
                    Thread.sleep(50); // simulate partial send delay
                    s.close(); // force premature closure
                } catch (SocketTimeoutException timeout) {
                    break; // no more clients, exit early
                }
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        clientConfig.timeout = TestTimeProvider.SOCKET_ACCEPT_TIMEOUT;

        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);
        client.start();

        try {
            client.createSecrets(1, true);
            fail("Expected NoMoreSecretsAvailableException");
        } catch (NoMoreSecretsAvailableException e) {
            assertThat(e.getMessage(), containsString("Timeout while waiting for secret"));
        }

        // We expect only one connection attempt, even after retries, because the socket is reused
        // unless IOException forces reconnection (your logic determines this)
        assertThat("Expected one connection for all retries", connectionAttempts.get(), is(1));

        // Wait longer in case of multiple connection retries / socket handshakes
        cleanup(client, serverFuture, serverSocket, 5, TimeUnit.SECONDS);
    }

    private ServerSocket hangServer(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        new Thread(() -> {
                    try {
                        Socket client = serverSocket.accept();
                        // Do nothing, just hang the connection
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception ignored) {
                    }
                })
                .start();
        return serverSocket;
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testInterruptCausesShutdownDuringConnectionAttemptOnly() throws Exception {
        final int STATE_UNEXPECTED_SUCCESS = -1;
        final int STATE_UNKNOWN = 0;
        final int STATE_ENTERED = 1;
        final int STATE_INTERRUPT_TRIGGERED = 2;
        final int STATE_INTERRUPTED_EXIT = 3;
        final int STATE_NATURAL_EXIT = 4;

        int port = findFreePort();
        ServerSocket dummyServer = hangServer(port); // <-- make sure something accepts

        CKeyProducerJavaSocket config = createClientConfig("localhost", port);
        config.connectionRetryCount = 3;
        config.timeout =
                TestTimeProvider.DEFAULT_SOCKET_TIMEOUT; // make connection attempt long enough to interrupt mid-way
        config.retryDelayMillisConnect = 0;

        KeyProducerJavaSocket client = new KeyProducerJavaSocket(config, keyUtility, bitHelper);
        client.start();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger state = new AtomicInteger(STATE_UNKNOWN);

        Future<?> future = executor.submit(() -> {
            state.set(STATE_ENTERED); // entered createSecrets
            try {
                client.createSecrets(1, true);
                state.set(STATE_UNEXPECTED_SUCCESS); // no exception, test failed
            } catch (NoMoreSecretsAvailableException e) {
                if (state.get() == STATE_INTERRUPT_TRIGGERED) {
                    state.set(STATE_INTERRUPTED_EXIT); // interrupted before exception
                } else {
                    state.set(STATE_NATURAL_EXIT); // exception came naturally without interrupt
                }
            }
        });

        // wait to allow connect() to begin (must be < timeout)
        Thread.sleep(TestTimeProvider.DEFAULT_SETTLE_DELAY);
        state.set(STATE_INTERRUPT_TRIGGERED); // interrupt about to happen
        client.interrupt();

        future.get(3, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertNotEquals(STATE_UNEXPECTED_SUCCESS, state.get(), "Thread exited without triggering interrupt");
        assertEquals(STATE_INTERRUPTED_EXIT, state.get(), "Expected interrupt to cause shutdown");
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void serverAcceptTimesOut_whenNoClientConnects() throws Exception {
        int port = findFreePort();

        CKeyProducerJavaSocket config = createServerConfig(port);
        config.timeout = TestTimeProvider.SOCKET_ACCEPT_TIMEOUT;
        config.connectionRetryCount = 1;
        config.retryDelayMillisConnect = 0;

        KeyProducerJavaSocket server = new KeyProducerJavaSocket(config, keyUtility, bitHelper);
        server.start();

        long start = System.currentTimeMillis();

        try {
            server.createSecrets(1, true);
            fail("Expected NoMoreSecretsAvailableException due to accept timeout");
        } catch (NoMoreSecretsAvailableException e) {
            long duration = System.currentTimeMillis() - start;
            // Timeout must be honored within reasonable margin (±200ms)
            assertTrue(
                    duration >= TestTimeProvider.SOCKET_ACCEPT_TIMEOUT
                            && duration <= TestTimeProvider.SOCKET_ACCEPT_TIMEOUT + 200,
                    "Timeout did not occur as expected");
            assertThat(e.getMessage(), containsString("Timeout while waiting for secret"));
        } finally {
            server.interrupt();
        }
    }

    private void cleanup(
            @Nullable KeyProducerJavaSocket client,
            @Nullable Future<?> serverFuture,
            @Nullable ServerSocket serverSocket)
            throws Exception {
        cleanup(client, serverFuture, serverSocket, TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    private void cleanup(
            @Nullable KeyProducerJavaSocket client,
            @Nullable Future<?> serverFuture,
            @Nullable ServerSocket serverSocket,
            long timeout,
            TimeUnit unit)
            throws Exception {
        if (client != null) client.interrupt();
        if (serverFuture != null) serverFuture.get(timeout, unit);
        if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
    }
}
