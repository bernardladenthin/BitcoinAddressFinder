// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.constants;

/**
 * Address-format constants shared across layers. Lives in the
 * {@link net.ladenthin.bitcoinaddressfinder.constants} leaf so that both the
 * {@code io} layer (address-file parsing in
 * {@code net.ladenthin.bitcoinaddressfinder.io.AddressTxtLine}) and the
 * {@code util} layer (CashAddr handling in
 * {@code net.ladenthin.bitcoinaddressfinder.util.Bech32Helper}) can reference a
 * single source of truth without taking a cross-layer dependency on each other.
 *
 * <p>Previously {@code BITCOIN_CASH_PREFIX} lived in {@code io.AddressTxtLine} and was
 * statically imported by {@code util.Bech32Helper}, creating a foundation&rarr;io upward
 * edge (a latent {@code util}&harr;{@code io} cycle hidden from ArchUnit only by
 * compile-time constant inlining). Hosting it here keeps both consumers pointing strictly
 * downward at the {@code constants} leaf.
 */
public final class AddressConstants {

    private AddressConstants() {
        // utility constant holder; not instantiable.
    }

    /** CashAddr URI scheme prefix ({@code "bitcoincash:"}) stripped from Bitcoin Cash addresses. */
    public static final String BITCOIN_CASH_PREFIX = "bitcoincash:";

    /** Number of version (prefix) bytes in a regular Base58Check address. */
    public static final int VERSION_BYTES_REGULAR = 1;

    /** Number of version (prefix) bytes in a ZCash transparent Base58Check address. */
    public static final int VERSION_BYTES_ZCASH = 2;

    /** Number of trailing checksum bytes in a Base58Check address. */
    public static final int CHECKSUM_BYTES_REGULAR = 4;

    /**
     * SegWit witness version 0 (Bech32) — used by P2WPKH / P2WSH. Defined in
     * <a href="https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki">BIP-173</a>.
     */
    public static final int WITNESS_VERSION_0 = 0;

    /**
     * SegWit witness version 1 (Bech32m) — used by P2TR (Taproot). Defined in
     * <a href="https://github.com/bitcoin/bips/blob/master/bip-0341.mediawiki">BIP-341</a> /
     * <a href="https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki">BIP-350</a>.
     */
    public static final int WITNESS_VERSION_1 = 1;
}
