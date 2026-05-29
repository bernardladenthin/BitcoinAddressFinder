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
 * value lookups to one. Presence-only structures (HashSet/SortedArray snapshots that
 * carry no value information) implement only {@link AddressPresence}.
 *
 * <p>The scan/consumer hot path uses only {@link AddressPresence#containsAddress};
 * {@link #getAmount} is consumed by export commands and the diagnostic hit-logging
 * path.
 *
 * <p>A typical chain looks like:
 *
 * <pre>{@code
 * AddressLookup lookup = lmdbPersistence;                            // base
 * lookup = new BloomFilterAccelerator(bloomFilter, lookup);          // optional pre-filter
 * consumerJava.useLookup(lookup);
 * }</pre>
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
