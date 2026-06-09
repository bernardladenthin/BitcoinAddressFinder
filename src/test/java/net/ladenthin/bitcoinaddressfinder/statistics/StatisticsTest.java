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
    @Test
    public void createStatisticsMessage_statisticsGiven_returnExpectedStatisticsMessage() {
        // arrange
        Statistics statistics = new Statistics();
        // TreeMap so the rendered per-producer breakdown is deterministic (sorted by label)
        Map<String, Long> batchesByProducer = new TreeMap<>();
        batchesByProducer.put("exampleRandom (Random, CPU)", 10L);
        batchesByProducer.put("exampleOpenCL (Random, GPU)", 20L);

        // act
        String result = statistics.createStatisticsMessage(
                234_000L, 999_000_000L, 345_000_000_000L, batchesByProducer, 2L, 4L, 4567L, 1234L, 5678L, 6789L);

        // assert
        assertThat(
                result,
                is(
                        equalTo(
                                "Statistics: [Checked 999 M keys in 3 minutes] [4269 k keys/second] [333 M keys/minute] [Batches per producer: exampleOpenCL (Random, GPU)=20, exampleRandom (Random, CPU)=10] [Producers running: 2] [Consumers running: 4] [Consumer ready for work (queue empty): 4567] [Producer blocked (queue full): 1234] [Average contains time: 345 ms] [keys queue size: 5678] [Hits: 6789]")));
    }

    @Test
    public void createStatisticsMessage_noBatches_rendersNone() {
        // arrange
        Statistics statistics = new Statistics();

        // act
        String result = statistics.createStatisticsMessage(
                234_000L, 999_000_000L, 345_000_000_000L, new TreeMap<>(), 0L, 0L, 0L, 0L, 0L, 0L);

        // assert
        assertThat(
                result,
                is(
                        equalTo(
                                "Statistics: [Checked 999 M keys in 3 minutes] [4269 k keys/second] [333 M keys/minute] [Batches per producer: none] [Producers running: 0] [Consumers running: 0] [Consumer ready for work (queue empty): 0] [Producer blocked (queue full): 0] [Average contains time: 345 ms] [keys queue size: 0] [Hits: 0]")));
    }
    // </editor-fold>

}
