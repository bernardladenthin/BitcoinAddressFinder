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

    public ProducerOpenCL(CProducerOpenCL producerOpenCL, Consumer consumer, KeyUtility keyUtility, SecretFactory secretFactory) {
        super(producerOpenCL, consumer, keyUtility, secretFactory);
        this.producerOpenCL = producerOpenCL;
        this.resultReaderThreadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(producerOpenCL.maxResultReaderThreads);
    }

    @Override
    public void initProducer() {
        super.initProducer();
        openCLContext = new OpenCLContext(producerOpenCL);
        try {
            openCLContext.init();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void processSecretBase(BigInteger secretBase) {
        if (openCLContext == null) {
            throw new IllegalStateException("ProducerOpenCL not initialized");
        }
        try {
            waitTillFreeThreadsInPool();
            OpenCLGridResult openCLGridResult = openCLContext.createKeys(secretBase);
            ResultReaderRunnable resultReaderRunnable = new ResultReaderRunnable(openCLGridResult, consumer, secretBase, this);

            resultReaderThreadPoolExecutor.submit(resultReaderRunnable, openCLContext);
        } catch (Exception e) {
            logErrorInProduceKeys(e, secretBase);
        }
    }
    
    protected static class ResultReaderRunnable implements Runnable {
        
        private final OpenCLGridResult openCLGridResult;
        private final Consumer consumer;
        private final BigInteger secretBase;
        private final AbstractProducer abstractProducer;
        
        ResultReaderRunnable(OpenCLGridResult openCLGridResult, Consumer consumer, BigInteger secretBase, AbstractProducer abstractProducer) {
            this.openCLGridResult = openCLGridResult;
            this.consumer = consumer;
            this.secretBase = secretBase;
            this.abstractProducer = abstractProducer;
        }

        @Override
        public void run() {
            try {
                PublicKeyBytes[] publicKeyBytesArray = openCLGridResult.getPublicKeyBytes();
                openCLGridResult.freeResult();
                consumer.consumeKeys(publicKeyBytesArray);
            } catch (Exception e) {
                abstractProducer.logErrorInProduceKeys(e, secretBase);
            }
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
    public void releaseProducer() {
        super.releaseProducer();
        if (openCLContext != null) {
            openCLContext.release();
            openCLContext = null;
        }
    }

}
