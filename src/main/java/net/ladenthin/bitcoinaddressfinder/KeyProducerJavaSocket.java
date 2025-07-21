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
package net.ladenthin.bitcoinaddressfinder;

import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaSocket;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyProducerJavaSocket extends KeyProducerJava<CKeyProducerJavaSocket> {

    private final KeyUtility keyUtility;
    
    private ServerSocket serverSocket;
    private Socket socket;
    private DataInputStream inputStream;
    private boolean isConnected = false;
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private static final int END_OF_STREAM = -1;
    
    private final Object connectionLock = new Object();
    
    private volatile boolean shouldStop = false;

    public KeyProducerJavaSocket(CKeyProducerJavaSocket cKeyProducerJavaSocket, KeyUtility keyUtility, BitHelper bitHelper) {
        super(cKeyProducerJavaSocket);
        this.keyUtility = keyUtility;
    }

    private void ensureConnection() throws NoMoreSecretsAvailableException {
        synchronized (connectionLock) {
            if (isConnected) {
                return;
            }

            int attempt = 0;
            Exception lastException = null;

            while (attempt < cKeyProducerJava.connectionRetryCount && !isConnected) {
                if (shouldStop) {
                    throw new NoMoreSecretsAvailableException("Interrupted before or during connection attempt");
                }
                try {
                    logger.info("Attempt {} to connect in {} mode...", attempt + 1, cKeyProducerJava.mode);
                    if (cKeyProducerJava.mode == CKeyProducerJavaSocket.Mode.SERVER) {
                        if (serverSocket == null) {
                            serverSocket = new ServerSocket(cKeyProducerJava.getPort());
                        }
                        socket = serverSocket.accept();
                        logger.info("Accepted client connection.");
                    } else {
                        socket = new Socket(cKeyProducerJava.getHost(), cKeyProducerJava.getPort());
                        logger.info("Connected to server at {}:{}", cKeyProducerJava.getHost(), cKeyProducerJava.getPort());
                    }
                    socket.setSoTimeout(cKeyProducerJava.timeout);
                    inputStream = new DataInputStream(socket.getInputStream());
                    isConnected = true;
                } catch (IOException e) {
                    lastException = e;
                    logger.warn("Connection attempt {} failed: {}", attempt + 1, e.getMessage());
                    attempt++;
                    try {
                        Thread.sleep(cKeyProducerJava.retryDelayMillisConnect);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new NoMoreSecretsAvailableException("Interrupted during connection retry", ie);
                    }
                }
            }

            if (!isConnected) {
                if (shouldStop) {
                    throw new NoMoreSecretsAvailableException("Stopped during connection retries");
                }
                logger.error("All {} connection attempts failed. Giving up.", cKeyProducerJava.connectionRetryCount);
                throw new NoMoreSecretsAvailableException("Failed to connect after " + cKeyProducerJava.connectionRetryCount + " attempts", lastException);
            }
        }
    }
    
    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly) throws NoMoreSecretsAvailableException {
        int length = returnStartSecretOnly ? 1 : overallWorkSize;
        BigInteger[] secrets = new BigInteger[length];
        byte[] buffer = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];

        for (int i = 0; i < length; i++) {
            boolean success = false;
            int readAttempts = 0;

            while (!success && readAttempts < cKeyProducerJava.readRetryCount) {
                if (shouldStop) {
                    throw new NoMoreSecretsAvailableException("Thread interrupted during secret creation");
                }
                try {
                    ensureConnection();

                    int readBytes = 0;
                    while (readBytes < buffer.length) {
                        int r = inputStream.read(buffer, readBytes, buffer.length - readBytes);
                        if (r == END_OF_STREAM) {
                            throw new IOException("Stream closed prematurely");
                        }
                        readBytes += r;
                    }

                    BigInteger secret = keyUtility.bigIntegerFromUnsignedByteArray(buffer);
                    secrets[i] = secret;

                    if (cKeyProducerJava.logReceivedSecret) {
                        logger.info("received secret: " + keyUtility.bigIntegerToFixedLengthHex(secret));
                    }

                    success = true;

                } catch (IOException e) {
                    readAttempts++;
                    logger.warn("Failed to read secret index {} from socket (attempt {}/{}): {}", i, readAttempts, cKeyProducerJava.readRetryCount, e.getMessage());
                    close();

                    if (readAttempts >= cKeyProducerJava.readRetryCount) {
                        throw new NoMoreSecretsAvailableException("Max read attempts exceeded", e);
                    }

                    try {
                        Thread.sleep(cKeyProducerJava.retryDelayMillisRead);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new NoMoreSecretsAvailableException("Interrupted while retrying read", ie);
                    }
                }
            }
        }

        return secrets;
    }

    public void close() {
        synchronized (connectionLock) {
            try { if (inputStream != null) inputStream.close(); } catch (IOException ignored) {}
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
            inputStream = null;
            socket = null;
            serverSocket = null;
            isConnected = false;
        }
    }
    
    @Override
    public void interrupt() {
        shouldStop = true;
        close();
    }
}