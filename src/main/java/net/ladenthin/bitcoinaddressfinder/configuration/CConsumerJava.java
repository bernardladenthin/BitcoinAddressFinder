// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for the Java-based consumer that queries the LMDB database.
 */
@ToString
@EqualsAndHashCode
public class CConsumerJava {

    /** Creates a new {@link CConsumerJava}. */
    public CConsumerJava() {}

    /** LMDB read-only configuration used to look up addresses. */
    public @NonNull CLMDBConfigurationReadOnly lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
    /** Interval, in seconds, at which throughput statistics are printed. */
    public int printStatisticsEveryNSeconds = 60;
    /**
     * Trailing window, in seconds, over which the current keys/second and keys/minute throughput
     * is averaged. Independent of {@link #printStatisticsEveryNSeconds}, which only controls how
     * often the line is printed. Unlike a lifetime average, this reflects <em>recent</em>
     * performance: it rises while the GPU warms up (clocks ramp), falls under thermal throttling,
     * and drops toward zero when producers stall — and it does not include the one-time startup
     * dead time (filter build, kernel compile) once the window has advanced past it. Default: 60.
     */
    public int statisticsRateWindowSeconds = 60;
    /** Number of consumer worker threads. */
    public int threads = 4;
    /**
     * Maximum time, in milliseconds, the consumer blocks per loop cycle waiting for
     * the next batch from the keys queue (the {@code poll} timeout). After draining
     * all immediately-available batches, the worker waits at most this long for more
     * work; it returns as soon as a batch arrives, so this is an upper bound on idle
     * wait per cycle, not a fixed delay. Only fully elapses when the queue stays
     * empty for the whole window. Default: {@code 100}.
     */
    public long queuePollTimeoutMillis = 100;
    /** Maximum number of pending key batches the consumer queue may hold. */
    public int queueSize = 10;

    /**
     * Maximum time (in seconds) {@link net.ladenthin.bitcoinaddressfinder.consumer.ConsumerJava#interrupt()}
     * waits for the worker pool to drain the keys queue before giving up and logging a warning.
     * Default: 60 seconds. Tests override with a small value (e.g. {@code 20}) to keep
     * the wait-and-assert branch fast.
     */
    public long awaitQueueEmptySeconds = 60L;

    /**
     * Enables runtime verification of public key calculation.
     *
     * This performs a full consistency check for each generated key by comparing it
     * against a known-correct Java-based calculation. Use this option only in very specific
     * debugging or validation scenarios, such as:
     *
     * <ul>
     *   <li>Suspected errors in the OpenCL implementation</li>
     *   <li>Hardware defects causing incorrect calculations</li>
     *   <li>Regression testing during development</li>
     * </ul>
     *
     * <b>Warning:</b> This has a significant performance impact.
     * <ul>
     *   <li>Enabled: ~20k keys/second</li>
     *   <li>Disabled (default): ~10 million keys/second (LMDB benchmark)</li>
     * </ul>
     *
     * <p>Do not enable in production or performance benchmarking environments.</p>
     */
    public boolean runtimePublicKeyCalculationCheck;

    /** Whether to enable vanity-address pattern matching. */
    public boolean enableVanity = false;

    /** Optional vanity pattern (regex) used when {@link #enableVanity} is {@code true}. */
    public @Nullable String vanityPattern;
}
