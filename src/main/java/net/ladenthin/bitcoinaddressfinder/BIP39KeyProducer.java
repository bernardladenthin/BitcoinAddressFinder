// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import net.ladenthin.bitcoinaddressfinder.keyproducer.NoMoreSecretsAvailableException;
import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import java.util.ArrayList;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDPath;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic key producer based on a BIP39 mnemonic + BIP44 path.
 * Allows sequential derivation of keys like Random.next() from a fixed HD wallet seed.
 */
public class BIP39KeyProducer extends java.util.Random {

    private final DeterministicKeyChain keyChain;
    private final List<ChildNumber> basePath;
    private final boolean hardened;
    @VisibleForTesting
    final AtomicInteger counter = new AtomicInteger(0);

    public BIP39KeyProducer(String mnemonic, String passphrase, String bip44BasePath, Instant creationTime, boolean hardened) {
        DeterministicSeed seed = DeterministicSeed.ofMnemonic(mnemonic, passphrase, creationTime);
        this.keyChain = DeterministicKeyChain.builder().seed(seed).build();
        this.basePath = HDPath.parsePath(bip44BasePath); // e.g., "M/44H/0H/0H/0"
        this.hardened = hardened;
    }
    
    /**
     * Returns the next derived key along the BIP44 path.
     */
    public DeterministicKey nextKey() throws NoMoreSecretsAvailableException {
        int index = counter.getAndIncrement();
        if (index < 0) {
            throw new NoMoreSecretsAvailableException("Child index overflow: counter exceeded Integer.MAX_VALUE");
        }
        List<ChildNumber> path = append(basePath, new ChildNumber(index, hardened));
        return keyChain.getKeyByPath(path, true);
    }
    
    public static List<ChildNumber> append(List<ChildNumber> base, ChildNumber child) {
        List<ChildNumber> extended = new ArrayList<>(base);
        extended.add(child);
        return extended;
    }

    /**
     * Overrides nextBytes to produce deterministic bytes from key material.
     */
    @Override
    public void nextBytes(byte[] bytes) {
        DeterministicKey key = nextKey();
        byte[] priv = key.getPrivKeyBytes();
        System.arraycopy(priv, 0, bytes, 0, Math.min(bytes.length, priv.length));
    }
}
