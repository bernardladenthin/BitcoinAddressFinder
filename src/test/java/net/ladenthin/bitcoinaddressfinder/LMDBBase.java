// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.AddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import org.bitcoinj.base.Network;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class LMDBBase {
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    
    protected final Network network = new NetworkParameterFactory().getNetwork();
    protected final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(true));
    
    protected Persistence createAndFillAndOpenLMDB(boolean useStaticAmount, AddressesFiles addressesFiles, boolean addInvalidAddresses, boolean useBloomFilter) throws IOException {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();

        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, addressesFiles, useStaticAmount, addInvalidAddresses);

        CLMDBConfigurationReadOnly lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        lmdbConfigurationReadOnly.useBloomFilter = useBloomFilter;
        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        Persistence persistence = new LMDBPersistence(lmdbConfigurationReadOnly, persistenceUtils);
        persistence.init();
        return persistence;
    }
}
