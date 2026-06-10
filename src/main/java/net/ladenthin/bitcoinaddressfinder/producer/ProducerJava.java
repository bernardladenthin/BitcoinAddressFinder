// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import java.math.BigInteger;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;

/**
 * CPU-based producer that derives public keys using bitcoinj's {@link PublicKeyBytes#fromPrivate}.
 */
@ToString(callSuper = true)
public class ProducerJava extends AbstractProducer {

    /** Producer-specific configuration. */
    protected final CProducerJava producerJava;

    /**
     * Creates a new CPU producer.
     *
     * @param producerJava the producer configuration
     * @param consumer     the downstream consumer
     * @param keyUtility   cryptographic helper
     * @param keyProducer  the secret supplying strategy
     * @param bitHelper    bit/batch-size helper
     * @param runtimeStatistics shared runtime metrics sink for per-producer batch counts
     */
    public ProducerJava(
            CProducerJava producerJava,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper,
            RuntimeStatistics runtimeStatistics) {
        super(producerJava, consumer, keyUtility, keyProducer, bitHelper, runtimeStatistics);
        this.producerJava = producerJava;
    }

    @Override
    protected ProducerType producerType() {
        return ProducerType.CPU;
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

    /**
     * Creates the grid of derived keys for the given secret base.
     *
     * @param secretBase the masked base secret
     * @return the array of derived {@link PublicKeyBytes}
     */
    protected PublicKeyBytes[] createGrid(final BigInteger secretBase) {
        PublicKeyBytes[] publicKeyBytesArray = new PublicKeyBytes[producerJava.getOverallWorkSize()];
        for (int i = 0; i < publicKeyBytesArray.length; i++) {
            // create uncompressed
            BigInteger gridSecret =
                    // ADD combine mode: CalculateSecretKeyBenchmark measures ADD faster than OR for the aligned base +
                    // offset.
                    KeyUtility.calculateSecretKey(secretBase, i, KeyUtility.CALCULATE_SECRET_KEY_USE_OR);
            if (privateKeyValidator.isOutsidePrivateKeyRange(gridSecret)) {
                publicKeyBytesArray[i] = PublicKeyBytes.INVALID_KEY_ONE;
                continue;
            }
            publicKeyBytesArray[i] = PublicKeyBytes.fromPrivate(gridSecret);
        }
        return publicKeyBytesArray;
    }
}
