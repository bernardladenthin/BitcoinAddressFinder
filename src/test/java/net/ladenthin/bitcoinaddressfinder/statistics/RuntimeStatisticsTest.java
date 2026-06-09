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
}
