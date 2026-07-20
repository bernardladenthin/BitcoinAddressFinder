// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Synthetic {@link AddressIterable} of deterministically generated hash160 values, used to compare
 * address-lookup backends without involving LMDB.
 *
 * <h2>Why this lives in main scope rather than under the benchmarks</h2>
 * It began as a benchmark-only fixture, but the {@code TuneConfiguration} command needs exactly the
 * same source at runtime: it sizes a filter to the database the user <em>intends</em> to run
 * against, without requiring that database to exist. Production code cannot reference test classes,
 * so the class moved here rather than being duplicated — one implementation keeps the tuner's filter
 * bit-for-bit identical to the one the benchmarks characterise.
 *
 * <h2>Why synthetic</h2>
 * Measuring filters against a real LMDB store conflates two very different costs. On a database
 * larger than free RAM the source walk is dominated by page-cache misses and read amplification —
 * measured at ~17&times; on the 61&nbsp;GB Full DB, where the NVMe delivered 332&nbsp;MB/s to yield
 * ~19&nbsp;MB/s of useful entries. That I/O noise swamps the filter's own cost and is not
 * reproducible between runs (it depends on what the OS happens to have cached). Generating
 * addresses from a seeded PRNG removes storage from the measurement entirely, so what remains is
 * the filter.
 *
 * <h2>Why streaming rather than a stored array</h2>
 * Addresses are produced on demand from the seed, never materialised. Holding 100&nbsp;M hash160
 * values would itself cost ~2&nbsp;GB and would compete with the filter under test for cache and
 * heap. Because every backend already builds from {@link AddressIterable}, this class substitutes
 * for the LMDB source with no changes to the filters.
 *
 * <h2>Determinism</h2>
 * The value at index {@code i} is a pure function of {@code (seed, i)} — a SplitMix64 mix of
 * {@code seed + i * GOLDEN} — so the same {@code (seed, count)} always yields the same set, in the
 * same order, on any platform, and two instances can be compared or re-run exactly. Successive
 * values are independent, giving the uniformly distributed keys that real hash160 values (SHA-256
 * then RIPEMD-160) also have.
 *
 * <p>Bytes 0..7 carry the mixed value big-endian — the same eight bytes every filter extracts as its
 * 64-bit key — and the remaining 12 bytes are filled from a second mix so the full 20-byte value is
 * distinct for backends that hash all of it (notably the Guava {@code BLOOM} accelerator).
 */
public final class PrngAddressIterable implements AddressIterable {

    /** Length of a hash160 entry in bytes. */
    public static final int BYTES_PER_ADDRESS = 20;

    /** Odd golden-ratio increment; any odd stride walks the full 64-bit space without repeats. */
    private static final long GOLDEN = 0x9E37_79B9_7F4A_7C15L;

    private final long seed;
    private final long count;

    /**
     * Creates a synthetic address source.
     *
     * @param seed  the generator seed; different seeds yield disjoint address sets, which is how
     *              member and non-member probe sets are kept independent
     * @param count the number of addresses this source yields
     */
    public PrngAddressIterable(long seed, long count) {
        this.seed = seed;
        this.count = Math.max(0L, count);
    }

    /**
     * Returns the 20-byte address at {@code index} for {@code seed}, as a pure function of both.
     *
     * @param seed  the generator seed
     * @param index the position in the sequence
     * @return a freshly allocated 20-byte address
     */
    public static byte[] addressAt(long seed, long index) {
        byte[] out = new byte[BYTES_PER_ADDRESS];
        long a = mix64(seed + index * GOLDEN);
        long b = mix64(a ^ GOLDEN);
        for (int i = 0; i < Long.BYTES; i++) {
            out[i] = (byte) (a >>> (56 - 8 * i));
        }
        for (int i = 0; i < BYTES_PER_ADDRESS - Long.BYTES; i++) {
            out[Long.BYTES + i] = (byte) (b >>> (56 - 8 * (i % Long.BYTES)));
        }
        return out;
    }

    /** SplitMix64 finaliser — strong avalanche, so consecutive indices give independent values. */
    private static long mix64(long z0) {
        long z = z0;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public Stream<ByteBuffer> addresses() {
        return LongStream.range(0L, count).mapToObj(i -> ByteBuffer.wrap(addressAt(seed, i)));
    }

    /**
     * Streams every address into {@code action}, reusing one buffer to keep allocation out of the
     * build measurement. The buffer is only valid until the next call, matching the contract of
     * {@code LMDBPersistence.forEachAddress}.
     *
     * @param action the consumer invoked once per address
     */
    @Override
    public void forEachAddress(Consumer<ByteBuffer> action) {
        byte[] scratch = new byte[BYTES_PER_ADDRESS];
        ByteBuffer view = ByteBuffer.wrap(scratch);
        for (long i = 0; i < count; i++) {
            long a = mix64(seed + i * GOLDEN);
            long b = mix64(a ^ GOLDEN);
            for (int j = 0; j < Long.BYTES; j++) {
                scratch[j] = (byte) (a >>> (56 - 8 * j));
            }
            for (int j = 0; j < BYTES_PER_ADDRESS - Long.BYTES; j++) {
                scratch[Long.BYTES + j] = (byte) (b >>> (56 - 8 * (j % Long.BYTES)));
            }
            view.clear();
            action.accept(view);
        }
    }

    @Override
    public long count() {
        return count;
    }

    @Override
    public String toString() {
        return "PrngAddressIterable{seed=" + seed + ", count=" + count + '}';
    }
}
