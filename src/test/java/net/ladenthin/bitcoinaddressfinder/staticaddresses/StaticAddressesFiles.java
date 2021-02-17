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
import org.bitcoinj.core.Coin;
import org.junit.rules.TemporaryFolder;

public class StaticAddressesFiles implements AddressesFiles {

    private final static String ADDRESS_FILE_ONE = "staticAddressesFile.txt";
    
    public static final Coin amountOtherAddresses = Coin.SATOSHI;

    public StaticAddressesFiles() {
    }

    @Override
    public List<String> createAddressesFiles(TemporaryFolder folder, boolean addInvalidAddresses) throws IOException {
        File one = folder.newFile(ADDRESS_FILE_ONE);

        Files.write(one.toPath(), getAllAddresses());
        List<String> addresses = new ArrayList<>();
        addresses.add(one.getAbsolutePath());
        return addresses;
    }
    
    public List<String> getSupportedAddresses() {
        List<String> addresses = new ArrayList<>();
        for (StaticP2PKHAddress address : StaticP2PKHAddress.values()) {
            addresses.add(address.getPublicAddress());
        }
        for (StaticP2SHAddress address : StaticP2SHAddress.values()) {
            addresses.add(address.getPublicAddress());
        }
        return addresses;
    }
    
    public List<String> getUnsupportedAddresses() {
        List<String> addresses = new ArrayList<>();
        for (StaticUnsupportedAddress address : StaticUnsupportedAddress.values()) {
            addresses.add(address.getPublicAddress());
        }
        return addresses;
    }
    
    public List<String> getAllAddresses() {
        List<String> addresses = new ArrayList<>();
        addresses.addAll(getSupportedAddresses());
        addresses.addAll(getUnsupportedAddresses());
        return addresses;
    }

    @Override
    public TestAddresses getTestAddresses() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
