// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.nio.ByteBuffer;
import java.util.stream.Stream;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import org.jspecify.annotations.NonNull;

/**
 * Self-contained presence-only snapshot backed by a Binary Fuse Filter with 8-bit
 * fingerprints implementing {@link AddressPresence}.
 *
 * <h2>Algorithm</h2>
 * Binary Fuse Filters (Graf &amp; Lemire, 2022) are a variant of XOR filters that use
 * fused segments to guarantee successful construction for any input with high probability.
 * Each key maps to three positions — one per segment — using independent MurmurHash3
 * invocations. The three stored fingerprints satisfy an XOR invariant, enabling O(1)
 * lookup. No false negatives are possible.
 *
 * <h2>Hash design</h2>
 * Three independent positions are derived per key:
 * <ul>
 *   <li>{@code h0 = reduce(hash64(key, seed),           segSize) + 0 * segSize}</li>
 *   <li>{@code h1 = reduce(hash64(key, rotl(seed, 21)), segSize) + 1 * segSize}</li>
 *   <li>{@code h2 = reduce(hash64(key, rotl(seed, 42)), segSize) + 2 * segSize}</li>
 * </ul>
 * Using rotations of the seed produces independent 64-bit hashes per segment, preventing
 * the correlated-key collisions that occur with a single-hash bit-window approach.
 * The fingerprint is {@code (byte)(primaryHash ^ (primaryHash >>> 32))} where
 * {@code primaryHash = hash64(key, seed)}.
 *
 * <h2>False-positive rate</h2>
 * With 8-bit fingerprints the theoretical FPR is approximately 1/256 &#x2248; 0.4&nbsp;%.
 * Because this filter is self-contained ({@link #requiresBackend()} returns {@code false}),
 * the LMDB env is closed after population and a false positive is <em>not</em> re-verified
 * against it — it surfaces as a reported hit. Genuine hits are astronomically rare under
 * random scanning, so an occasional spurious hit is a negligible cost.
 *
 * <h2>Memory cost</h2>
 * Approximately 1.30&nbsp;bytes per entry (one {@code byte} slot per fingerprint position,
 * with the segment allocation overhead). Peak construction memory is approximately
 * 29&nbsp;bytes per entry (working arrays during the peeling algorithm).
 *
 * <h2>No false negatives</h2>
 * Every key that was inserted during {@link #populateFrom(AddressIterable)} will always be
 * found by {@link #containsAddress(ByteBuffer)}.
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
public final class BinaryFuse8AddressPresence implements AddressPresence {

    /** Fixed length of a hash160 entry in bytes. */
    static final int BYTES_PER_ADDRESS = 20;

    /** Initial seed value used for the first construction attempt. */
    static final long INITIAL_SEED = 0xBEEF_CAFE_1234_5678L;

    /** Maximum number of seed attempts before giving up construction. */
    private static final int MAX_SEED_ATTEMPTS = 100;

    /** Number of segments in the fused layout. */
    private static final int SEGMENT_COUNT = 3;

    private final long seed;
    private final int segSize;

    // May be large — toString would be log-killing. slotCount() is included instead.
    @ToString.Exclude
    private final byte[] fingerprints;

    private BinaryFuse8AddressPresence(long seed, int segSize, byte[] fingerprints) {
        this.seed = seed;
        this.segSize = segSize;
        this.fingerprints = fingerprints;
    }

    /**
     * Builds a Binary Fuse Filter snapshot with 8-bit fingerprints from {@code source}.
     *
     * @param source the address set to materialise
     * @return a fully populated, self-contained presence lookup
     * @throws IllegalStateException if filter construction fails after {@value #MAX_SEED_ATTEMPTS} seed attempts
     */
    public static BinaryFuse8AddressPresence populateFrom(@NonNull AddressIterable source) {
        long[] keys = collectKeys(source);
        int n = keys.length;

        if (n == 0) {
            return new BinaryFuse8AddressPresence(INITIAL_SEED, 2, new byte[0]);
        }

        int segSize = Math.max(2, (int) Math.ceil(n * 1.3 / 3.0) + 3);
        int m = segSize * SEGMENT_COUNT;

        long seed = INITIAL_SEED;

        for (int attempt = 0; attempt < MAX_SEED_ATTEMPTS; attempt++) {
            byte[] table = tryBuild(keys, n, seed, segSize, m);
            if (table.length != 0) {
                return new BinaryFuse8AddressPresence(seed, segSize, table);
            }
            seed = hash64(seed, INITIAL_SEED);
        }

        throw new IllegalStateException(
                "BinaryFuse8 construction failed after " + MAX_SEED_ATTEMPTS + " seed attempts for n=" + n);
    }

    @Override
    public boolean containsAddress(ByteBuffer hash160) {
        if (hash160.remaining() != BYTES_PER_ADDRESS) {
            return false;
        }
        if (fingerprints.length == 0) {
            return false;
        }
        long key = hash160.getLong(hash160.position());
        long ph = hash64(key, seed);
        int h0 = reduce((int) hash64(key, seed), segSize);
        int h1 = reduce((int) hash64(key, rotl(seed, 21)), segSize) + segSize;
        int h2 = reduce((int) hash64(key, rotl(seed, 42)), segSize) + 2 * segSize;
        byte fp = fingerprint8(ph);
        return (byte) (fingerprints[h0] ^ fingerprints[h1] ^ fingerprints[h2]) == fp;
    }

    @Override
    public boolean requiresBackend() {
        return false;
    }

    /**
     * Returns the total number of fingerprint slots in the filter array.
     *
     * @return the number of slots (approximately 1.30 &#x00d7; the inserted key count)
     */
    @ToString.Include
    public int slotCount() {
        return fingerprints.length;
    }

    /**
     * Returns an immutable payload describing this filter for GPU VRAM upload.
     * <p>
     * Public bridge accessor: the engine layer reads this single object (instead of the
     * package-private getters, which are not visible across packages) and decomposes it into
     * the primitive arguments accepted by the OpenCL upload path. This keeps the OpenCL layer
     * free of any dependency on this persistence type.
     *
     * @return the GPU-upload payload (fingerprints reference plus seed and segment metadata)
     */
    public BinaryFuse8GpuFilterData toGpuFilterData() {
        return new BinaryFuse8GpuFilterData(
                getFingerprints(), getSeed(), getSegmentLength(), getSegmentLengthMask(), getSegmentCountLength());
    }

    /**
     * Returns the fingerprint slot array, exposed for GPU VRAM upload and tests.
     * <p>
     * The reference (not a defensive copy) is returned deliberately: the array can be large
     * and is treated as read-only by every caller (the GPU upload path copies it into device
     * memory; tests only read it). Callers must not mutate it.
     *
     * @return the fingerprint byte array; its length equals {@link #slotCount()}
     */
    @SuppressWarnings("EI_EXPOSE_REP")
    byte[] getFingerprints() {
        return fingerprints;
    }

    /**
     * Returns the construction seed of the first successful build.
     *
     * @return the seed value used by {@link #containsAddress(ByteBuffer)} for hashing
     */
    long getSeed() {
        return seed;
    }

    /**
     * Returns the per-segment length used by the {@code reduce} position mapping.
     *
     * @return the segment length
     */
    int getSegmentLength() {
        return segSize;
    }

    /**
     * Returns {@link #getSegmentLength()} minus one.
     * <p>
     * Provided as GPU-upload metadata to mirror the standard Binary Fuse filter layout; the
     * {@code reduce}-based position mapping used here does not consume the mask directly.
     *
     * @return {@code getSegmentLength() - 1}
     */
    int getSegmentLengthMask() {
        return segSize - 1;
    }

    /**
     * Returns the total number of fingerprint slots (the three segments combined).
     * <p>
     * Equals {@link #getFingerprints()}{@code .length}, so the value matches the size of the
     * buffer uploaded to GPU VRAM exactly.
     *
     * @return the total slot count across all segments
     */
    int getSegmentCountLength() {
        return fingerprints.length;
    }

    /**
     * MurmurHash3 64-bit finaliser seeded by XOR with {@code seed}.
     *
     * @param key  the 64-bit key extracted from the hash160
     * @param seed the per-segment seed (main seed or a rotation of it)
     * @return the 64-bit hash value
     */
    static long hash64(long key, long seed) {
        long h = key ^ seed;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    /**
     * 8-bit fingerprint extracted from a 64-bit hash value by XOR-folding the two 32-bit halves.
     *
     * @param h the primary hash value
     * @return the fingerprint byte
     */
    static byte fingerprint8(long h) {
        return (byte) (h ^ (h >>> 32));
    }

    /**
     * Maps a 32-bit hash value into the range {@code [0, m)} using a multiply-high trick
     * that avoids modulo division.
     *
     * @param h the 32-bit hash (treated as unsigned)
     * @param m the range upper bound (exclusive)
     * @return a value in {@code [0, m)}
     */
    static int reduce(int h, int m) {
        return (int) ((Integer.toUnsignedLong(h) * (long) m) >>> 32);
    }

    private static long rotl(long v, int r) {
        return (v << r) | (v >>> (64 - r));
    }

    private static long[] collectKeys(AddressIterable source) {
        long count = source.count();
        if (count > Integer.MAX_VALUE) {
            throw new IllegalStateException("Source contains more than Integer.MAX_VALUE entries: " + count);
        }
        long[] keys = new long[(int) count];
        int[] idx = {0};
        try (Stream<ByteBuffer> stream = source.addresses()) {
            stream.forEach(bb -> {
                if (idx[0] < keys.length && bb.remaining() == BYTES_PER_ADDRESS) {
                    keys[idx[0]++] = bb.getLong(bb.position());
                }
            });
        }
        return keys;
    }

    private static byte[] tryBuild(long[] keys, int n, long seed, int segSize, int m) {
        long seed1 = rotl(seed, 21);
        long seed2 = rotl(seed, 42);

        long[] primaryHashes = new long[n];
        int[] count = new int[m];
        int[] xorIdx = new int[m];

        for (int i = 0; i < n; i++) {
            long ph = hash64(keys[i], seed);
            primaryHashes[i] = ph;
            int h0 = reduce((int) ph, segSize);
            int h1 = reduce((int) hash64(keys[i], seed1), segSize) + segSize;
            int h2 = reduce((int) hash64(keys[i], seed2), segSize) + 2 * segSize;
            count[h0]++;
            count[h1]++;
            count[h2]++;
            xorIdx[h0] ^= i;
            xorIdx[h1] ^= i;
            xorIdx[h2] ^= i;
        }

        // Build initial queue of singleton positions.
        // Maximum writes: m initial + 3*n from peeling (up to 3 positions per key peeled).
        int[] queue = new int[m + 3 * n];
        int qHead = 0;
        int qTail = 0;
        for (int pos = 0; pos < m; pos++) {
            if (count[pos] == 1) {
                queue[qTail++] = pos;
            }
        }

        int[] order = new int[n];
        int[] alone = new int[n];
        int done = 0;

        // Peeling loop: peel one singleton at a time
        while (qHead < qTail) {
            int pos = queue[qHead++];
            if (count[pos] != 1) {
                continue;
            }
            int keyIdx = xorIdx[pos];
            order[done] = keyIdx;
            alone[done] = pos;
            done++;

            long key = keys[keyIdx];
            int h0 = reduce((int) hash64(key, seed), segSize);
            int h1 = reduce((int) hash64(key, seed1), segSize) + segSize;
            int h2 = reduce((int) hash64(key, seed2), segSize) + 2 * segSize;

            count[h0]--;
            xorIdx[h0] ^= keyIdx;
            if (count[h0] == 1) {
                queue[qTail++] = h0;
            }

            count[h1]--;
            xorIdx[h1] ^= keyIdx;
            if (count[h1] == 1) {
                queue[qTail++] = h1;
            }

            count[h2]--;
            xorIdx[h2] ^= keyIdx;
            if (count[h2] == 1) {
                queue[qTail++] = h2;
            }
        }

        if (done < n) {
            return new byte[0];
        }

        // Reverse assignment: fill fingerprints from last peeled to first
        byte[] table = new byte[m];
        for (int j = done - 1; j >= 0; j--) {
            long key = keys[order[j]];
            int h0 = reduce((int) hash64(key, seed), segSize);
            int h1 = reduce((int) hash64(key, seed1), segSize) + segSize;
            int h2 = reduce((int) hash64(key, seed2), segSize) + 2 * segSize;
            int pos = alone[j];
            byte fp = fingerprint8(primaryHashes[order[j]]);
            if (pos == h0) {
                table[h0] = (byte) (fp ^ table[h1] ^ table[h2]);
            } else if (pos == h1) {
                table[h1] = (byte) (fp ^ table[h0] ^ table[h2]);
            } else {
                table[h2] = (byte) (fp ^ table[h0] ^ table[h1]);
            }
        }

        return table;
    }
}
