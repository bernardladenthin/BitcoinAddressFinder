// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.secret.NoMoreSecretsAvailableException;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.PrivateKeyValidator;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for {@link Producer} implementations providing the common state machine,
 * key consumption loop and shutdown handling.
 */
@ToString
public abstract class AbstractProducer implements Producer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProducer.class);

    /**
     * Counted down once when {@link #run()} transitions to
     * {@link ProducerState#NOT_RUNNING}. {@link #waitTillProducerNotRunning()}
     * awaits this latch with the configured shutdown timeout instead of spinning
     * on the {@code state} field.
     */
    @ToString.Exclude
    protected final CountDownLatch notRunningLatch = new CountDownLatch(1);

    /** Flag indicating whether the producer is currently running. */
    protected final AtomicBoolean running = new AtomicBoolean(false);

    /** Configuration backing this producer. */
    protected final CProducer cProducer;

    /**
     * Downstream consumer that receives generated keys.
     *
     * <p>Excluded from {@link ToString} — stateful coordinator (executors + queue +
     * lifecycle), recursive/heavy in logs.
     */
    @ToString.Exclude
    protected final Consumer consumer;
    /** Cryptographic helper used to encode/decode private keys. */
    protected final KeyUtility keyUtility;

    /**
     * Strategy that supplies the next secret batch.
     *
     * <p>Excluded from {@link ToString} — stateful coordinator covered by its own
     * {@code toString}; including here would produce recursive output.
     */
    @ToString.Exclude
    protected final KeyProducer keyProducer;
    /** Helper for bit-level batch size arithmetic. */
    protected final BitHelper bitHelper;
    /** Validator that rejects or replaces invalid secp256k1 private keys. */
    protected final PrivateKeyValidator privateKeyValidator;

    /** Current life-cycle state. */
    protected volatile ProducerState state = ProducerState.UNINITIALIZED;

    /**
     * Flag controlling the main {@link #run()} loop; cleared via {@link #interrupt()}.
     *
     * <p>Excluded from {@link ToString} — uninformative lifecycle flag.
     */
    @ToString.Exclude
    protected final AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * Shared runtime metrics sink. Producers increment their per-producer batch counter
     * here; the consumer's statistics line reads it. Excluded from {@link ToString} — it
     * is a shared, mutable aggregate, not part of this producer's own identity.
     */
    @ToString.Exclude
    protected final RuntimeStatistics runtimeStatistics;

    /**
     * Class-name prefix shared by all built-in key producers; stripped to derive the short
     * strategy label (e.g. {@code KeyProducerJavaRandom} &rarr; {@code Random}).
     */
    private static final String KEY_PRODUCER_CLASS_PREFIX = "KeyProducerJava";

    /**
     * Creates a new producer with the given collaborators.
     *
     * @param cProducer        the producer configuration
     * @param consumer         the downstream consumer
     * @param keyUtility       the cryptographic helper
     * @param keyProducer      the secret supplying strategy
     * @param bitHelper        the bit/batch-size helper
     * @param runtimeStatistics shared runtime metrics sink for per-producer batch counts
     */
    public AbstractProducer(
            CProducer cProducer,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper,
            RuntimeStatistics runtimeStatistics) {
        this.cProducer = cProducer;
        this.consumer = consumer;
        this.keyUtility = keyUtility;
        this.keyProducer = keyProducer;
        this.bitHelper = bitHelper;
        this.privateKeyValidator = new PrivateKeyValidator();
        this.runtimeStatistics = runtimeStatistics;
    }

    /**
     * Returns the compute backend of this producer, used to label its runtime statistics.
     *
     * @return {@link ProducerType#CPU} or {@link ProducerType#GPU}
     */
    protected abstract ProducerType producerType();

    /**
     * Builds the per-producer statistics label
     * ({@code "<keyProducerId> (<Strategy>, <CPU|GPU>)"}), e.g.
     * {@code "exampleRandom (Random, GPU)"}. The strategy is derived from the bound
     * {@link KeyProducer} class; the backend from {@link #producerType()}.
     *
     * @return the label distinguishing this producer in the runtime statistics
     */
    protected String producerLabel() {
        String simpleName = keyProducer.getClass().getSimpleName();
        String strategy = simpleName.startsWith(KEY_PRODUCER_CLASS_PREFIX)
                ? simpleName.substring(KEY_PRODUCER_CLASS_PREFIX.length())
                : simpleName;
        return String.valueOf(cProducer.keyProducerId) + " (" + strategy + ", " + producerType() + ")";
    }

    @Override
    public void initProducer() throws Exception {
        LOGGER.info("Init producer.");
        this.state = ProducerState.INITIALIZED;
    }

    @Override
    public void releaseProducer() {
        LOGGER.info("Release producer.");
    }

    @Override
    public void run() {
        if (!shouldRun.get()) {
            LOGGER.info("Producer was interrupted before it started running.");
            signalNotRunning();
            return;
        }
        if (state != ProducerState.INITIALIZED) {
            throw new IllegalStateException("Producer not initialized. Current state: " + state);
        }
        state = ProducerState.RUNNING;
        while (shouldRun.get()) {
            try {
                produceKeys();
            } catch (Exception e) {
                LOGGER.error("Error in produceKeys", e);
                break;
            }
            if (cProducer.runOnce) {
                break;
            }
        }
        signalNotRunning();
    }

    /**
     * Transitions the producer to {@link ProducerState#NOT_RUNNING} and releases
     * any thread parked in {@link #waitTillProducerNotRunning()}.
     *
     * <p>Visible to subclasses and tests so a fake producer can simulate the
     * terminal transition without going through {@link #run()}.
     */
    protected void signalNotRunning() {
        state = ProducerState.NOT_RUNNING;
        notRunningLatch.countDown();
    }

    @Override
    public void produceKeys() throws Exception {
        try {
            BigInteger[] secrets;
            try {
                secrets = keyProducer.createSecrets(
                        cProducer.getOverallWorkSize(), cProducer.batchUsePrivateKeyIncrement);
            } catch (NoMoreSecretsAvailableException ex) {
                logNoMoreSecretsInSecretFactory();
                interrupt();
                return;
            }

            // assert the requested secrets array fulfill its request parameter
            if (cProducer.batchUsePrivateKeyIncrement) {
                if (secrets.length != 1) {
                    throw new IllegalStateException("secrets.length=" + secrets.length
                            + " but cProducer.batchUsePrivateKeyIncrement=true requires exactly 1");
                }
            } else {
                if (secrets.length != cProducer.getOverallWorkSize()) {
                    throw new IllegalStateException("secrets.length=" + secrets.length
                            + " != cProducer.getOverallWorkSize()=" + cProducer.getOverallWorkSize());
                }
            }
            privateKeyValidator.replaceInvalidPrivateKeys(secrets);

            consumeSecrets(secrets);
        } catch (RuntimeException e) {
            logErrorInProduceKeys(e);
            throw e;
        }
    }

    void consumeSecrets(BigInteger... secrets) {
        runtimeStatistics.incrementBatches(producerLabel());
        if (cProducer.batchUsePrivateKeyIncrement) {
            BigInteger secret = secrets[0];
            BigInteger secretBase = createSecretBase(secret, cProducer.logSecretBase);
            processSecretBase(secretBase);
        } else {
            processSecrets(secrets);
        }
    }

    /**
     * The method fromPrivate can throw an {@link IllegalArgumentException}.
     *
     * @param e      the throwable describing the failure
     * @param secret the secret to be able to recover the issue
     */
    protected void logErrorInProduceKeys(Throwable e, BigInteger secret) {
        LOGGER.error("Error in produceKey for secret " + secret + ".", e);
    }

    /**
     * Logs an unexpected error caught while running {@link #produceKeys()}.
     *
     * @param e the exception to log
     */
    protected void logErrorInProduceKeys(Exception e) {
        LOGGER.error("Error in produceKey", e);
    }

    /**
     * Logs that the underlying secret source has been exhausted.
     */
    protected void logNoMoreSecretsInSecretFactory() {
        LOGGER.error("No more keys in secret factory. Shutdown producer.");
    }

    @Override
    public void waitTillProducerNotRunning() {
        if (state != ProducerState.RUNNING) {
            return;
        }
        try {
            if (!notRunningLatch.await(cProducer.shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                LOGGER.error(
                        "waitTillProducerNotRunning timed out after {}s; producer state still {}. "
                                + "Continuing shutdown without confirming this producer stopped.",
                        cProducer.shutdownTimeoutSeconds,
                        state);
            }
        } catch (InterruptedException e) {
            LOGGER.warn("waitTillProducerNotRunning interrupted; continuing shutdown.", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Derives the per-batch secret base by masking off the lower bits of {@code secret}.
     *
     * @param secret        the candidate secret
     * @param logSecretBase whether to log the resulting secret base
     * @return the secret base used as the starting point of the next batch
     */
    public BigInteger createSecretBase(BigInteger secret, boolean logSecretBase) {
        BigInteger lowBitMask = bitHelper.getLowBitMask(cProducer.batchSizeInBits);
        BigInteger secretBase = keyUtility.alignDown(secret, lowBitMask);

        if (logSecretBase) {
            LOGGER.info("secretBase: " + keyUtility.bigIntegerToFixedLengthHex(secretBase) + "/"
                    + cProducer.batchSizeInBits);
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("secret BigInteger: " + secret);
            LOGGER.trace("secret as byte array: " + keyUtility.bigIntegerToFixedLengthHex(secret));
            LOGGER.trace("lowBitMask: " + Hex.encodeHexString(lowBitMask.toByteArray()));
            LOGGER.trace("secretBase: " + secretBase);
            LOGGER.trace("secretBase as byte array: " + keyUtility.bigIntegerToFixedLengthHex(secretBase));
        }

        return secretBase;
    }

    @Override
    public void interrupt() {
        shouldRun.set(false);
    }

    @Override
    public ProducerState getState() {
        return state;
    }
}
