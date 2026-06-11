// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

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
 * @param fingerprints       the fingerprint slot array (reference, treated as read-only)
 * @param seed               the construction seed used by the lookup hash
 * @param segmentLength      the per-segment {@code reduce} length
 * @param segmentLengthMask  {@code segmentLength - 1} (GPU metadata mirror)
 * @param segmentCountLength  the total fingerprint slot count ({@code fingerprints.length})
 */
// The fingerprint array is carried by reference as a read-only GPU-upload payload; this type is
// never compared with equals()/hashCode() (the array-component identity-semantics concern the
// ArrayRecordComponent check guards against does not apply here), so the array component is
// intentional rather than a defect.
@SuppressWarnings("ArrayRecordComponent")
public record BinaryFuse8GpuFilterData(
        byte[] fingerprints, long seed, int segmentLength, int segmentLengthMask, int segmentCountLength) {}
