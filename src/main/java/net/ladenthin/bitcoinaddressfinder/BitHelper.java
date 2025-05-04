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

import java.math.BigInteger;

public class BitHelper {
    
    public int convertBitsToSize(int bits) {
        return 1 << bits;
    }
    
    public BigInteger getKillBits(int bits) {
        return BigInteger.valueOf(2).pow(bits).subtract(BigInteger.ONE);
    }
    
    public void assertBatchSizeInBitsIsInRange(int batchSizeInBits) {
        if (batchSizeInBits < 0) {
            throw new IllegalArgumentException("batchSizeInBits must higher or equal to 0.");
        }
        if (batchSizeInBits > PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY) {
            throw new IllegalArgumentException("batchSizeInBits must be lower or equal than " + PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY + ".");
        }
    }
}
