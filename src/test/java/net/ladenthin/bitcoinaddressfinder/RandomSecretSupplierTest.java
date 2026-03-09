// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.util.Random;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.Test;

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
