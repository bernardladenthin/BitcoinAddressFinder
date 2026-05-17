// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

public class PersistenceUtilsTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);

    // <editor-fold defaultstate="collapsed" desc="longToByteBufferDirect_zeroValue">
    @Test
    public void longToByteBufferDirect_zeroValue_returnsNotNull() {
        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(0L);

        // assert
        assertThat(result, is(notNullValue()));
    }

    @Test
    public void longToByteBufferDirect_zeroValue_returnsSameInstanceOnRepeatedCalls() {
        // act
        ByteBuffer first = persistenceUtils.longToByteBufferDirect(0L);
        ByteBuffer second = persistenceUtils.longToByteBufferDirect(0L);

        // assert
        assertThat(first, is(sameInstance(second)));
    }

    @Test
    public void longToByteBufferDirect_zeroValue_capacityIsLongBytes() {
        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(0L);

        // assert
        assertThat(result.capacity(), is(equalTo(Long.BYTES)));
    }

    @Test
    public void longToByteBufferDirect_zeroValue_absoluteGetLongReturnsZero() {
        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(0L);

        // assert
        assertThat(result.getLong(0), is(equalTo(0L)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="longToByteBufferDirect_positiveValue">
    @Test
    public void longToByteBufferDirect_positiveValue_returnsNotNull() {
        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(42L);

        // assert
        assertThat(result, is(notNullValue()));
    }

    @Test
    public void longToByteBufferDirect_positiveValue_capacityIsLongBytes() {
        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(42L);

        // assert
        assertThat(result.capacity(), is(equalTo(Long.BYTES)));
    }

    @Test
    public void longToByteBufferDirect_positiveValue_absoluteGetLongReturnsCorrectValue() {
        // arrange
        long expected = 42L;

        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(expected);

        // assert
        assertThat(result.getLong(0), is(equalTo(expected)));
    }

    @Test
    public void longToByteBufferDirect_positiveValue_remainingIsLongBytes() {
        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(42L);

        // assert
        assertThat(result.remaining(), is(equalTo(Long.BYTES)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="longToByteBufferDirect_negativeValue">
    @Test
    public void longToByteBufferDirect_negativeValue_absoluteGetLongReturnsCorrectValue() {
        // arrange
        long expected = -1L;

        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(expected);

        // assert
        assertThat(result.getLong(0), is(equalTo(expected)));
    }

    @Test
    public void longToByteBufferDirect_negativeValue_capacityIsLongBytes() {
        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(-1L);

        // assert
        assertThat(result.capacity(), is(equalTo(Long.BYTES)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="longToByteBufferDirect_edgeCases">
    @Test
    public void longToByteBufferDirect_longMaxValue_absoluteGetLongReturnsMaxValue() {
        // arrange
        long expected = Long.MAX_VALUE;

        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(expected);

        // assert
        assertThat(result.getLong(0), is(equalTo(expected)));
    }

    @Test
    public void longToByteBufferDirect_longMinValue_absoluteGetLongReturnsMinValue() {
        // arrange
        long expected = Long.MIN_VALUE;

        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(expected);

        // assert
        assertThat(result.getLong(0), is(equalTo(expected)));
    }

    @Test
    public void longToByteBufferDirect_oneValue_absoluteGetLongReturnsOne() {
        // arrange
        long expected = 1L;

        // act
        ByteBuffer result = persistenceUtils.longToByteBufferDirect(expected);

        // assert
        assertThat(result.getLong(0), is(equalTo(expected)));
    }

    @Test
    public void longToByteBufferDirect_nonZeroValueCalledTwice_returnsDifferentInstances() {
        // act
        ByteBuffer first = persistenceUtils.longToByteBufferDirect(100L);
        ByteBuffer second = persistenceUtils.longToByteBufferDirect(100L);

        // assert
        assertThat(first, is(not(sameInstance(second))));
    }

    @Test
    public void longToByteBufferDirect_nonZeroValueNotSameAsZeroCachedBuffer_returnsDifferentInstance() {
        // act
        ByteBuffer zeroBuffer = persistenceUtils.longToByteBufferDirect(0L);
        ByteBuffer nonZeroBuffer = persistenceUtils.longToByteBufferDirect(1L);

        // assert
        assertThat(zeroBuffer, is(not(sameInstance(nonZeroBuffer))));
    }
    // </editor-fold>
}
