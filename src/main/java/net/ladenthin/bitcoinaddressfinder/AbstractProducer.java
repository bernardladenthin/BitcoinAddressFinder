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

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractProducer implements Producer {
    
    private final static int SLEEP_WAIT_TILL_RUNNING = 10;

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    protected final AtomicBoolean running = new AtomicBoolean(false);
    
    protected final CProducer cProducer;
    protected final Consumer consumer;
    protected final KeyUtility keyUtility;
    protected final SecretFactory secretFactory;
    protected final ProducerCompletionCallback producerCompletionCallback;
    protected final boolean runOnce;
    
    protected volatile ProducerState state = ProducerState.UNINITIALIZED;
    
    protected final AtomicBoolean shouldRun = new AtomicBoolean(true);

    public AbstractProducer(CProducer cProducer, Consumer consumer, KeyUtility keyUtility, SecretFactory secretFactory, ProducerCompletionCallback producerCompletionCallback, boolean runOnce) {
        this.cProducer = cProducer;
        this.consumer = consumer;
        this.keyUtility = keyUtility;
        this.secretFactory = secretFactory;
        this.producerCompletionCallback = producerCompletionCallback;
        this.runOnce = runOnce;
    }

    @Override
    public void initProducer() {
        this.state = ProducerState.INITIALIZED;
    }

    @Override
    public void releaseProducers() {
        this.state = ProducerState.RELEASED;
    }

    @Override
    public void run() {
        state = ProducerState.RUNNING;
        while (shouldRun.get()) {
            produceKeys();
            if (runOnce) {
                break;
            }
        }
        state = ProducerState.NOT_RUNNING;
        producerCompletionCallback.producerFinished();
    }
    
    @Override
    public void produceKeys() {
        try {
            BigInteger secret;
            try {
                secret = secretFactory.createSecret(cProducer.privateKeyMaxNumBits);
            } catch (NoMoreSecretsAvailableException ex) {
                logNoMoreSecretsInSecretFactory();
                interrupt();
                return;
            }
            
            if (PublicKeyBytes.isInvalid(secret)) {
                return;
            }
            
            processSecret(secret);
         } catch (RuntimeException e) {
            logErrorInProduceKeys(e);
            throw e;
        }
    }
    
    @Override
    public void processSecret(BigInteger secret) {
        BigInteger secretBase = createSecretBase(secret, cProducer.logSecretBase);
        processSecretBase(secretBase);
    }
    
    /**
     * fromPrivate can throw an {@link IllegalArgumentException}.
     * @param secret the secret to be able to recover the issue
     */
    protected void logErrorInProduceKeys(Exception e, BigInteger secret) {
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
            } catch (InterruptedException ex) {
            }
        }
    }

    public BigInteger createSecretBase(BigInteger secret, boolean logSecretBase) {
        BigInteger secretBase = keyUtility.killBits(secret, cProducer.getKillBits());
        
        if(logSecretBase) {
            logger.info("secretBase: " + org.bouncycastle.util.encoders.Hex.toHexString(secretBase.toByteArray()) + "/" + cProducer.gridNumBits);
        }
            
        if (logger.isTraceEnabled()) {
            logger.trace("secret BigInteger: " + secret);
            logger.trace("secret as byte array: " + Hex.encodeHexString(secret.toByteArray()));
            logger.trace("killBits: " + Hex.encodeHexString(cProducer.getKillBits().toByteArray()));
            logger.trace("secretBase: " + secretBase);
            logger.trace("secretBase as byte array: " + Hex.encodeHexString(secretBase.toByteArray()));
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
        state = ProducerState.INTERRUPTED;
        shouldRun.set(false);
    }
    
    @Override
    public ProducerState getState() {
        return state;
    }
}
