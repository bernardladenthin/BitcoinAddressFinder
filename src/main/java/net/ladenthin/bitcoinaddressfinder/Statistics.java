// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

public class Statistics {
    
    @Deprecated
    public static final int ONE_SECOND_IN_MILLISECONDS = 1000;
    
    String createStatisticsMessage(long uptime, long keys, long keysSumOfTimeToCheckContains, long emptyConsumer, long keysQueueSize, long hits) {
        // calculate uptime
        long uptimeInSeconds = uptime / (long) ONE_SECOND_IN_MILLISECONDS;
        long uptimeInMinutes = uptimeInSeconds / 60;
        // calculate per time, prevent division by zero with Math.max
        long keysPerSecond = keys / Math.max(uptimeInSeconds, 1);
        long keysPerMinute = keys / Math.max(uptimeInMinutes, 1);
        // calculate average contains time
        long averageContainsTime = keysSumOfTimeToCheckContains / Math.max(keys, 1);

        String message = "Statistics: [Checked " + (keys / 1_000_000L) + " M keys in " + uptimeInMinutes + " minutes] [" + (keysPerSecond/1_000L) + " k keys/second] [" + (keysPerMinute / 1_000_000L) + " M keys/minute] [Times an empty consumer: " + emptyConsumer + "] [Average contains time: " + averageContainsTime + " ms] [keys queue size: " + keysQueueSize + "] [Hits: " + hits + "]";
        return message;
    }
}
