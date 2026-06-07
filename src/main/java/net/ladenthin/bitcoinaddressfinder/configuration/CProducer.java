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
     * Maximum time (seconds) the orchestrator will wait inside
     * {@link net.ladenthin.bitcoinaddressfinder.producer.Producer#waitTillProducerNotRunning()}
     * for this producer to leave the {@code RUNNING} state during shutdown. After this
     * elapses {@code Finder.interrupt()} logs an error and continues with the rest of
     * the shutdown chain so a hung producer cannot block the whole process.
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
