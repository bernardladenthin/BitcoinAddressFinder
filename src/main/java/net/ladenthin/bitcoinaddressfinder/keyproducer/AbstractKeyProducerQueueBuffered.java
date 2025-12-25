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

import java.math.BigInteger;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaReceiver;
import org.slf4j.Logger;

/**
 * Base class to manage secret buffering using a blocking queue.
 * Intended for streaming protocols like WebSocket or ZMQ.
 */
public abstract class AbstractKeyProducerQueueBuffered<T extends CKeyProducerJavaReceiver> extends KeyProducerJava<T> {

    protected final KeyUtility keyUtility;
    protected final BlockingQueue<byte[]> secretQueue;
    protected volatile boolean shouldStop = false;
    
    public AbstractKeyProducerQueueBuffered(T config, KeyUtility keyUtility, Logger logger) {
        super(config, logger);
        this.keyUtility = keyUtility;
        this.secretQueue = new LinkedBlockingQueue<>();
    }

    protected AbstractKeyProducerQueueBuffered(
            T config,
            KeyUtility keyUtility,
            Logger logger,
            BlockingQueue<byte[]> queue
    ) {
        super(config, logger);
        this.keyUtility = keyUtility;
        this.secretQueue = queue;
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly) throws NoMoreSecretsAvailableException {
        verifyWorkSize(overallWorkSize, cKeyProducerJava.maxWorkSize);

        int length = returnStartSecretOnly ? 1 : overallWorkSize;
        BigInteger[] secrets = new BigInteger[length];

        for (int i = 0; i < length; i++) {
            if (shouldStop) {
                throw new NoMoreSecretsAvailableException("Interrupted while waiting for secrets");
            }

            try {
                byte[] secret = secretQueue.poll(getReadTimeout(), TimeUnit.MILLISECONDS);

                if (secret == null) {
                    throw new NoMoreSecretsAvailableException("Timeout while waiting for secret");
                }

                if (secret.length != PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES) {
                    throw new NoMoreSecretsAvailableException("Invalid secret length: " + secret.length);
                }

                secrets[i] = keyUtility.bigIntegerFromUnsignedByteArray(secret);

                if (cKeyProducerJava.logReceivedSecret) {
                    logger.info("Received key: {}", keyUtility.bigIntegerToFixedLengthHex(secrets[i]));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NoMoreSecretsAvailableException("Interrupted while polling secret", e);
            }
        }

        return secrets;
    }
    
    protected void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Add a raw secret (e.g. from socket, ZMQ, websocket).
     */
    protected void addSecret(byte[] secret) {
        if (!shouldStop) {
            if (!secretQueue.offer(secret)) {
                logger.error("Secret queue is full, ignore secret: {}", secret);
            }
        }
    }

    /**
     * Override to define how long createSecrets() should block.
     */
    protected abstract int getReadTimeout();
}
