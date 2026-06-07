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
    /** Number of consumer worker threads. */
    public int threads = 4;
    /**
     * Delay in milliseconds when the queue is empty before polling again.
     */
    public long delayEmptyConsumer = 100;
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
