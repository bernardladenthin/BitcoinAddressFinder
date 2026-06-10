// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for the {@code SecureRandom}-based key producer.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CKeyProducerJavaRandom extends CKeyProducerJava {

    /** Creates a new {@link CKeyProducerJavaRandom}. */
    public CKeyProducerJavaRandom() {}

    /**
     * Selects the pseudo-random number generator (PRNG) used for private-key creation.
     * See {@link CKeyProducerJavaRandomAlgorithm} for the available implementations.
     * Defaults to {@link CKeyProducerJavaRandomAlgorithm#SECURE_RANDOM} to prefer security
     * over convenience.
     */
    public CKeyProducerJavaRandomAlgorithm randomAlgorithm = CKeyProducerJavaRandomAlgorithm.SECURE_RANDOM;

    /**
     * Used for {@link CKeyProducerJavaRandomAlgorithm#RANDOM_CUSTOM_SEED}.
     * Also optionally used by {@link CKeyProducerJavaRandomAlgorithm#SHA1_PRNG}
     * if deterministic output is desired.
     * Nullable to simulate unset seed.
     */
    public @Nullable Long customSeed;
}
