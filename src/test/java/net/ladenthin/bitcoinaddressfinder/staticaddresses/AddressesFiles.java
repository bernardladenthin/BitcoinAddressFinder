// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface AddressesFiles {

    List<String> createAddressesFiles(Path folder, boolean addInvalidAddresses) throws IOException;

    TestAddresses getTestAddresses();
}
