// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

public class HexEncodeTest {
    
    // <editor-fold defaultstate="collapsed" desc="compare BouncyCastle vs Apache Commons Hex encoding">
    @Test
    public void encodeHexString_bouncyCastleAndApacheCommons_resultMustMatch() {
        // arrange
        byte[] data = {(byte) 0x8F, (byte) 0xC2, (byte) 0xAB, (byte) 0xDE};

        // act
        String hexBc = org.bouncycastle.util.encoders.Hex.toHexString(data);
        String hexApache = org.apache.commons.codec.binary.Hex.encodeHexString(data);

        // assert
        assertThat("Hex encodings from BouncyCastle and Apache Commons must match", hexBc, is(hexApache));
    }
    // </editor-fold>
}