// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.bloom;

import com.google.common.hash.BloomFilter;
import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressLookup;
import org.bitcoinj.base.Coin;
import org.jspecify.annotations.NonNull;

/**
 * Read-only accelerator that places a {@link BloomFilter} in front of an
 * arbitrary {@link AddressLookup} delegate.
 *
 * <p>The Bloom filter answers definitively for absent addresses ({@code mightContain == false}
 * means the address is definitely not in the underlying set) but only probabilistically for
 * present ones ({@code mightContain == true} may be a false positive); on the {@code true}
 * path the call falls through to the delegate to disambiguate. {@link #getAmount} always
 * delegates because a Bloom filter carries no value information.
 *
 * <p>Typical use: shorten the LMDB query path for the overwhelmingly common "address not in
 * database" case during a key-scan.
 *
 * <pre>{@code
 * BloomFilter<byte[]> bloom = ...;                         // built once, e.g. from LMDB cursor
 * AddressLookup chain = new BloomFilterAccelerator(bloom, lmdbPersistence);
 * }</pre>
 *
 * <p>The accelerator is thread-safe as long as the supplied delegate and the underlying
 * {@link BloomFilter} are thread-safe. Guava's {@link BloomFilter#mightContain} is safe for
 * concurrent reads.
 */
public final class BloomFilterAccelerator implements AddressLookup {

    @NonNull
    private final BloomFilter<byte[]> bloom;

    @NonNull
    private final AddressLookup delegate;

    /**
     * Creates a new accelerator.
     *
     * @param bloom    the pre-populated Bloom filter
     * @param delegate the underlying lookup consulted on Bloom hits and for {@link #getAmount}
     */
    public BloomFilterAccelerator(@NonNull BloomFilter<byte[]> bloom, @NonNull AddressLookup delegate) {
        this.bloom = bloom;
        this.delegate = delegate;
    }

    @Override
    public boolean containsAddress(ByteBuffer hash160) {
        byte[] bytes = new byte[hash160.remaining()];
        hash160.get(bytes);
        hash160.rewind();
        if (!bloom.mightContain(bytes)) {
            return false;
        }
        return delegate.containsAddress(hash160);
    }

    @Override
    public Coin getAmount(ByteBuffer hash160) {
        // Bloom filter has no value information; always delegate.
        return delegate.getAmount(hash160);
    }
}
