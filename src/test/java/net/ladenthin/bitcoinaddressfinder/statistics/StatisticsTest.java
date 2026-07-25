// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.statistics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

public class StatisticsTest {

    // <editor-fold defaultstate="collapsed" desc="createStatisticsMessage">
    /**
     * The GPU-filtered case: the producers generate far more candidates than reach LMDB, because the
     * GPU pre-filter discards almost all of them. The line must show <b>both</b> ends — the generated
     * rate (the GPU's real output) and the LMDB-lookup rate (what survives) — plus how much was
     * pre-filtered. Before this, only the (tiny) lookup rate was shown, which made a busy GPU look
     * idle.
     */
    @Test
    public void createStatisticsMessage_gpuFiltered_showsGeneratedAndReachingLmdb() {
        // arrange
        Statistics statistics = new Statistics();
        // TreeMap so the rendered per-producer breakdown is deterministic (sorted by label)
        Map<String, Long> generatedKeysByProducer = new TreeMap<>();
        generatedKeysByProducer.put("exampleRandom (Random, CPU)", 900_000_000L);
        generatedKeysByProducer.put("exampleOpenCL (Random, GPU)", 3_000_000_000L);

        // act — 130 M candidates/s generated, only 2 M hash160 lookups/s reach LMDB
        String result = statistics.createStatisticsMessage(
                180_000L, // uptime -> 3 min
                53_000_000L, // lookups against LMDB (lifetime)
                2_000_000.0, // lookup rate/s (post-filter)
                3_900_000_000L, // candidates generated (lifetime)
                130_000_000.0, // generation rate/s (pre-filter)
                60L,
                159_000_000L, // sum of contains time -> avg 3 ms
                generatedKeysByProducer,
                2L,
                4L,
                4567L,
                1234L,
                5678L,
                6789L);

        // assert
        assertThat(
                result,
                is(equalTo("Statistics: [uptime 3 min] [Generated 130 M/s (3900 M total)]"
                        + " [-> LMDB 2 M/s (53 M lookups total), 99.23% pre-filtered] [rate window 60s]"
                        + " [Keys per producer: exampleOpenCL (Random, GPU)=3 G (76.9%),"
                        + " exampleRandom (Random, CPU)=900 M (23.1%)]"
                        + " [Producers running: 2] [Consumers running: 4]"
                        + " [Consumer ready for work (queue empty): 4567]"
                        + " [Producer blocked (queue full): 1234] [Average contains time: 3 ms]"
                        + " [keys queue size: 5678] [Hits: 6789]")));
    }

    /**
     * Full-transfer (no GPU pre-filter): every generated candidate reaches LMDB as two hash160
     * lookups, so there is nothing to pre-filter and the {@code % pre-filtered} clause is omitted
     * rather than printed as {@code 0.00%}.
     */
    @Test
    public void createStatisticsMessage_fullTransfer_omitsPreFilterClause() {
        // arrange
        Statistics statistics = new Statistics();

        // act — 100 M candidates/s -> 200 M lookups/s (both variants of every candidate)
        String result = statistics.createStatisticsMessage(
                180_000L,
                400_000_000L,
                200_000_000.0,
                200_000_000L,
                100_000_000.0,
                60L,
                0L,
                new TreeMap<>(),
                0L,
                0L,
                0L,
                0L,
                0L,
                0L);

        // assert
        assertThat(
                result,
                is(equalTo("Statistics: [uptime 3 min] [Generated 100 M/s (200 M total)]"
                        + " [-> LMDB 200 M/s (400 M lookups total)] [rate window 60s]"
                        + " [Keys per producer: none] [Producers running: 0] [Consumers running: 0]"
                        + " [Consumer ready for work (queue empty): 0] [Producer blocked (queue full): 0]"
                        + " [Average contains time: 0 ms] [keys queue size: 0] [Hits: 0]")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="formatRate">
    @Test
    public void formatRate_autoScalesAcrossTheWholeRange() {
        assertThat(Statistics.formatRate(412.0), is(equalTo("412/s")));
        assertThat(Statistics.formatRate(2_013_000.0), is(equalTo("2 M/s")));
        assertThat(Statistics.formatRate(130_000_000.0), is(equalTo("130 M/s")));
        assertThat(Statistics.formatRate(1_500_000_000.0), is(equalTo("2 G/s")));
        assertThat(Statistics.formatRate(3_000.0), is(equalTo("3 k/s")));
        // Exact scale boundaries: pin the >= comparisons in formatRate (a > mutant would drop each
        // value into the next-smaller unit, e.g. 1_000_000_000 -> "1000 M/s" instead of "1 G/s").
        assertThat(Statistics.formatRate(1_000_000_000.0), is(equalTo("1 G/s")));
        assertThat(Statistics.formatRate(1_000_000.0), is(equalTo("1 M/s")));
        assertThat(Statistics.formatRate(1_000.0), is(equalTo("1 k/s")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="describeProducerShares">
    /**
     * The reason this group counts candidates instead of dispatched batches: two GPUs at
     * {@code batchSizeInBits} 24 and 21 running 1156 and 595 batches look like a 2:1 split, while
     * the candidates they actually produced are a 20:1 split. The rendered shares must show the
     * latter.
     */
    @Test
    public void describeProducerShares_producersWithDifferentGridSizes_showsTheWorkSplitNotTheBatchSplit() {
        Map<String, Long> generatedKeysByProducer = new TreeMap<>();
        generatedKeysByProducer.put("gpu0 (Incremental, GPU)", 1156L * 16_777_216L); // 2^24 grid
        generatedKeysByProducer.put("gpu1 (Random, GPU)", 595L * 2_097_152L); // 2^21 grid

        String result = Statistics.describeProducerShares(generatedKeysByProducer);

        assertThat(result, is(equalTo("gpu0 (Incremental, GPU)=19 G (94.0%), gpu1 (Random, GPU)=1 G (6.0%)")));
    }

    @Test
    public void describeProducerShares_noProducers_isNone() {
        assertThat(Statistics.describeProducerShares(new TreeMap<>()), is(equalTo("none")));
    }

    /**
     * A producer can be registered before it has generated anything (its first batch is still in
     * flight). Dividing by a zero total would yield {@code NaN%}; the share is omitted instead.
     */
    @Test
    public void describeProducerShares_registeredButNothingGenerated_omitsTheShare() {
        Map<String, Long> generatedKeysByProducer = new TreeMap<>();
        generatedKeysByProducer.put("gpu0 (Incremental, GPU)", 0L);

        String result = Statistics.describeProducerShares(generatedKeysByProducer);

        assertThat(result, is(equalTo("gpu0 (Incremental, GPU)=0")));
    }

    @Test
    public void describeProducerShares_singleProducer_isTheWholeHundredPercent() {
        Map<String, Long> generatedKeysByProducer = new TreeMap<>();
        generatedKeysByProducer.put("gpu0 (Random, GPU)", 79_455L * 4_194_304L);

        String result = Statistics.describeProducerShares(generatedKeysByProducer);

        assertThat(result, is(equalTo("gpu0 (Random, GPU)=333 G (100.0%)")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="formatCount">
    @Test
    public void formatCount_autoScalesAcrossTheWholeRange() {
        assertThat(Statistics.formatCount(412L), is(equalTo("412")));
        assertThat(Statistics.formatCount(3_000L), is(equalTo("3 k")));
        assertThat(Statistics.formatCount(130_000_000L), is(equalTo("130 M")));
        assertThat(Statistics.formatCount(19_398_263_296L), is(equalTo("19 G")));
        // Exact scale boundaries: pin the >= comparisons (a > mutant would drop each value into the
        // next-smaller unit, e.g. 1_000_000_000 -> "1000 M" instead of "1 G").
        assertThat(Statistics.formatCount(1_000_000_000L), is(equalTo("1 G")));
        assertThat(Statistics.formatCount(1_000_000L), is(equalTo("1 M")));
        assertThat(Statistics.formatCount(1_000L), is(equalTo("1 k")));
        assertThat(Statistics.formatCount(0L), is(equalTo("0")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="describeFilterEffect boundary">
    /**
     * Boundary guard for the pre-filter clause: when the LMDB-lookup rate is exactly zero (nothing
     * has reached the consumer yet) the clause must be omitted, not printed as {@code 100.00%
     * pre-filtered}. This pins the {@code lookupsPerSecond <= 0.0} boundary in describeFilterEffect.
     */
    @Test
    public void createStatisticsMessage_zeroLookupRate_omitsPreFilterClause() {
        Statistics statistics = new Statistics();

        String result = statistics.createStatisticsMessage(
                180_000L,
                0L, // lookups lifetime
                0.0, // lookup rate/s -> exactly the boundary
                200_000_000L, // generated lifetime
                100_000_000.0, // generation rate/s (> 0, so potentialLookups > 0)
                60L,
                0L,
                new TreeMap<>(),
                0L,
                0L,
                0L,
                0L,
                0L,
                0L);

        assertThat(result, containsString("[-> LMDB 0/s (0 M lookups total)] [rate window 60s]"));
    }
    // </editor-fold>
}
