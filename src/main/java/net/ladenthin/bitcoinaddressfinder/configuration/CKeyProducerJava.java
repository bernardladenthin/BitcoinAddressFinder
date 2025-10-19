// @formatter:off
/**
 * Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
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

import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;

public class CKeyProducerJava {
    public String keyProducerId;
    
    /**
     * (2<sup>{@code maxNumBits}</sup> - 1) can be set to a lower value to improve a search on specific ranges (e.g. the puzzle transaction <a href="https://privatekeys.pw/puzzles/bitcoin-puzzle-tx">bitcoin-puzzle-tx</a> ).
     * {@code 1} can't be tested because {@link org.bitcoinj.crypto.ECKey#fromPrivate} throws an {@link IllegalArgumentException}.
     * Range: {@code 2} (inclusive) to {@link PublicKeyBytes#PRIVATE_KEY_MAX_NUM_BITS} (inclusive).
     */
    public int privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
    
    /** Maximum allowed work size (number of secrets to generate) — 2^24 = 16,777,216*/
    public int maxWorkSize = 1 << PublicKeyBytes.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY;
}
