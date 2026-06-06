// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.*;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2PKH;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2SH;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2WPKH;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.PublicAddress;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.StaticUnsupportedAddress;
import org.bitcoinj.base.Coin;

public class StaticAddressesFiles implements AddressesFiles {

    private static final String ADDRESS_FILE_ONE = "staticAddressesFile.txt";

    public static final Coin amountOtherAddresses = Coin.SATOSHI;

    public StaticAddressesFiles() {}

    @Override
    public List<String> createAddressesFiles(Path folder, boolean addInvalidAddresses) throws IOException {
        File one = Files.createFile(folder.resolve(ADDRESS_FILE_ONE)).toFile();

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
