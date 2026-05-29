// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import net.ladenthin.bitcoinaddressfinder.configuration.AddressLookupBackend;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.bloom.BloomFilterAccelerator;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.HashSetAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.SortedArrayAddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.AddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.io.TempDir;

public class LMDBBase {

    @TempDir
    public Path folder;

    protected final Network network = new NetworkParameterFactory().getNetwork();
    protected final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(true));

    /**
     * Boolean overload kept for tests that only care about "bloom on/off" and never write
     * to the persistence after opening. {@code true} maps to
     * {@link AddressLookupBackend#BLOOM}, {@code false} to
     * {@link AddressLookupBackend#LMDB_ONLY}.
     */
    protected LMDBHandle createAndFillAndOpenLMDB(
            boolean useStaticAmount, AddressesFiles addressesFiles, boolean addInvalidAddresses, boolean useBloomFilter)
            throws IOException {
        return createAndFillAndOpenLMDB(
                useStaticAmount,
                addressesFiles,
                addInvalidAddresses,
                useBloomFilter ? AddressLookupBackend.BLOOM : AddressLookupBackend.LMDB_ONLY);
    }

    protected LMDBHandle createAndFillAndOpenLMDB(
            boolean useStaticAmount,
            AddressesFiles addressesFiles,
            boolean addInvalidAddresses,
            AddressLookupBackend backend)
            throws IOException {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();

        File lmdbFolderPath =
                testAddressesLMDB.createTestLMDB(folder, addressesFiles, useStaticAmount, addInvalidAddresses);

        CLMDBConfigurationReadOnly lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        lmdbConfigurationReadOnly.addressLookupBackend = backend;
        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        LMDBPersistence lmdb = new LMDBPersistence(lmdbConfigurationReadOnly, persistenceUtils);
        lmdb.init();

        AddressPresence lookup =
                switch (backend) {
                    case LMDB_ONLY -> lmdb;
                    case BLOOM ->
                        BloomFilterAccelerator.populateFrom(lmdb, lmdb, lmdbConfigurationReadOnly.bloomFilterFpp);
                    case HASHSET -> HashSetAddressPresence.populateFrom(lmdb);
                    case SORTED_ARRAY -> SortedArrayAddressPresence.populateFrom(lmdb);
                };

        return new LMDBHandle(lmdb, lookup);
    }
}
