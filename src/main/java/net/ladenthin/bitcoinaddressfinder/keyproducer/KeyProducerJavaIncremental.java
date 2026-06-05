// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaIncremental;
import org.jspecify.annotations.NonNull;

/**
 * Key producer that iterates a private-key range sequentially.
 */
public class KeyProducerJavaIncremental extends KeyProducerJava<CKeyProducerJavaIncremental> {

    private @NonNull BigInteger currentValue;

    /**
     * Creates a new incremental key producer.
     *
     * @param cKeyProducerJavaIncremental the incremental configuration
     * @param keyUtility                  cryptographic helper (unused but kept for symmetry)
     * @param bitHelper                   bit/batch-size helper (unused but kept for symmetry)
     */
    public KeyProducerJavaIncremental(
            CKeyProducerJavaIncremental cKeyProducerJavaIncremental, KeyUtility keyUtility, BitHelper bitHelper) {
        super(cKeyProducerJavaIncremental);
        // Use the config POJO's canonical parser rather than re-parsing the raw string
        // here, so the radix and any future format tightening live in one place.
        this.currentValue = cKeyProducerJavaIncremental.getStartPrivateKey();
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly)
            throws NoMoreSecretsAvailableException {
        verifyWorkSize(overallWorkSize, cKeyProducerJava.maxWorkSize);
        final BigInteger endPrivateKey = cKeyProducerJava.getEndPrivateKey();
        if (currentValue.compareTo(endPrivateKey) > 0) {
            throw new NoMoreSecretsAvailableException(currentValue + " exceeds ");
        }

        int length = returnStartSecretOnly ? 1 : overallWorkSize;
        BigInteger[] secrets = new BigInteger[length];
        BigInteger counter = currentValue;
        for (int i = 0; i < length; i++) {
            if (counter.compareTo(endPrivateKey) > 0) {
                throw new NoMoreSecretsAvailableException(counter + " exceeds end private key " + endPrivateKey);
            }
            secrets[i] = counter;
            counter = counter.add(BigInteger.ONE);
        }

        currentValue = currentValue.add(BigInteger.valueOf(overallWorkSize));

        return secrets;
    }

    @Override
    public void interrupt() {}
}
