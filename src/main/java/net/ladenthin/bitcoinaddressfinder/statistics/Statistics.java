// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.statistics;

/**
 * Builds the human-readable statistics line printed by the consumer.
 */
public class Statistics {

    /** Creates a new {@link Statistics}. */
    public Statistics() {}

    /** Number of milliseconds per second. */
    @Deprecated
    public static final int ONE_SECOND_IN_MILLISECONDS = 1000;

    /**
     * Builds the periodic human-readable statistics line logged by the consumer.
     *
     * @param uptime                       elapsed run time in milliseconds
     * @param keys                         total number of keys checked so far
     * @param keysSumOfTimeToCheckContains cumulative time (ms) spent in presence lookups
     * @param emptyConsumer                number of consumer iterations that found the queue empty
     * @param keysQueueSize                current number of batches waiting in the queue
     * @param hits                         total number of address hits found so far
     * @return the formatted statistics message
     */
    public String createStatisticsMessage(
            long uptime,
            long keys,
            long keysSumOfTimeToCheckContains,
            long emptyConsumer,
            long keysQueueSize,
            long hits) {
        // calculate uptime
        long uptimeInSeconds = uptime / (long) ONE_SECOND_IN_MILLISECONDS;
        long uptimeInMinutes = uptimeInSeconds / 60;
        // calculate per time, prevent division by zero with Math.max
        long keysPerSecond = keys / Math.max(uptimeInSeconds, 1);
        long keysPerMinute = keys / Math.max(uptimeInMinutes, 1);
        // calculate average contains time
        long averageContainsTime = keysSumOfTimeToCheckContains / Math.max(keys, 1);

        return "Statistics: [Checked " + (keys / 1_000_000L) + " M keys in " + uptimeInMinutes + " minutes] ["
                + (keysPerSecond / 1_000L) + " k keys/second] [" + (keysPerMinute / 1_000_000L)
                + " M keys/minute] [Times an empty consumer: " + emptyConsumer + "] [Average contains time: "
                + averageContainsTime + " ms] [keys queue size: " + keysQueueSize + "] [Hits: " + hits + "]";
    }
}
