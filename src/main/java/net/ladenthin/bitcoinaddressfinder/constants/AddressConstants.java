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
}
