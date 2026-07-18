// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.nio.ByteBuffer;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.FilterBuildProgress;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Presence-only snapshot backed by a <b>blocked Bloom filter</b> implementing {@link AddressPresence}.
 *
 * <h2>Why blocked Bloom</h2>
 * Unlike {@link BinaryFuse8AddressPresence} (which needs a multi-array peeling construction with
 * ~29&nbsp;bytes/entry <em>peak</em> heap — ~42&nbsp;GB for a billion-entry database, which is heavy
 * but, contrary to an earlier claim here, not prohibitive), a blocked
 * Bloom filter is built in a <b>single streaming pass</b>: allocate the bit array once, then for
 * each key set {@code k} bits. Peak build memory is therefore just the filter itself (~2&nbsp;GB),
 * so it builds on databases of any size, including the full 1.38&nbsp;B-entry tier.
 *
 * <h2>Blocked layout (one cache line per lookup)</h2>
 * The bit array is split into {@value #BLOCK_BITS}-bit <b>blocks</b> ({@value #LONGS_PER_BLOCK}
 * {@code long}s = 64&nbsp;bytes = one CPU cache line / one coalesced GPU memory transaction). All
 * {@code k} bits of a key live in the <em>same</em> block, so a lookup touches exactly <b>one</b>
 * 64-byte region instead of {@code k} scattered reads — the key to GPU throughput.
 *
 * <h2>Hashing (byte-exact for the GPU port)</h2>
 * <pre>{@code
 *   key = hash160[0..7] as big-endian long        // same extraction as the fuse filter
 *   a   = murmur64(key)
 *   b   = murmur64(key + GOLDEN)
 *   block = (int)(a >>> (64 - logBlocks))          // uniform block in [0, 2^logBlocks)
 *   x     = (int)b
 *   y     = (int)(b >>> 32) | 1                    // stride MUST be odd - see oddStride()
 *   for i in 0..k-1:  bit = (x + i * y) & 511      // k distinct positions in [0, 512)
 * }</pre>
 * The {@code | 1} is load-bearing: with an even stride the probe sequence's period collapses and a
 * fraction of keys set far fewer than {@code k} distinct bits, which measured a ~5&times; worse
 * false-positive rate. See {@link #oddStride(long)}.
 * A key is "possibly present" iff all {@code k} of its bits are set. No false negatives.
 *
 * <h2>False-positive rate</h2>
 * With {@code m} total bits, {@code n} keys and {@code k} bits/key the (unblocked) FPR is
 * approximately {@code (1 - e^(-k*n/m))^k}; blocking adds a small penalty from per-block key-count
 * variance. Auto-sized to ~{@value #DEFAULT_BITS_PER_ENTRY} bits/entry, so at the default
 * {@code k} the FPR lands around 1&nbsp;%. Like the fuse filters this is a pre-filter: every hit is
 * verified against the exact backend (LMDB), so {@link #requiresBackend()} returns {@code true}.
 *
 * <h2>Concurrency</h2>
 * Thread-safe for concurrent reads after construction. No mutation API is exposed.
 */
@ToString
public final class BlockedBloomAddressPresence implements AddressPresence {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockedBloomAddressPresence.class);

    /** Human-readable prefix for construction progress log lines. */
    private static final String PROGRESS_NAME = "Blocked Bloom filter";

    /** Fixed length of a hash160 entry in bytes. */
    static final int BYTES_PER_ADDRESS = 20;

    /** Bits per block (one cache line / one coalesced GPU read). */
    static final int BLOCK_BITS = 512;

    /** {@code long}s per block ({@value #BLOCK_BITS} / 64). */
    static final int LONGS_PER_BLOCK = BLOCK_BITS / Long.SIZE;

    /** Mask for a bit index within a block ({@value #BLOCK_BITS} - 1). */
    static final int BLOCK_MASK = BLOCK_BITS - 1;

    /** Golden-ratio constant used to derive the second, independent hash. */
    static final long GOLDEN = 0x9E37_79B9_7F4A_7C15L;

    /** Default number of bits set per key. */
    static final int DEFAULT_K = 8;

    /** Default target bits per entry used to auto-size the filter (~2&nbsp;GiB at 1.5&nbsp;B keys). */
    static final int DEFAULT_BITS_PER_ENTRY = 11;

    private final long[] words;
    private final int logBlocks;
    private final int k;

    private BlockedBloomAddressPresence(long[] words, int logBlocks, int k) {
        this.words = words;
        this.logBlocks = logBlocks;
        this.k = k;
    }

    /**
     * Builds a blocked Bloom filter from {@code source} using the default {@code k} and bits/entry.
     *
     * @param source the address set to materialise
     * @return a fully populated, self-contained presence lookup
     */
    public static BlockedBloomAddressPresence populateFrom(@NonNull AddressIterable source) {
        return populateFrom(source, DEFAULT_K, DEFAULT_BITS_PER_ENTRY);
    }

    /**
     * Builds a blocked Bloom filter from {@code source} in a single streaming pass.
     *
     * @param source        the address set to materialise
     * @param k             number of bits set per key (accuracy knob)
     * @param bitsPerEntry  target bits per entry (size knob); the block count is rounded up to a
     *                      power of two so at least this many bits are allocated
     * @return a fully populated, self-contained presence lookup
     */
    public static BlockedBloomAddressPresence populateFrom(@NonNull AddressIterable source, int k, int bitsPerEntry) {
        long count = source.count();
        int logBlocks = chooseLogBlocks(count, bitsPerEntry);
        long numBlocks = 1L << logBlocks;
        int wordCount = (int) (numBlocks * LONGS_PER_BLOCK);

        long totalBits = numBlocks * BLOCK_BITS;
        LOGGER.info(
                "{}: building over {} addresses -> {} blocks x {} bits = {} MiB ({} bits/entry, k={}) ...",
                PROGRESS_NAME,
                count,
                numBlocks,
                BLOCK_BITS,
                totalBits / 8 / (1024 * 1024),
                count == 0 ? 0 : totalBits / Math.max(count, 1),
                k);

        long[] words = new long[wordCount];
        int blockShift = 64 - logBlocks;
        // Streaming single pass over the whole database. At the billion-entry tier this runs for
        // many minutes with no other output, so report bounded progress (see FilterBuildProgress).
        FilterBuildProgress progress =
                new FilterBuildProgress(LOGGER::info, PROGRESS_NAME + ": inserting addresses", count);
        long[] processed = {0L};
        source.forEachAddress(bb -> {
            if (bb.remaining() == BYTES_PER_ADDRESS) {
                setKey(words, bb.getLong(bb.position()), blockShift, k);
            }
            progress.report(++processed[0]);
        });

        LOGGER.info("{}: ready ({} blocks, k={}, {} addresses inserted).", PROGRESS_NAME, numBlocks, k, processed[0]);
        return new BlockedBloomAddressPresence(words, logBlocks, k);
    }

    @Override
    public boolean containsAddress(ByteBuffer hash160) {
        if (hash160.remaining() != BYTES_PER_ADDRESS) {
            return false;
        }
        if (words.length == 0) {
            return false;
        }
        long key = hash160.getLong(hash160.position());
        long a = murmur64(key);
        long b = murmur64(key + GOLDEN);
        int block = (int) (a >>> (64 - logBlocks));
        int base = block * LONGS_PER_BLOCK;
        int x = (int) b;
        int y = oddStride(b);
        for (int i = 0; i < k; i++) {
            int bit = (x + i * y) & BLOCK_MASK;
            if ((words[base + (bit >>> 6)] & (1L << (bit & 63))) == 0L) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean requiresBackend() {
        return true;
    }

    /**
     * Derives the probe stride, forced <b>odd</b>.
     *
     * <p>This is not cosmetic. The probe sequence is {@code bit_i = (x + i*y) mod 512}; its period is
     * {@code 512 / gcd(y, 512)}. With an unconstrained {@code y} that period collapses whenever
     * {@code y} is a large power of two: 1 key in 512 gets {@code y ≡ 0 (mod 512)} and lands all
     * {@code k} probes on a <em>single</em> bit, 1 in 256 reaches only 2 distinct bits, 1 in 128 only
     * 4. Those degenerate keys are individually near-useless as filter entries and dominate the
     * aggregate false-positive rate — measured 0.258 % against a theoretical 0.052 % at {@code k = 8}
     * and 16.24 bits/entry, a ~5× regression traced entirely to this effect.
     *
     * <p>Forcing {@code y} odd makes {@code gcd(y, 512) = 1}, so the sequence has full period 512 and
     * the {@code k} probes are <em>always</em> distinct (standard enhanced double hashing, Kirsch &amp;
     * Mitzenmacher). Cost: one OR instruction.
     *
     * @param b the second hash word
     * @return an odd stride in {@code [1, 2^32)}
     */
    private static int oddStride(long b) {
        return ((int) (b >>> 32)) | 1;
    }

    /** Sets the {@code k} bits of one key. Shared by construction (kept branch-light for the hot pass). */
    private static void setKey(long[] words, long key, int blockShift, int k) {
        long a = murmur64(key);
        long b = murmur64(key + GOLDEN);
        int base = ((int) (a >>> blockShift)) * LONGS_PER_BLOCK;
        int x = (int) b;
        int y = oddStride(b);
        for (int i = 0; i < k; i++) {
            int bit = (x + i * y) & BLOCK_MASK;
            words[base + (bit >>> 6)] |= 1L << (bit & 63);
        }
    }

    /**
     * Chooses {@code logBlocks} so the filter holds at least {@code bitsPerEntry} bits per entry,
     * rounded up to a power-of-two block count. Clamped so the backing {@code long[]} stays within
     * {@link Integer#MAX_VALUE} elements.
     *
     * @param count        the entry count
     * @param bitsPerEntry the target bits per entry
     * @return the base-2 logarithm of the block count
     */
    static int chooseLogBlocks(long count, int bitsPerEntry) {
        long targetBlocks = (long) Math.ceil((double) Math.max(count, 1) * bitsPerEntry / BLOCK_BITS);
        int raw = targetBlocks <= 1 ? 0 : (64 - Long.numberOfLeadingZeros(targetBlocks - 1));
        // Lower bound 1: at least one block, and the block shift (64 - logBlocks) must be a valid
        // (1..63) Java shift — a shift of 64 is a no-op. Upper bound 27: words = numBlocks *
        // LONGS_PER_BLOCK must stay within Integer.MAX_VALUE (2^27 blocks -> 2^30 longs).
        return Math.max(1, Math.min(raw, 27));
    }

    /**
     * MurmurHash3 64-bit finaliser (identical to the fuse filters, so the GPU can share one port).
     *
     * @param h the value to mix
     * @return the mixed value
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
     * Returns the backing bit array, exposed for GPU VRAM upload and tests. The reference (not a
     * copy) is returned deliberately; callers must treat it as read-only.
     *
     * @return the {@code long[]} bit array
     */
    @SuppressWarnings("EI_EXPOSE_REP")
    long[] getWords() {
        return words;
    }

    /**
     * Returns {@code log2(numBlocks)}; the block for a key is {@code (int)(murmur64(key) >>> (64 - logBlocks))}.
     *
     * @return the base-2 logarithm of the block count
     */
    int getLogBlocks() {
        return logBlocks;
    }

    /**
     * Returns the number of bits set/checked per key.
     *
     * @return {@code k}
     */
    int getK() {
        return k;
    }

    /**
     * Returns the number of 512-bit blocks in the filter.
     *
     * @return the block count
     */
    long blockCount() {
        return 1L << logBlocks;
    }
}
