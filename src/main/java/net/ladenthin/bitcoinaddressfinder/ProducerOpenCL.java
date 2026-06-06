// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GPU-based producer using OpenCL kernels to derive public keys in batches.
 */
@ToString(callSuper = true)
public class ProducerOpenCL extends AbstractProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerOpenCL.class);

    private final CProducerOpenCL producerOpenCL;

    // ThreadPoolExecutor toString is verbose internal pool state — not useful in logs.
    @ToString.Exclude
    private final ThreadPoolExecutor resultReaderThreadPoolExecutor;

    /**
     * Slot semaphore that gates submission to {@link #resultReaderThreadPoolExecutor}.
     * Acquired in {@link #processSecretBase(BigInteger)} before {@code execute()},
     * released in the runnable's {@code finally}. Replaces the previous
     * {@code Thread.sleep}-based spin on {@code getActiveCount()}, eliminating
     * idle GPU-pacing latency and giving correct event-driven backpressure on
     * the otherwise unbounded inner work queue (the default
     * {@code Executors.newFixedThreadPool} uses an unbounded
     * {@link java.util.concurrent.LinkedBlockingQueue}; without this semaphore
     * the GPU would submit faster than result-readers can drain, holding result
     * buffers in memory indefinitely).
     */
    @ToString.Exclude
    private final Semaphore submitSlot;

    // OpenCLContext aggregates JOCL native pointers + own state — exposed via the
    // isInitialized() getter below instead so callers see "initialized=true/false"
    // without the heavyweight inner dump.
    @ToString.Exclude
    private @Nullable OpenCLContext openCLContext;

    /**
     * Returns whether the OpenCL context has been initialised and not yet released.
     *
     * <p>The context is {@code null} both before {@link #initProducer()} and after
     * {@link #releaseProducer()}; this getter lets callers and tests observe the
     * initialised/released lifecycle state without needing direct access to the
     * internal {@code openCLContext} field.
     *
     * @return {@code true} when the context has been initialised and is still open;
     *     {@code false} when not yet initialised or after release
     */
    @ToString.Include(name = "initialized")
    public boolean isInitialized() {
        return openCLContext != null;
    }

    /**
     * Creates a new OpenCL producer with a default fixed-size result-reader thread pool
     * sized by {@code producerOpenCL.maxResultReaderThreads}.
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
        this(producerOpenCL, consumer, keyUtility, keyProducer, bitHelper, (ThreadPoolExecutor)
                Executors.newFixedThreadPool(producerOpenCL.maxResultReaderThreads));
    }

    /**
     * Test-friendly constructor that injects the result-reader thread pool.
     *
     * <p>Production callers should use the 5-arg constructor above; this overload exists
     * so tests can substitute their own {@link ThreadPoolExecutor} and assert on its
     * post-shutdown state without reaching into the producer's internal field.
     *
     * @param producerOpenCL                  the OpenCL producer configuration
     * @param consumer                        the downstream consumer
     * @param keyUtility                      cryptographic helper
     * @param keyProducer                     the secret supplying strategy
     * @param bitHelper                       bit/batch-size helper
     * @param resultReaderThreadPoolExecutor  pool used to drain GPU results back to the host
     */
    @VisibleForTesting
    ProducerOpenCL(
            CProducerOpenCL producerOpenCL,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper,
            ThreadPoolExecutor resultReaderThreadPoolExecutor) {
        super(producerOpenCL, consumer, keyUtility, keyProducer, bitHelper);
        this.producerOpenCL = producerOpenCL;
        this.resultReaderThreadPoolExecutor = resultReaderThreadPoolExecutor;
        this.submitSlot = new Semaphore(producerOpenCL.maxResultReaderThreads);
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
        final OpenCLContext localOpenCLContext = openCLContext;
        if (localOpenCLContext == null) {
            throw new IllegalStateException(
                    "ProducerOpenCL.processSecretBase(" + secretBase
                            + ") called before initProducer(); openCLContext is null");
        }
        try {
            submitSlot.acquire();
            boolean submitted = false;
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("openCLContext.createKeys for secretBase: " + secretBase);
                }
                OpenCLGridResult openCLGridResult = localOpenCLContext.createKeys(secretBase);
                ResultReaderRunnable resultReaderRunnable =
                        new ResultReaderRunnable(openCLGridResult, consumer, secretBase, this, submitSlot);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("submit resultReaderRunnable for secretBase: " + secretBase);
                }
                // Use execute() rather than submit() because we never consume the
                // returned Future: ResultReaderRunnable reports completion via the
                // consumer pipeline, and this producer submits in a tight loop
                // without joining per-task.
                resultReaderThreadPoolExecutor.execute(resultReaderRunnable);
                submitted = true;
            } finally {
                if (!submitted) {
                    // Release the permit if createKeys threw or execute rejected
                    // (e.g. shutdown race); otherwise ownership transfers to the
                    // runnable, which releases in its own finally.
                    submitSlot.release();
                }
            }
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
        private final Semaphore submitSlot;

        ResultReaderRunnable(
                OpenCLGridResult openCLGridResult,
                Consumer consumer,
                BigInteger secretBase,
                AbstractProducer abstractProducer,
                Semaphore submitSlot) {
            this.openCLGridResult = openCLGridResult;
            this.consumer = consumer;
            this.secretBase = secretBase;
            this.abstractProducer = abstractProducer;
            this.submitSlot = submitSlot;
        }

        @Override
        public void run() {
            LOGGER.trace("ResultReaderRunnable started");
            try {
                try {
                    PublicKeyBytes[] publicKeyBytesArray = openCLGridResult.getPublicKeyBytes();
                    consumer.consumeKeys(publicKeyBytesArray);
                } catch (Throwable e) {
                    abstractProducer.logErrorInProduceKeys(e, secretBase);
                }
            } finally {
                // Outer finally — survives any task exception or Error so the
                // submitter unblocks even when the result-reader crashes.
                submitSlot.release();
            }
            LOGGER.trace("ResultReaderRunnable finished");
        }
    }

    /**
     * Returns the number of slots currently available for new GPU result-reader
     * submissions. Backed by {@link Semaphore#availablePermits()} so the
     * diagnostic value matches the actual backpressure primitive.
     *
     * @return number of free submission slots
     */
    int getFreeThreads() {
        return submitSlot.availablePermits();
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
