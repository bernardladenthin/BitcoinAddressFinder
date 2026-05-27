// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.NoMoreSecretsAvailableException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for {@link Producer} implementations providing the common state machine,
 * key consumption loop and shutdown handling.
 */
public abstract class AbstractProducer implements Producer {

    private final static int SLEEP_WAIT_TILL_RUNNING = 10;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

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
    public AbstractProducer(CProducer cProducer, Consumer consumer, KeyUtility keyUtility, KeyProducer keyProducer, BitHelper bitHelper) {
        this.cProducer = cProducer;
        this.consumer = consumer;
        this.keyUtility = keyUtility;
        this.keyProducer = keyProducer;
        this.bitHelper = bitHelper;
        this.privateKeyValidator = new PrivateKeyValidator();
    }

    @Override
    public void initProducer() {
        logger.info("Init producer.");
        this.state = ProducerState.INITIALIZED;
    }

    @Override
    public void releaseProducer() {
        logger.info("Release producer.");
    }

    @Override
    public void run() {
        if (!shouldRun.get()) {
            logger.info("Producer was interrupted before it started running.");
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
                logger.error("Error in produceKeys", e);
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
                secrets = keyProducer.createSecrets(cProducer.getOverallWorkSize(bitHelper), cProducer.batchUsePrivateKeyIncrement);
            } catch (NoMoreSecretsAvailableException ex) {
                logNoMoreSecretsInSecretFactory();
                interrupt();
                return;
            }
            
            // assert the requested secrets array fulfill its request parameter
            if (cProducer.batchUsePrivateKeyIncrement) {
                if(secrets.length != 1) {
                    throw new RuntimeException("secrets.length != 1");
                }
            } else {
                if(secrets.length != cProducer.getOverallWorkSize(bitHelper)) {
                    throw new RuntimeException("secrets.length != bitHelper.convertBitsToSize(cProducer.batchSizeInBits)");
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
        logger.error("Error in produceKey for secret " + secret + ".", e);
    }

    /**
     * Logs an unexpected error caught while running {@link #produceKeys()}.
     *
     * @param e the exception to log
     */
    protected void logErrorInProduceKeys(Exception e) {
        logger.error("Error in produceKey", e);
    }

    /**
     * Logs that the underlying secret source has been exhausted.
     */
    protected void logNoMoreSecretsInSecretFactory() {
        logger.error("No more keys in secret factory. Shutdown producer.");
    }

    @Override
    public void waitTillProducerNotRunning() {
        while(state == ProducerState.RUNNING) {
            try {
                Thread.sleep(SLEEP_WAIT_TILL_RUNNING);
            } catch (InterruptedException e) {
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
        BigInteger killBits = bitHelper.getKillBits(cProducer.batchSizeInBits);
        BigInteger secretBase = keyUtility.killBits(secret, killBits);
        
        if(logSecretBase) {
            logger.info("secretBase: " + keyUtility.bigIntegerToFixedLengthHex(secretBase) + "/" + cProducer.batchSizeInBits);
        }
            
        if (logger.isTraceEnabled()) {
            logger.trace("secret BigInteger: " + secret);
            logger.trace("secret as byte array: " + keyUtility.bigIntegerToFixedLengthHex(secret));
            logger.trace("killBits: " + Hex.encodeHexString(killBits.toByteArray()));
            logger.trace("secretBase: " + secretBase);
            logger.trace("secretBase as byte array: " + keyUtility.bigIntegerToFixedLengthHex(secretBase));
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
    
    Logger getLogger() {
        return logger;
    }
    
    void setLogger(Logger logger) {
        this.logger = logger;
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
