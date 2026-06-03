// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.math.BigInteger;
import java.util.Locale;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;

/**
 * Configuration for the incremental (sequential range) key producer.
 */
public class CKeyProducerJavaIncremental extends CKeyProducerJava {

    /** Creates a new {@link CKeyProducerJavaIncremental}. */
    public CKeyProducerJavaIncremental() {}

    /** Inclusive lower bound of the scanned private-key range as an uppercase hex string. */
    public String startPrivateKey =
            PublicKeyBytes.MIN_VALID_PRIVATE_KEY.toString(BitHelper.RADIX_HEX).toUpperCase(Locale.ROOT);
    /** Inclusive upper bound of the scanned private-key range as a hex string. */
    public String endPrivateKey = PublicKeyBytes.MAX_PRIVATE_KEY_HEX;

    /**
     * Returns the configured inclusive lower bound of the scanned private-key range.
     *
     * @return the configured start private key as a {@link BigInteger}
     */
    public BigInteger getStartPrivateKey() {
        return new BigInteger(startPrivateKey, BitHelper.RADIX_HEX);
    }

    /**
     * Returns the configured inclusive upper bound of the scanned private-key range.
     *
     * @return the configured end private key as a {@link BigInteger}
     */
    public BigInteger getEndPrivateKey() {
        return new BigInteger(endPrivateKey, BitHelper.RADIX_HEX);
    }
}
