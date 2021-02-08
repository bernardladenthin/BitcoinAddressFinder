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
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BYTES_TO_MIB, location = CommonDataProvider.class)
    public void byteToMib_bytesGiven_returnExpectedMib(long bytes, double expectedMib) throws IOException, InterruptedException, DecoderException {
        // arrange
        ByteConversion byteConversion = new ByteConversion();
        
        // act
        double result = byteConversion.bytesToMib(bytes);
        
        // assert
        assertThat(result, is(equalTo(expectedMib)));
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_MIB_TO_BYTES, location = CommonDataProvider.class)
    public void byteToMib_bytesGiven_returnExpectedMib(long mib, long expectedBytes) throws IOException, InterruptedException, DecoderException {
        // arrange
        ByteConversion byteConversion = new ByteConversion();
        
        // act
        long result = byteConversion.mibToBytes(mib);
        
        // assert
        assertThat(result, is(equalTo(expectedBytes)));
    }
}
