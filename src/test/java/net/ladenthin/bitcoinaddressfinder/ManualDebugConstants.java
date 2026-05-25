// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

/**
 * Constants used exclusively for manual debugging and verification.
 */
public final class ManualDebugConstants {

    private ManualDebugConstants() {
        // Utility class – no instantiation allowed
    }

    /**
     * Enables runtime validation of public key generation logic.
     */
    public static final boolean ENABLE_RUNTIME_PUBLIC_KEY_CALCULATION_CHECK = false;
}
