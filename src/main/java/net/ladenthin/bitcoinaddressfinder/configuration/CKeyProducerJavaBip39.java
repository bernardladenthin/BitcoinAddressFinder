// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

public class CKeyProducerJavaBip39 extends CKeyProducerJava {

    public static final String DEFAULT_MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
    
    /** Default BIP32 path used for external addresses (BIP44). */
    public static final String DEFAULT_BIP32_PATH = "M/44H/0H/0H/0";
    
    /**
     * Must be a valid BIP39 mnemonic phrase (typically 12 or 24 words).
     */
    public String mnemonic = DEFAULT_MNEMONIC;

    /**
     * Optional passphrase used in combination with the BIP39 mnemonic.
     * Can be an empty string.
     */
    public String passphrase = "";

    /**
    * Indicates whether the key derivation uses "hardened" child keys.
    * 
    * In BIP32 hierarchical deterministic wallets, the hardened bit (0x80000000) is set in the child index
    * to mark hardened keys. Hardened derivation prevents the possibility of deriving child public keys 
    * from a parent public key alone.
    * 
    * With hardened keys:
    * - It is impossible to derive child public keys using only the parent public key.
    * - This enhances security by preventing attacks where knowledge of a parent public key and a 
    *   child private key could allow recovery of the parent private key.
    * 
    * With non-hardened keys:
    * - You can derive child public keys from a parent public key without needing any private key.
    * - However, this introduces a potential vulnerability: if a child private key and the parent public key 
    *   are both compromised, the parent private key can be exposed due to elliptic curve mathematics.
    * 
    * Setting this flag to true enables hardened key derivation; false means non-hardened.
    * 
    * See also {@link org.bitcoinj.crypto.ChildNumber#HARDENED_BIT}.
    */
   public boolean hardened = false;

    /**
     * Optional base path for BIP32/BIP44 derivation, e.g., "M/44H/0H/0H/0".
     */
    public String bip32Path = DEFAULT_BIP32_PATH;
    
    /**
     * Optional wallet creation time (in epoch seconds) used for BIP39 seed.
     * If not set, defaults to {@link java.time.Instant#ofEpochSecond(long)} Instant.ofEpochSecond(0).
     */
    public @Nullable Long creationTimeSeconds;
    
    /**
     * Returns the creation time as an Instant.
     * Defaults to Instant.ofEpochSecond(0) if not set.
     */
    public Instant getCreationTimeInstant() {
        return Instant.ofEpochSecond(this.creationTimeSeconds != null ? this.creationTimeSeconds : 0L);
    }
}
