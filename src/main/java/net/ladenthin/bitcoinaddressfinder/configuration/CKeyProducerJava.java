// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import org.jspecify.annotations.Nullable;

public class CKeyProducerJava {
    public @Nullable String keyProducerId;
    
    /**
     * (2<sup>{@code maxNumBits}</sup> - 1) can be set to a lower value to improve a search on specific ranges (e.g. the puzzle transaction <a href="https://privatekeys.pw/puzzles/bitcoin-puzzle-tx">bitcoin-puzzle-tx</a> ).
     * {@code 1} can't be tested because {@link org.bitcoinj.crypto.ECKey#fromPrivate} throws an {@link IllegalArgumentException}.
     * Range: {@code 2} (inclusive) to {@link PublicKeyBytes#PRIVATE_KEY_MAX_NUM_BITS} (inclusive).
     */
    public int privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
    
    /** Maximum allowed work size (number of secrets to generate) — 2^24 = 16,777,216*/
    public int maxWorkSize = 1 << PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY;
}
