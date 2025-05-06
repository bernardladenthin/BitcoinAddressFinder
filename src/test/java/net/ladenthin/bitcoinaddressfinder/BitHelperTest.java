// @formatter:off
/**
 * Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
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
import java.math.BigInteger;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class BitHelperTest {
    
    // <editor-fold defaultstate="collapsed" desc="getKillBits">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_KILL_BITS, location = CommonDataProvider.class)
    public void getKillBits_bitsGiven_killBitsEqualsExpectation(int bits, BigInteger killBits) throws IOException {
        // arrange
        BitHelper bitHelper = new BitHelper();

        // act, assert
        assertThat(bitHelper.getKillBits(bits), is(equalTo(killBits)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="convertBitsToSize">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BITS_TO_SIZE, location = CommonDataProvider.class)
    public void convertBitsToSize_bitsGiven_sizeEqualsExpectation(int bits, int size) throws IOException {
        // arrange
        BitHelper bitHelper = new BitHelper();

        // act, assert
        assertThat(bitHelper.convertBitsToSize(bits), is(equalTo(size)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="assertBatchSizeInBitsIsInRange">
    @Test(expected = IllegalArgumentException.class)
    public void assertBatchSizeInBitsIsInRange_bitsGivenBelowMinimum_exceptionThrown() throws IOException {
        // arrange
        BitHelper bitHelper = new BitHelper();

        // act, assert
        bitHelper.assertBatchSizeInBitsIsInRange(-1);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void assertBatchSizeInBitsCorrect_bitsGivenOverMaximum_exceptionThrown() throws IOException {
        // arrange
        BitHelper bitHelper = new BitHelper();

        // act, assert
        bitHelper.assertBatchSizeInBitsIsInRange(PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY + 1);
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BIT_SIZES_AT_MOST_24, location = CommonDataProvider.class)
    public void assertBatchSizeInBitsIsInRange_bitsGivenInRange_exceptionThrown(int bits) throws IOException {
        // arrange
        BitHelper bitHelper = new BitHelper();

        // act, assert
        bitHelper.assertBatchSizeInBitsIsInRange(bits);
    }
    // </editor-fold>
}
