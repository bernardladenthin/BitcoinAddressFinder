// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.Persistence;

/**
 * Test-only carrier returned by {@code LMDBBase.createAndFillAndOpenLMDB(...)}.
 *
 * <ul>
 *   <li>{@link #persistence()} - write / lifecycle / stats path (the LMDB instance itself).</li>
 *   <li>{@link #lookup()} - read path; equal to {@code persistence()} when {@code LMDB_ONLY},
 *       a {@code BloomFilterAccelerator} wrapping it when {@code BLOOM}, or a self-contained
 *       in-memory snapshot when {@code HASHSET} / {@code SORTED_ARRAY}.</li>
 * </ul>
 */
public record LMDBHandle(Persistence persistence, AddressPresence lookup) implements AutoCloseable {
    @Override
    public void close() throws Exception {
        persistence.close();
    }
}
