// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.util.Random;

public class RandomSecretSupplier implements SecretSupplier {
    private final Random random;

    public RandomSecretSupplier(Random random) {
        this.random = random;
    }

    @Override
    public BigInteger nextSecret(int bitLength) {
        return new BigInteger(bitLength, random);
    }
}
