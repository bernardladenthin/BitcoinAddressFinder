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
import org.slf4j.LoggerFactory;

/**
 * Base class to manage secret buffering using a blocking queue.
 * Intended for streaming protocols like WebSocket or ZMQ.
 *
 * @param <T> the configuration type for this receiver-based key producer
 */
public abstract class AbstractKeyProducerQueueBuffered<T extends CKeyProducerJavaReceiver> extends KeyProducerJava<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractKeyProducerQueueBuffered.class);

    /**
     * Sentinel value pushed onto {@link #secretQueue} by {@link #signalShutdown()}
     * to wake any consumer currently blocked inside {@link #createSecrets(int, boolean)}.
     * Identified by reference equality (==), so it cannot be confused with a real
     * secret: {@link #addSecret(byte[])} only enqueues freshly-received messages.
     */
    private static final byte[] SHUTDOWN_SENTINEL = new byte[0];

    /** Shared {@link KeyUtility} for converting between byte arrays and {@link BigInteger}. */
    protected final KeyUtility keyUtility;
    /** Queue of pending secrets received from the underlying transport. */
    protected final BlockingQueue<byte[]> secretQueue;
    /** Flag set to {@code true} once shutdown has been signalled. */
    protected volatile boolean shouldStop = false;

    /**
     * Creates a new instance with an unbounded internal queue.
     *
     * @param config     the receiver configuration
     * @param keyUtility the helper used to decode received byte arrays
     */
    public AbstractKeyProducerQueueBuffered(T config, KeyUtility keyUtility) {
        super(config);
        this.keyUtility = keyUtility;
        this.secretQueue = new LinkedBlockingQueue<>();
    }

    /**
     * Creates a new instance backed by the given queue (mainly for tests).
     *
     * @param config     the receiver configuration
     * @param keyUtility the helper used to decode received byte arrays
     * @param queue      the queue used to buffer received secrets
     */
    protected AbstractKeyProducerQueueBuffered(T config, KeyUtility keyUtility, BlockingQueue<byte[]> queue) {
        super(config);
        this.keyUtility = keyUtility;
        this.secretQueue = queue;
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly)
            throws NoMoreSecretsAvailableException {
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
                    LOGGER.info("Received key: {}", keyUtility.bigIntegerToFixedLengthHex(secrets[i]));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NoMoreSecretsAvailableException("Interrupted while polling secret", e);
            }
        }

        return secrets;
    }

    /**
     * Sleeps for the given duration, restoring the interrupt flag on interruption.
     *
     * @param millis the duration in milliseconds
     */
    protected void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Add a raw secret (e.g. from socket, ZMQ, websocket).
     *
     * @param secret the raw secret bytes to enqueue
     */
    protected void addSecret(byte[] secret) {
        if (!shouldStop) {
            if (!secretQueue.offer(secret)) {
                LOGGER.error("Secret queue is full, ignore secret: {}", secret);
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
        if (!secretQueue.offer(SHUTDOWN_SENTINEL)) {
            LOGGER.trace("Shutdown sentinel not enqueued (queue full); consumer will observe shouldStop after drain.");
        }
    }

    /**
     * Override to define how long createSecrets() should block.
     *
     * <p>A positive value is a per-receive timeout in milliseconds; a negative
     * value (typically {@code -1}) means &quot;block indefinitely&quot; until a
     * message arrives or {@link #signalShutdown()} is called.
     *
     * @return the read timeout in milliseconds, or a negative value to block indefinitely
     */
    protected abstract int getReadTimeout();
}
