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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;

public class KeyProducerJavaRandom extends KeyProducerJava {

    private final CKeyProducerJavaRandom cKeyProducerJavaRandom;
    private final KeyUtility keyUtility;
    
    /**
     * It is already thread local, no need for {@link java.util.concurrent.ThreadLocalRandom}.
     */
    private final Random random;
    
    public KeyProducerJavaRandom(CKeyProducerJavaRandom cKeyProducerJavaRandom, KeyUtility keyUtility) {
        super(cKeyProducerJavaRandom);
        this.cKeyProducerJavaRandom = cKeyProducerJavaRandom;
        this.keyUtility = keyUtility;
        
        switch (cKeyProducerJavaRandom.keyProducerJavaRandomInstance) {
            case SECURE_RANDOM:
                try {
                    random = SecureRandom.getInstanceStrong();
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                break;
            case RANDOM_SEED_CURRENT_TIME_MILLIS:
                random = new Random(System.currentTimeMillis());
                break;
            case RANDOM_CUSTOM_SEED:
                random = new Random(cKeyProducerJavaRandom.customSeed);
                break;
            default:
                throw new RuntimeException("Unknown keyProducerJavaRandomInstance: " + cKeyProducerJavaRandom.keyProducerJavaRandomInstance);
        }
    }
    
    @Override
    public BigInteger createSecret(int maximumBitLength) {
        BigInteger secret = keyUtility.createSecret(maximumBitLength, random);
        return secret;
    }
}
