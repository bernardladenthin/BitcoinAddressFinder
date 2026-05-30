// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Configuration for the {@code LMDBToAddressFile} export command.
 */
public class CLMDBToAddressFile {

    /** Creates a new {@link CLMDBToAddressFile}. */
    public CLMDBToAddressFile() {}

    /** LMDB read-only configuration used as the export source. */
    public CLMDBConfigurationReadOnly lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();

    /** Destination address file to write. */
    public String addressesFile = "";

    /** Output format for the destination address file. */
    public CAddressFileOutputFormat addressFileOutputFormat = CAddressFileOutputFormat.HexHash;
}
