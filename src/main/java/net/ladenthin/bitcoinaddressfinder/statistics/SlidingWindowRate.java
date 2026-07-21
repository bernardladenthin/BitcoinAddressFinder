// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.statistics;

import java.util.ArrayDeque;
import java.util.Deque;
import lombok.ToString;

/**
 * Trailing (sliding-window) rate estimator over a monotonically increasing counter.
 *
 * <p>Records {@code (timestampMillis, value)} samples, evicts samples that have fallen out of the
 * configured trailing window, and reports the average per-second increase across the retained window.
 * The rate never goes negative: a non-advancing counter or a non-positive time span yields {@code 0}.
 * The window tracks warm-up and load changes instead of a lifetime average.
 *
 * <p><b>Not thread-safe.</b> {@link #sample(long, long)} and {@link #ratePerSecond(long, long)} must be
 * called from a single thread (or under external synchronization). This matches the sampler and printer
 * running on one scheduler in {@code ConsumerJava}, and a single reader elsewhere.
 */
@ToString
public class SlidingWindowRate {

    /** One immutable {@code (timestamp, value)} odometer sample. */
    private record Sample(long timestampMillis, long value) {}

    /** Samples, oldest first. Only touched by the owning single thread. */
    @ToString.Exclude
    private final Deque<Sample> samples = new ArrayDeque<>();

    private final long windowMillis;

    /**
     * Creates a rate estimator with the given trailing window width.
     *
     * @param windowMillis the trailing window width in milliseconds; must be positive
     * @throws IllegalArgumentException if {@code windowMillis <= 0}
     */
    public SlidingWindowRate(long windowMillis) {
        if (windowMillis <= 0L) {
            throw new IllegalArgumentException("windowMillis must be > 0 but was " + windowMillis);
        }
        this.windowMillis = windowMillis;
    }

    /**
     * Records a sample and evicts everything older than the trailing window, always keeping at least
     * one sample so a rate can still be computed.
     *
     * @param nowMillis the sample timestamp (epoch ms)
     * @param value     the current counter value at {@code nowMillis}
     */
    public void sample(long nowMillis, long value) {
        samples.addLast(new Sample(nowMillis, value));
        long cutoff = nowMillis - windowMillis;
        // Keep at least one sample; evict only while the oldest has fallen out of the window.
        while (samples.size() > 1) {
            Sample first = samples.peekFirst();
            if (first == null || first.timestampMillis() >= cutoff) {
                break;
            }
            samples.removeFirst();
        }
    }

    /**
     * Average per-second increase from the oldest retained sample to {@code (nowMillis, value)}.
     *
     * @param nowMillis the current timestamp (epoch ms)
     * @param value     the current counter value
     * @return value/second averaged over the window, or {@code 0} if there is no sample yet, the
     *     interval is non-positive, or the counter did not advance
     */
    public double ratePerSecond(long nowMillis, long value) {
        Sample oldest = samples.peekFirst();
        if (oldest == null) {
            return 0.0;
        }
        long deltaMillis = nowMillis - oldest.timestampMillis();
        if (deltaMillis <= 0L) {
            return 0.0;
        }
        long deltaValue = value - oldest.value();
        if (deltaValue <= 0L) {
            return 0.0;
        }
        return deltaValue * 1_000.0 / deltaMillis;
    }
}
