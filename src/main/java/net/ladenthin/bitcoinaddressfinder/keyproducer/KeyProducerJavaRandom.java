// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
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

/**
 * Key producer that draws secrets from a configurable random-number generator.
 */
public class KeyProducerJavaRandom extends KeyProducerJava<CKeyProducerJavaRandom> {

    /**
     * Final no-op to prevent the finalizer-attack vector flagged by spotbugs
     * CT_CONSTRUCTOR_THROW: the constructor wraps two checked
     * {@link java.security.NoSuchAlgorithmException}s into RuntimeExceptions for
     * the SECURE_RANDOM / SHA1_PRNG branches, which leaves a partially
     * constructed instance reachable to any subclass that overrides
     * {@link Object#finalize()}. Marking {@code finalize()} final here defeats
     * the override while keeping the class itself subclassable for future
     * Mockito spies or test doubles.
     */
    @SuppressWarnings({"deprecation", "removal"})
    @Override
    protected final void finalize() {
        // no-op
    }

    private final KeyUtility keyUtility;
    private final SecretSupplier randomSupplier;

    /**
     * Creates a new random key producer using the algorithm configured in the supplied {@link CKeyProducerJavaRandom}.
     *
     * @param cKeyProducerJavaRandom the random configuration
     * @param keyUtility             cryptographic helper
     * @param bitHelper              bit/batch-size helper
     */
    @SuppressWarnings({"squid:S2245"})
    public KeyProducerJavaRandom(
            CKeyProducerJavaRandom cKeyProducerJavaRandom, KeyUtility keyUtility, BitHelper bitHelper) {
        super(cKeyProducerJavaRandom);
        this.keyUtility = keyUtility;

        // It is already thread local, no need for ThreadLocalRandom.
        Random random;
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
                random = cKeyProducerJavaRandom.customSeed != null
                        ? new Random(cKeyProducerJavaRandom.customSeed)
                        : new Random();
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
                throw new RuntimeException("Unknown keyProducerJavaRandomInstance: "
                        + cKeyProducerJavaRandom.keyProducerJavaRandomInstance);
        }
        randomSupplier = new RandomSecretSupplier(random);
    }

    @Override
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly)
            throws NoMoreSecretsAvailableException {
        verifyWorkSize(overallWorkSize, cKeyProducerJava.maxWorkSize);
        return keyUtility.createSecrets(
                overallWorkSize, returnStartSecretOnly, this.cKeyProducerJava.privateKeyMaxNumBits, randomSupplier);
    }

    @Override
    public void interrupt() {}
}
