// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.command.AddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;

public class TestAddressesLMDB {

    public File createTestLMDB(
            Path folder, AddressesFiles addressesFiles, boolean useStaticAmount, boolean addInvalidAddresses)
            throws IOException {
        CAddressFilesToLMDB addressFilesToLMDBConfigurationWrite = new CAddressFilesToLMDB();

        List<String> files = addressesFiles.createAddressesFiles(folder, addInvalidAddresses);
        addressFilesToLMDBConfigurationWrite.addressesFiles.addAll(files);
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite = new CLMDBConfigurationWrite();
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite.useStaticAmount = useStaticAmount;
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite.staticAmount = 0L;
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();
        String lmdbFolderPath = lmdbFolder.getAbsolutePath();
        addressFilesToLMDBConfigurationWrite.lmdbConfigurationWrite.lmdbDirectory = lmdbFolderPath;
        AddressFilesToLMDB addressFilesToLMDB = new AddressFilesToLMDB(addressFilesToLMDBConfigurationWrite);
        addressFilesToLMDB.run();
        return lmdbFolder;
    }
}
