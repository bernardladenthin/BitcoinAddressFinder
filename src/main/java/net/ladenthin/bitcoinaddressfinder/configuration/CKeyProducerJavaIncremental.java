// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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
