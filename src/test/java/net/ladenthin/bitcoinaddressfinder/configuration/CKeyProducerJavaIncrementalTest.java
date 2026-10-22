// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.constants.Radix;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.util.PrivateKeyValidator;
import org.junit.jupiter.api.Test;

public class CKeyProducerJavaIncrementalTest {

    private static final String START_PRIVATE_KEY_CUSTOM_HEX = "FF";
    private static final String END_PRIVATE_KEY_CUSTOM_HEX = "FF";

    // <editor-fold defaultstate="collapsed" desc="getStartPrivateKey">
    @Test
    public void getStartPrivateKey_defaultStartAddress_returnsMinValidPrivateKey() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();

        // act
        BigInteger result = sut.getStartPrivateKey();

        // assert
        assertThat(result, is(equalTo(Secp256k1Constants.MIN_VALID_PRIVATE_KEY)));
    }

    @Test
    public void getStartPrivateKey_customUppercaseHexStartAddress_returnsExpectedBigInteger() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.startPrivateKey = START_PRIVATE_KEY_CUSTOM_HEX.toUpperCase();

        // act
        BigInteger result = sut.getStartPrivateKey();

        // assert
        assertThat(result, is(equalTo(new BigInteger(START_PRIVATE_KEY_CUSTOM_HEX, Radix.HEX))));
    }

    @Test
    public void getStartPrivateKey_customLowercaseHexStartAddress_returnsExpectedBigInteger() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.startPrivateKey = START_PRIVATE_KEY_CUSTOM_HEX.toLowerCase();

        // act
        BigInteger result = sut.getStartPrivateKey();

        // assert
        assertThat(result, is(equalTo(new BigInteger(START_PRIVATE_KEY_CUSTOM_HEX, Radix.HEX))));
    }

    @Test
    public void getStartPrivateKey_maxPrivateKeyHexStartAddress_returnsMaxPrivateKey() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.startPrivateKey = Secp256k1Constants.MAX_PRIVATE_KEY_HEX;

        // act
        BigInteger result = sut.getStartPrivateKey();

        // assert
        assertThat(result, is(equalTo(Secp256k1Constants.MAX_PRIVATE_KEY)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getEndPrivateKey">
    /**
     * The end is inclusive, so the default must be the last valid key, n - 1. It was n itself, which
     * has not been a valid key since the group order became the exclusive bound (F45): a scan of
     * explicit keys to the default end then checked the replacement key in place of n.
     */
    @Test
    public void getEndPrivateKey_defaultEndAddress_returnsTheLastValidPrivateKey() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();

        // act
        BigInteger result = sut.getEndPrivateKey();

        // assert
        assertThat(result, is(equalTo(Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.ONE))));
        assertThat(new PrivateKeyValidator().isOutsidePrivateKeyRange(result), is(false));
        assertThat(new PrivateKeyValidator().isOutsidePrivateKeyRange(result.add(BigInteger.ONE)), is(true));
    }

    @Test
    public void getEndPrivateKey_customUppercaseHexEndAddress_returnsExpectedBigInteger() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.endPrivateKey = END_PRIVATE_KEY_CUSTOM_HEX.toUpperCase();

        // act
        BigInteger result = sut.getEndPrivateKey();

        // assert
        assertThat(result, is(equalTo(new BigInteger(END_PRIVATE_KEY_CUSTOM_HEX, Radix.HEX))));
    }

    @Test
    public void getEndPrivateKey_customLowercaseHexEndAddress_returnsExpectedBigInteger() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.endPrivateKey = END_PRIVATE_KEY_CUSTOM_HEX.toLowerCase();

        // act
        BigInteger result = sut.getEndPrivateKey();

        // assert
        assertThat(result, is(equalTo(new BigInteger(END_PRIVATE_KEY_CUSTOM_HEX, Radix.HEX))));
    }

    @Test
    public void getEndPrivateKey_maxPrivateKeyHexEndAddress_returnsMaxPrivateKey() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.endPrivateKey = Secp256k1Constants.MAX_PRIVATE_KEY_HEX;

        // act
        BigInteger result = sut.getEndPrivateKey();

        // assert
        assertThat(result, is(equalTo(Secp256k1Constants.MAX_PRIVATE_KEY)));
    }

    @Test
    public void getEndPrivateKey_minValidPrivateKeyHexEndAddress_returnsMinValidPrivateKey() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.endPrivateKey = Secp256k1Constants.MIN_VALID_PRIVATE_KEY_HEX;

        // act
        BigInteger result = sut.getEndPrivateKey();

        // assert
        assertThat(result, is(equalTo(Secp256k1Constants.MIN_VALID_PRIVATE_KEY)));
    }
    // </editor-fold>
}
