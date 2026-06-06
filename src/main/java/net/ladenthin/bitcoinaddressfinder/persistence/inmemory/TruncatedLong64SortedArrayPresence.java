// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Stream;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import org.jspecify.annotations.NonNull;

/**
 * Self-contained presence-only snapshot backed by 256 sorted {@code long[]} arrays,
 * bucketed by the first byte of each hash160. Each entry is the next 64 bits of the
 * hash160 (bytes 1&#x2013;8) stored as a primitive {@code long}.
 *
 * <h2>Why truncate to 64 bits</h2>
 * hash160 is the output of SHA-256 followed by RIPEMD-160; the resulting bytes are
 * cryptographically uniform, so any subset of the bits is also uniform. The bucket
 * index encodes the first 8 bits and each stored {@code long} encodes the next 64
 * bits, giving 72 effective bits of resolution. The probability that a random query
 * collides with one of {@code N} stored entries is {@code N / 2^64}; for the
 * project's "Full database" tier of ~1.4 billion entries that is ~7.5 &#x00d7; 10
 * <sup>&#x2212;11</sup> per query, and any such collision is disambiguated by the
 * LMDB backend that this lookup sits in front of.
 *
 * <h2>Memory cost</h2>
 * Exactly 8 bytes per entry plus 256 array headers - no per-entry object overhead.
 * Roughly 10&#x00d7; more compact than {@link HashSetAddressPresence} (~80 B/entry).
 * For the Light database (~132 M entries) the in-memory footprint is ~1.1 GB.
 *
 * <h2>Lookup</h2>
 * O(1) bucket pick by first byte, then {@link Arrays#binarySearch(long[], long)} in
 * the bucket. The JDK's primitive-array binary search is heavily JIT-optimised and
 * generates branchless comparisons on modern CPUs; the stored values are cache-line
 * dense (8 {@code long}s per cache line) so the search walks very little memory.
 *
 * <h2>Lifecycle</h2>
 * Once populated this class holds no reference to its source.
 * {@link #requiresBackend()} returns {@code false}; the backing storage can be
 * closed and garbage collected after population.
 *
 * <h2>Concurrency</h2>
 * Thread-safe for concurrent reads after construction. No mutation API is exposed.
 */
@ToString
public final class TruncatedLong64SortedArrayPresence implements AddressPresence {

    /** Fixed length of a hash160 entry in bytes. */
    static final int BYTES_PER_ADDRESS = 20;

    /** Offset within hash160 at which the 8-byte truncation starts (after the bucket byte). */
    static final int LONG_OFFSET = 1;

    /** One bucket per first-byte value. */
    static final int BUCKET_COUNT = 256;

    /** Sorted primitive arrays, one per first-byte bucket. */
    // Up to 256 long[] arrays, each potentially holding millions of entries — toString would
    // be log-killing. The size() getter is included instead (see @ToString.Include below).
    @ToString.Exclude
    private final long[][] buckets;

    private TruncatedLong64SortedArrayPresence(long[][] buckets) {
        this.buckets = buckets;
    }

    /**
     * Builds a truncated-long presence snapshot from {@code source}. Iterates the source
     * twice (count, then fill) and sorts each per-bucket {@code long[]} via
     * {@link Arrays#sort(long[])}.
     *
     * @param source the address set to materialise
     * @return a fully populated, self-contained presence lookup
     */
    public static TruncatedLong64SortedArrayPresence populateFrom(@NonNull AddressIterable source) {
        long[] counts = new long[BUCKET_COUNT];

        // First pass: count entries per bucket
        try (Stream<ByteBuffer> stream = source.addresses()) {
            stream.forEach(bb -> {
                if (bb.remaining() != BYTES_PER_ADDRESS) {
                    return;
                }
                counts[bb.get(bb.position()) & 0xFF]++;
            });
        }

        long[][] buckets = new long[BUCKET_COUNT][];
        int[] writeOffsets = new int[BUCKET_COUNT];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            if (counts[i] > Integer.MAX_VALUE - 8L) {
                throw new IllegalStateException("Bucket " + i + " would exceed Integer.MAX_VALUE entries (" + counts[i]
                        + "); a finer bucket prefix is required for this dataset");
            }
            buckets[i] = new long[(int) counts[i]];
        }

        // Second pass: fill (preserve buffer position by reading absolute indices)
        try (Stream<ByteBuffer> stream = source.addresses()) {
            stream.forEach(bb -> {
                if (bb.remaining() != BYTES_PER_ADDRESS) {
                    return;
                }
                int pos = bb.position();
                int bucket = bb.get(pos) & 0xFF;
                long key = bb.getLong(pos + LONG_OFFSET);
                int offset = writeOffsets[bucket];
                buckets[bucket][offset] = key;
                writeOffsets[bucket] = offset + 1;
            });
        }

        for (long[] data : buckets) {
            Arrays.sort(data);
        }

        return new TruncatedLong64SortedArrayPresence(buckets);
    }

    @Override
    public boolean containsAddress(ByteBuffer hash160) {
        if (hash160.remaining() != BYTES_PER_ADDRESS) {
            return false;
        }
        int pos = hash160.position();
        int bucket = hash160.get(pos) & 0xFF;
        long key = hash160.getLong(pos + LONG_OFFSET);
        long[] data = buckets[bucket];
        if (data.length == 0) {
            return false;
        }
        return Arrays.binarySearch(data, key) >= 0;
    }

    @Override
    public boolean requiresBackend() {
        return false;
    }

    /**
     * Returns the total number of stored entries.
     *
     * @return total entry count across all buckets
     */
    @ToString.Include
    public long size() {
        long total = 0L;
        for (long[] b : buckets) {
            total += b.length;
        }
        return total;
    }
}
