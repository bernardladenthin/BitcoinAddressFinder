// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class BitHelperTest {

    // <editor-fold defaultstate="collapsed" desc="getLowBitMask">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_LOW_BIT_MASK)
    public void getLowBitMask_bitsGiven_lowBitMaskEqualsExpectation(int bits, BigInteger lowBitMask) throws IOException {
        // arrange
        BitHelper bitHelper = new BitHelper();

        // act, assert
        assertThat(bitHelper.getLowBitMask(bits), is(equalTo(lowBitMask)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="convertBitsToSize">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BITS_TO_SIZE)
    public void convertBitsToSize_bitsGiven_sizeEqualsExpectation(int bits, int size) throws IOException {
        // arrange
        BitHelper bitHelper = new BitHelper();

        // act, assert
        assertThat(bitHelper.convertBitsToSize(bits), is(equalTo(size)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="assertBatchSizeInBitsIsInRange">
    @Test
    public void assertBatchSizeInBitsIsInRange_bitsGivenBelowMinimum_exceptionThrown() throws IOException {
        // arrange
        BitHelper bitHelper = new BitHelper();

        // act, assert
        assertThrows(IllegalArgumentException.class, () -> bitHelper.assertBatchSizeInBitsIsInRange(-1));
    }

    @Test
    public void assertBatchSizeInBitsCorrect_bitsGivenOverMaximum_exceptionThrown() throws IOException {
        // arrange
        BitHelper bitHelper = new BitHelper();

        // act, assert
        assertThrows(
                IllegalArgumentException.class,
                () -> bitHelper.assertBatchSizeInBitsIsInRange(PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY + 1));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BIT_SIZES_AT_MOST_MAX)
    public void assertBatchSizeInBitsIsInRange_bitsGivenInRange_exceptionThrown(int bits) throws IOException {
        // arrange
        BitHelper bitHelper = new BitHelper();

        // act, assert
        bitHelper.assertBatchSizeInBitsIsInRange(bits);
    }
    // </editor-fold>
}
