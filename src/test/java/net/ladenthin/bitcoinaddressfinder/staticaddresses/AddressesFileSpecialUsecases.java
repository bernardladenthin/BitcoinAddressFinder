// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static net.ladenthin.bitcoinaddressfinder.SeparatorFormat.COMMA;
import static net.ladenthin.bitcoinaddressfinder.SeparatorFormat.SEMICOLON;
import static net.ladenthin.bitcoinaddressfinder.SeparatorFormat.TAB_SPLIT;

public class AddressesFileSpecialUsecases implements AddressesFiles {

    private final static String ADDRESS_FILE_ONE = "staticAddressesFile.txt";

    public final static int NUMBER_OF_ADRESSES = 3;

    private final TestAddresses42 testAddresses;

    public AddressesFileSpecialUsecases() {
        testAddresses = new TestAddresses42(NUMBER_OF_ADRESSES, false);
    }

    @Override
    public List<String> createAddressesFiles(Path folder, boolean addInvalidAddresses) throws IOException {
        File one = Files.createFile(folder.resolve(ADDRESS_FILE_ONE)).toFile();

        Files.write(one.toPath(), getAllAddresses());
        List<String> addresses = new ArrayList<>();
        addresses.add(one.getAbsolutePath());
        return addresses;
    }
    
    /**
     * See {@link net.ladenthin.bitcoinaddressfinder.AddressFileToLMDBTest#addressFilesToLMDB_addressWithAmountOfZero_noExceptionThrown} why {@code 0} is necessary.
     */
    public List<String> getAllAddresses() {
        List<String> addresses = new ArrayList<>();
        addresses.add(testAddresses.getIndexAsBase58String(0) + COMMA.getSymbol() + "0");
        addresses.add(testAddresses.getIndexAsBase58String(1) + TAB_SPLIT.getSymbol() + "0");
        addresses.add(testAddresses.getIndexAsBase58String(2) + SEMICOLON.getSymbol() + "0");
        return addresses;
    }

    @Override
    public TestAddresses getTestAddresses() {
        return testAddresses;
    }
}
