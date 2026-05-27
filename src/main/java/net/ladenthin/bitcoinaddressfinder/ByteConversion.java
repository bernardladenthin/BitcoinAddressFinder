// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

/**
 * Conversions between mebibytes (MiB) and bytes.
 */
public class ByteConversion {

    /** Creates a new {@link ByteConversion}. */
    public ByteConversion() {
    }

    /**
     * Converts mebibytes to bytes.
     *
     * @param mib amount in MiB
     * @return amount in bytes ({@code mib * 1024 * 1024})
     */
    public long mibToBytes(long mib) {
        return mib * 1_024L * 1_024L;
    }

    /**
     * Converts bytes to mebibytes.
     *
     * @param bytes amount in bytes
     * @return amount in MiB ({@code bytes / 1024 / 1024})
     */
    public double bytesToMib(long bytes) {
        return (double)bytes / (double)(1_024L * 1_024L);
    }
}
