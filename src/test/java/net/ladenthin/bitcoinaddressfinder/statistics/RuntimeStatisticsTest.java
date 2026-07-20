// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.statistics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class RuntimeStatisticsTest {

    // <editor-fold defaultstate="collapsed" desc="batchesByProducer">
    @Test
    public void incrementBatches_multipleLabels_snapshotCountsAndSortedByLabel() {
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();
        runtimeStatistics.incrementBatches("b (Random, GPU)");
        runtimeStatistics.incrementBatches("b (Random, GPU)");
        runtimeStatistics.incrementBatches("a (Random, CPU)");

        Map<String, Long> snapshot = runtimeStatistics.batchesByProducerSnapshot();

        assertThat(snapshot.get("b (Random, GPU)"), is(equalTo(2L)));
        assertThat(snapshot.get("a (Random, CPU)"), is(equalTo(1L)));
        // sorted by label: "a ..." comes before "b ..."
        assertThat(snapshot.keySet().iterator().next(), is(equalTo("a (Random, CPU)")));
    }

    @Test
    public void batchesByProducerSnapshot_noBatches_isEmpty() {
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();

        assertThat(runtimeStatistics.batchesByProducerSnapshot().isEmpty(), is(equalTo(true)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="runningProducersGauge">
    @Test
    public void getRunningProducers_defaultGauge_returnsZero() {
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();

        assertThat(runtimeStatistics.getRunningProducers(), is(equalTo(0L)));
    }

    @Test
    public void getRunningProducers_afterSetGauge_returnsGaugeValue() {
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();
        runtimeStatistics.setRunningProducersGauge(() -> 5L);

        assertThat(runtimeStatistics.getRunningProducers(), is(equalTo(5L)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="generatedKeys">
    @Test
    public void getGeneratedKeys_initially_returnsZero() {
        assertThat(new RuntimeStatistics().getGeneratedKeys(), is(equalTo(0L)));
    }

    @Test
    public void addGeneratedKeys_accumulatesAcrossBatches() {
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();
        runtimeStatistics.addGeneratedKeys(4_194_304L); // one 2^22 grid
        runtimeStatistics.addGeneratedKeys(4_194_304L); // a second one

        assertThat(runtimeStatistics.getGeneratedKeys(), is(equalTo(8_388_608L)));
    }

    @Test
    public void generatedKeys_isIndependentOfBatchCounts() {
        // The two counters answer different questions: generatedKeys is the candidate total that
        // entered the pipeline, batchesByProducer is how many dispatches produced it. A test that
        // conflated them would hide a regression in either.
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();
        runtimeStatistics.incrementBatches("p (Random, GPU)");
        runtimeStatistics.addGeneratedKeys(1_000_000L);

        assertThat(runtimeStatistics.getGeneratedKeys(), is(equalTo(1_000_000L)));
        assertThat(runtimeStatistics.batchesByProducerSnapshot().get("p (Random, GPU)"), is(equalTo(1L)));
    }
    // </editor-fold>
}
