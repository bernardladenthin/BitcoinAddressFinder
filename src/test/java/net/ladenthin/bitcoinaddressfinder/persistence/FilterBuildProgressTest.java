// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilterBuildProgressTest {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final List<String> lines = new ArrayList<>();
    private final FilterBuildProgress.LogSink sink = lines::add;

    /** Manually advanced clock so rendered rate/ETA are deterministic. */
    private static final class FakeClock implements FilterBuildProgress.NanoClock {
        private long nanos;

        @Override
        public long nanoTime() {
            return nanos;
        }

        void advanceSeconds(long seconds) {
            nanos += seconds * NANOS_PER_SECOND;
        }
    }

    private final FakeClock clock = new FakeClock();

    @Test
    void smallTotal_belowMinEntries_neverLogs() {
        FilterBuildProgress progress =
                new FilterBuildProgress(sink, "phase", FilterBuildProgress.MIN_ENTRIES - 1, clock);

        // even reporting completion of every unit must stay silent for small inputs
        for (long done = 1; done <= FilterBuildProgress.MIN_ENTRIES - 1; done += 50_000) {
            progress.report(done);
        }
        progress.report(FilterBuildProgress.MIN_ENTRIES - 1);

        assertThat(lines, is(empty()));
    }

    @Test
    void largeTotal_belowFirstMilestone_doesNotLogYet() {
        long total = FilterBuildProgress.MIN_ENTRIES; // step = total / SEGMENTS
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", total, clock);

        progress.report((total / FilterBuildProgress.SEGMENTS) - 1);

        assertThat(lines, is(empty()));
    }

    @Test
    void largeTotal_firstMilestone_logsPercentCountsRateAndEta() {
        long total = FilterBuildProgress.MIN_ENTRIES; // 1_000_000, step = 100_000
        long step = total / FilterBuildProgress.SEGMENTS;
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", total, clock);

        // 100_000 units in 1s => 100k/s; 900_000 remaining => ETA 9s
        clock.advanceSeconds(1);
        progress.report(step);

        assertThat(lines, contains("phase: 10% (100000/1000000), 100k/s, ETA 9s, elapsed 1s."));
    }

    @Test
    void largeTotal_fullSweep_logsOncePerSegment() {
        long total = 2_000_000L; // step = 200_000, exactly SEGMENTS milestones
        long step = total / FilterBuildProgress.SEGMENTS;
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", total, clock);

        for (long done = step; done <= total; done += step) {
            clock.advanceSeconds(1);
            progress.report(done);
        }

        assertThat(lines, hasSize(FilterBuildProgress.SEGMENTS));
        assertThat(lines.get(0), is("phase: 10% (200000/2000000), 200k/s, ETA 9s, elapsed 1s."));
        // final line is 100% complete: no remaining work, so no ETA is rendered
        assertThat(lines.get(FilterBuildProgress.SEGMENTS - 1), containsString("100% (2000000/2000000)"));
        assertThat(lines.get(FilterBuildProgress.SEGMENTS - 1), not(containsString("ETA")));
    }

    @Test
    void largeTotal_singleGiantJump_logsOnlyOnce() {
        long total = 5_000_000L;
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", total, clock);

        // one report that overshoots every milestone must still emit at most one line
        clock.advanceSeconds(1);
        progress.report(total);

        assertThat(lines, hasSize(1));
        assertThat(lines.get(0), containsString("100% (5000000/5000000)"));
    }

    /**
     * The ETA must follow a throughput change rather than the cumulative average. This is the
     * behaviour that matters in production: a build runs at page-cache speed until the source walk
     * leaves the cached region, then drops sharply (measured ~7x on the billion-entry tier). A
     * cumulative-average ETA would stay anchored to the fast early regime and under-predict badly.
     */
    @Test
    void eta_reflectsRecentSegmentRate_notCumulativeAverage() {
        long total = 10_000_000L; // step = 1_000_000
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", total, clock);

        // Fast regime: 1M units in 1s => 1.0M/s, 9M remaining => ETA 9s
        clock.advanceSeconds(1);
        progress.report(1_000_000L);
        assertThat(lines.get(0), containsString("1.0M/s"));
        assertThat(lines.get(0), containsString("ETA 9s"));

        // Slow regime: the next 1M takes 10s => 100k/s. Cumulative average would still be
        // ~181k/s (2M/11s) and predict ~44s; the windowed rate must instead predict 8M/100k/s = 80s.
        clock.advanceSeconds(10);
        progress.report(2_000_000L);
        assertThat(lines.get(1), containsString("100k/s"));
        assertThat(lines.get(1), containsString("ETA 1m20s"));
        assertThat(lines.get(1), containsString("elapsed 11s"));
    }

    @Test
    void durations_areRenderedCompactlyAcrossMagnitudes() {
        long total = 100_000_000L; // step = 10_000_000
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", total, clock);

        // 10M units in 10s => 1.0M/s; 90M remaining => ETA 90s => "1m30s"
        clock.advanceSeconds(10);
        progress.report(10_000_000L);
        assertThat(lines.get(0), containsString("ETA 1m30s"));

        // next 10M in 1000s => 10k/s; 80M remaining => 8000s => "2h13m"
        clock.advanceSeconds(1000);
        progress.report(20_000_000L);
        assertThat(lines.get(1), containsString("10k/s"));
        assertThat(lines.get(1), containsString("ETA 2h13m"));
        assertThat(lines.get(1), containsString("elapsed 16m50s"));
    }
}
