// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;

/**
 * The shape of an OpenCL producer that is shut down while its consumer is stuck, without a device: the
 * grid in progress ends when the test says so, and the result readers never finish, so the wait for the
 * pending batches runs to its bound every time. That bound is {@link #remainingShutdownNanos()}, the one
 * {@link ProducerOpenCL#awaitPendingBatches()} uses.
 */
public class StuckReadersProducerTestImpl extends AbstractProducerTestImpl {

    /** Upper bound of the grid in progress; a test that forgets to finish it fails instead of hanging. */
    private static final Duration GRID_MAX = Duration.ofSeconds(30);

    private final CountDownLatch gridStarted = new CountDownLatch(1);

    private final CountDownLatch gridFinished = new CountDownLatch(1);

    /** Never counted down: the readers are stuck in the consumer for the whole test. */
    private final CountDownLatch readersDone = new CountDownLatch(1);

    public StuckReadersProducerTestImpl(
            CProducer cProducer,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper,
            RuntimeStatistics runtimeStatistics) {
        super(cProducer, consumer, keyUtility, keyProducer, bitHelper, runtimeStatistics);
    }

    /**
     * Waits until the producer is inside its first grid.
     *
     * @param timeout how long to wait at most
     * @return {@code true} once the grid is in progress, {@code false} on timeout
     * @throws InterruptedException if interrupted while waiting
     */
    boolean awaitGridStarted(Duration timeout) throws InterruptedException {
        return gridStarted.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    /** Lets the grid in progress finish; the run loop then sees whether it was interrupted. */
    void finishGrid() {
        gridFinished.countDown();
    }

    @Override
    public void produceKeys() {
        gridStarted.countDown();
        try {
            if (!gridFinished.await(GRID_MAX.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("the grid in progress was not finished within " + GRID_MAX);
            }
        } catch (InterruptedException e) {
            // the test's executor shutting down: the grid ends here, the run loop ends on its own
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void awaitPendingBatches() {
        try {
            readersDone.await(remainingShutdownNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
