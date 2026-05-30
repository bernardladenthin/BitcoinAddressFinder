// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Top-level operation mode of the CLI tool.
 */
public enum CCommand {
    /** Scan candidate private keys and check the LMDB database. */
    Find,
    /** Export the LMDB database to one or more address files. */
    LMDBToAddressFile,
    /** Import one or more address files into the LMDB database. */
    AddressFilesToLMDB,
    /** Print information about the available OpenCL platforms and devices. */
    OpenCLInfo
}
