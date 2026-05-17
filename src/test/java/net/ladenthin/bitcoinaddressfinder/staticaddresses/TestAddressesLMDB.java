// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.io.File;
import java.io.IOException;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.AddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import org.junit.rules.TemporaryFolder;

public class TestAddressesLMDB {
    
    
    public File createTestLMDB(TemporaryFolder folder, AddressesFiles addressesFiles, boolean useStaticAmount, boolean addInvalidAddresses) throws IOException {
        CAddressFilesToLMDB addressFilesToLMDBConfigurationWrite = new CAddressFilesToLMDB();
        
        List<String> files = addressesFiles.createAddressesFiles(folder, addInvalidAddresses);
        addressFilesToLMDBConfigurationWrite.addressesFiles.addAll(files);
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite = new CLMDBConfigurationWrite();
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite.useStaticAmount = useStaticAmount;
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite.staticAmount = 0L;
        File lmdbFolder = folder.newFolder("lmdb");
        String lmdbFolderPath = lmdbFolder.getAbsolutePath();
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite.lmdbDirectory = lmdbFolderPath;
        AddressFilesToLMDB addressFilesToLMDB = new AddressFilesToLMDB(addressFilesToLMDBConfigurationWrite);
        addressFilesToLMDB.run();
        return lmdbFolder;
    }
}
