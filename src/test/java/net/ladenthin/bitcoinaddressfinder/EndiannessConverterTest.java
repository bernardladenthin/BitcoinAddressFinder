// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import static org.mockito.Mockito.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class EndiannessConverterTest {

    private final ByteBufferUtility byteBufferUtility = mock(ByteBufferUtility.class);

    // <editor-fold defaultstate="collapsed" desc="mustConvert">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ENDIANNESS, location = CommonDataProvider.class)
    public void mustConvertTest(ByteOrder sourceOrder, ByteOrder targetOrder, boolean expectedMustConvert) {
        // arrange
        EndiannessConverter converter = new EndiannessConverter(sourceOrder, targetOrder, byteBufferUtility);

        // act
        boolean result = converter.mustConvert();

        // assert
        assertThat(result, is(expectedMustConvert));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="convertEndian">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ENDIANNESS, location = CommonDataProvider.class)
    public void convertEndianTest(ByteOrder sourceOrder, ByteOrder targetOrder, boolean mustConvert) {
        // arrange
        EndiannessConverter converter = new EndiannessConverter(sourceOrder, targetOrder, byteBufferUtility);
        byte[] array = {1, 2, 3};

        // act
        converter.convertEndian(array);

        // assert
        if (mustConvert) {
            verify(byteBufferUtility).reverse(array);
        } else {
            verify(byteBufferUtility, never()).reverse(array);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="source/target getters">
    @Test
    public void getSourceOrder_returnsCorrectSourceOrder() {
        // arrange
        EndiannessConverter converter = new EndiannessConverter(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN, byteBufferUtility);

        // act
        ByteOrder source = converter.getSourceOrder();

        // assert
        assertThat(source, is(ByteOrder.BIG_ENDIAN));
    }

    @Test
    public void getTargetOrder_returnsCorrectTargetOrder() {
        // arrange
        EndiannessConverter converter = new EndiannessConverter(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN, byteBufferUtility);

        // act
        ByteOrder target = converter.getTargetOrder();

        // assert
        assertThat(target, is(ByteOrder.LITTLE_ENDIAN));
    }
    // </editor-fold>
}
