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
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFileOutputFormat;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import org.apache.commons.io.FileUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class LMDBToAddressFileTest extends LMDBBase {

    @Before
    public void init() throws IOException {
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void writeAllAmountsToAddressFileAsDynamicWidthBase58BitcoinAddressWithAmount(boolean compressed, boolean useStaticAmount) throws IOException {
        // arrange
        TestAddressesFiles testAddressesFiles = new TestAddressesFiles(compressed);
        Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, testAddressesFiles, false);

        // act
        File file = folder.newFile();
        persistence.writeAllAmountsToAddressFile(file, CAddressFileOutputFormat.DynamicWidthBase58BitcoinAddressWithAmount);

        // assert
        try {
            List<String> contents = FileUtils.readLines(file, "UTF-8");

            // set/sort the result because the list might not have the same order for different test executions
            Set<String> contentsAsSet = new HashSet<>(contents);
            
            final Set<String> expected;
            if (compressed && useStaticAmount) {
                expected = testAddressesFiles.compressedTestAddressesWithStaticAmountAsDynamicWidthBase58BitcoinAddressWithAmount;
            } else if(compressed) {
                expected = testAddressesFiles.compressedTestAddressesAsDynamicWidthBase58BitcoinAddressWithAmount;
            } else if(useStaticAmount) {
                expected = testAddressesFiles.uncompressedTestAddressesWithStaticAmountAsDynamicWidthBase58BitcoinAddressWithAmount;
            } else {
                expected = testAddressesFiles.uncompressedTestAddressesAsDynamicWidthBase58BitcoinAddressWithAmount;
            }
            
            assertThat(contentsAsSet, is(equalTo(expected)));
            
        } finally {
            persistence.close();
        }
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void writeAllAmountsToAddressFileAsFixedWidthBase58BitcoinAddress(boolean compressed, boolean useStaticAmount) throws IOException {
        // arrange
        TestAddressesFiles testAddressesFiles = new TestAddressesFiles(compressed);
        Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, testAddressesFiles, false);

        // act
        File file = folder.newFile();
        persistence.writeAllAmountsToAddressFile(file, CAddressFileOutputFormat.FixedWidthBase58BitcoinAddress);

        // assert
        try {
            List<String> contents = FileUtils.readLines(file, "UTF-8");

            // set/sort the result because the list might not have the same order for different test executions
            Set<String> contentsAsSet = new HashSet<>(contents);

            final Set<String> expected;
            if (compressed && useStaticAmount) {
                expected = testAddressesFiles.compressedTestAddressesWithStaticAmountAsFixedWidthBase58BitcoinAddress;
            } else if(compressed) {
                expected = testAddressesFiles.compressedTestAddressesAsFixedWidthBase58BitcoinAddress;
            } else if(useStaticAmount) {
                expected = testAddressesFiles.uncompressedTestAddressesWithStaticAmountAsFixedWidthBase58BitcoinAddress;
            } else {
                expected = testAddressesFiles.uncompressedTestAddressesAsFixedWidthBase58BitcoinAddress;
            }
            
            assertThat(contentsAsSet, is(equalTo(expected)));
            
        } finally {
            persistence.close();
        }
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void writeAllAmountsToAddressFileAsHexHash(boolean compressed, boolean useStaticAmount) throws IOException {
        // arrange
        TestAddressesFiles testAddressesFiles = new TestAddressesFiles(compressed);
        Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, testAddressesFiles, false);

        // act
        File file = folder.newFile();
        persistence.writeAllAmountsToAddressFile(file, CAddressFileOutputFormat.HexHash);

        // assert
        try {
            List<String> contents = FileUtils.readLines(file, "UTF-8");

            // set/sort the result because the list might not have the same order for different test executions
            Set<String> contentsAsSet = new HashSet<>(contents);

            final Set<String> expected;
            if (compressed && useStaticAmount) {
                expected = testAddressesFiles.compressedTestAddressesWithStaticAmountAsHexHash;
            } else if(compressed) {
                expected = testAddressesFiles.compressedTestAddressesAsHexHash;
            } else if(useStaticAmount) {
                expected = testAddressesFiles.uncompressedTestAddressesWithStaticAmountAsHexHash;
            } else {
                expected = testAddressesFiles.uncompressedTestAddressesAsHexHash;
            }
            
            assertThat(contentsAsSet, is(equalTo(expected)));
            
        } finally {
            persistence.close();
        }
    }
}
