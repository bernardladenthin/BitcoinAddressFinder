// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.nio.ByteBuffer;
import java.util.List;
import org.bitcoinj.crypto.ECKey;

public interface TestAddresses {

    int getNumberOfAddresses();

    List<ECKey> getECKeys();

    String getAsBase58Strings();

    List<String> getAsBase58StringList();

    String getIndexAsBase58String(int index);

    String getIndexAsHash160HexEncoded(int index);

    byte[] getIndexAsHash160(int index);

    ByteBuffer getIndexAsHash160ByteBuffer(int index);
}
