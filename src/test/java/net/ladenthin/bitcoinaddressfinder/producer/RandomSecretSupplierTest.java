// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

import java.math.BigInteger;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.secret.RandomSecretSupplier;
import org.junit.jupiter.api.Test;

public class RandomSecretSupplierTest {

    // <editor-fold defaultstate="collapsed" desc="nextSecret">
    @Test
    public void nextSecret_zeroBitLength_returnsZero() {
        // arrange
        RandomSecretSupplier supplier = new RandomSecretSupplier(new Random(0L));

        // act
        BigInteger result = supplier.nextSecret(0);

        // assert
        assertThat(result, is(equalTo(BigInteger.ZERO)));
    }

    @Test
    public void nextSecret_256BitLength_returnsNonNullValue() {
        // arrange
        RandomSecretSupplier supplier = new RandomSecretSupplier(new Random(42L));

        // act
        BigInteger result = supplier.nextSecret(256);

        // assert
        assertThat(result, is(notNullValue()));
    }

    @Test
    public void nextSecret_256BitLength_returnValueFitsIn256Bits() {
        // arrange
        RandomSecretSupplier supplier = new RandomSecretSupplier(new Random(42L));
        BigInteger maxValue = BigInteger.TWO.pow(256);

        // act
        BigInteger result = supplier.nextSecret(256);

        // assert
        assertThat(result, is(greaterThanOrEqualTo(BigInteger.ZERO)));
        assertThat(result, is(lessThan(maxValue)));
    }

    @Test
    public void nextSecret_1BitLength_returnsZeroOrOne() {
        // arrange
        RandomSecretSupplier supplier = new RandomSecretSupplier(new Random(0L));
        BigInteger two = BigInteger.TWO;

        // act
        BigInteger result = supplier.nextSecret(1);

        // assert
        assertThat(result, is(greaterThanOrEqualTo(BigInteger.ZERO)));
        assertThat(result, is(lessThan(two)));
    }

    @Test
    public void nextSecret_seededRandom_returnsDeterministicValue() {
        // arrange
        long seed = 12345L;
        int bitLength = 128;
        // Two suppliers with the same seed must produce the same value
        RandomSecretSupplier supplier1 = new RandomSecretSupplier(new Random(seed));
        RandomSecretSupplier supplier2 = new RandomSecretSupplier(new Random(seed));

        // act
        BigInteger result1 = supplier1.nextSecret(bitLength);
        BigInteger result2 = supplier2.nextSecret(bitLength);

        // assert
        assertThat(result1, is(equalTo(result2)));
    }

    @Test
    public void nextSecret_consecutiveCalls_canProduceDifferentValues() {
        // arrange
        // Use a seed that is known to produce two different 256-bit values
        RandomSecretSupplier supplier = new RandomSecretSupplier(new Random(1L));

        // act
        BigInteger first = supplier.nextSecret(256);
        BigInteger second = supplier.nextSecret(256);

        // assert – with a real Random the two calls should differ (not guaranteed but
        // statistically certain for 256-bit outputs with any reasonable seed)
        assertThat(first.equals(second), is(false));
    }
    // </editor-fold>
}
