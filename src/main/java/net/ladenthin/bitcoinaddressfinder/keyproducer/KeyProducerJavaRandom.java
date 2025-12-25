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
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.RandomSecretSupplier;
import net.ladenthin.bitcoinaddressfinder.SecretSupplier;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import org.slf4j.Logger;

public class KeyProducerJavaRandom extends KeyProducerJava<CKeyProducerJavaRandom> {

    private final KeyUtility keyUtility;
    private final BitHelper bitHelper;
    private final SecretSupplier randomSupplier;
    
    /**
     * It is already thread local, no need for {@link java.util.concurrent.ThreadLocalRandom}.
     */
    private final Random random;

    @SuppressWarnings({"squid:S2245"})
    public KeyProducerJavaRandom(CKeyProducerJavaRandom cKeyProducerJavaRandom, KeyUtility keyUtility, BitHelper bitHelper, Logger logger) {
        super(cKeyProducerJavaRandom, logger);
        this.keyUtility = keyUtility;
        this.bitHelper = bitHelper;
        
        switch (cKeyProducerJavaRandom.keyProducerJavaRandomInstance) {
            case SECURE_RANDOM:
                try {
                    random = SecureRandom.getInstanceStrong();
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                break;
            case RANDOM_CURRENT_TIME_MILLIS_SEED:
                // EXPLOIT for: https://cwe.mitre.org/data/definitions/338
                random = new Random(System.currentTimeMillis());
                break;
            case RANDOM_CUSTOM_SEED:
                // EXPLOIT for: https://cwe.mitre.org/data/definitions/338
                random = new Random();
                if (cKeyProducerJavaRandom.customSeed != null) {
                    random.setSeed(cKeyProducerJavaRandom.customSeed); // only if explicitly configured
                }
                break;
            case SHA1_PRNG:
                try {
                    random = SecureRandom.getInstance("SHA1PRNG");
                    
                    // To simulate bug: do NOT set a seed at all
                    if (cKeyProducerJavaRandom.customSeed != null) {
                        random.setSeed(cKeyProducerJavaRandom.customSeed); // only if explicitly configured
                    }
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                break;
            default:
                throw new RuntimeException("Unknown keyProducerJavaRandomInstance: " + cKeyProducerJavaRandom.keyProducerJavaRandomInstance);
        }
        randomSupplier = new RandomSecretSupplier(random);
    }
    
    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly) throws NoMoreSecretsAvailableException {
        verifyWorkSize(overallWorkSize, cKeyProducerJava.maxWorkSize);
        return keyUtility.createSecrets(overallWorkSize, returnStartSecretOnly, this.cKeyProducerJava.privateKeyMaxNumBits, randomSupplier);
    }

    @Override
    public void interrupt() {
    }
}
