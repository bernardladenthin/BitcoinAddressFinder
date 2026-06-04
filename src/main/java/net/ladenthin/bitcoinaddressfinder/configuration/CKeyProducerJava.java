// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import org.jspecify.annotations.Nullable;

/**
 * Common configuration shared by all Java-based key producers.
 */
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
     * <p>This is the only remaining dependency from {@code configuration} on the
     * producer-side {@link PublicKeyBytes} class. {@code BIT_COUNT_FOR_MAX_CHUNKS_ARRAY}
     * encodes Java's array-size cap for the GPU result buffer &mdash; a producer concern,
     * not a secp256k1 spec value &mdash; so it stays in {@link PublicKeyBytes} until a
     * future producer-constants extraction (see {@code workspace/policies/code-quality-todos.md}
     * &sect;4).
     */
    public int maxWorkSize = 1 << PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY;
}
