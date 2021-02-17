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
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.rules.TemporaryFolder;

import static net.ladenthin.bitcoinaddressfinder.AddressTxtLine.COMMA;
import static net.ladenthin.bitcoinaddressfinder.AddressTxtLine.SEMICOLON;
import static net.ladenthin.bitcoinaddressfinder.AddressTxtLine.TAB_SPLIT;

public class AddressesFileSpecialUsecases implements AddressesFiles {

    private final static String ADDRESS_FILE_ONE = "staticAddressesFile.txt";
    
    public final static int NUMBER_OF_ADRESSES = 3;
    
    private final TestAddresses42 testAddresses;
    
    public AddressesFileSpecialUsecases() {
        testAddresses = new TestAddresses42(NUMBER_OF_ADRESSES, false);
    }
    
    @Override
    public List<String> createAddressesFiles(TemporaryFolder folder, boolean addInvalidAddresses) throws IOException {
        File one = folder.newFile(ADDRESS_FILE_ONE);

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
        addresses.add(testAddresses.getIndexAsBase58String(0) + COMMA + "0");
        addresses.add(testAddresses.getIndexAsBase58String(1) + TAB_SPLIT + "0");
        addresses.add(testAddresses.getIndexAsBase58String(2) + SEMICOLON + "0");
        return addresses;
    }

    @Override
    public TestAddresses getTestAddresses() {
        return testAddresses;
    }
}
