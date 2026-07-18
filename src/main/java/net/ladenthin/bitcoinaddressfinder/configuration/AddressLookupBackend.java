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
 *   <li>{@link #TRUNCATED_LONG_64} - 256-bucket sorted {@code long[]} snapshot keeping
 *       only the first 8 bytes after the bucket byte (72-bit effective resolution).
 *       Probabilistic with negligible false-positive rate (N/2&#x2074;&#x2074;), the
 *       enclosing chain must fall through to the LMDB delegate to disambiguate a
 *       collision but in practice that is expected ~10&#x207B;&#x00b9;&#x00b9; per
 *       query at the project's largest published database size. ~8 B/entry, primitive
 *       {@code long} binary search uses the JDK intrinsic and is typically the fastest
 *       lookup of any backend (~10&#x00d7; more compact than HASHSET at near-HASHSET
 *       latency).</li>
 *   <li>{@link #BINARY_FUSE_8} - Binary Fuse Filter (8-bit fingerprints) in front of LMDB.
 *       ~1.14 B/entry, FPR &#x2248; 0.4&nbsp;%. No false negatives. Like {@link #BLOOM} it is a
 *       decorator: a filter miss is definitive, a filter hit falls through to LMDB to reject
 *       false positives, so the LMDB env must stay open. Recommended for most workloads — the
 *       filter fits Full DB in ~1.8 GB on any modern workstation and also feeds the optional
 *       GPU pre-filter.</li>
 *   <li>{@link #BINARY_FUSE_16} - Binary Fuse Filter (16-bit fingerprints) in front of LMDB.
 *       ~2.28 B/entry, FPR &#x2248; 0.0015&nbsp;%. No false negatives. Same decorator behaviour
 *       as {@link #BINARY_FUSE_8}; LMDB stays open. Use when the 0.4&nbsp;% false-positive rate
 *       of {@link #BINARY_FUSE_8} sends too many hits to LMDB for verification.</li>
 *   <li>{@link #BLOCKED_BLOOM} - Blocked Bloom filter in front of LMDB. Measured 1.56&nbsp;B/entry
 *       (2.0&nbsp;GiB) on the Full DB and 2.06&nbsp;B/entry (256&nbsp;MiB) on the Light DB, with a
 *       measured FPR of 0.49&nbsp;% and 0.18&nbsp;% respectively. No false negatives.
 *       Same decorator behaviour as {@link #BINARY_FUSE_8}; LMDB stays open. Unlike the fuse filters
 *       it builds in a single streaming pass with peak build memory ≈ the filter itself (~2&nbsp;GB
 *       instead of the fuse construction's ~42&nbsp;GB), so it is the backend that builds on the
 *       full billion-entry tier on a commodity-RAM machine, and its cache-line-aligned blocks feed
 *       the GPU pre-filter with a single coalesced read per lookup.</li>
 * </ul>
 */
public enum AddressLookupBackend {
    /** LMDB only - no accelerator. */
    LMDB_ONLY,
    /** Bloom filter in front of LMDB; LMDB must stay open. */
    BLOOM,
    /** HashSet snapshot in RAM; LMDB closed and GC'd after population. */
    HASHSET,
    /**
     * Truncated 64-bit snapshot in RAM (256 buckets by first byte; each entry stored
     * as the next 8 bytes of the hash160 as a primitive {@code long}). Probabilistic
     * with negligible FPR; LMDB closed and GC'd after population.
     */
    TRUNCATED_LONG_64,

    /**
     * Binary Fuse Filter (8-bit fingerprints) in front of LMDB. ~1.14 B/entry, FPR &#x2248; 0.4 %.
     * No false negatives. Decorator like BLOOM: filter hits are verified against LMDB to reject
     * false positives, so LMDB must stay open. Recommended for most workloads and feeds the GPU
     * pre-filter.
     */
    BINARY_FUSE_8,

    /**
     * Binary Fuse Filter (16-bit fingerprints) in front of LMDB. ~2.28 B/entry, FPR &#x2248; 0.0015 %.
     * No false negatives. Decorator like BINARY_FUSE_8; LMDB stays open. Use when the 0.4 %
     * false-positive rate of BINARY_FUSE_8 sends too many hits to LMDB for verification.
     */
    BINARY_FUSE_16,

    /**
     * Blocked Bloom filter in front of LMDB. Auto-sized in power-of-two block counts: 2.0 GiB
     * (1.56 B/entry, measured FPR 0.49 %) at the 1.377 B-entry Full DB tier, 256 MiB (2.06 B/entry,
     * measured FPR 0.18 %) at the 132 M-entry Light DB tier. No false negatives. Decorator like BINARY_FUSE_8; LMDB stays open. Builds in a single streaming
     * pass (peak build memory ≈ the filter itself), so it is the backend that scales to the full
     * billion-entry database on commodity RAM where the fuse construction's ~42 GB peak does not fit.
     */
    BLOCKED_BLOOM
}
