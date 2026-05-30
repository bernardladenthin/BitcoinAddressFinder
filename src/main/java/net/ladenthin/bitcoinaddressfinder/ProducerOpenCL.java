// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GPU-based producer using OpenCL kernels to derive public keys in batches.
 */
public class ProducerOpenCL extends AbstractProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerOpenCL.class);

    private final CProducerOpenCL producerOpenCL;

    @VisibleForTesting
    final ThreadPoolExecutor resultReaderThreadPoolExecutor;

    @VisibleForTesting
    @Nullable
    OpenCLContext openCLContext;

    /**
     * Creates a new OpenCL producer.
     *
     * @param producerOpenCL the OpenCL producer configuration
     * @param consumer       the downstream consumer
     * @param keyUtility     cryptographic helper
     * @param keyProducer    the secret supplying strategy
     * @param bitHelper      bit/batch-size helper
     */
    public ProducerOpenCL(
            CProducerOpenCL producerOpenCL,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper) {
        super(producerOpenCL, consumer, keyUtility, keyProducer, bitHelper);
        this.producerOpenCL = producerOpenCL;
        this.resultReaderThreadPoolExecutor =
                (ThreadPoolExecutor) Executors.newFixedThreadPool(producerOpenCL.maxResultReaderThreads);
        if (false) {
            int prestartedThreads = resultReaderThreadPoolExecutor.prestartAllCoreThreads();
            if (prestartedThreads != producerOpenCL.maxResultReaderThreads) {
                throw new RuntimeException("Unable to prestart core threads.");
            }
        }
    }

    @Override
    public void initProducer() throws Exception {
        super.initProducer();
        openCLContext = new OpenCLContext(producerOpenCL, bitHelper);
        openCLContext.init();
    }

    @Override
    public void processSecretBase(BigInteger secretBase) {
        if (openCLContext == null) {
            throw new IllegalStateException("ProducerOpenCL not initialized");
        }
        try {
            waitTillFreeThreadsInPool();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("openCLContext.createKeys for secretBase: " + secretBase);
            }
            OpenCLGridResult openCLGridResult = openCLContext.createKeys(secretBase);
            ResultReaderRunnable resultReaderRunnable =
                    new ResultReaderRunnable(openCLGridResult, consumer, secretBase, this);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("submit resultReaderRunnable for secretBase: " + secretBase);
            }
            // Use execute() rather than submit() because we never consume the
            // returned Future: ResultReaderRunnable reports completion via the
            // consumer pipeline, and this producer submits in a tight loop
            // without joining per-task.
            resultReaderThreadPoolExecutor.execute(resultReaderRunnable);
        } catch (Exception e) {
            logErrorInProduceKeys(e, secretBase);
        }
    }

    @Override
    public void processSecrets(BigInteger[] secrets) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Reads OpenCL kernel results back to the host and forwards them to the consumer.
     */
    protected static class ResultReaderRunnable implements Runnable {

        private static final Logger LOGGER = LoggerFactory.getLogger(ResultReaderRunnable.class);

        private final OpenCLGridResult openCLGridResult;
        private final Consumer consumer;
        private final BigInteger secretBase;
        private final AbstractProducer abstractProducer;

        ResultReaderRunnable(
                OpenCLGridResult openCLGridResult,
                Consumer consumer,
                BigInteger secretBase,
                AbstractProducer abstractProducer) {
            this.openCLGridResult = openCLGridResult;
            this.consumer = consumer;
            this.secretBase = secretBase;
            this.abstractProducer = abstractProducer;
        }

        @Override
        public void run() {
            LOGGER.trace("ResultReaderRunnable started");
            try {
                PublicKeyBytes[] publicKeyBytesArray = openCLGridResult.getPublicKeyBytes();

                final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
                byteBufferUtility.freeByteBuffer(openCLGridResult.getResult());

                consumer.consumeKeys(publicKeyBytesArray);
            } catch (Throwable e) {
                abstractProducer.logErrorInProduceKeys(e, secretBase);
            }
            LOGGER.trace("ResultReaderRunnable finished");
        }
    }

    @VisibleForTesting
    void waitTillFreeThreadsInPool() throws InterruptedException {
        while (getFreeThreads() < 1) {
            Thread.sleep(producerOpenCL.delayBlockedReader);
            LOGGER.trace("No possible free threads to read OpenCL results. May increase maxResultReaderThreads.");
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
