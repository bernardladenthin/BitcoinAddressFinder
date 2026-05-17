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
     * 
     * Intended only for debugging purposes such as verifying OpenCL results or testing hardware correctness.
     * 
     * <p><b>Performance impact:</b> Enabling this drastically reduces throughput.</p>
     * 
     * @see net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava#runtimePublicKeyCalculationCheck
     */
    public static final boolean ENABLE_RUNTIME_PUBLIC_KEY_CALCULATION_CHECK = false;
}
