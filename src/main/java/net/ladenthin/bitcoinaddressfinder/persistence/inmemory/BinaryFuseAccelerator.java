// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.nio.ByteBuffer;
import java.util.Optional;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.persistence.AbstractFilterAccelerator;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import org.jspecify.annotations.NonNull;

/**
 * Read-only accelerator that places a Binary Fuse Filter ({@link BinaryFuse8AddressPresence}
 * or {@link BinaryFuse16AddressPresence}) in front of an exact {@link AddressPresence}
 * delegate (typically the LMDB read store).
 *
 * <p>Binary Fuse Filters have <em>no false negatives</em> but a non-negligible
 * <em>false-positive</em> rate (&#x2248; 0.4&nbsp;% for the 8-bit variant,
 * &#x2248; 0.0015&nbsp;% for the 16-bit variant). That rate is far too high to report a filter
 * positive as a final hit — at a 0.4&nbsp;% FPR roughly one in every 250 scanned addresses
 * would be a spurious hit. The verify-on-hit behaviour and the {@code requiresBackend() == true}
 * contract are inherited from {@link AbstractFilterAccelerator} (shared with the Bloom filter
 * accelerator); this class only supplies the Fuse filter probe via {@link #mightContain(ByteBuffer)}.
 *
 * <h2>GPU pre-filtering</h2>
 * When the wrapped filter is a {@link BinaryFuse8AddressPresence}, {@link #getGpuFilterData()}
 * exposes its VRAM-upload payload so the OpenCL producer can run the same filter on the GPU to
 * shrink the GPU&#x2192;CPU transfer. Survivors of the GPU pre-filter are still verified against
 * the exact delegate on the CPU through {@link #containsAddress(ByteBuffer)}.
 *
 * <h2>Concurrency</h2>
 * Thread-safe for concurrent reads as long as the wrapped filter and delegate are; the Binary
 * Fuse filters and the {@code LMDBPersistence} read path both are.
 */
@ToString(callSuper = true)
public final class BinaryFuseAccelerator extends AbstractFilterAccelerator<AddressPresence> {

    private final @NonNull AddressPresence filter;

    /**
     * Creates a new accelerator.
     *
     * @param filter   the pre-populated Binary Fuse filter consulted first
     * @param delegate the exact lookup consulted to disambiguate filter positives
     */
    public BinaryFuseAccelerator(@NonNull AddressPresence filter, @NonNull AddressPresence delegate) {
        super(delegate);
        this.filter = filter;
    }

    /**
     * Probes the Binary Fuse filter; no false negatives, so a miss is definitive.
     *
     * @param hash160 the address hash to test (position/limit preserved by the filter)
     * @return {@code true} if the filter reports the address as possibly present
     */
    @Override
    protected boolean mightContain(ByteBuffer hash160) {
        return filter.containsAddress(hash160);
    }

    /**
     * Returns the GPU VRAM-upload payload of the wrapped filter, if it is a
     * {@link BinaryFuse8AddressPresence}.
     *
     * @return the Binary Fuse 8 GPU-upload payload, or {@link Optional#empty()} if the wrapped
     *     filter is not a {@link BinaryFuse8AddressPresence} (e.g. the 16-bit variant, whose
     *     payload is obtained from {@link #getGpuFilterData16()} instead)
     */
    public Optional<BinaryFuse8GpuFilterData> getGpuFilterData() {
        if (filter instanceof BinaryFuse8AddressPresence fuse8) {
            return Optional.of(fuse8.toGpuFilterData());
        }
        return Optional.empty();
    }

    /**
     * Returns the GPU VRAM-upload payload of the wrapped filter, if it is a
     * {@link BinaryFuse16AddressPresence}.
     *
     * <p>Deliberately a <em>second, separate</em> accessor rather than one that returns a common
     * supertype of both payloads: the fingerprint width is what defines the filter, so a payload of
     * one width uploaded and probed as the other does not throw — it silently reports misses for
     * addresses that are present. A caller that wants 16-bit fingerprints must therefore ask for
     * them by name and cannot accidentally be handed the 8-bit payload.
     *
     * @return the Binary Fuse 16 GPU-upload payload, or {@link Optional#empty()} if the wrapped
     *     filter is not a {@link BinaryFuse16AddressPresence}
     */
    public Optional<BinaryFuse16GpuFilterData> getGpuFilterData16() {
        if (filter instanceof BinaryFuse16AddressPresence fuse16) {
            return Optional.of(fuse16.toGpuFilterData());
        }
        return Optional.empty();
    }
}
