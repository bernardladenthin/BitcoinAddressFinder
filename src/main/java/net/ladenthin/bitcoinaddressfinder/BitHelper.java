// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;

/**
 * Helpers for bit-count based batch sizing used by producers.
 */
public class BitHelper {

    /** Creates a new {@link BitHelper}. */
    public BitHelper() {}

    // RADIX_HEX = 16 used to live here. It has moved to
    // net.ladenthin.bitcoinaddressfinder.constants.Radix#HEX so the configuration
    // layer can reference the hex radix without depending on this root-package
    // helper, and so there is exactly one named "16" in the codebase.

    /**
     * Converts a number of bits into the corresponding batch size (2 to the power of {@code bits}).
     *
     * @param bits the number of bits
     * @return {@code 1 << bits}
     */
    public int convertBitsToSize(int bits) {
        return 1 << bits;
    }

    /**
     * Returns a {@link BigInteger} bitmask with the lowest {@code bits} bits set
     * (i.e. {@code 2^bits - 1}). Used to keep only the low-order {@code bits}
     * bits of a value via bitwise AND.
     *
     * @param bits the number of low bits to set in the returned mask
     * @return {@code 2^bits - 1} as a {@link BigInteger}
     */
    public BigInteger getLowBitMask(int bits) {
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
        if (batchSizeInBits > OpenClKernelConstants.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY) {
            throw new IllegalArgumentException("batchSizeInBits must be less than or equal to "
                    + OpenClKernelConstants.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY + ".");
        }
    }
}
