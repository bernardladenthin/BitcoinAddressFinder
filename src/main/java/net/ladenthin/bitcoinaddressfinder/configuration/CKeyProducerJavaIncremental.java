// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;

/**
 * Configuration for the incremental (sequential range) key producer.
 */
public class CKeyProducerJavaIncremental extends CKeyProducerJava {

    /** Radix used by hex-encoded private-key bounds in this configuration. */
    private static final int HEX_RADIX = 16;

    /** Creates a new {@link CKeyProducerJavaIncremental}. */
    public CKeyProducerJavaIncremental() {}

    /** Inclusive lower bound of the scanned private-key range as an uppercase hex string. */
    public String startPrivateKey = Secp256k1Constants.MIN_VALID_PRIVATE_KEY_HEX;
    /** Inclusive upper bound of the scanned private-key range as a hex string. */
    public String endPrivateKey = Secp256k1Constants.MAX_PRIVATE_KEY_HEX;

    /**
     * Returns the configured inclusive lower bound of the scanned private-key range.
     *
     * @return the configured start private key as a {@link BigInteger}
     */
    public BigInteger getStartPrivateKey() {
        return new BigInteger(startPrivateKey, HEX_RADIX);
    }

    /**
     * Returns the configured inclusive upper bound of the scanned private-key range.
     *
     * @return the configured end private key as a {@link BigInteger}
     */
    public BigInteger getEndPrivateKey() {
        return new BigInteger(endPrivateKey, HEX_RADIX);
    }
}
