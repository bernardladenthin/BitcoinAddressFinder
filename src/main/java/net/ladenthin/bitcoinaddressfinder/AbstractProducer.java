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

import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.NoMoreSecretsAvailableException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractProducer implements Producer {
    
    private final static int SLEEP_WAIT_TILL_RUNNING = 10;

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    protected final AtomicBoolean running = new AtomicBoolean(false);
    
    protected final CProducer cProducer;
    protected final Consumer consumer;
    protected final KeyUtility keyUtility;
    protected final KeyProducer keyProducer;
    protected final BitHelper bitHelper;
    
    protected volatile ProducerState state = ProducerState.UNINITIALIZED;
    
    protected final AtomicBoolean shouldRun = new AtomicBoolean(true);

    public AbstractProducer(CProducer cProducer, Consumer consumer, KeyUtility keyUtility, KeyProducer keyProducer, BitHelper bitHelper) {
        this.cProducer = cProducer;
        this.consumer = consumer;
        this.keyUtility = keyUtility;
        this.keyProducer = keyProducer;
        this.bitHelper = bitHelper;
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
            KeyUtility.replaceInvalidPrivateKeys(secrets);
            
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
     * @param secret the secret to be able to recover the issue
     */
    protected void logErrorInProduceKeys(Throwable e, BigInteger secret) {
        logger.error("Error in produceKey for secret " + secret + ".", e);
    }
    
    protected void logErrorInProduceKeys(Exception e) {
        logger.error("Error in produceKey", e);
    }
    
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
