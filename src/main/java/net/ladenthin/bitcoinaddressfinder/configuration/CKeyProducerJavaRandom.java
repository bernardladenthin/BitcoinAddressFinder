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
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.time.Instant;

public class CKeyProducerJavaRandom extends CKeyProducerJava {
    
    /** Default BIP32 path used for external addresses (BIP44). */
    public static final String DEFAULT_BIP32_PATH = "M/44H/0H/0H/0";

    /**
     * Defines which PRNG implementation to use for key generation.
     * See {@link CKeyProducerJavaRandomInstance} for available options.
     */
    public CKeyProducerJavaRandomInstance keyProducerJavaRandomInstance;

    /**
     * Used for {@link CKeyProducerJavaRandomInstance#RANDOM_CUSTOM_SEED}.
     * Also optionally used by {@link CKeyProducerJavaRandomInstance#SHA1_PRNG}
     * if deterministic output is desired.
     */
    public Long customSeed;

    /**
     * Used only with {@link CKeyProducerJavaRandomInstance#BIP39_SEED}.
     * Must be a valid BIP39 mnemonic phrase (typically 12 or 24 words).
     */
    public String mnemonic;

    /**
     * Optional passphrase used in combination with the BIP39 mnemonic.
     * Used only with {@link CKeyProducerJavaRandomInstance#BIP39_SEED}.
     * Can be an empty string.
     */
    public String passphrase;

    /**
     * Optional base path for BIP32/BIP44 derivation, e.g., "M/44H/0H/0H/0".
     * Used only with {@link CKeyProducerJavaRandomInstance#BIP39_SEED}.
     */
    public String bip32Path = DEFAULT_BIP32_PATH;
    
    /**
     * Optional wallet creation time (in epoch seconds) used for BIP39 seed.
     * If not set, defaults to {@link java.time.Instant#ofEpochSecond(long)} Instant.ofEpochSecond(0).
     * Used only with {@link CKeyProducerJavaRandomInstance#BIP39_SEED}.
     */
    public Long creationTimeSeconds;

    /**
     * Returns the creation time as an Instant.
     * Defaults to Instant.ofEpochSecond(0) if not set.
     */
    public Instant getCreationTimeInstant() {
        return Instant.ofEpochSecond(this.creationTimeSeconds != null ? this.creationTimeSeconds : 0L);
    }

}
