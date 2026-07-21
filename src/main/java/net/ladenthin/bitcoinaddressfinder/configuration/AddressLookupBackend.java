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
 *       {@code long} binary search uses the JDK intrinsic and is among the fastest
 *       lookups on a <em>small</em> database (~10&#x00d7; more compact than HASHSET at
 *       near-HASHSET latency). Latency degrades with size faster than any other backend,
 *       however: the search costs ~log&#x2082;(n/256) dependent cache misses, measured at
 *       79 / 145 / 363 ns for 100&nbsp;K / 1&nbsp;M / 10&nbsp;M entries, by which point it is
 *       the slowest backend measured. See {@code FilterLookupBenchmark}.</li>
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
 *   <li>{@link #BLOCKED_BLOOM} - Blocked Bloom filter in front of LMDB. Sized exactly via fastrange
 *       (no power-of-two rounding) to the configured density; the default 11 bits/entry
 *       ({@code k = 6}) gives ~1.375&nbsp;B/entry (~182&nbsp;MiB Light DB, ~1.89&nbsp;GiB Full DB) at
 *       ~0.76&nbsp;% FPR. No false negatives. Same decorator behaviour as {@link #BINARY_FUSE_8}; LMDB
 *       stays open. Builds in a single streaming pass (peak build memory ≈ the filter itself), roughly
 *       2× faster than the fuse construction's multi-pass peeling — the pick for rebuild-heavy or
 *       heap-constrained builds; on <b>total</b> cost {@link #BINARY_FUSE_8} is the recommended
 *       full-tier backend (smaller, lower FPR).</li>
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
     * Blocked Bloom filter in front of LMDB. Sized exactly via <i>fastrange</i> (no power-of-two
     * rounding) to the configured density: the shipped default of 11 bits/entry with {@code k = 6}
     * gives ~1.375 B/entry (~182 MiB at the 132 M-entry Light DB, ~1.89 GiB at the 1.377 B-entry Full
     * DB) at a false-positive rate of ~0.76 %. Configurable via {@code blockedBloomBitsPerEntry} /
     * {@code blockedBloomK}. No false negatives. Decorator like {@link #BINARY_FUSE_8}; LMDB stays open.
     *
     * <p>All {@code k} probes share one cache-line-aligned 512-bit block, so a cache-cold lookup costs
     * one cache miss against a fuse lookup's three. Its distinguishing advantage is <b>build cost</b>:
     * it allocates the bit array once and streams every address through it in a single pass — no
     * peeling, no auxiliary arrays — so peak build memory is ≈ the filter itself (~2 GB at the Full DB
     * tier) and it builds roughly 2× faster than the fuse construction's multi-pass peeling (~29 B/entry,
     * ~42 GB transient at the Full DB). That makes it the pick for <b>rebuild-heavy or heap-constrained</b>
     * builds. On <b>total</b> cost, however, {@link #BINARY_FUSE_8} is the recommended full-tier backend:
     * it is ~27 % smaller with a lower FPR, and the one-time build cost amortises over the database's
     * life. (An earlier claim that Fuse-8 could not build at the Full DB tier was refuted.)
     */
    BLOCKED_BLOOM
}
