// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.ladenthin.bitcoinaddressfinder.keyproducer.NoMoreSecretsAvailableException;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDPath;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;

/**
 * Deterministic key producer based on a BIP39 mnemonic + BIP44 path.
 * Allows sequential derivation of keys like Random.next() from a fixed HD wallet seed.
 */
public class BIP39KeyProducer extends java.util.Random {

    private static final long serialVersionUID = 1L;

    /**
     * Underlying deterministic key chain seeded from the mnemonic.
     * <p>
     * Marked transient because {@link DeterministicKeyChain} is not Serializable.
     * Serialization is inherited from {@link java.util.Random} as a side effect
     * of using Random as a façade for this deterministic producer; it is not a
     * supported operation. A deserialised instance will have a {@code null}
     * keyChain and NPE on first use, which is the intended fail-fast. The
     * field cannot be declared {@code final} together with {@code transient}
     * because spotbugs NFF_NON_FUNCTIONAL_FIELD flags the combination
     * (a {@code final transient} field can never be restored on
     * deserialisation); the field is in practice immutable since it is only
     * assigned by the constructor.
     */
    private transient DeterministicKeyChain keyChain;
    /**
     * Parsed BIP44 base derivation path.
     * <p>
     * Marked transient for the same reason as {@link #keyChain}.
     */
    private transient List<ChildNumber> basePath;
    /** Whether derived child indices are hardened. */
    private final boolean hardened;
    /** Monotonically increasing child-index counter (visible for testing). */
    @VisibleForTesting
    final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Creates a new deterministic key producer.
     *
     * @param mnemonic       the BIP39 mnemonic phrase
     * @param passphrase     the optional BIP39 passphrase
     * @param bip44BasePath  the BIP44 base derivation path (e.g. {@code M/44H/0H/0H/0})
     * @param creationTime   the seed creation time
     * @param hardened       whether the derived child indices are hardened
     */
    public BIP39KeyProducer(
            String mnemonic, String passphrase, String bip44BasePath, Instant creationTime, boolean hardened) {
        DeterministicSeed seed = DeterministicSeed.ofMnemonic(mnemonic, passphrase, creationTime);
        this.keyChain = DeterministicKeyChain.builder().seed(seed).build();
        this.basePath = HDPath.parsePath(bip44BasePath); // e.g., "M/44H/0H/0H/0"
        this.hardened = hardened;
    }

    /**
     * Returns the next derived key along the BIP44 path.
     *
     * @return the next {@link DeterministicKey}
     * @throws NoMoreSecretsAvailableException if the internal child-index counter overflows
     */
    public DeterministicKey nextKey() throws NoMoreSecretsAvailableException {
        int index = counter.getAndIncrement();
        if (index < 0) {
            throw new NoMoreSecretsAvailableException("Child index overflow: counter exceeded Integer.MAX_VALUE");
        }
        List<ChildNumber> path = append(basePath, new ChildNumber(index, hardened));
        return keyChain.getKeyByPath(path, true);
    }

    /**
     * Returns a new list with {@code child} appended to {@code base}.
     *
     * @param base  the base path
     * @param child the child number to append
     * @return a new list containing all entries of {@code base} followed by {@code child}
     */
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
