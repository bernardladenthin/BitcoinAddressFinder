// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
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

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.io.IOException;
import org.apache.commons.codec.DecoderException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class ByteConversionTest {

    private final ByteConversion byteConversion = new ByteConversion();

    // <editor-fold defaultstate="collapsed" desc="bytesToMib">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BYTES_TO_MIB, location = CommonDataProvider.class)
    public void bytesToMib_bytesGiven_returnExpectedMib(long bytes, double expectedMib) throws IOException, InterruptedException, DecoderException {
        // act
        double result = byteConversion.bytesToMib(bytes);

        // assert
        assertThat(result, is(equalTo(expectedMib)));
    }

    @Test
    public void bytesToMib_zeroBytes_returnsZero() {
        // act
        double result = byteConversion.bytesToMib(0L);

        // assert
        assertThat(result, is(equalTo(0.0)));
    }

    @Test
    public void bytesToMib_oneMib_returnsOne() {
        // arrange
        long oneMemInBytes = 1024L * 1024L;

        // act
        double result = byteConversion.bytesToMib(oneMemInBytes);

        // assert
        assertThat(result, is(equalTo(1.0)));
    }

    @Test
    public void bytesToMib_twoMib_returnsTwo() {
        // arrange
        long twoMibInBytes = 2L * 1024L * 1024L;

        // act
        double result = byteConversion.bytesToMib(twoMibInBytes);

        // assert
        assertThat(result, is(equalTo(2.0)));
    }

    @Test
    public void bytesToMib_largeValue_returnsCorrectMib() {
        // arrange
        long oneThouandMibInBytes = 1000L * 1024L * 1024L;

        // act
        double result = byteConversion.bytesToMib(oneThouandMibInBytes);

        // assert
        assertThat(result, is(equalTo(1000.0)));
    }

    @Test
    public void bytesToMib_singleByte_returnsSmallFraction() {
        // act
        double result = byteConversion.bytesToMib(1L);

        // assert
        assertThat(result > 0.0, is(true));
        assertThat(result < 0.001, is(true));
    }

    @Test
    public void bytesToMib_maxLongValue_returnsLargeValue() {
        // act
        double result = byteConversion.bytesToMib(Long.MAX_VALUE);

        // assert
        assertThat(result > 0.0, is(true));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="mibToBytes">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_MIB_TO_BYTES, location = CommonDataProvider.class)
    public void mibToBytes_mibGiven_returnExpectedBytes(long mib, long expectedBytes) throws IOException, InterruptedException, DecoderException {
        // act
        long result = byteConversion.mibToBytes(mib);

        // assert
        assertThat(result, is(equalTo(expectedBytes)));
    }

    @Test
    public void mibToBytes_zeroMib_returnsZeroBytes() {
        // act
        long result = byteConversion.mibToBytes(0L);

        // assert
        assertThat(result, is(equalTo(0L)));
    }

    @Test
    public void mibToBytes_oneMib_returnsOneMibInBytes() {
        // act
        long result = byteConversion.mibToBytes(1L);

        // assert
        assertThat(result, is(equalTo(1024L * 1024L)));
    }

    @Test
    public void mibToBytes_twoMib_returnsTwoMibInBytes() {
        // act
        long result = byteConversion.mibToBytes(2L);

        // assert
        assertThat(result, is(equalTo(2L * 1024L * 1024L)));
    }

    @Test
    public void mibToBytes_largeValue_returnsCorrectBytes() {
        // arrange
        long largeValue = 1000L;

        // act
        long result = byteConversion.mibToBytes(largeValue);

        // assert
        assertThat(result, is(equalTo(1000L * 1024L * 1024L)));
    }

    @Test
    public void mibToBytes_oneGigabyte_returnsCorrectBytes() {
        // arrange
        long oneGibInMib = 1024L;

        // act
        long result = byteConversion.mibToBytes(oneGibInMib);

        // assert
        assertThat(result, is(equalTo(1024L * 1024L * 1024L)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="round-trip conversion">
    @Test
    public void mibToBytes_bytesToMib_roundTrip_preservesValue() {
        // arrange
        long originalMib = 512L;

        // act
        long bytes = byteConversion.mibToBytes(originalMib);
        double resultMib = byteConversion.bytesToMib(bytes);

        // assert
        assertThat(resultMib, is(equalTo((double) originalMib)));
    }

    @Test
    public void bytesToMib_mibToBytes_roundTrip_preservesValue() {
        // arrange
        long originalBytes = 10L * 1024L * 1024L;

        // act
        double mib = byteConversion.bytesToMib(originalBytes);
        long resultBytes = byteConversion.mibToBytes((long) mib);

        // assert
        assertThat(resultBytes, is(equalTo(originalBytes)));
    }
    // </editor-fold>
}
