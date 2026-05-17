// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProducerOpenCL extends AbstractProducer {

    private final CProducerOpenCL producerOpenCL;

    @VisibleForTesting
    final ThreadPoolExecutor resultReaderThreadPoolExecutor;
    @VisibleForTesting
    @Nullable
    OpenCLContext openCLContext;

    public ProducerOpenCL(CProducerOpenCL producerOpenCL, Consumer consumer, KeyUtility keyUtility, KeyProducer keyProducer, BitHelper bitHelper) {
        super(producerOpenCL, consumer, keyUtility, keyProducer, bitHelper);
        this.producerOpenCL = producerOpenCL;
        this.resultReaderThreadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(producerOpenCL.maxResultReaderThreads);
        if (false) {
            int prestartedThreads = resultReaderThreadPoolExecutor.prestartAllCoreThreads();
            if (prestartedThreads != producerOpenCL.maxResultReaderThreads) {
                throw new RuntimeException("Unable to prestart core threads.");
            }
        }
    }

    @Override
    public void initProducer() {
        super.initProducer();
        openCLContext = new OpenCLContext(producerOpenCL, bitHelper);
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
            if(getLogger().isDebugEnabled()) {
                getLogger().debug("openCLContext.createKeys for secretBase: " + secretBase);
            }
            OpenCLGridResult openCLGridResult = openCLContext.createKeys(secretBase);
            ResultReaderRunnable resultReaderRunnable = new ResultReaderRunnable(openCLGridResult, consumer, secretBase, this);

            if(getLogger().isDebugEnabled()) {
                getLogger().debug("submit resultReaderRunnable for secretBase: " + secretBase);
            }
            resultReaderThreadPoolExecutor.submit(resultReaderRunnable, openCLContext);
        } catch (Exception e) {
            logErrorInProduceKeys(e, secretBase);
        }
    }

    @Override
    public void processSecrets(BigInteger[] secrets) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    protected static class ResultReaderRunnable implements Runnable {
        
        private final Logger logger = LoggerFactory.getLogger(this.getClass());
        
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
            logger.trace("ResultReaderRunnable started");
            try {
                PublicKeyBytes[] publicKeyBytesArray = openCLGridResult.getPublicKeyBytes();

                final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
                byteBufferUtility.freeByteBuffer(openCLGridResult.getResult());

                consumer.consumeKeys(publicKeyBytesArray);
            } catch (Throwable e) {
                abstractProducer.logErrorInProduceKeys(e, secretBase);
            }
            logger.trace("ResultReaderRunnable finished");
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
        resultReaderThreadPoolExecutor.shutdown();
        if (openCLContext != null) {
            openCLContext.close();
            openCLContext = null;
        }
    }

}
