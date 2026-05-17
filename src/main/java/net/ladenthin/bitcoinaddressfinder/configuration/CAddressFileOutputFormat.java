// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

public enum CAddressFileOutputFormat {
    /**
     * The hash160 will be written encoded in hex without the amount. Optimal to view with a viewer with a fixed width (e.g. HxD).
     */
    HexHash,
    /**
     * The addresses will be written with a fixed width and without the amount. Optimal to view with a viewer with a fixed width (e.g. HxD).
     */
    FixedWidthBase58BitcoinAddress,
    /**
     * The addresses will be written with amount. Separated with a {@link net.ladenthin.bitcoinaddressfinder.SeparatorFormat#COMMA}.
     */
    DynamicWidthBase58BitcoinAddressWithAmount
}
