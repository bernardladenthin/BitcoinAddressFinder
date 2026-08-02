// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ProducerReleaser}.
 *
 * <p>The invariant here used to be implicit — the orchestrator released whatever its producer list
 * held and then cleared the list, so "exactly once" held only as long as nobody else was doing the
 * same thing at the same time. Two callers can arrive at once in practice: the teardown runs both on
 * the ordinary completion path and from the JVM shutdown hook, and a {@code Ctrl+C} landing while a
 * finished run releases its producers is enough to overlap them. Both then read the list before
 * either cleared it and both released the same producer. Against a real driver the second release of
 * an OpenCL producer failed with {@code CL_INVALID_MEM_OBJECT}, turning a successful run into a fatal
 * error at the very end.
 *
 * <p>Making the guarantee an object of its own is what allows it to be stated as a test at all: the
 * assertions below need no GPU, no database and no configuration.
 */
class ProducerReleaserTest {

    /** Number of threads that tear down simultaneously in the concurrency test. */
    private static final int CONCURRENT_CALLERS = 2;

    /** Number of producers released per call. */
    private static final int PRODUCER_COUNT = 3;

    /** Upper bound for the concurrent teardown; a hang here is a failure, not a slow machine. */
    private static final int AWAIT_SECONDS = 30;

    // <editor-fold defaultstate="collapsed" desc="release">
    @Test
    void release_singleCall_releasesEveryProducerOnce() {
        // arrange
        final ProducerReleaser releaser = new ProducerReleaser();
        final List<CountingProducer> producers = createProducers();

        // act
        releaser.release(List.copyOf(producers));

        // assert
        for (CountingProducer producer : producers) {
            assertThat(producer.releaseCount(), is(equalTo(1)));
        }
    }

    @Test
    void release_calledTwiceSequentially_releasesEveryProducerOnce() {
        // arrange
        final ProducerReleaser releaser = new ProducerReleaser();
        final List<CountingProducer> producers = createProducers();

        // act
        releaser.release(List.copyOf(producers));
        releaser.release(List.copyOf(producers));

        // assert
        for (CountingProducer producer : producers) {
            assertThat(producer.releaseCount(), is(equalTo(1)));
        }
    }

    @Test
    void release_calledConcurrentlyFromTwoThreads_releasesEveryProducerOnce() throws Exception {
        // arrange
        final ProducerReleaser releaser = new ProducerReleaser();
        final List<CountingProducer> producers = createProducers();
        // Both callers see the very same producers, which is exactly the situation that produced the
        // double release: each had its own snapshot taken before either had finished.
        final List<Producer> snapshot = List.copyOf(producers);
        final CountDownLatch startLine = new CountDownLatch(1);
        final ExecutorService callers = Executors.newFixedThreadPool(CONCURRENT_CALLERS);

        // act
        try {
            final List<Future<?>> futures = new ArrayList<>(CONCURRENT_CALLERS);
            for (int i = 0; i < CONCURRENT_CALLERS; i++) {
                futures.add(callers.submit(() -> {
                    awaitStartLine(startLine);
                    releaser.release(snapshot);
                }));
            }
            startLine.countDown();
            for (Future<?> future : futures) {
                future.get(AWAIT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            callers.shutdownNow();
        }

        // assert
        for (CountingProducer producer : producers) {
            assertThat(producer.releaseCount(), is(equalTo(1)));
        }
    }
    // </editor-fold>

    /**
     * Creates the producers used by every test in this class.
     *
     * @return freshly created counting producers
     */
    private List<CountingProducer> createProducers() {
        final List<CountingProducer> producers = new ArrayList<>(PRODUCER_COUNT);
        for (int i = 0; i < PRODUCER_COUNT; i++) {
            producers.add(new CountingProducer());
        }
        return producers;
    }

    /**
     * Blocks until every caller has been released at the same moment.
     *
     * @param startLine the latch the callers wait on
     */
    private static void awaitStartLine(CountDownLatch startLine) {
        try {
            startLine.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the start line", e);
        }
    }

    /** Producer stand-in that records how often it was released. */
    private static final class CountingProducer implements Producer {

        private final AtomicInteger releaseCount = new AtomicInteger();

        @Override
        public void releaseProducer() {
            releaseCount.incrementAndGet();
        }

        /**
         * Returns how often {@link #releaseProducer()} was called.
         *
         * @return the release count
         */
        int releaseCount() {
            return releaseCount.get();
        }

        @Override
        public void run() {
            // never started: these tests only exercise the teardown
        }

        @Override
        public void interrupt() {
            // no work to stop
        }

        @Override
        public void initProducer() {
            // nothing to initialise
        }

        @Override
        public void produceKeys() {
            // never produces
        }

        @Override
        public void processSecretBase(BigInteger secretBase) {
            // never produces
        }

        @Override
        public void processSecrets(BigInteger[] secrets) {
            // never produces
        }

        @Override
        public void waitTillProducerNotRunning() {
            // never ran, so it is already not running
        }

        @Override
        public ProducerState getState() {
            return ProducerState.NOT_RUNNING;
        }

        @Override
        public Throwable getLastFailure() {
            throw new UnsupportedOperationException("no run, so no failure to report");
        }
    }
}
