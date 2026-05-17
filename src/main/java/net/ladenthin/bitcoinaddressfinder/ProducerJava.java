// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;

public class ProducerJava extends AbstractProducer {

    protected final CProducerJava producerJava;

    public ProducerJava(CProducerJava producerJava, Consumer consumer, KeyUtility keyUtility, KeyProducer keyProducer, BitHelper bitHelper) {
        super(producerJava, consumer, keyUtility, keyProducer, bitHelper);
        this.producerJava = producerJava;
    }

    @Override
    public void processSecretBase(BigInteger secretBase) {
        try {
            PublicKeyBytes[] publicKeyBytesArray = createGrid(secretBase);
            consumer.consumeKeys(publicKeyBytesArray);
        } catch (Exception e) {
            logErrorInProduceKeys(e, secretBase);
        }
    }

    @Override
    public void processSecrets(BigInteger[] secrets) {
        try {
            PublicKeyBytes[] publicKeyBytesArray = new PublicKeyBytes[secrets.length];
            for (int i = 0; i < secrets.length; i++) {
                publicKeyBytesArray[i] = PublicKeyBytes.fromPrivate(secrets[i]);
            }
            consumer.consumeKeys(publicKeyBytesArray);
        } catch (Exception e) {
            logErrorInProduceKeys(e);
        }
    }

    protected PublicKeyBytes[] createGrid(final BigInteger secretBase) {
        PublicKeyBytes[] publicKeyBytesArray = new PublicKeyBytes[producerJava.getOverallWorkSize(bitHelper)];
        for (int i = 0; i < publicKeyBytesArray.length; i++) {
            // create uncompressed
            BigInteger gridSecret = calculateSecretKey(secretBase, i);
            if (privateKeyValidator.isOutsidePrivateKeyRange(gridSecret)) {
                publicKeyBytesArray[i] = PublicKeyBytes.INVALID_KEY_ONE;
                continue;
            }
            publicKeyBytesArray[i] = PublicKeyBytes.fromPrivate(gridSecret);
        }
        return publicKeyBytesArray;
    }
    
    @Override
    public String toString() {
        return "ProducerJava@" + Integer.toHexString(System.identityHashCode(this));
    }
}
