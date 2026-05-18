// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
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

    /**
     * Sentinel value pushed onto {@link #secretQueue} by {@link #signalShutdown()}
     * to wake any consumer currently blocked inside {@link #createSecrets(int, boolean)}.
     * Identified by reference equality (==), so it cannot be confused with a real
     * secret: {@link #addSecret(byte[])} only enqueues freshly-received messages.
     */
    private static final byte[] SHUTDOWN_SENTINEL = new byte[0];

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
                int timeout = getReadTimeout();
                byte[] secret;
                if (timeout < 0) {
                    // Block indefinitely until addSecret() enqueues a message or
                    // signalShutdown() pushes the sentinel.
                    secret = secretQueue.take();
                } else {
                    secret = secretQueue.poll(timeout, TimeUnit.MILLISECONDS);
                    if (secret == null) {
                        throw new NoMoreSecretsAvailableException("Timeout while waiting for secret");
                    }
                }

                if (secret == SHUTDOWN_SENTINEL) {
                    throw new NoMoreSecretsAvailableException("Interrupted while waiting for secret");
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
     * Signal that the producer is shutting down. Sets {@link #shouldStop} and
     * wakes any consumer currently blocked inside {@link #createSecrets(int, boolean)}
     * so it can observe the flag and exit promptly. Subclasses should call this from
     * their {@code interrupt()} implementations instead of setting {@code shouldStop}
     * directly.
     *
     * <p>The wake-up uses a sentinel value pushed via
     * {@link BlockingQueue#offer(Object)}, so it never blocks the caller and never
     * throws even when the underlying queue is bounded and full &#x2014; in that
     * case the consumer will read {@code shouldStop} on its next loop iteration
     * after draining the existing entries.
     */
    protected void signalShutdown() {
        shouldStop = true;
        secretQueue.offer(SHUTDOWN_SENTINEL);
    }

    /**
     * Override to define how long createSecrets() should block.
     *
     * <p>A positive value is a per-receive timeout in milliseconds; a negative
     * value (typically {@code -1}) means &quot;block indefinitely&quot; until a
     * message arrives or {@link #signalShutdown()} is called.
     */
    protected abstract int getReadTimeout();
}
