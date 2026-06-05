// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import org.jspecify.annotations.Nullable;

/**
 * Common configuration shared by all Java-based key producers.
 */
@ToString
@EqualsAndHashCode
public class CKeyProducerJava {

    /** Creates a new {@link CKeyProducerJava}. */
    public CKeyProducerJava() {}

    /** Unique id by which producers reference this key producer. */
    public @Nullable String keyProducerId;

    /**
     * (2<sup>{@code maxNumBits}</sup> - 1) can be set to a lower value to improve a search on specific ranges (e.g. the puzzle transaction <a href="https://privatekeys.pw/puzzles/bitcoin-puzzle-tx">bitcoin-puzzle-tx</a> ).
     * {@code 1} can't be tested because {@link org.bitcoinj.crypto.ECKey#fromPrivate} throws an {@link IllegalArgumentException}.
     * Range: {@code 2} (inclusive) to {@link Secp256k1Constants#PRIVATE_KEY_MAX_NUM_BITS} (inclusive).
     */
    public int privateKeyMaxNumBits = Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS;

    /**
     * Maximum allowed work size (number of secrets to generate) &mdash; {@code 2^24 = 16 777 216}.
     *
     * <p>The bound comes from {@link OpenClKernelConstants#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY},
     * which encodes Java's array-size cap for the GPU result buffer given the
     * 104-byte OpenCL chunk size. Reading from the constants leaf keeps the
     * configuration layer free of producer-class dependencies.
     */
    public int maxWorkSize = 1 << OpenClKernelConstants.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY;
}
