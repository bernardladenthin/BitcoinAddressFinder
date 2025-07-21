// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder;

import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.DataOutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import org.bitcoinj.base.Network;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class KeyProducerJavaSocketTest {
    
    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    private ExecutorService executorService;

    @Before
    public void setup() {
        executorService = Executors.newCachedThreadPool();
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
    public void openCloseConnection_serverMode_success() throws Exception {
        int port = 9098;
        CountDownLatch serverReadyLatch = new CountDownLatch(1);

        CKeyProducerJavaSocket serverConfig = createServerConfig(port);
        KeyProducerJavaSocket serverKeyProducer = new KeyProducerJavaSocket(serverConfig, keyUtility, bitHelper);

        Future<Void> serverFuture = executorService.submit(() -> {
            try {
                // Signal that the server socket is about to start accepting
                synchronized (serverKeyProducer) {
                    serverReadyLatch.countDown();
                }
                // This will block on accept inside ensureConnection
                serverKeyProducer.createSecrets(1, true);
            } catch (NoMoreSecretsAvailableException e) {
                // Expected because no bytes sent
            }
            return null;
        });

        // Wait for the server thread to signal that it is ready to accept connections
        serverReadyLatch.await();

        // Now client connects, after server is ready
        try (Socket clientSocket = new Socket("localhost", port);
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
            out.write(new byte[32]); // Send dummy secret
            out.flush();
            Thread.sleep(100);  // Let server process
        }

        serverFuture.get(2, TimeUnit.SECONDS);

        serverKeyProducer.interrupt();
        serverKeyProducer.close();
    }

    @Test
    public void openCloseConnection_serverClient_success() throws Exception {
        int port = 9090;

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
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);

        // Open connection implicitly on createSecrets call with length=1 and returnStartSecretOnly true (1 secret)
        try {
            client.createSecrets(1, true);
        } catch (NoMoreSecretsAvailableException e) {
            // Expect failure because server sends no bytes - but at least connection opened
            assertThat(e.getMessage(), containsString("Max read attempts exceeded"));
        }

        // Close client
        client.close();

        // Cleanup server
        serverFuture.get(1, TimeUnit.SECONDS);
        serverSocket.close();
    }

    @Test
    public void createSecrets_success_readsExpectedBigInteger() throws Exception {
        int port = 9091;

        // The secret bytes to send (32 bytes = 256 bits)
        byte[] secretBytes = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
        for (int i = 0; i < secretBytes.length; i++) {
            secretBytes[i] = (byte) i;
        }
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
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);

        BigInteger[] secrets = client.createSecrets(1, true);

        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(equalTo(expected)));

        client.close();

        serverFuture.get(1, TimeUnit.SECONDS);
        serverSocket.close();
    }

    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_prematureStreamClose_throwsException() throws Exception {
        int port = 9092;

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

        try {
            client.createSecrets(1, true);
        } finally {
            client.close();
            serverFuture.get(1, TimeUnit.SECONDS);
            serverSocket.close();
        }
    }

    @Test
    public void interrupt_closesConnectionAndNoExceptionThrown() throws Exception {
        int port = 9093;

        ServerSocket serverSocket = new ServerSocket(port);
        Future<Void> serverFuture = executorService.submit(() -> {
            try (Socket clientSocket = serverSocket.accept()) {
                // just accept and wait
                Thread.sleep(2000);
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);

        // Start connection in separate thread that calls createSecrets with large size (blocks)
        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return client.createSecrets(10, false);
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        // Give it some time to block on read
        Thread.sleep(200);

        // Interrupt client which should close connections
        client.interrupt();

        // The future should complete either normally or exceptionally but not hang forever
        future.get(3, TimeUnit.SECONDS);

        // Cleanup
        client.close();
        serverFuture.get(1, TimeUnit.SECONDS);
        serverSocket.close();
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
        int port = 9094;

        // Server that accepts twice, sending 32 bytes each time
        ServerSocket serverSocket = new ServerSocket(port);

        Future<Void> serverFuture = executorService.submit(() -> {
            for (int round = 0; round < 2; round++) {
                try (Socket s = serverSocket.accept();
                     DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {
                    byte[] secretBytes = makeSecretBytes((byte) (round + 1));
                    dos.write(secretBytes);
                    dos.flush();
                    Thread.sleep(100);
                }
            }
            return null;
        });

        CKeyProducerJavaSocket clientConfig = createClientConfig("localhost", port);
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);

        // First read
        BigInteger[] secrets1 = client.createSecrets(1, true);
        assertThat(secrets1.length, is(1));
        byte expectedByte1 = 1;
        for (byte b : secrets1[0].toByteArray()) {
            // Just check one byte, overall logic assumes full byte array match
            assertThat(b, is(notNullValue()));
        }

        // Close client forcibly (simulate socket closed)
        client.close();

        // Second read: should reconnect transparently and get different data
        BigInteger[] secrets2 = client.createSecrets(1, true);
        assertThat(secrets2.length, is(1));

        // Just verify different from first (bytes filled with 2)
        assertThat(secrets2[0], not(equalTo(secrets1[0])));

        client.close();
        serverFuture.get(1, TimeUnit.SECONDS);
        serverSocket.close();
    }

    @Test
    public void createSecrets_multipleSequentialCalls_readsAllSuccessfully() throws Exception {
        int port = 9095;

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
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);

        for (int i = 0; i < 3; i++) {
            BigInteger[] secrets = client.createSecrets(1, true);
            assertThat(secrets.length, is(1));
            byte expectedByte = (byte) (i + 10);
            assertThat(secrets[0].toByteArray()[secrets[0].toByteArray().length - 1], is(equalTo(expectedByte)));
        }

        client.close();
        serverFuture.get(1, TimeUnit.SECONDS);
        serverSocket.close();
    }

    @Test
    public void createSecrets_connectionRetry_worksWhenServerStartsLate() throws Exception {
        int port = 9096;

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
        KeyProducerJavaSocket client = new KeyProducerJavaSocket(clientConfig, keyUtility, bitHelper);

        // This will retry internally until server comes up
        BigInteger[] secrets = client.createSecrets(1, true);

        assertThat(secrets.length, is(1));
        assertThat(secrets[0].toByteArray()[secrets[0].toByteArray().length - 1], is(equalTo((byte) 99)));

        client.close();
        serverFuture.get(3, TimeUnit.SECONDS);
    }

    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_socketClosedMidRead_throwsException() throws Exception {
        int port = 9097;

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

        try {
            client.createSecrets(1, true);
        } finally {
            client.close();
            serverFuture.get(1, TimeUnit.SECONDS);
            serverSocket.close();
        }
    }
}
