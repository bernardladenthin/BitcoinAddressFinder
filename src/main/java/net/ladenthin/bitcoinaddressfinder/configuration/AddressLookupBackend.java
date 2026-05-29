// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Selects which {@code AddressPresence} chain the consumer should use in front of the
 * LMDB read store.
 *
 * <p>The choices trade off RAM cost, lookup latency, and whether the LMDB env can be
 * closed after the in-memory snapshot is built:
 *
 * <ul>
 *   <li>{@link #LMDB_ONLY} - no accelerator. Every {@code containsAddress} call hits
 *       LMDB. Lowest RAM, highest per-lookup cost.</li>
 *   <li>{@link #BLOOM} - Bloom filter in front of LMDB. Fast "address is definitely
 *       absent" path; positives fall through to LMDB to disambiguate the false-positive
 *       case. LMDB must stay open.</li>
 *   <li>{@link #HASHSET} - {@code HashSet<ByteBuffer>} replacement populated from LMDB
 *       once at startup. Exact lookup. LMDB env is closed after population - the on-disk
 *       store is no longer needed. ~80 B/entry; suitable for the light database
 *       (~10 GB RAM at 132 M entries) on machines with enough RAM.</li>
 *   <li>{@link #SORTED_ARRAY} - 256-bucket sorted flat {@code byte[]} snapshot,
 *       populated from LMDB once at startup. Exact lookup. LMDB env is closed after
 *       population. ~20 B/entry; about 4&#xD7; more compact than HASHSET, suitable for
 *       larger datasets up to ~27 billion entries.</li>
 * </ul>
 */
public enum AddressLookupBackend {
    /** LMDB only - no accelerator. */
    LMDB_ONLY,
    /** Bloom filter in front of LMDB; LMDB must stay open. */
    BLOOM,
    /** HashSet snapshot in RAM; LMDB closed and GC'd after population. */
    HASHSET,
    /** Sorted-array snapshot in RAM (256 buckets by first byte); LMDB closed and GC'd after population. */
    SORTED_ARRAY
}
