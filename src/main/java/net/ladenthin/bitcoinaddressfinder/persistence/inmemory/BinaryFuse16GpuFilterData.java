// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * VRAM-upload payload for {@link BinaryFuse16AddressPresence}.
 *
 * <p>Structurally identical to {@link BinaryFuse8GpuFilterData} except for the fingerprint width:
 * the geometry fields (seed, segment length, mask, count) mean exactly the same thing and are
 * uploaded as the same five-word metadata block, so the OpenCL side reuses {@code fuse8_mix} and
 * {@code fuse8_position} and differs only in reading 16-bit slots.
 *
 * <h2>Why a separate record rather than a widened one</h2>
 * The fingerprint width <em>is</em> the filter: {@code (short)(hash ^ (hash >>> 32))} against
 * {@code (byte)(...)} produces a different, independent filter with a 242&times; lower
 * false-positive rate (0.0016 % against 0.39 %, measured). Keeping the two payload types distinct
 * makes it impossible to upload one and probe it as the other, which would not fail loudly — it
 * would silently produce false negatives.
 *
 * <h2>Memory</h2>
 * ~2.25 B/entry against Fuse-8's ~1.13. On an OpenCL device the filter is a <b>single</b>
 * allocation and therefore bounded by {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE}, which is
 * vendor-dependent: an RTX 3070 reports 2047 MB of 8191 (the spec minimum, a quarter), an
 * RX 7900 XTX 20876 MB of 24560 (85 %). At 2.25 B/entry the NVIDIA limit admits ~909 M entries.
 *
 * @param fingerprints       the fingerprint slot array; the reference is shared, not copied, and
 *                           must be treated as read-only by callers
 * @param seed               construction seed driving {@code murmur64(key + seed)}
 * @param segmentLength      length of one fused segment
 * @param segmentLengthMask  mask selecting a within-segment offset
 * @param segmentCountLength total addressable slot count used for the base position
 */
@SuppressWarnings("ArrayRecordComponent")
public record BinaryFuse16GpuFilterData(
        short[] fingerprints, long seed, int segmentLength, int segmentLengthMask, int segmentCountLength) {

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BinaryFuse16GpuFilterData other)) {
            return false;
        }
        return seed == other.seed
                && segmentLength == other.segmentLength
                && segmentLengthMask == other.segmentLengthMask
                && segmentCountLength == other.segmentCountLength
                && Arrays.equals(fingerprints, other.fingerprints);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(seed, segmentLength, segmentLengthMask, segmentCountLength);
        result = 31 * result + Arrays.hashCode(fingerprints);
        return result;
    }

    /**
     * Returns a short description without dumping the (potentially gigabyte-sized) slot array.
     *
     * @return a summary naming the slot count and geometry
     */
    @Override
    public String toString() {
        return "BinaryFuse16GpuFilterData[fingerprints=" + fingerprints.length
                + " slots, seed=" + seed
                + ", segmentLength=" + segmentLength
                + ", segmentLengthMask=" + segmentLengthMask
                + ", segmentCountLength=" + segmentCountLength
                + "]";
    }

    /**
     * Returns the payload size in bytes, i.e. what the upload will occupy in VRAM.
     *
     * @return the payload size in bytes
     */
    public long sizeInBytes() {
        return (long) fingerprints.length * Short.BYTES;
    }
}
