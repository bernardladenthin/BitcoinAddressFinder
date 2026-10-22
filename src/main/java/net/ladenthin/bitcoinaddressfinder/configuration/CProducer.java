// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Common configuration shared by all producers.
 */
@ToString
@EqualsAndHashCode
public class CProducer {

    /** Creates a new {@link CProducer}. */
    public CProducer() {}

    /** Id of the key producer this producer pulls secrets from. */
    public @Nullable String keyProducerId;

    /**
     * Range: {@code 0} (inclusive) to
     * {@link net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY} (inclusive).
     */
    public int batchSizeInBits = 0;

    /**
     * The batch mode will use a private key increment internal to increase the performance.
     */
    public boolean batchUsePrivateKeyIncrement = true;

    /**
     * Enable the log output for the secret address.
     */
    public boolean logSecretBase;

    /**
     * Enable to let the producer run one time only.
     */
    public boolean runOnce = false;

    /**
     * Maximum time (seconds) this producer gets to stop once it is interrupted, measured from the
     * interrupt. It covers the batch in progress and the producer's own wait for its pending batches
     * (an OpenCL producer's result readers, which a stuck consumer can hold): at the deadline the
     * producer gives up on them, logs an error and reports itself stopped. The orchestrator's wait in
     * {@link net.ladenthin.bitcoinaddressfinder.producer.Producer#waitTillProducerNotRunning()} runs to
     * the same deadline plus a one-second grace, so a producer that gives up exactly at its bound is seen
     * as stopped and released; one still running after that is logged, its resources are left to the
     * process exit, and the shutdown chain continues, so a hung producer cannot block the whole process.
     * A run that ends by itself, without an interrupt, gives its pending batches this whole time from the
     * end of its last batch. Zero or less means no wait at all for the batch in progress; the orchestrator
     * still waits its one-second grace.
     *
     * <p>Default: {@code 300} (5 minutes). Increase for producers known to do long
     * batches at shutdown (large OpenCL grids, slow blocking I/O); decrease for fast
     * unit-test setups.
     */
    public int shutdownTimeoutSeconds = 300;

    /**
     * Computes the overall work size implied by {@link #batchSizeInBits}.
     *
     * @return {@code 2^batchSizeInBits}
     */
    public int getOverallWorkSize() {
        return 1 << batchSizeInBits;
    }
}
