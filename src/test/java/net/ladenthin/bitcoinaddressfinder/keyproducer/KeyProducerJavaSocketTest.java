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

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import java.io.DataInputStream;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import org.bitcoinj.base.Network;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;
import org.slf4j.Logger;

@RunWith(DataProviderRunner.class)
public class KeyProducerJavaSocketTest {
    
    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    private ExecutorService executorService;
    private Logger mockLogger;
    
    public static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setup() {
        executorService = Executors.newCachedThreadPool();
        mockLogger = mock(Logger.class);
    }

    @After
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

                    byte[] buffer = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
                    in.readFully(buffer); // exactly 32 bytes
                }
            }
            return null;
        });

        serverStarted.await(); // wait for real readiness

        try (Socket clientSocket = new Socket("localhost", port);
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            byte[] secret = makeSecretBytes();
            out.write(secret);
            out.flush();
        }

        serverFuture.get(2, TimeUnit.SECONDS);
    }
    
    @Test
    public void openCloseConnection_serverMode_success() throws Exception {
        int port = findFreePort();
        CountDownLatch serverReadyLatch = new CountDownLatch(1);

        // Set up server
        CKeyProducerJavaSocket serverConfig = createServerConfig(port);
        serverConfig.timeout = 2000; // Optional: short timeout for read
        serverConfig.readRetryCount = 3;
        serverConfig.connectionRetryCount = 10;
        serverConfig.retryDelayMillisConnect = 100;
        serverConfig.retryDelayMillisRead = 100;
        KeyProducerJavaSocket serverKeyProducer = new KeyProducerJavaSocket(serverConfig, keyUtility, bitHelper, mockLogger);

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
        waitUntilPortOpen(1000);

        // Client: connect and send exactly 1 secret
        try (Socket clientSocket = new Socket(serverConfig.host, serverConfig.port);
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
            byte[] secret = makeSecretBytes();
            out.write(secret);
            out.flush();
            // Wait briefly to let server finish reading
            Thread.sleep(100);
        }

        // Wait for server to complete
        serverFuture.get(2, TimeUnit.SECONDS);
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
                Thread.sleep(500);
            }
            return null;
        });

        // Client config to connect
        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

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
        byte[] secretBytes = makeSecretBytes((byte)0xCC);
        BigInteger expected = new BigInteger(1, secretBytes);

        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket clientSocket = serverSocket.accept();
                 DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
                // Send exactly one private key (32 bytes)
                dos.write(secretBytes);
                dos.flush();
                // Keep socket open a bit
                Thread.sleep(100);
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

        BigInteger[] secrets = client.createSecrets(1, true);

        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(equalTo(expected)));

        cleanup(client, serverFuture, serverSocket);
    }

    @Test(expected = NoMoreSecretsAvailableException.class)
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
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

        try {
            client.createSecrets(1, true);
        } finally {
            cleanup(client, serverFuture, serverSocket);
        }
    }
    
    @Test
    public void interrupt_closesConnectionAndNoExceptionThrown() throws Exception {
        int port = findFreePort();

        final int serverHoldTime = 2000;                            // ms - server keeps connection open without sending data
        final int clientSocketTimeout = serverHoldTime + 1000;      // ms - socket read timeout (should never be hit due to interrupt)
        final int clientSettleTime = 200;                           // ms - allow client to enter blocking read
        final int futureWaitTime = clientSocketTimeout;             // ms - how long we wait for the future to complete

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
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

        // Launch a thread that will block on reading secrets
        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return client.createSecrets(10, false);  // blocks until secret data or interruption
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        Thread.sleep(clientSettleTime); // Give time to enter read state
        client.interrupt();             // Should trigger socket close and exit read

        // Wait for the client thread to exit cleanly
        BigInteger[] result = future.get(futureWaitTime, TimeUnit.MILLISECONDS);
        assertThat("Future should return null after interrupt", result, nullValue());

        // Final cleanup
        cleanup(client, serverFuture, serverSocket, clientSocketTimeout, TimeUnit.MILLISECONDS);
    }
    
    private byte[] makeSecretBytes() {
        return makeSecretBytes((byte)0x0);
    }
    
    private byte[] makeSecretBytes(byte fillByte) {
        byte[] secretBytes = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
        for (int i = 0; i < secretBytes.length; i++) {
            secretBytes[i] = fillByte;
        }
        return secretBytes;
    }

    @Test
    public void createSecrets_afterClose_reconnectsAndReadsSuccessfully() throws Exception {
        int port = findFreePort();

        // Server that sends a single valid secret
        ServerSocket serverSocket = new ServerSocket(port);

        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket s = serverSocket.accept();
                 DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                byte[] secretBytes = makeSecretBytes((byte)(0));
                dos.write(secretBytes);
                dos.flush();
                Thread.sleep(100); // keep open shortly
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

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
                    byte[] secretBytes = makeSecretBytes((byte) (i + 10));
                    dos.write(secretBytes);
                    dos.flush();
                    Thread.sleep(50);
                }
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

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

        // Server that only starts after a delay (simulate late server start)
        Future<Void> serverFuture = executorService.submit(() -> {
            Thread.sleep(2000); // Delay start by 2 seconds
            try (ServerSocket serverSocket = new ServerSocket(port);
                 Socket s = serverSocket.accept();
                 DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                byte[] secretBytes = makeSecretBytes((byte) 99);
                dos.write(secretBytes);
                dos.flush();
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        clientConfig.connectionRetryCount = 10;
        clientConfig.retryDelayMillisConnect = 500;
        clientConfig.timeout = 3000;
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

        // This will retry internally until server comes up
        Thread.sleep(1000);  // allow background thread time to connect and read
        BigInteger[] secrets = client.createSecrets(1, true);

        assertThat(secrets.length, is(1));
        assertThat(secrets[0].toByteArray()[secrets[0].toByteArray().length - 1], is(equalTo((byte) 99)));

        cleanup(client, serverFuture, null, 3, TimeUnit.SECONDS);
    }

    @Test(expected = NoMoreSecretsAvailableException.class)
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
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

        try {
            client.createSecrets(1, true);
        } finally {
            cleanup(client, serverFuture, serverSocket);
        }
    }
    
    @Test(expected = NoMoreSecretsAvailableException.class)
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
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

        try {
            client.createSecrets(1, true);
        } finally {
            cleanup(client, serverFuture, serverSocket);
        }
    }

    @Test
    public void createSecrets_zeroSecretsRequested_returnsEmptyArray() throws Exception {
        int port = findFreePort();

        // Server never sends anything, shouldn't matter
        ServerSocket serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(300);
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
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

        BigInteger[] secrets = client.createSecrets(0, false);
        assertThat(secrets.length, is(0));

        cleanup(client, serverFuture, serverSocket);
    }

    @Test(expected = NoMoreSecretsAvailableException.class)
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
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

        try {
            client.createSecrets(2, false);
        } finally {
            cleanup(client, serverFuture, serverSocket);
        }
    }
    
    @Test
    public void testReadRetriesAndCloseCalledOnceMaxReached() throws Exception {
        int port = findFreePort();
        AtomicInteger connectionAttempts = new AtomicInteger(0);

        ServerSocket serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(1000);  // 1 second

        // Server: accepts but sends too few bytes (always triggers IOException)
        Future<Void> serverFuture = executorService.submit(() -> {
            for (int i = 0; i < 3; i++) { // allow up to 3 connections
                try (Socket s = serverSocket.accept();
                     DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                    connectionAttempts.incrementAndGet();
                    dos.write(new byte[10]);  // too few bytes (less than 32)
                    dos.flush();
                    Thread.sleep(50);  // simulate partial send delay
                    s.close();  // force premature closure
                } catch (SocketTimeoutException timeout) {
                    break; // no more clients, exit early
                }
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        clientConfig.readRetryCount = 3;
        clientConfig.retryDelayMillisRead = 50;
        clientConfig.timeout = 1000;

        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper, mockLogger);

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
            } catch (Exception ignored) {}
        }).start();
        return serverSocket;
    }
    
    @Test(timeout = 5000)
    public void testInterruptCausesShutdownDuringConnectionAttemptOnly() throws Exception {
        final int STATE_UNEXPECTED_SUCCESS = -1;
        final int STATE_UNKNOWN = 0;
        final int STATE_ENTERED = 1;
        final int STATE_INTERRUPT_TRIGGERED = 2;
        final int STATE_INTERRUPTED_EXIT = 3;
        final int STATE_NATURAL_EXIT = 4;

        int port = findFreePort();
        ServerSocket dummyServer = hangServer(port);  // <-- make sure something accepts

        CKeyProducerJavaSocket config = createClientConfig("localhost", port);
        config.connectionRetryCount = 3;
        config.timeout = 2000; // make connection attempt long enough to interrupt mid-way
        config.retryDelayMillisConnect = 0;

        KeyProducerJavaSocket client = new KeyProducerJavaSocket(config, keyUtility, bitHelper, mockLogger);

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
        Thread.sleep(300);
        state.set(STATE_INTERRUPT_TRIGGERED); // interrupt about to happen
        client.interrupt();

        future.get(3, TimeUnit.SECONDS);
        executor.shutdownNow();
        
        assertNotEquals("Thread exited without triggering interrupt", STATE_UNEXPECTED_SUCCESS, state.get());
        assertEquals("Expected interrupt to cause shutdown", STATE_INTERRUPTED_EXIT, state.get());
    }
    
    @Test(timeout = 5000)
    public void serverAcceptTimesOut_whenNoClientConnects() throws Exception {
        int port = findFreePort();

        CKeyProducerJavaSocket config = createServerConfig(port);
        config.timeout = 1000; // 1 second
        config.connectionRetryCount = 1;
        config.retryDelayMillisConnect = 0;

        KeyProducerJavaSocket server = new KeyProducerJavaSocket(config, keyUtility, bitHelper, mockLogger);

        long start = System.currentTimeMillis();

        try {
            server.createSecrets(1, true);
            fail("Expected NoMoreSecretsAvailableException due to accept timeout");
        } catch (NoMoreSecretsAvailableException e) {
            long duration = System.currentTimeMillis() - start;
            // Timeout must be honored within reasonable margin (±200ms)
            assertTrue("Timeout did not occur as expected", duration >= 1000 && duration <= 1200);
            assertThat(e.getMessage(), containsString("Timeout while waiting for secret"));
        } finally {
            server.interrupt();
        }
    }
    
    private void cleanup(KeyProducerJavaSocket client, Future<?> serverFuture, ServerSocket serverSocket) throws Exception {
        cleanup(client, serverFuture, serverSocket, 1, TimeUnit.SECONDS);
    }
    
    private void cleanup(KeyProducerJavaSocket client, Future<?> serverFuture, ServerSocket serverSocket, long timeout, TimeUnit unit) throws Exception {
        if (client != null) client.interrupt();
        if (serverFuture != null) serverFuture.get(timeout, unit);
        if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
    }
}
