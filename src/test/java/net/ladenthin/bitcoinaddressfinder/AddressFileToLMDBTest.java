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
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.*;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
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
        AddressFilesToLMDB addressFilesToLMDB = new AddressFilesToLMDB(addressFilesToLMDBConfigurationWrite, new AtomicBoolean(true));
        addressFilesToLMDB.run();
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void addressFilesToLMDB_createLMDB_containingTestAddressesHashesWithCorrectAmount(boolean compressed, boolean useStaticAmount) throws IOException {
        // arrange, act
        AddressesFiles addressesFiles = new TestAddressesFiles(compressed);
        Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, addressesFiles, false);

        // assert
        try {
            assertThat(persistence.count(), is(equalTo(6L)));
            
            Coin[] amounts = new Coin[TestAddressesFiles.NUMBER_OF_ADRESSES];
            String[] base58Adresses = addressesFiles.getTestAddresses().getAsBase58StringList().toArray(new String[0]);
            
            for (int i = 0; i < amounts.length; i++) {
                String base58Adresse = base58Adresses[i];
                LegacyAddress fromBase58 = LegacyAddress.fromBase58(networkParameters, base58Adresse);
                ByteBuffer hash160 = keyUtility.addressToByteBuffer(fromBase58);
                amounts[i] = persistence.getAmount(hash160);
                if (useStaticAmount) {
                    assertThat(amounts[i], is(equalTo(Coin.ZERO)));
                } else {
                    assertThat(amounts[i], is(equalTo(TestAddressesFiles.AMOUNTS[i])));
                }
            }
        } finally {
            persistence.close();
        }
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void addressFilesToLMDB_createLMDBWithStaticAddresses_containingStaticHashes(boolean useStaticAmount) throws IOException {
        // arrange, act
        StaticAddressesFiles staticAddressesFiles = new StaticAddressesFiles();
        Persistence persistence = createAndFillAndOpenLMDB(useStaticAmount, staticAddressesFiles, false);

        // assert
        try {
            assertThat(persistence.count(), is(equalTo((long)staticAddressesFiles.getSupportedAddresses().size())));
            
            for (StaticP2PKHAddress staticTestAddress : StaticP2PKHAddress.values()) {
                ByteBuffer hash160AsByteBuffer = staticTestAddress.getPublicKeyHashAsByteBuffer();
                boolean contains = persistence.containsAddress(hash160AsByteBuffer);
                assertThat(contains, is(equalTo(Boolean.TRUE)));
            }
        } finally {
            persistence.close();
        }
    }

    /**
     * I got in the past the exception:
     * {@link java.nio.BufferUnderflowException} because zero values are stored with {@code byteBuffer.capacity() == 0}.
     */
    @Test
    public void addressFilesToLMDB_addressWithAmountOfZero_noExceptionThrown() throws IOException {
        // arrange, act
        AddressesFileSpecialUsecases addressesFileSpecialUsecases = new AddressesFileSpecialUsecases();
        //TestAddressesFiles testAddressesFiles = new TestAddressesFiles(true);
        Persistence persistence = createAndFillAndOpenLMDB(false, addressesFileSpecialUsecases, false);

        // assert
        try {
            assertThat(persistence.count(), is(equalTo((long)addressesFileSpecialUsecases.getAllAddresses().size())));
            
            TestAddresses testAddresses = addressesFileSpecialUsecases.getTestAddresses();
            
            for (int i = 0; i < testAddresses.getNumberOfAddresses(); i++) {
                ByteBuffer hash160 = testAddresses.getIndexAsHash160ByteBuffer(i);
                Coin amount = persistence.getAmount(hash160);
                assertThat(amount, is(equalTo(Coin.ZERO)));
                
                boolean contains = persistence.containsAddress(hash160);
                assertThat(contains, is(equalTo(Boolean.TRUE)));
            }
        } finally {
            persistence.close();
        }
    }

}
