// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;

/**
 * Helpers for bit-count based batch sizing used by producers.
 */
public class BitHelper {

    /** Creates a new {@link BitHelper}. */
    public BitHelper() {
    }

    /** Radix used by hexadecimal {@link BigInteger} conversions. */
    public static final int RADIX_HEX = 16;

    /**
     * Converts a number of bits into the corresponding batch size (2 to the power of {@code bits}).
     *
     * @param bits the number of bits
     * @return {@code 1 &lt;&lt; bits}
     */
    public int convertBitsToSize(int bits) {
        return 1 << bits;
    }

    /**
     * Returns a bit mask covering the lowest {@code bits} bits.
     *
     * @param bits the number of low bits to mask
     * @return {@code 2^bits - 1} as a {@link BigInteger}
     */
    public BigInteger getKillBits(int bits) {
        return BigInteger.valueOf(2).pow(bits).subtract(BigInteger.ONE);
    }

    /**
     * Validates that {@code batchSizeInBits} is within the supported range.
     *
     * @param batchSizeInBits the batch size in bits
     * @throws IllegalArgumentException if the value is negative or exceeds the supported maximum
     */
    public void assertBatchSizeInBitsIsInRange(int batchSizeInBits) {
        if (batchSizeInBits < 0) {
            throw new IllegalArgumentException("batchSizeInBits must be greater than or equal to 0.");
        }
        if (batchSizeInBits > PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY) {
            throw new IllegalArgumentException("batchSizeInBits must be less than or equal to " + PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY + ".");
        }
    }
}
