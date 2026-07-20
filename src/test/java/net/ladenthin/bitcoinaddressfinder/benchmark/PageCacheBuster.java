// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Evicts the operating system's file cache by allocating and writing a large amount of anonymous
 * memory, so that a subsequent measurement starts from a genuinely cold page cache.
 *
 * <h2>Why this is needed</h2>
 * Any benchmark that reads a database larger than RAM is dominated by whatever the OS happens to
 * have cached from previous runs. Measured on the 61&nbsp;GB Full DB, a partially warm cache made the
 * build run at ~2.9&nbsp;M entries/s for the first ~30&nbsp;% and ~0.43&nbsp;M/s afterwards — a
 * ~7&times; difference produced entirely by cache state, which is far larger than most effects worth
 * measuring (for example an {@code MDB_NORDAHEAD} A/B). Comparing two runs with different cache
 * states is therefore meaningless. Rebooting between runs works but is slow and disruptive; forcing
 * memory pressure achieves the same thing in a couple of minutes.
 *
 * <h2>Why the memory is filled with pseudo-random data</h2>
 * Allocating without writing would not help: the pages would never be committed, so no pressure
 * reaches the file cache. Writing <em>zeroes</em> is also unreliable — zero pages are exactly what
 * memory deduplication, compression and copy-on-write optimisations collapse, so the physical
 * footprint can stay far below the logical allocation. Incompressible pseudo-random content
 * guarantees every page is real, distinct and resident, which is what actually pushes the cached
 * file pages out of the standby list.
 *
 * <p>A fast xorshift generator is used rather than {@link java.util.Random} or {@code SecureRandom}:
 * filling tens of gigabytes must be bounded by memory bandwidth, not by the generator.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -Xmx54g -cp ... PageCacheBuster [gibToAllocate]
 * </pre>
 * Allocates in 1&nbsp;GiB chunks and exits, releasing everything back to the OS — the eviction has
 * already happened by then. Choose a size that leaves headroom for the OS and other applications
 * (roughly 8-12&nbsp;GiB below total RAM); allocating too much just causes the machine to swap,
 * which is slow and helps nothing. This is a diagnostic tool, never used by production code.
 */
public final class PageCacheBuster {

    /** Chunk size: 1 GiB expressed as a {@code long[]} element count (1 GiB / 8 bytes). */
    private static final int LONGS_PER_GIB = 128 * 1024 * 1024;

    /** Default allocation when no size is given, in GiB. */
    private static final int DEFAULT_GIB = 48;

    private PageCacheBuster() {
        // no instances
    }

    /**
     * Allocates and fills memory, then exits.
     *
     * @param args optionally {@code [gibToAllocate]}; defaults to {@value #DEFAULT_GIB} GiB
     */
    public static void main(String[] args) {
        int gib = args.length >= 1 ? Integer.parseInt(args[0]) : DEFAULT_GIB;
        System.out.printf("PageCacheBuster: allocating %d GiB of incompressible data ...%n", gib);

        List<long[]> chunks = new ArrayList<>(gib);
        long state = 0x9E37_79B9_7F4A_7C15L;
        long startNanos = System.nanoTime();

        for (int chunk = 0; chunk < gib; chunk++) {
            long[] block;
            try {
                block = new long[LONGS_PER_GIB];
            } catch (OutOfMemoryError oom) {
                // Stopping early is fine: whatever was allocated has already applied pressure.
                System.out.printf("PageCacheBuster: stopped at %d GiB (heap exhausted)%n", chunk);
                break;
            }
            // Write every element so the page is committed and incompressible.
            for (int i = 0; i < block.length; i++) {
                state ^= state << 13;
                state ^= state >>> 7;
                state ^= state << 17;
                block[i] = state;
            }
            chunks.add(block);
            System.out.printf(
                    "PageCacheBuster: %d/%d GiB written (%.1fs)%n",
                    chunk + 1, gib, (System.nanoTime() - startNanos) / 1e9);
        }

        // Consume the data so neither the JIT nor the GC can elide the writes.
        long checksum = 0L;
        for (long[] block : chunks) {
            checksum ^= block[0] ^ block[block.length - 1];
        }
        System.out.printf(
                "PageCacheBuster: done, %d GiB resident, checksum=%d — releasing.%n", chunks.size(), checksum);
    }
}
