// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.NoMoreSecretsAvailableException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for {@link Producer} implementations providing the common state machine,
 * key consumption loop and shutdown handling.
 */
public abstract class AbstractProducer implements Producer {

    private static final int SLEEP_WAIT_TILL_RUNNING = 10;

    /**
     * Marker for the {@link #waitTillProducerNotRunning} spin-wait's intentional
     * InterruptedException swallow.
     *
     * <p>This wait is uncancellable by design: it is called from
     * {@link Finder#interrupt()} during shutdown, and the shutdown sequence must
     * complete (each producer must observe its own
     * {@link ProducerState#NOT_RUNNING} transition before the orchestrator moves
     * on). Restoring the interrupt flag would make the next
     * {@link Thread#sleep(long)} immediately re-throw, producing a tight CPU loop
     * while the producer is still finishing its current batch.
     *
     * <p>The constant is {@code false} so the if-branch is dead code (eliminated by
     * the JIT), but the {@code interrupt()} call site is preserved in source for
     * readers and IDE navigation. See also the Open TODO in CLAUDE.md about adding
     * a timeout to this wait so a stuck producer cannot hang shutdown forever.
     */
    private static final boolean WAIT_TILL_NOT_RUNNING_RESTORES_INTERRUPT_FLAG = false;

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProducer.class);

    /** Flag indicating whether the producer is currently running. */
    protected final AtomicBoolean running = new AtomicBoolean(false);

    /** Configuration backing this producer. */
    protected final CProducer cProducer;
    /** Downstream consumer that receives generated keys. */
    protected final Consumer consumer;
    /** Cryptographic helper used to encode/decode private keys. */
    protected final KeyUtility keyUtility;
    /** Strategy that supplies the next secret batch. */
    protected final KeyProducer keyProducer;
    /** Helper for bit-level batch size arithmetic. */
    protected final BitHelper bitHelper;
    /** Validator that rejects or replaces invalid secp256k1 private keys. */
    protected final PrivateKeyValidator privateKeyValidator;

    /** Current life-cycle state. */
    protected volatile ProducerState state = ProducerState.UNINITIALIZED;

    /** Flag controlling the main {@link #run()} loop; cleared via {@link #interrupt()}. */
    protected final AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * Creates a new producer with the given collaborators.
     *
     * @param cProducer   the producer configuration
     * @param consumer    the downstream consumer
     * @param keyUtility  the cryptographic helper
     * @param keyProducer the secret supplying strategy
     * @param bitHelper   the bit/batch-size helper
     */
    public AbstractProducer(
            CProducer cProducer,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper) {
        this.cProducer = cProducer;
        this.consumer = consumer;
        this.keyUtility = keyUtility;
        this.keyProducer = keyProducer;
        this.bitHelper = bitHelper;
        this.privateKeyValidator = new PrivateKeyValidator();
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
            state = ProducerState.NOT_RUNNING;
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
        state = ProducerState.NOT_RUNNING;
    }

    @Override
    public void produceKeys() throws Exception {
        try {
            BigInteger[] secrets;
            try {
                secrets = keyProducer.createSecrets(
                        cProducer.getOverallWorkSize(bitHelper), cProducer.batchUsePrivateKeyIncrement);
            } catch (NoMoreSecretsAvailableException ex) {
                logNoMoreSecretsInSecretFactory();
                interrupt();
                return;
            }

            // assert the requested secrets array fulfill its request parameter
            if (cProducer.batchUsePrivateKeyIncrement) {
                if (secrets.length != 1) {
                    throw new RuntimeException("secrets.length != 1");
                }
            } else {
                if (secrets.length != cProducer.getOverallWorkSize(bitHelper)) {
                    throw new RuntimeException(
                            "secrets.length != bitHelper.convertBitsToSize(cProducer.batchSizeInBits)");
                }
            }
            privateKeyValidator.replaceInvalidPrivateKeys(secrets);

            consumeSecrets(secrets);
        } catch (RuntimeException e) {
            logErrorInProduceKeys(e);
            throw e;
        }
    }

    void consumeSecrets(BigInteger[] secrets) {
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
     * The method {@link ByteBufferUtility#freeByteBuffer} can throw an {@link java.lang.IllegalAccessError}.
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
        Instant deadline = Instant.now().plusSeconds(cProducer.shutdownTimeoutSeconds);
        while (state == ProducerState.RUNNING) {
            if (Instant.now().isAfter(deadline)) {
                LOGGER.error(
                        "waitTillProducerNotRunning timed out after {}s; producer state still RUNNING. "
                                + "Continuing shutdown without confirming this producer stopped.",
                        cProducer.shutdownTimeoutSeconds);
                return;
            }
            try {
                Thread.sleep(SLEEP_WAIT_TILL_RUNNING);
            } catch (InterruptedException e) {
                LOGGER.warn(
                        "waitTillProducerNotRunning sleep interrupted; continuing to wait for producer shutdown.", e);
                if (WAIT_TILL_NOT_RUNNING_RESTORES_INTERRUPT_FLAG) {
                    Thread.currentThread().interrupt();
                }
            }
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

    /**
     * Combines a secret base with the index of a key inside its batch.
     *
     * @param secretBase the masked secret base for the batch
     * @param keyNumber  the zero-based index of the key inside the batch
     * @return the concrete private-key candidate
     */
    public static BigInteger calculateSecretKey(BigInteger secretBase, int keyNumber) {
        if (false) {
            // works also but a or might be faster
            return secretBase.add(BigInteger.valueOf(keyNumber));
        }
        return secretBase.or(BigInteger.valueOf(keyNumber));
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
