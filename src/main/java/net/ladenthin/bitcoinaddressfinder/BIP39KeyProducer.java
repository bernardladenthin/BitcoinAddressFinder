// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder;

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
    private final AtomicInteger counter = new AtomicInteger(0);

    public BIP39KeyProducer(String mnemonic, String passphrase, String bip44BasePath, Instant creationTime) {
        DeterministicSeed seed = DeterministicSeed.ofMnemonic(mnemonic, passphrase, creationTime);
        this.keyChain = DeterministicKeyChain.builder().seed(seed).build();
        this.basePath = HDPath.parsePath(bip44BasePath); // e.g., "M/44H/0H/0H/0"
    }

    /**
     * Returns the next derived key along the BIP44 path.
     */
    public DeterministicKey nextKey() {
        int index = counter.getAndIncrement();
        List<ChildNumber> path = append(basePath, new ChildNumber(index, false));
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
