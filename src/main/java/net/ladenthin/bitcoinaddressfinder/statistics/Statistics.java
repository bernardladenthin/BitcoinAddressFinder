// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.statistics;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the human-readable statistics line printed by the consumer.
 */
public class Statistics {

    /** Creates a new {@link Statistics}. */
    public Statistics() {}

    /** Number of milliseconds per second. */
    @Deprecated
    public static final int ONE_SECOND_IN_MILLISECONDS = 1000;

    /** Number of seconds per minute. */
    private static final double SECONDS_PER_MINUTE = 60.0;

    /**
     * Builds the periodic human-readable statistics line logged by the consumer.
     *
     * <p>The {@code Checked … keys} figure is the lifetime total; the keys/second and keys/minute
     * figures are the <em>current windowed</em> throughput (averaged over
     * {@code rateWindowSeconds}), not a lifetime average, so they track warm-up and thermal
     * throttling rather than settling to a long-run mean.
     *
     * @param uptime                       elapsed run time in milliseconds (lifetime)
     * @param keys                         total number of keys checked so far (lifetime)
     * @param windowKeysPerSecond          current throughput in keys/second, averaged over the
     *                                     trailing {@code rateWindowSeconds} window
     * @param rateWindowSeconds            the trailing window (seconds) the rate is averaged over
     * @param keysSumOfTimeToCheckContains cumulative time (ms) spent in presence lookups
     * @param batchesByProducer            dispatched-batch counts keyed by producer label
     *                                     ({@code "<keyProducerId> (<Strategy>, <CPU|GPU>)"}); lets
     *                                     concurrently running producers be told apart
     * @param producersRunning             number of producers currently in the RUNNING state
     * @param consumersRunning             number of consumer worker threads currently running
     * @param consumerReady               number of consume cycles the consumer was ready for work
     *                                     (empty queue, nothing to do); rising is normal/healthy — the
     *                                     CPU keeps up and an empty queue is the desired state
     * @param producerBlocked              number of times a producer hit a full queue on enqueue;
     *                                     rising is the warning sign — the consumer/CPU is too slow (CPU-bound)
     * @param keysQueueSize                current number of batches waiting in the queue
     * @param hits                         total number of address hits found so far
     * @return the formatted statistics message
     */
    public String createStatisticsMessage(
            long uptime,
            long keys,
            double windowKeysPerSecond,
            long rateWindowSeconds,
            long keysSumOfTimeToCheckContains,
            Map<String, Long> batchesByProducer,
            long producersRunning,
            long consumersRunning,
            long consumerReady,
            long producerBlocked,
            long keysQueueSize,
            long hits) {
        // calculate uptime (lifetime)
        long uptimeInSeconds = uptime / (long) ONE_SECOND_IN_MILLISECONDS;
        long uptimeInMinutes = uptimeInSeconds / 60;
        // current windowed throughput, rounded to the displayed k / M units
        long keysPerSecondK = Math.round(windowKeysPerSecond / 1_000.0);
        long keysPerMinuteM = Math.round(windowKeysPerSecond * SECONDS_PER_MINUTE / 1_000_000.0);
        // calculate average contains time
        long averageContainsTime = keysSumOfTimeToCheckContains / Math.max(keys, 1);
        // per-producer batch breakdown, rendered as "label=count, label=count" (or "none")
        String batches = batchesByProducer.isEmpty()
                ? "none"
                : batchesByProducer.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(", "));

        return "Statistics: [Checked " + (keys / 1_000_000L) + " M keys in " + uptimeInMinutes + " minutes] ["
                + keysPerSecondK + " k keys/second over " + rateWindowSeconds + "s] [" + keysPerMinuteM
                + " M keys/minute over " + rateWindowSeconds + "s] [Batches per producer: " + batches
                + "] [Producers running: " + producersRunning + "] [Consumers running: " + consumersRunning
                + "] [Consumer ready for work (queue empty): " + consumerReady
                + "] [Producer blocked (queue full): " + producerBlocked + "] [Average contains time: "
                + averageContainsTime + " ms] [keys queue size: " + keysQueueSize + "] [Hits: " + hits + "]";
    }
}
