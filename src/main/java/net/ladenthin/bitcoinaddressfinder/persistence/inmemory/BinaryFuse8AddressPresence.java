// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.FilterBuildProgress;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Self-contained presence-only snapshot backed by a Binary Fuse Filter with 8-bit
 * fingerprints implementing {@link AddressPresence}.
 *
 * <h2>Algorithm</h2>
 * This is a faithful implementation of the Binary Fuse Filter (Graf &amp; Lemire, 2022), the
 * same construction shipped by the reference <em>FastFilter</em> C library. Unlike a plain XOR
 * filter (three <em>disjoint</em> segments, ~1.23&#x00d7; space) a binary fuse uses <em>fused,
 * overlapping</em> segments: each key derives a single 64-bit hash whose high bits pick a base
 * position and whose bit-windows pick three slots in three <em>consecutive</em> segments. This
 * geometry makes construction succeed on the first attempt with very high probability at only
 * ~1.125&#x00d7; space, avoiding the repeated re-seeding that a marginally-sized XOR layout
 * suffers on large inputs.
 *
 * <h2>Hash design</h2>
 * A single mix {@code hash = murmur64(key + seed)} is computed per key. The three fingerprint
 * positions are then, for {@code index} in {@code {0, 1, 2}}:
 * <pre>{@code
 *   base = unsignedMulHigh(hash, segmentCountLength)   // in [0, segmentCountLength)
 *   h0   = base
 *   h1   = base + segmentLength      ^ ((hash >>> 18) & segmentLengthMask)
 *   h2   = base + 2 * segmentLength  ^ ( hash         & segmentLengthMask)
 * }</pre>
 * Because {@code segmentLength} is a power of two and the three indices land in three distinct
 * segment-aligned blocks, the three positions are always distinct and within
 * {@code [0, arrayLength)} where {@code arrayLength = (segmentCount + 2) * segmentLength}. The
 * fingerprint is {@code (byte)(hash ^ (hash >>> 32))}.
 *
 * <h2>Duplicate keys</h2>
 * The filter keys on the first 8 bytes of each hash160. Two distinct addresses that share those
 * bytes collapse to the same 64-bit key; a binary fuse cannot place two identical keys, so such
 * duplicates are removed before construction. This is lossless for a presence pre-filter: every
 * filter hit is verified against the exact backend (LMDB) anyway, so collapsing two 64-bit-equal
 * addresses into one slot never causes a missed balance.
 *
 * <h2>False-positive rate</h2>
 * With 8-bit fingerprints the theoretical FPR is approximately 1/256 &#x2248; 0.4&nbsp;%.
 * That rate is far too high to treat a filter hit as a final answer, so this filter is not
 * used standalone: it is wrapped by {@link BinaryFuseAccelerator}, which verifies every hit
 * against an exact delegate (the LMDB read store) and rejects false positives. Because there
 * are no false negatives, a filter <em>miss</em> is always definitive and short-circuits
 * without consulting the delegate.
 *
 * <h2>Memory cost</h2>
 * Approximately 1.13&nbsp;bytes per entry (one {@code byte} slot per fingerprint position, with
 * the segment allocation overhead). Peak construction memory is dominated by the peeling working
 * arrays.
 *
 * <h2>No false negatives</h2>
 * Every key that was inserted during {@link #populateFrom(AddressIterable)} will always be
 * found by {@link #containsAddress(ByteBuffer)}.
 *
 * <h2>Lifecycle</h2>
 * Once populated this class holds no reference to its source. As a bare filter it owns no
 * delegate, so {@link #requiresBackend()} returns {@code false}; in production it is wrapped by
 * {@link BinaryFuseAccelerator}, whose {@code requiresBackend()} returns {@code true} to keep
 * the LMDB verifier open. Unlike the exact snapshots ({@code HASHSET}, {@code TRUNCATED_LONG_64})
 * the LMDB env is therefore <em>not</em> released after population.
 *
 * <h2>Concurrency</h2>
 * Thread-safe for concurrent reads after construction. No mutation API is exposed.
 */
@ToString
public final class BinaryFuse8AddressPresence implements AddressPresence {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryFuse8AddressPresence.class);

    /** Human-readable prefix for construction progress log lines. */
    private static final String PROGRESS_NAME = "Binary Fuse8 filter";

    /** Fixed length of a hash160 entry in bytes. */
    static final int BYTES_PER_ADDRESS = 20;

    /** Fixed hypergraph arity: each key maps to three fingerprint slots. */
    private static final int ARITY = 3;

    /** Upper bound on the per-segment length, matching the reference implementation. */
    private static final int MAX_SEGMENT_LENGTH = 262144;

    /** Maximum number of seed attempts before giving up construction. */
    private static final int MAX_SEED_ATTEMPTS = 100;

    /** Initial construction seed; re-mixed on each failed attempt. */
    static final long INITIAL_SEED = 0xBEEF_CAFE_1234_5678L;

    private final long seed;
    private final int segmentLength;
    private final int segmentLengthMask;
    private final int segmentCountLength;

    // May be large — toString would be log-killing. slotCount() is included instead.
    @ToString.Exclude
    private final byte[] fingerprints;

    private BinaryFuse8AddressPresence(
            long seed, int segmentLength, int segmentLengthMask, int segmentCountLength, byte[] fingerprints) {
        this.seed = seed;
        this.segmentLength = segmentLength;
        this.segmentLengthMask = segmentLengthMask;
        this.segmentCountLength = segmentCountLength;
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
        long[] keys = deduplicate(collectKeys(source));
        int size = keys.length;

        if (size == 0) {
            LOGGER.info("{}: no addresses to index; filter is empty.", PROGRESS_NAME);
            return new BinaryFuse8AddressPresence(INITIAL_SEED, 4, 3, 0, new byte[0]);
        }

        int segmentLength = calculateSegmentLength(size);
        int segmentLengthMask = segmentLength - 1;
        double sizeFactor = calculateSizeFactor(size);
        int capacity = (int) Math.round(size * sizeFactor);
        int initSegmentCount = (capacity + segmentLength - 1) / segmentLength - (ARITY - 1);
        int arrayLength = (initSegmentCount + ARITY - 1) * segmentLength;
        int segmentCount = (arrayLength + segmentLength - 1) / segmentLength;
        segmentCount = segmentCount <= ARITY - 1 ? 1 : segmentCount - (ARITY - 1);
        arrayLength = (segmentCount + ARITY - 1) * segmentLength;
        int segmentCountLength = segmentCount * segmentLength;

        long attemptSeed = INITIAL_SEED;
        for (int attempt = 0; attempt < MAX_SEED_ATTEMPTS; attempt++) {
            byte[] table = tryBuild(
                    keys, attemptSeed, segmentLength, segmentLengthMask, segmentCountLength, arrayLength, attempt);
            if (table != null) {
                LOGGER.info("{}: ready ({} addresses, {} fingerprint slots).", PROGRESS_NAME, size, table.length);
                return new BinaryFuse8AddressPresence(
                        attemptSeed, segmentLength, segmentLengthMask, segmentCountLength, table);
            }
            LOGGER.info(
                    "{}: construction attempt {} did not converge; retrying with a new seed.",
                    PROGRESS_NAME,
                    attempt + 1);
            attemptSeed = murmur64(attemptSeed + INITIAL_SEED);
        }

        throw new IllegalStateException(
                "BinaryFuse8 construction failed after " + MAX_SEED_ATTEMPTS + " seed attempts for n=" + size);
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
        long hash = mix(key, seed);
        int h0 = hashPosition(0, hash, segmentCountLength, segmentLength, segmentLengthMask);
        int h1 = hashPosition(1, hash, segmentCountLength, segmentLength, segmentLengthMask);
        int h2 = hashPosition(2, hash, segmentCountLength, segmentLength, segmentLengthMask);
        byte fp = fingerprint8(hash);
        return (byte) (fingerprints[h0] ^ fingerprints[h1] ^ fingerprints[h2]) == fp;
    }

    @Override
    public boolean requiresBackend() {
        return false;
    }

    /**
     * Returns the total number of fingerprint slots in the filter array.
     *
     * @return the number of slots (approximately 1.13 &#x00d7; the inserted key count)
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
     * Returns the construction seed of the successful build.
     *
     * @return the seed value used by {@link #containsAddress(ByteBuffer)} for hashing
     */
    long getSeed() {
        return seed;
    }

    /**
     * Returns the per-segment length (a power of two) used by the fused position mapping.
     *
     * @return the segment length
     */
    int getSegmentLength() {
        return segmentLength;
    }

    /**
     * Returns {@code segmentLength - 1}, the mask applied to each within-segment bit-window.
     *
     * @return the segment-length mask
     */
    int getSegmentLengthMask() {
        return segmentLengthMask;
    }

    /**
     * Returns {@code segmentCount * segmentLength}, the exclusive upper bound of the base
     * position produced by the high-bits reduction. This is <em>not</em> the fingerprint array
     * length (which is {@code (segmentCount + 2) * segmentLength}).
     *
     * @return the segment-count length used by the position mapping
     */
    int getSegmentCountLength() {
        return segmentCountLength;
    }

    /**
     * MurmurHash3 64-bit finaliser.
     *
     * @param h the value to mix
     * @return the mixed 64-bit value
     */
    static long murmur64(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    /**
     * Per-key mix: {@code murmur64(key + seed)}. A single mix drives all three positions and the
     * fingerprint.
     *
     * @param key  the 64-bit key extracted from the hash160
     * @param seed the construction seed
     * @return the 64-bit mixed hash
     */
    static long mix(long key, long seed) {
        return murmur64(key + seed);
    }

    /**
     * 8-bit fingerprint extracted from a 64-bit hash value by XOR-folding the two 32-bit halves.
     *
     * @param hash the per-key mixed hash
     * @return the fingerprint byte
     */
    static byte fingerprint8(long hash) {
        return (byte) (hash ^ (hash >>> 32));
    }

    /**
     * Computes the fingerprint slot for one of the three fused positions of a key.
     *
     * @param index              the position index (0, 1 or 2)
     * @param hash               the per-key mixed hash
     * @param segmentCountLength {@code segmentCount * segmentLength}
     * @param segmentLength      the per-segment length (power of two)
     * @param segmentLengthMask  {@code segmentLength - 1}
     * @return the slot index in {@code [0, (segmentCount + 2) * segmentLength)}
     */
    static int hashPosition(int index, long hash, int segmentCountLength, int segmentLength, int segmentLengthMask) {
        long h = Math.unsignedMultiplyHigh(hash, Integer.toUnsignedLong(segmentCountLength));
        h += (long) index * segmentLength;
        // h0 is the bare base position; only h1 and h2 xor a within-segment window, drawn from
        // distinct low/mid hash bit-ranges that do not overlap the high bits used by the base.
        if (index == 1) {
            h ^= (hash >>> 18) & Integer.toUnsignedLong(segmentLengthMask);
        } else if (index == 2) {
            h ^= hash & Integer.toUnsignedLong(segmentLengthMask);
        }
        return (int) h;
    }

    /**
     * Exact capacity of the peeling queue for a build with {@code arrayLength} fingerprint slots
     * and {@code size} keys. During peeling, position counts only decrease, so every position
     * reaches {@code count == 1} at most once and is therefore enqueued at most once — the total
     * number of enqueues can never exceed the number of positions ({@code arrayLength}), regardless
     * of {@code size}. Sizing the queue as {@code arrayLength} is therefore both sufficient and free
     * of the {@code int} overflow that the earlier {@code arrayLength + 3 * size} sizing incurred
     * above ~716&nbsp;M keys (where {@code 3 * size} wraps past {@link Integer#MAX_VALUE}).
     *
     * @param arrayLength the number of fingerprint slots (positions); the exact capacity
     * @param size        the number of keys being inserted; does not affect the capacity
     * @return {@code arrayLength} — always {@code >= arrayLength} and never overflowed
     */
    static int peelingQueueLength(int arrayLength, int size) {
        // Intentionally independent of size: max enqueues == arrayLength (each position at most once).
        return arrayLength;
    }

    /** Reference segment-length heuristic for arity 3, capped at {@link #MAX_SEGMENT_LENGTH}. */
    static int calculateSegmentLength(int size) {
        if (size == 0) {
            return 4;
        }
        int length = 1 << (int) Math.floor(Math.log(size) / Math.log(3.33) + 2.25);
        return Math.min(length, MAX_SEGMENT_LENGTH);
    }

    /** Reference size-factor heuristic for arity 3. */
    static double calculateSizeFactor(int size) {
        if (size <= 1) {
            return 0.0;
        }
        return Math.max(1.125, 0.875 + 0.25 * Math.log(1_000_000.0) / Math.log(size));
    }

    private static long[] collectKeys(AddressIterable source) {
        long count = source.count();
        if (count > Integer.MAX_VALUE) {
            throw new IllegalStateException("Source contains more than Integer.MAX_VALUE entries: " + count);
        }
        LOGGER.info("{}: reading {} addresses from the source ...", PROGRESS_NAME, count);
        long[] keys = new long[(int) count];
        int[] idx = {0};
        FilterBuildProgress progress =
                new FilterBuildProgress(LOGGER::info, PROGRESS_NAME + ": reading addresses", count);
        source.forEachAddress(bb -> {
            if (idx[0] < keys.length && bb.remaining() == BYTES_PER_ADDRESS) {
                keys[idx[0]++] = bb.getLong(bb.position());
                progress.report(idx[0]);
            }
        });
        // trim in case the source yielded fewer valid entries than count() reported
        return idx[0] == keys.length ? keys : Arrays.copyOf(keys, idx[0]);
    }

    /**
     * Removes duplicate 64-bit keys (produced when two distinct hash160 share their first 8 bytes).
     * The input array is sorted in place; a compacted copy is returned only when duplicates exist.
     */
    private static long[] deduplicate(long[] keys) {
        if (keys.length < 2) {
            return keys;
        }
        Arrays.sort(keys);
        int write = 1;
        for (int read = 1; read < keys.length; read++) {
            if (keys[read] != keys[write - 1]) {
                keys[write++] = keys[read];
            }
        }
        if (write == keys.length) {
            return keys;
        }
        LOGGER.info(
                "{}: removed {} truncation-collision duplicate key(s); {} unique keys remain.",
                PROGRESS_NAME,
                keys.length - write,
                write);
        return Arrays.copyOf(keys, write);
    }

    /**
     * One construction attempt over the fused hypergraph. Returns the filled fingerprint table, or
     * {@code null} if peeling could not place every key (the caller retries with a new seed).
     */
    private static byte @Nullable [] tryBuild(
            long[] keys,
            long seed,
            int segmentLength,
            int segmentLengthMask,
            int segmentCountLength,
            int arrayLength,
            int attempt) {
        String suffix = attempt == 0 ? "" : " (attempt " + (attempt + 1) + ")";
        int size = keys.length;

        int[] count = new int[arrayLength];
        int[] xorIdx = new int[arrayLength];

        FilterBuildProgress indexing =
                new FilterBuildProgress(LOGGER::info, PROGRESS_NAME + ": indexing" + suffix, size);
        for (int i = 0; i < size; i++) {
            long hash = mix(keys[i], seed);
            int h0 = hashPosition(0, hash, segmentCountLength, segmentLength, segmentLengthMask);
            int h1 = hashPosition(1, hash, segmentCountLength, segmentLength, segmentLengthMask);
            int h2 = hashPosition(2, hash, segmentCountLength, segmentLength, segmentLengthMask);
            count[h0]++;
            count[h1]++;
            count[h2]++;
            xorIdx[h0] ^= i;
            xorIdx[h1] ^= i;
            xorIdx[h2] ^= i;
            indexing.report(i + 1);
        }

        // Peeling queue of singleton positions; see peelingQueueLength for the exact-capacity /
        // no-overflow rationale (each position is enqueued at most once).
        int[] queue = new int[peelingQueueLength(arrayLength, size)];
        int qHead = 0;
        int qTail = 0;
        for (int pos = 0; pos < arrayLength; pos++) {
            if (count[pos] == 1) {
                queue[qTail++] = pos;
            }
        }

        int[] order = new int[size];
        int[] alone = new int[size];
        int done = 0;

        // Peeling loop: peel one singleton at a time.
        FilterBuildProgress peeling = new FilterBuildProgress(LOGGER::info, PROGRESS_NAME + ": peeling" + suffix, size);
        while (qHead < qTail) {
            int pos = queue[qHead++];
            if (count[pos] != 1) {
                continue;
            }
            int keyIdx = xorIdx[pos];
            order[done] = keyIdx;
            alone[done] = pos;
            done++;
            peeling.report(done);

            long hash = mix(keys[keyIdx], seed);
            int h0 = hashPosition(0, hash, segmentCountLength, segmentLength, segmentLengthMask);
            int h1 = hashPosition(1, hash, segmentCountLength, segmentLength, segmentLengthMask);
            int h2 = hashPosition(2, hash, segmentCountLength, segmentLength, segmentLengthMask);

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

        if (done < size) {
            return null;
        }

        // Reverse assignment: fill fingerprints from last peeled to first.
        byte[] table = new byte[arrayLength];
        FilterBuildProgress assigning =
                new FilterBuildProgress(LOGGER::info, PROGRESS_NAME + ": assigning" + suffix, size);
        for (int j = done - 1; j >= 0; j--) {
            int keyIdx = order[j];
            long hash = mix(keys[keyIdx], seed);
            int h0 = hashPosition(0, hash, segmentCountLength, segmentLength, segmentLengthMask);
            int h1 = hashPosition(1, hash, segmentCountLength, segmentLength, segmentLengthMask);
            int h2 = hashPosition(2, hash, segmentCountLength, segmentLength, segmentLengthMask);
            int pos = alone[j];
            byte fp = fingerprint8(hash);
            if (pos == h0) {
                table[h0] = (byte) (fp ^ table[h1] ^ table[h2]);
            } else if (pos == h1) {
                table[h1] = (byte) (fp ^ table[h0] ^ table[h2]);
            } else {
                table[h2] = (byte) (fp ^ table[h0] ^ table[h1]);
            }
            assigning.report(done - j);
        }

        return table;
    }
}
