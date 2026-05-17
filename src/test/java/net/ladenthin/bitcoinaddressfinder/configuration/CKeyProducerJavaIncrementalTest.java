// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.math.BigInteger;
import org.junit.Test;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class CKeyProducerJavaIncrementalTest {

    private static final String START_ADDRESS_CUSTOM_HEX = "FF";
    private static final String END_ADDRESS_CUSTOM_HEX = "FF";

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
        sut.startAddress = START_ADDRESS_CUSTOM_HEX.toUpperCase();

        // act
        BigInteger result = sut.getStartAddress();

        // assert
        assertThat(result, is(equalTo(new BigInteger(START_ADDRESS_CUSTOM_HEX, BitHelper.RADIX_HEX))));
    }

    @Test
    public void getStartAddress_customLowercaseHexStartAddress_returnsExpectedBigInteger() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.startAddress = START_ADDRESS_CUSTOM_HEX.toLowerCase();

        // act
        BigInteger result = sut.getStartAddress();

        // assert
        assertThat(result, is(equalTo(new BigInteger(START_ADDRESS_CUSTOM_HEX, BitHelper.RADIX_HEX))));
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
        sut.endAddress = END_ADDRESS_CUSTOM_HEX.toUpperCase();

        // act
        BigInteger result = sut.getEndAddress();

        // assert
        assertThat(result, is(equalTo(new BigInteger(END_ADDRESS_CUSTOM_HEX, BitHelper.RADIX_HEX))));
    }

    @Test
    public void getEndAddress_customLowercaseHexEndAddress_returnsExpectedBigInteger() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.endAddress = END_ADDRESS_CUSTOM_HEX.toLowerCase();

        // act
        BigInteger result = sut.getEndAddress();

        // assert
        assertThat(result, is(equalTo(new BigInteger(END_ADDRESS_CUSTOM_HEX, BitHelper.RADIX_HEX))));
    }

    @Test
    public void getEndAddress_maxPrivateKeyHexEndAddress_returnsMaxPrivateKey() {
        // arrange
        CKeyProducerJavaIncremental sut = new CKeyProducerJavaIncremental();
        sut.endAddress = PublicKeyBytes.MAX_PRIVATE_KEY_HEX;

        // act
        BigInteger result = sut.getEndAddress();

        // assert
        assertThat(result, is(equalTo(PublicKeyBytes.MAX_PRIVATE_KEY)));
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
