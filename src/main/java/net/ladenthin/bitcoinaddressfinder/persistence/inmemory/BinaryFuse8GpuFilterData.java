// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, cross-layer payload describing a {@link BinaryFuse8AddressPresence} filter for
 * GPU VRAM upload.
 *
 * <p>This carrier exists so the engine layer can read the filter's state through a single
 * <b>public</b> accessor ({@link BinaryFuse8AddressPresence#toGpuFilterData()}) and decompose
 * it into the primitive arguments that the OpenCL layer's upload method accepts &mdash; the
 * OpenCL layer never depends on this persistence type. The five scalar fields mirror the
 * filter's package-private getters; {@code seed} is split into a low/high {@code int} pair by
 * the caller before it reaches the kernel.
 *
 * <p>{@link #equals(Object)}, {@link #hashCode()} and {@link #toString()} are overridden to give
 * the {@code fingerprints} array proper value semantics (the record's auto-generated versions
 * would compare by array identity); {@code toString} prints only the array length, since the
 * fingerprint array can be large.
 *
 * @param fingerprints       the fingerprint slot array (reference, treated as read-only)
 * @param seed               the construction seed used by the lookup hash
 * @param segmentLength      the per-segment {@code reduce} length
 * @param segmentLengthMask  {@code segmentLength - 1} (GPU metadata mirror)
 * @param segmentCountLength  the total fingerprint slot count ({@code fingerprints.length})
 */
// Error Prone's ArrayRecordComponent is purely syntactic (it flags any array record component
// because the auto-generated equals/hashCode use identity); it is suppressed here because this
// record provides explicit Arrays-aware equals/hashCode/toString below, giving the array correct
// value semantics. SonarCloud rule S6218 is satisfied by those same overrides.
@SuppressWarnings("ArrayRecordComponent")
public record BinaryFuse8GpuFilterData(
        byte[] fingerprints, long seed, int segmentLength, int segmentLengthMask, int segmentCountLength) {

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BinaryFuse8GpuFilterData other)) {
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

    @Override
    public String toString() {
        // Length only — the fingerprint array can be large and would be log-killing in full.
        return "BinaryFuse8GpuFilterData[fingerprints.length=" + fingerprints.length + ", seed=" + seed
                + ", segmentLength=" + segmentLength + ", segmentLengthMask=" + segmentLengthMask
                + ", segmentCountLength=" + segmentCountLength + "]";
    }
}
