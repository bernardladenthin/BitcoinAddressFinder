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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HexEncodeTest {

    @Test
    public void testEncodeHexString() {
        byte[] data = { (byte) 0x8f, (byte) 0xc2, (byte) 0xab, (byte) 0xde };

        String resultBouncyCastle = org.bouncycastle.util.encoders.Hex.toHexString(data);

        String resultApache = org.apache.commons.codec.binary.Hex.encodeHexString(data);

        assertEquals("Die Hex-Strings sollten gleich sein", resultBouncyCastle, resultApache);
    }
}