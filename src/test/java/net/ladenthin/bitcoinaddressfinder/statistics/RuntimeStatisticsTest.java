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

    private static final String PRODUCER_GPU = "exampleOpenCL (Incremental, GPU)";
    private static final String PRODUCER_CPU = "exampleRandom (Random, CPU)";

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
        runtimeStatistics.addGeneratedKeys(PRODUCER_GPU, 4_194_304L); // one 2^22 grid
        runtimeStatistics.addGeneratedKeys(PRODUCER_GPU, 4_194_304L); // a second one

        assertThat(runtimeStatistics.getGeneratedKeys(), is(equalTo(8_388_608L)));
    }

    @Test
    public void generatedKeys_isIndependentOfBatchCounts() {
        // The two counters answer different questions: generatedKeys is the candidate total that
        // entered the pipeline, batchesByProducer is how many dispatches produced it. A test that
        // conflated them would hide a regression in either.
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();
        runtimeStatistics.incrementBatches(PRODUCER_GPU);
        runtimeStatistics.addGeneratedKeys(PRODUCER_GPU, 1_000_000L);

        assertThat(runtimeStatistics.getGeneratedKeys(), is(equalTo(1_000_000L)));
        assertThat(runtimeStatistics.batchesByProducerSnapshot().get(PRODUCER_GPU), is(equalTo(1L)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="generatedKeysByProducer">
    /**
     * The whole point of the per-producer candidate counter: with two producers whose
     * {@code batchSizeInBits} differ, equal batch counts do <b>not</b> mean equal work. Here both
     * producers dispatch one batch each, yet one contributes 8× the candidates — the batch snapshot
     * cannot show that, the generated snapshot must.
     */
    @Test
    public void generatedKeysByProducerSnapshot_equalBatchesDifferentGridSizes_showsTheRealSplit() {
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();
        runtimeStatistics.incrementBatches(PRODUCER_GPU);
        runtimeStatistics.addGeneratedKeys(PRODUCER_GPU, 16_777_216L); // 2^24 grid
        runtimeStatistics.incrementBatches(PRODUCER_CPU);
        runtimeStatistics.addGeneratedKeys(PRODUCER_CPU, 2_097_152L); // 2^21 grid

        Map<String, Long> batches = runtimeStatistics.batchesByProducerSnapshot();
        Map<String, Long> generated = runtimeStatistics.generatedKeysByProducerSnapshot();

        assertThat(batches.get(PRODUCER_GPU), is(equalTo(batches.get(PRODUCER_CPU))));
        assertThat(generated.get(PRODUCER_GPU), is(equalTo(16_777_216L)));
        assertThat(generated.get(PRODUCER_CPU), is(equalTo(2_097_152L)));
    }

    @Test
    public void generatedKeysByProducerSnapshot_multipleLabels_sortedByLabel() {
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();
        runtimeStatistics.addGeneratedKeys("b (Random, GPU)", 2L);
        runtimeStatistics.addGeneratedKeys("a (Random, CPU)", 1L);

        Map<String, Long> snapshot = runtimeStatistics.generatedKeysByProducerSnapshot();

        assertThat(snapshot.keySet().iterator().next(), is(equalTo("a (Random, CPU)")));
    }

    @Test
    public void generatedKeysByProducerSnapshot_noKeys_isEmpty() {
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();

        assertThat(runtimeStatistics.generatedKeysByProducerSnapshot().isEmpty(), is(equalTo(true)));
    }

    /**
     * The per-producer shares must sum to the lifetime total, otherwise the rendered percentages
     * would not add up to 100 %.
     */
    @Test
    public void generatedKeysByProducerSnapshot_sumsToTheLifetimeTotal() {
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();
        runtimeStatistics.addGeneratedKeys(PRODUCER_GPU, 16_777_216L);
        runtimeStatistics.addGeneratedKeys(PRODUCER_CPU, 2_097_152L);

        long sumOfShares = runtimeStatistics.generatedKeysByProducerSnapshot().values().stream()
                .mapToLong(Long::longValue)
                .sum();

        assertThat(sumOfShares, is(equalTo(runtimeStatistics.getGeneratedKeys())));
    }
    // </editor-fold>
}
