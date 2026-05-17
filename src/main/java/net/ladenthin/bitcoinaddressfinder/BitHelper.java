// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;

public class BitHelper {

    public static final int RADIX_HEX = 16;

    public int convertBitsToSize(int bits) {
        return 1 << bits;
    }

    public BigInteger getKillBits(int bits) {
        return BigInteger.valueOf(2).pow(bits).subtract(BigInteger.ONE);
    }

    public void assertBatchSizeInBitsIsInRange(int batchSizeInBits) {
        if (batchSizeInBits < 0) {
            throw new IllegalArgumentException("batchSizeInBits must be greater than or equal to 0.");
        }
        if (batchSizeInBits > PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY) {
            throw new IllegalArgumentException("batchSizeInBits must be less than or equal to " + PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY + ".");
        }
    }
}
