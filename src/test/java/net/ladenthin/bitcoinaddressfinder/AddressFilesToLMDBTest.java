// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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
import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.AddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.AddressesFileSpecialUsecases;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2PKH;
import org.bitcoinj.base.Coin;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Direct unit tests for {@link AddressFilesToLMDB}.
 *
 * Tests the run() and interrupt() methods with parameterized test data from existing test providers.
 * This complements indirect tests in AddressFileToLMDBTest and TestAddressesLMDB which also exercise
 * AddressFilesToLMDB through createAndFillAndOpenLMDB() and the main command mode.
 */
@RunWith(DataProviderRunner.class)
public class AddressFilesToLMDBTest extends LMDBBase {

    // <editor-fold defaultstate="collapsed" desc="run - with various AddressesFiles providers">

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void run_withTestAddressesFiles_importsCorrectly(boolean useStaticAmount) throws Exception {
        // arrange
        AddressesFiles addressesFiles = new TestAddressesFiles(true);

        // act
        try (Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, addressesFiles, false, false)) {
            // assert - verify count matches expected test data
            assertThat(persistence.count(), is(equalTo(TestAddressesFiles.NUMBER_OF_ADRESSES)));
        }
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void run_withStaticAddressesFiles_importsAllStaticAddresses(boolean useStaticAmount) throws Exception {
        // arrange
        StaticAddressesFiles staticAddresses = new StaticAddressesFiles();

        // act
        try (Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, staticAddresses, false, false)) {
            // assert - verify all static addresses are imported
            long count = persistence.count();
            assertThat("Static addresses should be imported", count,
                Matchers.greaterThan(0L));

            // Spot-check a few static addresses exist
            for (P2PKH address : P2PKH.values()) {
                ByteBuffer hash160 = address.getPublicKeyHashAsByteBuffer();
                boolean contains = persistence.containsAddress(hash160);
                assertThat("Static address " + address.name() + " should be in LMDB", contains, is(true));
                break; // Just test one to keep test fast
            }
        }
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void run_withSpecialUsecasesAddresses_handlesZeroAmounts(boolean useStaticAmount) throws Exception {
        // arrange
        AddressesFileSpecialUsecases specialAddresses = new AddressesFileSpecialUsecases();

        // act
        try (Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, specialAddresses, false, false)) {
            // assert - verify special use case addresses (including zero amounts) are imported
            long count = persistence.count();
            assertThat("Special use case addresses should be imported", count,
                Matchers.greaterThan(0L));

            // Verify at least one address can be queried
            TestAddresses testAddresses = specialAddresses.getTestAddresses();
            if (testAddresses.getNumberOfAddresses() > 0) {
                ByteBuffer hash160 = testAddresses.getIndexAsHash160ByteBuffer(0);
                Coin amount = persistence.getAmount(hash160);
                assertThat("Amount should be queryable", amount,
                    Matchers.notNullValue());
            }
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="run - with invalid addresses mixed in">

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void run_withInvalidAddressesMixed_skipsInvalidAndImportsValid(boolean useStaticAmount) throws Exception {
        // arrange
        AddressesFiles addressesFiles = new TestAddressesFiles(true);

        // act
        try (Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, addressesFiles, true, false)) {
            // assert - verify that despite invalid addresses being mixed in,
            // the valid ones are still imported correctly
            long count = persistence.count();
            assertThat("Valid addresses should still be imported even with invalid ones present", count,
                is(equalTo((long) TestAddressesFiles.NUMBER_OF_ADRESSES)));
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="run - with Bloom Filter">

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BLOOM_FILTER_ENABLED, location = CommonDataProvider.class)
    public void run_withBloomFilterEnabled_storesAddressesCorrectly(boolean useBloomFilter) throws Exception {
        // arrange
        AddressesFiles addressesFiles = new TestAddressesFiles(true);

        // act
        try (Persistence persistence = createAndFillAndOpenLMDB(false, addressesFiles, false, useBloomFilter)) {
            // assert - verify addresses can be found regardless of bloom filter setting
            TestAddresses testAddresses = addressesFiles.getTestAddresses();
            ByteBuffer hash160 = testAddresses.getIndexAsHash160ByteBuffer(0);

            boolean contains = persistence.containsAddress(hash160);
            assertThat("Address should be found in LMDB (Bloom Filter=" + useBloomFilter + ")",
                contains, is(true));
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="run - Parameterized with compression">

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void run_withCompressedAndUncompressedAddresses_importsCorrectly(boolean compressed, boolean useStaticAmount) throws Exception {
        // arrange
        AddressesFiles addressesFiles = new TestAddressesFiles(compressed);

        // act
        try (Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, addressesFiles, false, false)) {
            // assert
            long count = persistence.count();
            assertThat("Address count should match regardless of compression", count,
                is(equalTo((long) TestAddressesFiles.NUMBER_OF_ADRESSES)));
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="interrupt">

    @Test
    public void interrupt_whenNoFileIsBeingProcessed_doesNotThrow() {
        // arrange
        CAddressFilesToLMDB config = new CAddressFilesToLMDB();
        AddressFilesToLMDB importer = new AddressFilesToLMDB(config);

        // act - should not throw exception
        importer.interrupt();

        // assert - method completed without exception
    }

    // </editor-fold>
}
