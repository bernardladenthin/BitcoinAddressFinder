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
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;

public class ProducerJava extends AbstractProducer {

    private final CProducerJava producerJava;

    public ProducerJava(CProducerJava producerJava, AtomicBoolean shouldRun, Consumer consumer, KeyUtility keyUtility, Random random) {
        super(shouldRun, consumer, keyUtility, random);
        this.producerJava = producerJava;
    }

    @Override
    public void initProducers() {
    }

    @Override
    public void produceKeys() {
        BigInteger secret = null;
        try {
            secret = keyUtility.createSecret(producerJava.privateKeyMaxNumBits, random);
            if (PublicKeyBytes.isInvalid(secret)) {
                return;
            }
            
            final BigInteger secretBase = createSecretBase(producerJava, secret, producerJava.logSecretBase);
            
            PublicKeyBytes[] publicKeyBytesArray = new PublicKeyBytes[producerJava.getWorkSize()];
            for (int i = 0; i < publicKeyBytesArray.length; i++) {
                // create uncompressed
                BigInteger gridSecret = calculateSecretKey(secretBase, i);
                if (PublicKeyBytes.isInvalid(gridSecret)) {
                    publicKeyBytesArray[i] = PublicKeyBytes.INVALID_KEY_ONE;
                    continue;
                }
                publicKeyBytesArray[i] = PublicKeyBytes.fromPrivate(gridSecret);
            }

            consumer.consumeKeys(publicKeyBytesArray);
        } catch (Exception e) {
            logErrorInProduceKeys(e, secret);
        }
    }

    @Override
    public void releaseProducers() {
    }

}
