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
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;

public class ProducerJava extends AbstractProducer {

    protected final CProducerJava producerJava;

    public ProducerJava(CProducerJava producerJava, Stoppable stoppable, Consumer consumer, KeyUtility keyUtility, SecretFactory secretFactory, ProducerCompletionCallback producerCompletionCallback) {
        super(stoppable, consumer, keyUtility, secretFactory, producerCompletionCallback, producerJava.runOnce);
        this.producerJava = producerJava;
    }

    @Override
    public void initProducer() {
    }

    @Override
    public void produceKeys() {
        BigInteger secret = secretFactory.createSecret(producerJava.privateKeyMaxNumBits);
        processSecret(secret);
    }
    
    protected void processSecret(BigInteger secret) {
        try {
            if (PublicKeyBytes.isInvalid(secret)) {
                return;
            }
            
            final BigInteger secretBase = createSecretBase(producerJava, secret, producerJava.logSecretBase);
            PublicKeyBytes[] publicKeyBytesArray = createGrid(secretBase);
            consumer.consumeKeys(publicKeyBytesArray);
        } catch (Exception e) {
            logErrorInProduceKeys(e, secret);
        }
    }

    protected PublicKeyBytes[] createGrid(final BigInteger secretBase) {
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
        return publicKeyBytesArray;
    }

    @Override
    public void releaseProducers() {
    }

}
