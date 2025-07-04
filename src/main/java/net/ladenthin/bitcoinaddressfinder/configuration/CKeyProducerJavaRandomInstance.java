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

/**
 * Enumeration of PRNG (Pseudo-Random Number Generator) strategies used for generating keys.
 * 
 * From a security perspective, some options are considered cryptographically secure and suitable 
 * for wallet generation, while others are insecure and may have been used in weak or vulnerable wallets.
 * 
 * This enum is useful both for:
 * - Secure wallet generation (using strong entropy sources)
 * - Wallet cracking or forensic analysis (by replicating weak/random key generation)
 */
public enum CKeyProducerJavaRandomInstance {

    /**
     * Cryptographically secure PRNG provided by the OS.
     * 
     * ✅ Strong entropy source, suitable for generating secure wallets.
     * ❌ Not suitable for brute-force reproduction (unpredictable output).
     * 
     * Internally uses system sources like `/dev/urandom` or platform CSPRNG.
     */
    SECURE_RANDOM,

    /**
     * Standard Java Random seeded with the current system time in milliseconds.
     * 
     * ❌ Extremely insecure. Many compromised wallets were generated this way.
     * ❗ Easily brute-forced by scanning a narrow time window.
     * 
     * Common mistake in early Bitcoin wallet implementations.
     */
    RANDOM_CURRENT_TIME_MILLIS_SEED,

    /**
     * Standard Java Random seeded with a user-supplied long value.
     * 
     * ❌ Insecure if the seed is low-entropy or guessable.
     * ❗ Useful for forensic key regeneration if the seed is known or derived.
     */
    RANDOM_CUSTOM_SEED,

    /**
     * SHA1PRNG – an old deterministic PRNG available in some Java implementations.
     * 
     * ⚠️ Not cryptographically strong by modern standards.
     * 🛠 Historically used on Android (notably flawed pre-2013).
     * ❗ Potential attack surface if used in old or custom wallets.
     */
    SHA1_PRNG,

    /**
     * Deterministic key generation from a BIP39 mnemonic seed (e.g. 12 or 24 words).
     * 
     * ✅ Industry standard for HD wallets.
     * 🔁 Fully deterministic and recoverable.
     * ❌ Security depends entirely on the mnemonic entropy.
     * 
     * Not vulnerable if the mnemonic is generated securely (uses 128–256 bits of entropy).
     */
    BIP39_SEED
}
