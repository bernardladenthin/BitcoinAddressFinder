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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bitcoinj.base.Coin;
import org.junit.rules.TemporaryFolder;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.*;

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
        addresses.addAll(extractAddresses(P2PKH.class));
        addresses.addAll(extractAddresses(P2SH.class));
        addresses.addAll(extractAddresses(P2WPKH.class));
        return addresses;
    }
    
    public List<String> getAllAddresses() {
        List<String> addresses = new ArrayList<>();
        addresses.addAll(getSupportedAddresses());
        addresses.addAll(getUnsupportedAddresses());
        return addresses;
    }
    
    public List<String> getUnsupportedAddresses() {
        return extractAddresses(StaticUnsupportedAddress.class);
    }
    
    private <T extends Enum<T> & PublicAddress> List<String> extractAddresses(Class<T> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                     .map(PublicAddress::getPublicAddress)
                     .collect(Collectors.toList());
    }

    @Override
    public TestAddresses getTestAddresses() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
