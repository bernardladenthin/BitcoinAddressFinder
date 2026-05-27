// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;

/**
 * Configuration for the incremental (sequential range) key producer.
 */
public class CKeyProducerJavaIncremental extends CKeyProducerJava {

    /** Creates a new {@link CKeyProducerJavaIncremental}. */
    public CKeyProducerJavaIncremental() {
    }

    /** Inclusive lower bound of the scanned private-key range as an uppercase hex string. */
    public String startAddress = PublicKeyBytes.MIN_VALID_PRIVATE_KEY.toString(BitHelper.RADIX_HEX).toUpperCase();
    /** Inclusive upper bound of the scanned private-key range as a hex string. */
    public String endAddress = PublicKeyBytes.MAX_PRIVATE_KEY_HEX;

    /**
     * Returns the configured start address.
     *
     * @return the configured start address as a {@link BigInteger}
     */
    public BigInteger getStartAddress() {
        return new BigInteger(startAddress, BitHelper.RADIX_HEX);
    }

    /**
     * Returns the configured end address.
     *
     * @return the configured end address as a {@link BigInteger}
     */
    public BigInteger getEndAddress() {
        return new BigInteger(endAddress, BitHelper.RADIX_HEX);
    }
}
