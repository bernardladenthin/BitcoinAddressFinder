// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * VRAM-upload payload for the blocked Bloom filter — the bit array plus the two scalars the kernel
 * needs to reproduce {@link BlockedBloomAddressPresence#containsAddress}.
 *
 * <p>Counterpart to {@link BinaryFuse8GpuFilterData}. Where the fuse payload carries a fingerprint
 * array and four segment scalars, this one carries the raw {@code long[]} block array plus
 * {@code logBlocks} and {@code k}; the kernel derives everything else from the key.
 *
 * <p>The device-side probe ({@code blockedbloom_contains} in {@code inc_ecc_secp256k1custom.cl})
 * must reproduce the Java formula exactly — any divergence yields silent <em>false negatives</em>,
 * i.e. a stored address that is never flagged, which is the one failure this project cannot
 * tolerate. The formula is pinned by
 * {@code BlockedBloomAddressPresenceTest#gpuStyleLookup_agreesWithContainsAddress}.
 *
 * <p>Kernel metadata layout is {@code [logBlocks, k]}, deliberately shorter than the fuse filter's
 * five words: the blocked layout needs no segment geometry, since all probes of a key are confined
 * to one 512-bit block.
 *
 * @param words     the backing bit array, {@code 2^logBlocks * 8} longs
 * @param logBlocks base-2 logarithm of the block count
 * @param k         number of bits probed per key
 */
@SuppressWarnings("ArrayRecordComponent")
public record BlockedBloomGpuFilterData(long[] words, int logBlocks, int k) {

    /**
     * Renders the kernel metadata array in the layout {@code blockedbloom_contains} expects.
     *
     * @return {@code [logBlocks, k]}
     */
    public int[] toMetadata() {
        return new int[] {logBlocks, k};
    }

    /**
     * Size of the bit array in bytes, i.e. how much VRAM the upload occupies.
     *
     * @return the payload size in bytes
     */
    public long sizeInBytes() {
        return (long) words.length * Long.BYTES;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockedBloomGpuFilterData other)) {
            return false;
        }
        return logBlocks == other.logBlocks && k == other.k && Arrays.equals(words, other.words);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(logBlocks, k);
        result = 31 * result + Arrays.hashCode(words);
        return result;
    }

    @Override
    public String toString() {
        // Length only — the block array reaches gigabytes and would be log-killing in full.
        return "BlockedBloomGpuFilterData[words.length=" + words.length + ", logBlocks=" + logBlocks + ", k=" + k
                + ", sizeInBytes=" + sizeInBytes() + "]";
    }
}
