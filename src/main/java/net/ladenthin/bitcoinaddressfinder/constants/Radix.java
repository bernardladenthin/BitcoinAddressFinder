// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.constants;

/**
 * Named radix constants used across the project. Lives in the
 * {@link net.ladenthin.bitcoinaddressfinder.constants} leaf so the
 * configuration layer, the runtime layer, and the test layer can all
 * route through a single source of truth for "{@code 16}" (rather than
 * inlining the literal or carrying a {@code BitHelper.RADIX_HEX}
 * duplicate in the root package).
 *
 * <p>Add a new constant here only when at least two call sites would
 * otherwise inline the same numeric literal.
 */
public final class Radix {

    private Radix() {
        // utility constant holder; not instantiable.
    }

    /** Hexadecimal radix ({@code 16}) for {@link java.math.BigInteger} / {@link Integer} parsing. */
    public static final int HEX = 16;
}
