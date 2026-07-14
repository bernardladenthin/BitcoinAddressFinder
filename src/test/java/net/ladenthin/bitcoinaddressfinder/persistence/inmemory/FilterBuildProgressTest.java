// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilterBuildProgressTest {

    private final List<String> lines = new ArrayList<>();
    private final FilterBuildProgress.LogSink sink = lines::add;

    @Test
    void smallTotal_belowMinEntries_neverLogs() {
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", FilterBuildProgress.MIN_ENTRIES - 1);

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
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", total);

        progress.report((total / FilterBuildProgress.SEGMENTS) - 1);

        assertThat(lines, is(empty()));
    }

    @Test
    void largeTotal_firstMilestone_logsCorrectPercentDoneAndTotal() {
        long total = FilterBuildProgress.MIN_ENTRIES; // 1_000_000, step = 100_000
        long step = total / FilterBuildProgress.SEGMENTS;
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", total);

        progress.report(step);

        assertThat(lines, contains("phase: 10% (100000/1000000)."));
    }

    @Test
    void largeTotal_fullSweep_logsOncePerSegment() {
        long total = 2_000_000L; // step = 200_000, exactly SEGMENTS milestones
        long step = total / FilterBuildProgress.SEGMENTS;
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", total);

        for (long done = step; done <= total; done += step) {
            progress.report(done);
        }

        assertThat(lines, hasSize(FilterBuildProgress.SEGMENTS));
        assertThat(lines.get(0), is("phase: 10% (200000/2000000)."));
        assertThat(lines.get(FilterBuildProgress.SEGMENTS - 1), is("phase: 100% (2000000/2000000)."));
    }

    @Test
    void largeTotal_singleGiantJump_logsOnlyOnce() {
        long total = 5_000_000L;
        FilterBuildProgress progress = new FilterBuildProgress(sink, "phase", total);

        // one report that overshoots every milestone must still emit at most one line
        progress.report(total);

        assertThat(lines, hasSize(1));
        assertThat(lines.get(0), is("phase: 100% (5000000/5000000)."));
    }
}
