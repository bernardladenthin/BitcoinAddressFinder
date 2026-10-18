// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.nio.ByteBuffer;
import org.bitcoinj.base.Coin;

/**
 * Read contract that adds value lookup on top of {@link AddressPresence}.
 *
 * <p>Implementations are full key&#x2192;value backends (LMDB) or decorators that delegate
 * value lookups to one. Presence-only structures that carry no value information (the in-RAM
 * snapshots and filters, e.g. {@code HashSetAddressPresence}, {@code TruncatedLong64SortedArrayPresence},
 * {@code BinaryFuse8AddressPresence}) implement only {@link AddressPresence}.
 *
 * <p>The scan/consumer hot path uses only {@link AddressPresence#containsAddress};
 * {@link #getAmount} is consumed by export commands and the diagnostic hit-logging
 * path.
 *
 * <p>A typical chain looks like:
 *
 * <pre>{@code
 * AddressLookup lookup = lmdbPersistence;                                    // exact base
 * lookup = BloomFilterAccelerator.populateFrom(lmdbPersistence, lookup, fpp); // optional pre-filter
 * boolean present = lookup.containsAddress(hash160);
 * }</pre>
 *
 * <p>In a scan the consumer builds this chain itself from {@code addressLookupBackend}
 * ({@code ConsumerJava.initLMDB()}); {@code ConsumerJava.initWithLookup} takes a ready
 * {@link AddressPresence} for callers without a database, such as the tuner.
 */
public interface AddressLookup extends AddressPresence {

    /**
     * Returns the stored amount for the given address.
     *
     * @param hash160 the address hash to look up
     * @return the stored amount for the address (or {@link Coin#ZERO} if absent)
     */
    Coin getAmount(ByteBuffer hash160);
}
