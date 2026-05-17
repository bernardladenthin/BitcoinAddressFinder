// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

public class CLMDBToAddressFile {

    public CLMDBConfigurationReadOnly lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
    
    public String addressesFile = "";
    
    public CAddressFileOutputFormat addressFileOutputFormat = CAddressFileOutputFormat.HexHash;
}
