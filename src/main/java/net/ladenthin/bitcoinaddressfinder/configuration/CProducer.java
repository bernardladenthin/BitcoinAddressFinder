// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import org.jspecify.annotations.Nullable;

public class CProducer {
    
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
    
    public int getOverallWorkSize(BitHelper bitHelper) {
        return bitHelper.convertBitsToSize(batchSizeInBits);
    }
}
