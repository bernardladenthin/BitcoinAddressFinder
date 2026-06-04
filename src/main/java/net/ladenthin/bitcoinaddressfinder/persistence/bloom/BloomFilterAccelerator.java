// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.bloom;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.ByteBuffer;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
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

    private final @NonNull BloomFilter<byte[]> bloom;

    private final @NonNull AddressLookup delegate;

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

    /**
     * Bloom is a probabilistic decorator: a {@code mightContain == true} answer may be a
     * false positive that must be disambiguated against the delegate. The backing
     * storage must therefore remain open for the lifetime of this accelerator.
     *
     * @return {@code true} - bloom always requires its delegate to be available
     */
    @Override
    public boolean requiresBackend() {
        return true;
    }

    /**
     * Builds an accelerator by streaming every address from {@code source} into a fresh
     * Bloom filter, then wrapping {@code delegate} for the disambiguation path.
     *
     * <p>The source is iterated exactly once (one stream open/close cycle); the source
     * reference is dropped before this method returns so it has no impact on whether
     * the backing storage can be released.
     *
     * @param source                   the address set to materialise into the filter
     * @param delegate                 the lookup used to disambiguate Bloom positives
     * @param falsePositiveProbability the Bloom-filter FPP target (e.g. {@code 0.01})
     * @return a populated accelerator wrapping {@code delegate}
     */
    public static BloomFilterAccelerator populateFrom(
            @NonNull AddressIterable source, @NonNull AddressLookup delegate, double falsePositiveProbability) {
        long count = source.count();
        BloomFilter<byte[]> bloom =
                BloomFilter.create(Funnels.byteArrayFunnel(), Math.max(count, 1L), falsePositiveProbability);
        try (Stream<ByteBuffer> stream = source.addresses()) {
            stream.forEach(bb -> {
                byte[] bytes = new byte[bb.remaining()];
                bb.duplicate().get(bytes);
                bloom.put(bytes);
            });
        }
        return new BloomFilterAccelerator(bloom, delegate);
    }
}
