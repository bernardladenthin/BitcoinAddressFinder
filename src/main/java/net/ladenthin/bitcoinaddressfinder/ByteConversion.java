// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

public class ByteConversion {
    
    public long mibToBytes(long mib) {
        return mib * 1_024L * 1_024L;
    }
    
    public double bytesToMib(long bytes) {
        return (double)bytes / (double)(1_024L * 1_024L);
    }
}
