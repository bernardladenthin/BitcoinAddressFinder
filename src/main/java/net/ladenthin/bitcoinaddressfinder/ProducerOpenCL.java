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

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;

public class ProducerOpenCL extends AbstractProducer {

    private final CProducerOpenCL producerOpenCL;

    private ThreadPoolExecutor resultReaderThreadPoolExecutor;
    private OpenCLContext openCLContext;

    public ProducerOpenCL(CProducerOpenCL producerOpenCL, AtomicBoolean shouldRun, Consumer consumer, KeyUtility keyUtility, Random random) {
        super(shouldRun, consumer, keyUtility, random);
        this.producerOpenCL = producerOpenCL;
    }

    @Override
    public void initProducer() {
        resultReaderThreadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(producerOpenCL.maxResultReaderThreads);
        
        openCLContext = new OpenCLContext(producerOpenCL);
        try {
            openCLContext.init();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void produceKeys() {
        BigInteger secret = null;
        try {
            secret = keyUtility.createSecret(producerOpenCL.privateKeyMaxNumBits, random);
            if (PublicKeyBytes.isInvalid(secret)) {
                return;
            }

            final BigInteger secretBase = createSecretBase(producerOpenCL, secret, producerOpenCL.logSecretBase);

            waitTillFreeThreadsInPool();
            OpenCLGridResult createKeys = openCLContext.createKeys(secretBase);
            
            resultReaderThreadPoolExecutor.submit(
                () ->{
                    PublicKeyBytes[] publicKeyBytesArray = createKeys.getPublicKeyBytes();
                    createKeys.freeResult();
                    try {
                        consumer.consumeKeys(publicKeyBytesArray);
                    } catch (Exception e) {
                        logErrorInProduceKeys(e, secretBase);
                    }
                }
            );
        } catch (Exception e) {
            logErrorInProduceKeys(e, secret);
        }
    }
    
    private void waitTillFreeThreadsInPool() throws InterruptedException {
        while(getFreeThreads() < 1) {
            Thread.sleep(producerOpenCL.delayBlockedReader);
            getLogger().trace("No possible free threads to read OpenCL results. May increase maxResultReaderThreads.");
        }
    }

    private int getFreeThreads() {
        return resultReaderThreadPoolExecutor.getMaximumPoolSize() - resultReaderThreadPoolExecutor.getActiveCount();
    }

    @Override
    public void releaseProducers() {
        openCLContext.release();
    }

}
