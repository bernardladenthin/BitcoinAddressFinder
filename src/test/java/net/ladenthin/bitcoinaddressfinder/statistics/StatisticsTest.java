// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.statistics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

public class StatisticsTest {

    // <editor-fold defaultstate="collapsed" desc="createStatisticsMessage">
    @Test
    public void createStatisticsMessage_statisticsGiven_returnExpectedStatisticsMessage() {
        // arrange
        Statistics statistics = new Statistics();

        // act
        String result = statistics.createStatisticsMessage(
                234_000L, 999_000_000L, 345_000_000_000L, 4567L, 1234L, 5678L, 6789L);

        // assert
        assertThat(
                result,
                is(
                        equalTo(
                                "Statistics: [Checked 999 M keys in 3 minutes] [4269 k keys/second] [333 M keys/minute] [Consumer starved (empty queue): 4567] [Producer blocked (queue full): 1234] [Average contains time: 345 ms] [keys queue size: 5678] [Hits: 6789]")));
    }
    // </editor-fold>

}
