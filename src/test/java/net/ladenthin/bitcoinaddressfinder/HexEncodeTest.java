// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

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