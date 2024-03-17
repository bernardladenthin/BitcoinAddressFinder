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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import javax.annotation.Nullable;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;

public class ProducerOpenCL extends AbstractProducer {

    private final CProducerOpenCL producerOpenCL;

    @VisibleForTesting
    final ThreadPoolExecutor resultReaderThreadPoolExecutor;
    @VisibleForTesting
    @Nullable
    OpenCLContext openCLContext;

    public ProducerOpenCL(CProducerOpenCL producerOpenCL, Consumer consumer, KeyUtility keyUtility, SecretFactory secretFactory, ProducerCompletionCallback producerCompletionCallback) {
        super(consumer, keyUtility, secretFactory, producerCompletionCallback, producerOpenCL.runOnce);
        this.producerOpenCL = producerOpenCL;
        this.resultReaderThreadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(producerOpenCL.maxResultReaderThreads);
    }

    @Override
    public void initProducer() {
        openCLContext = new OpenCLContext(producerOpenCL);
        try {
            openCLContext.init();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void produceKeys() {
        if (openCLContext == null) {
            throw new IllegalStateException("openCLContext is null");
        }
        
        BigInteger secret = null;
        try {
            secret = secretFactory.createSecret(producerOpenCL.privateKeyMaxNumBits);
            
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
    
    @VisibleForTesting
    void waitTillFreeThreadsInPool() throws InterruptedException {
        while(getFreeThreads() < 1) {
            Thread.sleep(producerOpenCL.delayBlockedReader);
            getLogger().trace("No possible free threads to read OpenCL results. May increase maxResultReaderThreads.");
        }
    }

    @VisibleForTesting
    int getFreeThreads() {
        return resultReaderThreadPoolExecutor.getMaximumPoolSize() - resultReaderThreadPoolExecutor.getActiveCount();
    }

    @Override
    public void releaseProducers() {
        if (openCLContext != null) {
            openCLContext.release();
            openCLContext = null;
        }
    }

}
