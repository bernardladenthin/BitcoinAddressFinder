// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import org.jspecify.annotations.Nullable;

/**
 * Common configuration shared by all producers.
 */
public class CProducer {

    /** Creates a new {@link CProducer}. */
    public CProducer() {}

    /** Id of the key producer this producer pulls secrets from. */
    public @Nullable String keyProducerId;

    /**
     * Range: {@code 0} (inclusive) to {@link PublicKeyBytes#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY} (inclusive).
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
     * Computes the overall work size implied by {@link #batchSizeInBits}.
     *
     * @param bitHelper helper used to convert bits to size
     * @return {@code 2^batchSizeInBits}
     */
    public int getOverallWorkSize(BitHelper bitHelper) {
        return bitHelper.convertBitsToSize(batchSizeInBits);
    }
}
