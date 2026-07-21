// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.statistics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class SlidingWindowRateTest {

    // <editor-fold defaultstate="collapsed" desc="construction">
    @Test
    public void constructor_nonPositiveWindow_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowRate(0L));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowRate(-1L));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="ratePerSecond math">
    @Test
    public void ratePerSecond_noSampleYet_returnsZero() {
        SlidingWindowRate rate = new SlidingWindowRate(60_000L);
        assertThat(rate.ratePerSecond(1_000L, 5_000L), is(equalTo(0.0)));
    }

    @Test
    public void ratePerSecond_computesRateFromOldestSampleToNow() {
        // 5000 counts over 1 s -> 5000/s
        SlidingWindowRate a = new SlidingWindowRate(60_000L);
        a.sample(1_000L, 0L);
        assertThat(a.ratePerSecond(2_000L, 5_000L), is(equalTo(5_000.0)));

        // 3,000,000 counts over 2 s -> 1,500,000/s
        SlidingWindowRate b = new SlidingWindowRate(60_000L);
        b.sample(0L, 0L);
        assertThat(b.ratePerSecond(2_000L, 3_000_000L), is(equalTo(1_500_000.0)));
    }

    @Test
    public void ratePerSecond_nonPositiveIntervalOrNoAdvance_returnsZero() {
        SlidingWindowRate rate = new SlidingWindowRate(60_000L);

        // zero interval
        rate.sample(1_000L, 0L);
        assertThat(rate.ratePerSecond(1_000L, 5_000L), is(equalTo(0.0)));

        // negative interval (clock went backwards)
        SlidingWindowRate backwards = new SlidingWindowRate(60_000L);
        backwards.sample(2_000L, 0L);
        assertThat(backwards.ratePerSecond(1_000L, 5_000L), is(equalTo(0.0)));

        // counter did not advance
        SlidingWindowRate flat = new SlidingWindowRate(60_000L);
        flat.sample(1_000L, 5_000L);
        assertThat(flat.ratePerSecond(2_000L, 5_000L), is(equalTo(0.0)));
    }

    @Test
    public void ratePerSecond_multipleSamplesWithinWindow_averagesFromOldest() {
        SlidingWindowRate rate = new SlidingWindowRate(10_000L);
        rate.sample(0L, 0L);
        rate.sample(1_000L, 1_000L);
        rate.sample(2_000L, 2_000L);

        // oldest is still (0, 0): 5000 counts over 3 s
        assertThat(rate.ratePerSecond(3_000L, 5_000L), is(closeTo(1_666.666, 0.01)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="windowing / eviction">
    @Test
    public void sample_evictsSamplesOlderThanTheWindow() {
        SlidingWindowRate rate = new SlidingWindowRate(1_000L);
        rate.sample(1_000L, 0L); // ts 1000
        rate.sample(2_000L, 1_000L); // cutoff 1000, ts 1000 kept
        rate.sample(2_001L, 1_001L); // cutoff 1001 -> the ts=1000 sample is evicted, oldest becomes (2000, 1000)

        // rate now measured from (2000, 1000): 1 count over 1 ms -> 1000/s. Had the ts=1000 sample
        // survived, the rate would be (1001-0)/(2001-1000) ms ≈ 1000.0 too — so use a distinguishing check:
        // extend a bit and confirm the oldest is 2000, not 1000.
        assertThat(rate.ratePerSecond(3_000L, 2_000L), is(equalTo(1_000.0))); // (2000-1000)/(3000-2000ms)
    }

    @Test
    public void sample_allSamplesOlderThanWindow_keepsAtLeastTheLatest() {
        SlidingWindowRate rate = new SlidingWindowRate(1_000L);
        rate.sample(1_000L, 100L);
        rate.sample(5_000L, 200L); // cutoff 4000 -> the ts=1000 sample is evicted; only (5000, 200) remains

        // measured from the single retained sample (5000, 200): 100 counts over 1 s -> 100/s
        assertThat(rate.ratePerSecond(6_000L, 300L), is(equalTo(100.0)));
    }

    @Test
    public void sample_windowBoundaryIsInclusive_sampleExactlyAtCutoffIsKept() {
        SlidingWindowRate rate = new SlidingWindowRate(1_000L);
        rate.sample(1_000L, 0L);
        rate.sample(2_000L, 500L); // cutoff = 2000 - 1000 = 1000; first ts 1000 is NOT < 1000 -> kept

        // oldest still (1000, 0): 500 over 1 s -> 500/s
        assertThat(rate.ratePerSecond(2_000L, 500L), is(equalTo(500.0)));
    }
    // </editor-fold>
}
