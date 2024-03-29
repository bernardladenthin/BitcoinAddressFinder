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
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.util.Random;

public class MockKeyProducer implements KeyProducer {

    private final KeyUtility keyUtility;
    private final Random random;
    
    MockKeyProducer(KeyUtility keyUtility, Random random) {
        this.keyUtility = keyUtility;
        this.random = random;
    }

    @Override
    public BigInteger createSecret(int maximumBitLength) {
        BigInteger secret = keyUtility.createSecret(maximumBitLength, random);
        return secret;
    }

    
}
