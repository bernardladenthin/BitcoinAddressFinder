// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.io.IOException;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public interface AddressesFiles {

    List<String> createAddressesFiles(TemporaryFolder folder, boolean addInvalidAddresses) throws IOException;

    TestAddresses getTestAddresses();
}
