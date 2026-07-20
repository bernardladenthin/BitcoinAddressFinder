// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.statistics;

import static org.hamcrest.MatcherAssert.assertThat;
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
        Map<String, Long> batchesByProducer = new TreeMap<>();
        batchesByProducer.put("exampleRandom (Random, CPU)", 10L);
        batchesByProducer.put("exampleOpenCL (Random, GPU)", 20L);

        // act — 130 M candidates/s generated, only 2 M hash160 lookups/s reach LMDB
        String result = statistics.createStatisticsMessage(
                180_000L, // uptime -> 3 min
                53_000_000L, // lookups against LMDB (lifetime)
                2_000_000.0, // lookup rate/s (post-filter)
                3_900_000_000L, // candidates generated (lifetime)
                130_000_000.0, // generation rate/s (pre-filter)
                60L,
                159_000_000L, // sum of contains time -> avg 3 ms
                batchesByProducer,
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
                        + " [Batches per producer: exampleOpenCL (Random, GPU)=20, exampleRandom (Random, CPU)=10]"
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
                        + " [Batches per producer: none] [Producers running: 0] [Consumers running: 0]"
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
    }
    // </editor-fold>
}
