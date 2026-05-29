// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import org.jspecify.annotations.NonNull;

/**
 * Self-contained presence-only snapshot backed by 256 sorted flat {@code byte[]} arrays
 * bucketed by the first byte of each hash160.
 *
 * <h2>Why 256 buckets</h2>
 * A single Java {@code byte[]} cannot exceed {@link Integer#MAX_VALUE} bytes
 * (~2.1 GB). At {@value #BYTES_PER_ADDRESS} bytes per address that limits one
 * array to ~107 million entries. The largest published address-database tier in
 * this project's README ("Full database") holds ~1.377 billion entries - too
 * large for a single array. Bucketing by the first byte distributes addresses
 * across {@value #BUCKET_COUNT} arrays (average ~5.4 M entries each at full
 * scale) which keeps every bucket well below the array-size limit and gives up
 * to ~27.5 billion total capacity.
 *
 * <h2>Memory cost</h2>
 * Exactly {@value #BYTES_PER_ADDRESS} bytes per address plus the 256 array
 * headers - no per-entry object overhead. Roughly 4x more compact than
 * {@link HashSetAddressPresence} ({@code ~20 B/entry} vs {@code ~80 B/entry}).
 *
 * <h2>Lookup</h2>
 * O(1) bucket pick by first byte, then binary search inside the bucket
 * (O(log N / 256)). Lookups allocate one 20-byte temporary {@code byte[]} per
 * call to extract the key from the input {@link ByteBuffer}; this is the cost
 * of accepting the {@link AddressPresence} API contract.
 *
 * <h2>Lifecycle</h2>
 * Once populated this class holds no reference to its source.
 * {@link #requiresBackend()} returns {@code false}; the backing storage can be
 * closed and garbage collected after population.
 *
 * <h2>Concurrency</h2>
 * Thread-safe for concurrent reads after construction. No mutation API is
 * exposed.
 */
public final class SortedArrayAddressPresence implements AddressPresence {

    /** Fixed length of a hash160 entry in bytes. */
    static final int BYTES_PER_ADDRESS = 20;

    /** One bucket per first-byte value. */
    static final int BUCKET_COUNT = 256;

    /** Flat byte arrays, one per first-byte bucket. Each is sorted lexicographically. */
    private final byte[][] buckets;

    private SortedArrayAddressPresence(byte[][] buckets) {
        this.buckets = buckets;
    }

    /**
     * Builds a sorted-array presence snapshot from {@code source}. Iterates the source
     * twice: first to count addresses per bucket, then to fill the per-bucket arrays.
     * Each bucket is sorted lexicographically as the final step.
     *
     * @param source the address set to materialise
     * @return a fully populated, self-contained presence lookup
     */
    public static SortedArrayAddressPresence populateFrom(@NonNull AddressIterable source) {
        long[] counts = new long[BUCKET_COUNT];

        // First pass: count entries per bucket
        try (Stream<ByteBuffer> stream = source.addresses()) {
            stream.forEach(bb -> {
                if (bb.remaining() != BYTES_PER_ADDRESS) {
                    return;
                }
                int firstByte = bb.get(bb.position()) & 0xFF;
                counts[firstByte]++;
            });
        }

        // Allocate exact-size byte[] per bucket and verify the array-size limit
        byte[][] buckets = new byte[BUCKET_COUNT][];
        int[] writeOffsets = new int[BUCKET_COUNT];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            long byteCount = counts[i] * (long) BYTES_PER_ADDRESS;
            if (byteCount > Integer.MAX_VALUE - 8L) {
                throw new IllegalStateException("Bucket " + i + " would exceed Integer.MAX_VALUE bytes (" + byteCount
                        + "); a finer bucket prefix is required for this dataset");
            }
            buckets[i] = new byte[(int) byteCount];
        }

        // Second pass: fill (preserve buffer position by working on a duplicate)
        try (Stream<ByteBuffer> stream = source.addresses()) {
            stream.forEach(bb -> {
                if (bb.remaining() != BYTES_PER_ADDRESS) {
                    return;
                }
                int firstByte = bb.get(bb.position()) & 0xFF;
                int offset = writeOffsets[firstByte];
                bb.duplicate().get(buckets[firstByte], offset, BYTES_PER_ADDRESS);
                writeOffsets[firstByte] = offset + BYTES_PER_ADDRESS;
            });
        }

        // Sort each bucket lexicographically
        for (int i = 0; i < BUCKET_COUNT; i++) {
            sortBucket(buckets[i]);
        }

        return new SortedArrayAddressPresence(buckets);
    }

    /**
     * Sorts a flat {@code byte[]} of {@value #BYTES_PER_ADDRESS}-byte chunks in
     * lexicographic order using a temporary {@code byte[][]} for {@link Arrays#sort}.
     *
     * <p>Peak extra memory: ~24 + {@value #BYTES_PER_ADDRESS} bytes per chunk during
     * sort (object header + array header + payload), dropped immediately after.
     */
    private static void sortBucket(byte[] data) {
        int chunks = data.length / BYTES_PER_ADDRESS;
        if (chunks < 2) {
            return;
        }
        byte[][] view = new byte[chunks][];
        for (int i = 0; i < chunks; i++) {
            byte[] chunk = new byte[BYTES_PER_ADDRESS];
            System.arraycopy(data, i * BYTES_PER_ADDRESS, chunk, 0, BYTES_PER_ADDRESS);
            view[i] = chunk;
        }
        Arrays.sort(view, Arrays::compareUnsigned);
        for (int i = 0; i < chunks; i++) {
            System.arraycopy(view[i], 0, data, i * BYTES_PER_ADDRESS, BYTES_PER_ADDRESS);
        }
    }

    @Override
    public boolean containsAddress(ByteBuffer hash160) {
        if (hash160.remaining() != BYTES_PER_ADDRESS) {
            return false;
        }
        byte[] key = new byte[BYTES_PER_ADDRESS];
        hash160.duplicate().get(key);
        int bucket = key[0] & 0xFF;
        byte[] data = buckets[bucket];
        if (data == null || data.length == 0) {
            return false;
        }
        return binarySearchBucket(data, key) >= 0;
    }

    @Override
    public boolean requiresBackend() {
        return false;
    }

    /**
     * Returns the total number of addresses across all buckets.
     *
     * @return total entry count
     */
    public long size() {
        long total = 0L;
        for (byte[] b : buckets) {
            total += b.length / BYTES_PER_ADDRESS;
        }
        return total;
    }

    /**
     * Binary search for {@code key} within a sorted flat {@code byte[]} of
     * {@value #BYTES_PER_ADDRESS}-byte chunks.
     *
     * @return non-negative chunk index if found, {@code -(insertion-point + 1)} otherwise
     */
    static int binarySearchBucket(byte[] data, byte[] key) {
        int lo = 0;
        int hi = data.length / BYTES_PER_ADDRESS - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = compareChunk(data, mid * BYTES_PER_ADDRESS, key);
            if (cmp < 0) {
                lo = mid + 1;
            } else if (cmp > 0) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -(lo + 1);
    }

    /**
     * Unsigned lexicographic comparison of the {@value #BYTES_PER_ADDRESS} bytes starting
     * at {@code data[offset..]} against {@code key}.
     */
    private static int compareChunk(byte[] data, int offset, byte[] key) {
        for (int i = 0; i < BYTES_PER_ADDRESS; i++) {
            int a = data[offset + i] & 0xFF;
            int b = key[i] & 0xFF;
            if (a != b) {
                return a - b;
            }
        }
        return 0;
    }
}
