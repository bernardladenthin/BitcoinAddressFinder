// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.math.BigInteger;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.constants.Radix;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;

/**
 * Configuration for the incremental (sequential range) key producer.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CKeyProducerJavaIncremental extends CKeyProducerJava {

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
        return new BigInteger(startPrivateKey, Radix.HEX);
    }

    /**
     * Returns the configured inclusive upper bound of the scanned private-key range.
     *
     * @return the configured end private key as a {@link BigInteger}
     */
    public BigInteger getEndPrivateKey() {
        return new BigInteger(endPrivateKey, Radix.HEX);
    }
}
