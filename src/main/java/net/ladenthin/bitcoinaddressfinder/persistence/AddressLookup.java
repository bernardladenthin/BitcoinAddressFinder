// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.nio.ByteBuffer;
import org.bitcoinj.base.Coin;

/**
 * Read-only address lookup contract. Extracted from {@link Persistence} so that small
 * accelerators (Bloom filters, in-memory caches, sorted-array prefixes) can be composed
 * in front of any full {@link Persistence} backend without having to implement the full
 * write/lifecycle/stats surface.
 *
 * <p>A typical chain looks like:
 *
 * <pre>{@code
 * AddressLookup lookup = lmdbPersistence;                            // base
 * lookup = new BloomFilterAccelerator(bloomFilter, lookup);          // optional pre-filter
 * lookup = new HashMapAccelerator(hotSet, lookup);                   // optional hot cache
 * consumerJava.useLookup(lookup);
 * }</pre>
 *
 * <p>Accelerator implementations should call through to their delegate when their own
 * structure cannot give a definitive answer (e.g. a Bloom filter answering
 * {@code mightContain == true} must consult the delegate to disambiguate the false
 * positive).
 */
public interface AddressLookup {

    /**
     * Checks whether the given address is present.
     *
     * @param hash160 the address hash to look up
     * @return {@code true} if the address is present
     */
    boolean containsAddress(ByteBuffer hash160);

    /**
     * Returns the stored amount for the given address.
     *
     * @param hash160 the address hash to look up
     * @return the stored amount for the address (or {@link Coin#ZERO} if absent)
     */
    Coin getAmount(ByteBuffer hash160);
}
