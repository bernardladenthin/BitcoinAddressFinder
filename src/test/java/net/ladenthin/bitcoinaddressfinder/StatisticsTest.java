// @formatter:off
/**
 * Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Test;

public class StatisticsTest {
    
    // <editor-fold defaultstate="collapsed" desc="createStatisticsMessage">
    @Test
    public void createStatisticsMessage_statisticsGiven_returnExpectedStatisticsMessage() {
        // arrange
        Statistics statistics = new Statistics();
        
        // act
        String result = statistics.createStatisticsMessage(234_000L, 999_000_000L, 345_000_000_000L, 4567L, 5678L, 6789L);
        
        // assert
        assertThat(result, is(equalTo("Statistics: [Checked 999 M keys in 3 minutes] [4269 k keys/second] [333 M keys/minute] [Times an empty consumer: 4567] [Average contains time: 345 ms] [keys queue size: 5678] [Hits: 6789]")));
    }
    // </editor-fold>
    
}
