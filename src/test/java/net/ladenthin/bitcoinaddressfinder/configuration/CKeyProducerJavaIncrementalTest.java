// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Test;

public class CKeyProducerJavaIncrementalTest {

    // <editor-fold defaultstate="collapsed" desc="getStartAddress">
    @Test
    public void getStartAddress_defaultStartAddress_returnsMinValidPrivateKey() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();

        // act
        BigInteger result = sut.getStartAddress();

        // assert
        assertThat(result, is(equalTo(PublicKeyBytes.MIN_VALID_PRIVATE_KEY)));
    }

    @Test
    public void getStartAddress_customUppercaseHexStartAddress_returnsExpectedBigInteger() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.startAddress = "FF";

        // act
        BigInteger result = sut.getStartAddress();

        // assert
        assertThat(result, is(equalTo(BigInteger.valueOf(255))));
    }

    @Test
    public void getStartAddress_customLowercaseHexStartAddress_returnsExpectedBigInteger() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.startAddress = "ff";

        // act
        BigInteger result = sut.getStartAddress();

        // assert
        assertThat(result, is(equalTo(BigInteger.valueOf(255))));
    }

    @Test
    public void getStartAddress_maxPrivateKeyHexStartAddress_returnsMaxPrivateKey() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.startAddress = PublicKeyBytes.MAX_PRIVATE_KEY_HEX;

        // act
        BigInteger result = sut.getStartAddress();

        // assert
        assertThat(result, is(equalTo(PublicKeyBytes.MAX_PRIVATE_KEY)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getEndAddress">
    @Test
    public void getEndAddress_defaultEndAddress_returnsMaxPrivateKey() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();

        // act
        BigInteger result = sut.getEndAddress();

        // assert
        assertThat(result, is(equalTo(PublicKeyBytes.MAX_PRIVATE_KEY)));
    }

    @Test
    public void getEndAddress_customUppercaseHexEndAddress_returnsExpectedBigInteger() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.endAddress = "FF";

        // act
        BigInteger result = sut.getEndAddress();

        // assert
        assertThat(result, is(equalTo(BigInteger.valueOf(255))));
    }

    @Test
    public void getEndAddress_customLowercaseHexEndAddress_returnsExpectedBigInteger() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.endAddress = "ff";

        // act
        BigInteger result = sut.getEndAddress();

        // assert
        assertThat(result, is(equalTo(BigInteger.valueOf(255))));
    }

    @Test
    public void getEndAddress_minValidPrivateKeyHexEndAddress_returnsMinValidPrivateKey() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.endAddress = PublicKeyBytes.MIN_VALID_PRIVATE_KEY_HEX;

        // act
        BigInteger result = sut.getEndAddress();

        // assert
        assertThat(result, is(equalTo(PublicKeyBytes.MIN_VALID_PRIVATE_KEY)));
    }
    // </editor-fold>
}
