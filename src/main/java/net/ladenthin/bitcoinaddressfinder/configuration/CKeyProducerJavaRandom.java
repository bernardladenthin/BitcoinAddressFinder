// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import org.jspecify.annotations.Nullable;

public class CKeyProducerJavaRandom extends CKeyProducerJava {

    /**
     * Selects the pseudo-random number generator (PRNG) used for private-key creation.
     * See {@link CKeyProducerJavaRandomInstance} for the available implementations.
     * Defaults to {@link CKeyProducerJavaRandomInstance#SECURE_RANDOM} to prefer security
     * over convenience.
     */
    public CKeyProducerJavaRandomInstance keyProducerJavaRandomInstance = CKeyProducerJavaRandomInstance.SECURE_RANDOM;

    /**
     * Used for {@link CKeyProducerJavaRandomInstance#RANDOM_CUSTOM_SEED}.
     * Also optionally used by {@link CKeyProducerJavaRandomInstance#SHA1_PRNG}
     * if deterministic output is desired.
     * Nullable to simulate unset seed.
     */
    public @Nullable Long customSeed;
}
