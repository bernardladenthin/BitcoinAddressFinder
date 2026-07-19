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
 *   block = unsignedMultiplyHigh(a, numBlocks)     // uniform block in [0, numBlocks)
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

    /**
     * Default number of bits set per key.
     *
     * <p><b>Measured, not derived.</b> At the shipped {@link #DEFAULT_BITS_PER_ENTRY} the optimum is
     * {@code k = 6}: sweeping k at a true 11 bits/entry gives FPR 0.998 / 0.811 / <b>0.753</b> /
     * 0.759 / 0.802 / 0.993 % for k = 4 / 5 / <b>6</b> / 7 / 8 / 10, reproduced bit-identically on two
     * machines. {@code k = 6} also probes fewer bits, so it is simultaneously the faster and the more
     * accurate choice — the shipped {@code k = 8} cost ~6.5 % relative FPR and ~20 % throughput.
     *
     * <p><b>The optimum grows sub-linearly with density and saturates.</b> Measured across a full
     * density × k grid:
     *
     * <pre>
     *   bits/entry   8   11   14   17   21   26
     *   optimum k    5    6    7    7    8    9
     * </pre>
     *
     * The ratio k/bitsPerEntry falls from 0.63 to 0.35, so an earlier "{@code k ≈ 0.55 ×
     * bitsPerEntry}" rule — derived from three densities that all happened to lie between 11 and 16
     * — does <b>not</b> extrapolate and has been withdrawn. It would put k at 14 for 26 bits/entry
     * where the measured optimum is 9. The saturation is expected for this layout: all probes are
     * confined to one 512-bit block, so past a point extra probes only fill the same block faster.
     * The textbook unblocked {@code (m/n)·ln2} is even further off, for the same reason.
     *
     * <p><b>Raise this together with {@link #DEFAULT_BITS_PER_ENTRY}</b>; the two are coupled, and
     * changing one alone lands off the optimum — which is exactly what happened when the sizing
     * moved to fastrange while k stayed at 8.
     */
    static final int DEFAULT_K = 6;

    /**
     * Default target bits per entry used to auto-size the filter.
     *
     * <p>Since the block count is chosen with a multiply-shift rather than rounded up to a power of
     * two, this is now the <em>literal</em> density: 11 bits/entry everywhere, where the old sizing
     * delivered anywhere from 11 to 21.5 depending on how the entry count happened to fall. That
     * makes the filter smaller (131 MiB instead of 256 MiB at 100 M entries) but denser, and density
     * costs lookup speed — {@code containsAddress} short-circuits at the first unset bit, so more set
     * bits means more probes before a miss can be answered.
     *
     * <p><b>The right value depends on what a false positive costs, which was measured and is far
     * higher than long assumed.</b> Every filter hit is verified against LMDB, and that verification
     * costs 4.1&nbsp;µs on a warm 132&nbsp;M-entry store, 6.7&nbsp;µs warm at 1.377&nbsp;B, and
     * <b>105&nbsp;µs / 293&nbsp;µs cold</b> — against a filter probe of roughly 120&nbsp;ns. Earlier
     * reasoning in this project assumed ~200&nbsp;ns and was wrong by a factor of 20 to 1500.
     *
     * <p>Total cost per query is therefore {@code probe + FPR × verification}, and the FPR term
     * dominates whenever the database does not fit in RAM:
     *
     * <pre>
     *   verification    knee of the curve      beyond the knee
     *   4-7 µs (warm)   ~14 bits/entry         no further gain, only memory
     *   105-293 µs      still falling at 26    denser is better as far as measured
     * </pre>
     *
     * The default of 11 predates that measurement. For a warm light database it is defensible; for a
     * large or cold store, 14-17 is clearly better and 21-26 better still. It is left at 11 pending
     * a second machine's confirmation rather than changed on one host's data.
     *
     * <p>Coupled to {@link #DEFAULT_K}: see the table documented there before changing either.
     */
    static final int DEFAULT_BITS_PER_ENTRY = 11;

    private final long[] words;
    private final int numBlocks;
    private final int k;

    private BlockedBloomAddressPresence(long[] words, int numBlocks, int k) {
        this.words = words;
        this.numBlocks = numBlocks;
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
        int numBlocks = chooseBlocks(count, bitsPerEntry);
        int wordCount = numBlocks * LONGS_PER_BLOCK;

        long totalBits = (long) numBlocks * BLOCK_BITS;
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
        // Streaming single pass over the whole database. At the billion-entry tier this runs for
        // many minutes with no other output, so report bounded progress (see FilterBuildProgress).
        FilterBuildProgress progress =
                new FilterBuildProgress(LOGGER::info, PROGRESS_NAME + ": inserting addresses", count);
        long[] processed = {0L};
        source.forEachAddress(bb -> {
            if (bb.remaining() == BYTES_PER_ADDRESS) {
                setKey(words, bb.getLong(bb.position()), numBlocks, k);
            }
            progress.report(++processed[0]);
        });

        LOGGER.info("{}: ready ({} blocks, k={}, {} addresses inserted).", PROGRESS_NAME, numBlocks, k, processed[0]);
        return new BlockedBloomAddressPresence(words, numBlocks, k);
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
        int block = (int) Math.unsignedMultiplyHigh(a, numBlocks);
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
    private static void setKey(long[] words, long key, int numBlocks, int k) {
        long a = murmur64(key);
        long b = murmur64(key + GOLDEN);
        int base = ((int) Math.unsignedMultiplyHigh(a, numBlocks)) * LONGS_PER_BLOCK;
        int x = (int) b;
        int y = oddStride(b);
        for (int i = 0; i < k; i++) {
            int bit = (x + i * y) & BLOCK_MASK;
            words[base + (bit >>> 6)] |= 1L << (bit & 63);
        }
    }

    /**
     * Chooses the block count so the filter holds at least {@code bitsPerEntry} bits per entry.
     *
     * <p>Any count is admissible, not just powers of two, because the block index is derived with a
     * multiply-shift ({@code unsignedMultiplyHigh(hash, numBlocks)}, Lemire's fastrange) rather than
     * a mask. Requiring a power of two used to waste up to 2&times; the memory: at 100 M entries a
     * requested 11 bits/entry rounded up to 21.47 (256 MiB instead of ~131 MiB), which measurably
     * hurt the GPU probe by spilling caches a correctly sized filter would have fit in.
     *
     * @param count        the entry count
     * @param bitsPerEntry the target bits per entry
     * @return the block count, at least 1 and small enough that the backing {@code long[]} stays
     *     within {@link Integer#MAX_VALUE} elements
     */
    static int chooseBlocks(long count, int bitsPerEntry) {
        long blocks = (long) Math.ceil((double) Math.max(count, 1L) * bitsPerEntry / BLOCK_BITS);
        // words = blocks * LONGS_PER_BLOCK must stay addressable by an int.
        long maxBlocks = Integer.MAX_VALUE / LONGS_PER_BLOCK;
        return (int) Math.max(1L, Math.min(blocks, maxBlocks));
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
     * Builds the VRAM-upload payload for the GPU probe.
     *
     * <p>Shares the {@code long[]} rather than copying it: the array reaches 2 GiB at the Full DB
     * tier, and the payload is a short-lived staging object that is dropped after the one-time
     * upload. Callers must treat it as read-only.
     *
     * @return the payload consumed by {@code blockedbloom_contains}
     */
    @SuppressWarnings("EI_EXPOSE_REP")
    public BlockedBloomGpuFilterData toGpuFilterData() {
        return new BlockedBloomGpuFilterData(words, numBlocks, k);
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
     * Returns the block count; the block for a key is
     * {@code (int) Math.unsignedMultiplyHigh(murmur64(key), numBlocks)}.
     *
     * @return the number of 512-bit blocks
     */
    int getNumBlocks() {
        return numBlocks;
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
        return numBlocks;
    }
}
