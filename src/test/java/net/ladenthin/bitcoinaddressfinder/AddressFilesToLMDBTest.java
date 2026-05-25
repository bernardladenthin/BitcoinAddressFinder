// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.*;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2PKH;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.LegacyAddress;
import org.junit.jupiter.api.BeforeEach;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Direct unit tests for {@link AddressFilesToLMDB}.
 *
 * Tests the run() and interrupt() methods with parameterized test data from existing test providers.
 */
public class AddressFilesToLMDBTest extends LMDBBase {

    @BeforeEach
    public void init() throws IOException {
    }

    @Test
    public void addressFilesToLMDB_addressFileDoesNotExists_throwsIllegalArgumentException() throws IOException {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            // arrange, act
            CAddressFilesToLMDB addressFilesToLMDBConfigurationWrite = new CAddressFilesToLMDB();
            
            addressFilesToLMDBConfigurationWrite.addressesFiles.add("thisFileDoesNotExists.txt");
            addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite = new CLMDBConfigurationWrite();
            File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();
            String lmdbFolderPath = lmdbFolder.getAbsolutePath();
            addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite.lmdbDirectory = lmdbFolderPath;
            AddressFilesToLMDB addressFilesToLMDB = new AddressFilesToLMDB(addressFilesToLMDBConfigurationWrite);
            addressFilesToLMDB.run();
        });
    }

    @ParameterizedTest
    @MethodSource("net.ladenthin.bitcoinaddressfinder.CommonDataProvider#compressedAndStaticAmount")
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

    @ParameterizedTest
    @MethodSource("net.ladenthin.bitcoinaddressfinder.CommonDataProvider#staticAmount")
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
    
    @ParameterizedTest
    @MethodSource("net.ladenthin.bitcoinaddressfinder.CommonDataProvider#bloomFilterEnabled")
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

    @ParameterizedTest
    @MethodSource("net.ladenthin.bitcoinaddressfinder.CommonDataProvider#bloomFilterEnabled")
    public void containsAddress_returnsFalseForUnknownAddress(boolean useBloomFilter) throws Exception {
        AddressesFiles addressesFiles = new TestAddressesFiles(true);

        try (Persistence persistence = createAndFillAndOpenLMDB(false, addressesFiles, false, useBloomFilter)) {
            ByteBuffer hash160 = keyUtility.byteBufferUtility().byteArrayToByteBuffer(TestAddressesFiles.NON_EXISTING_ADDRESS);

            boolean contains = persistence.containsAddress(hash160);
            assertThat("containsAddress() must return false for a known non-existing address used for negative testing.", contains, is(false));
        }
    }

    // <editor-fold defaultstate="collapsed" desc="interrupt() edge cases">

    @Test
    public void interrupt_withNoCurrentFile_doesNotThrow() {
        // arrange
        CAddressFilesToLMDB config = new CAddressFilesToLMDB();
        AddressFilesToLMDB importer = new AddressFilesToLMDB(config);

        // act - should not throw exception
        importer.interrupt();

        // assert - method completed without exception
    }

    // </editor-fold>
}
