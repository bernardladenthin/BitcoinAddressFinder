// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;

public class CKeyProducerJavaIncremental extends CKeyProducerJava {
    
    public String startAddress = PublicKeyBytes.MIN_VALID_PRIVATE_KEY.toString(BitHelper.RADIX_HEX).toUpperCase();
    public String endAddress = PublicKeyBytes.MAX_PRIVATE_KEY_HEX;
    
    public BigInteger getStartAddress() {
        return new BigInteger(startAddress, BitHelper.RADIX_HEX);
    }
    
    public BigInteger getEndAddress() {
        return new BigInteger(endAddress, BitHelper.RADIX_HEX);
    }
}
