// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;

/**
 * Exception thrown when a given private key exceeds the safe upper bound
 * for use in grid-based key chunking (incrementing the base key).
 * <p>
 * The maximum allowed private key is
 * {@link net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants#MAX_PRIVATE_KEY}.
 * This exception is typically thrown if the base key plus the chunk range (2^bits) exceeds this bound.
 */
public class PrivateKeyTooLargeException extends IllegalArgumentException {

    /** The offending private key. */
    private final BigInteger providedKey;
    /** The maximum allowed private key. */
    private final BigInteger maxAllowedKey;
    /** The batch size in bits that produced the bound. */
    private final int batchSizeInBits;

    /**
     * Creates a new exception with the offending key and bound.
     *
     * @param providedKey      the key that was rejected
     * @param maxAllowedKey    the configured maximum allowed key
     * @param batchSizeInBits  the batch size in bits that produced the bound
     */
    public PrivateKeyTooLargeException(BigInteger providedKey, BigInteger maxAllowedKey, int batchSizeInBits) {
        super(buildMessage(providedKey, maxAllowedKey, batchSizeInBits));
        this.providedKey = providedKey;
        this.maxAllowedKey = maxAllowedKey;
        this.batchSizeInBits = batchSizeInBits;
    }

    private static String buildMessage(BigInteger providedKey, BigInteger maxAllowedKey, int batchSizeInBits) {
        return "Private key exceeds maximum allowed range for chunked grid mode: " + "\nProvided key:        0x"
                + providedKey.toString(16) + "\nMaximum allowed key: 0x"
                + maxAllowedKey.toString(16) + "\n(batchSizeInBits = "
                + batchSizeInBits + ")"
                + "\nThe maximum private key is defined in: Secp256k1Constants.MAX_PRIVATE_KEY";
    }

    /**
     * Returns the offending private key.
     *
     * @return the offending private key
     */
    public BigInteger getProvidedKey() {
        return providedKey;
    }

    /**
     * Returns the maximum allowed private key.
     *
     * @return the maximum allowed private key
     */
    public BigInteger getMaxAllowedKey() {
        return maxAllowedKey;
    }

    /**
     * Returns the batch size used when computing the bound.
     *
     * @return the batch size (in bits) used when computing the bound
     */
    public int getBatchSizeInBits() {
        return batchSizeInBits;
    }
}
