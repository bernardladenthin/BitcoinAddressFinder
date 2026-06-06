// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.secret;

import java.math.BigInteger;
import java.util.Random;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * {@link SecretSupplier} backed by a {@link Random} instance.
 *
 * <p>Lombok-generated equals/hashCode compare the wrapped {@link Random} by identity
 * (Random does not override Object equality). Two suppliers wrapping the same Random
 * instance therefore compare equal; two suppliers wrapping different Random instances —
 * even ones seeded identically — do not.
 */
@ToString
@EqualsAndHashCode
public class RandomSecretSupplier implements SecretSupplier {
    private final Random random;

    /**
     * Creates a new supplier using the given random source.
     *
     * @param random the random source
     */
    public RandomSecretSupplier(Random random) {
        this.random = random;
    }

    @Override
    public BigInteger nextSecret(int bitLength) {
        return new BigInteger(bitLength, random);
    }
}
