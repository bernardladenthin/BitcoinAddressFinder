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
import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.*;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2PKH;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.LegacyAddress;
import org.junit.Before;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class AddressFileToLMDBTest extends LMDBBase {

    @Before
    public void init() throws IOException {
    }

    @Test(expected = IllegalArgumentException.class)
    public void addressFilesToLMDB_addressFileDoesNotExists_throwsIllegalArgumentException() throws IOException {
        // arrange, act
        CAddressFilesToLMDB addressFilesToLMDBConfigurationWrite = new CAddressFilesToLMDB();
        
        addressFilesToLMDBConfigurationWrite.addressesFiles.add("thisFileDoesNotExists.txt");
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite = new CLMDBConfigurationWrite();
        File lmdbFolder = folder.newFolder("lmdb");
        String lmdbFolderPath = lmdbFolder.getAbsolutePath();
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite.lmdbDirectory = lmdbFolderPath;
        AddressFilesToLMDB addressFilesToLMDB = new AddressFilesToLMDB(addressFilesToLMDBConfigurationWrite);
        addressFilesToLMDB.run();
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void addressFilesToLMDB_createLMDB_containingTestAddressesHashesWithCorrectAmount(boolean compressed, boolean useStaticAmount) throws Exception {
        // arrange, act
        AddressesFiles addressesFiles = new TestAddressesFiles(compressed);
        try (Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, addressesFiles, false, false)) {
            // assert
            assertThat(persistence.count(), is(equalTo(6L)));

            Coin[] amounts = new Coin[TestAddressesFiles.NUMBER_OF_ADRESSES];
            String[] base58Adresses = addressesFiles.getTestAddresses().getAsBase58StringList().toArray(new String[0]);

            for (int i = 0; i < amounts.length; i++) {
                String base58Adresse = base58Adresses[i];
                LegacyAddress fromBase58 = LegacyAddress.fromBase58(base58Adresse, network);
                ByteBuffer hash160 = keyUtility.addressToByteBuffer(fromBase58);
                amounts[i] = persistence.getAmount(hash160);
                if (useStaticAmount) {
                    assertThat(amounts[i], is(equalTo(Coin.ZERO)));
                } else {
                    assertThat(amounts[i], is(equalTo(TestAddressesFiles.AMOUNTS[i])));
                }
            }
        }
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void addressFilesToLMDB_createLMDBWithStaticAddresses_containingStaticHashes(boolean useStaticAmount) throws Exception {
        // arrange, act
        StaticAddressesFiles staticAddressesFiles = new StaticAddressesFiles();

        // assert
        try (Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, staticAddressesFiles, false, false)) {
            assertThat(persistence.count(), is(equalTo((long)staticAddressesFiles.getSupportedAddresses().size())));
            
            for (P2PKH staticTestAddress : P2PKH.values()) {
                ByteBuffer hash160AsByteBuffer = staticTestAddress.getPublicKeyHashAsByteBuffer();
                boolean contains = persistence.containsAddress(hash160AsByteBuffer);
                assertThat(contains, is(equalTo(Boolean.TRUE)));
            }
        }
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BLOOM_FILTER_ENABLED, location = CommonDataProvider.class)
    public void containsAddress_behavesCorrectly_withOrWithoutBloomFilter(boolean useBloomFilter) throws Exception {
        AddressesFiles addressesFiles = new TestAddressesFiles(true);

        try (Persistence persistence = createAndFillAndOpenLMDB(false, addressesFiles, false, useBloomFilter)) {
            TestAddresses testAddresses = addressesFiles.getTestAddresses();
            ByteBuffer hash160 = testAddresses.getIndexAsHash160ByteBuffer(0);

            boolean contains = persistence.containsAddress(hash160);
            assertThat("Should find known address", contains, is(true));
        }
    }

    /**
     * I got in the past the exception:
     * {@link java.nio.BufferUnderflowException} because zero values are stored with {@code byteBuffer.capacity() == 0}.
     */
    @Test
    public void addressFilesToLMDB_addressWithAmountOfZero_noExceptionThrown() throws Exception {
        // arrange, act
        AddressesFileSpecialUsecases addressesFileSpecialUsecases = new AddressesFileSpecialUsecases();
        //TestAddressesFiles testAddressesFiles = new TestAddressesFiles(true);

        // assert
        try (Persistence persistence = createAndFillAndOpenLMDB(false, addressesFileSpecialUsecases, false, false)) {
            assertThat(persistence.count(), is(equalTo((long)addressesFileSpecialUsecases.getAllAddresses().size())));
            
            TestAddresses testAddresses = addressesFileSpecialUsecases.getTestAddresses();
            
            for (int i = 0; i < testAddresses.getNumberOfAddresses(); i++) {
                ByteBuffer hash160 = testAddresses.getIndexAsHash160ByteBuffer(i);
                Coin amount = persistence.getAmount(hash160);
                assertThat(amount, is(equalTo(Coin.ZERO)));
                
                boolean contains = persistence.containsAddress(hash160);
                assertThat(contains, is(equalTo(Boolean.TRUE)));
            }
        }
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BLOOM_FILTER_ENABLED, location = CommonDataProvider.class)
    public void containsAddress_returnsFalseForUnknownAddress(boolean useBloomFilter) throws Exception {
        AddressesFiles addressesFiles = new TestAddressesFiles(true);

        try (Persistence persistence = createAndFillAndOpenLMDB(false, addressesFiles, false, useBloomFilter)) {
            ByteBuffer hash160 = keyUtility.byteBufferUtility().byteArrayToByteBuffer(TestAddressesFiles.NON_EXISTING_ADDRESS);

            boolean contains = persistence.containsAddress(hash160);
            assertThat("containsAddress() must return false for a known non-existing address used for negative testing.", contains, is(false));
        }
    }
}
