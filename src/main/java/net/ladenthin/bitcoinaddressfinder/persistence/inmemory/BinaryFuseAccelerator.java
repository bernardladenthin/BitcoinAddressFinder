// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.nio.ByteBuffer;
import java.util.Optional;
import lombok.ToString;
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
 * would be a spurious hit. The filter is therefore used exactly like a Bloom filter (see
 * {@code net.ladenthin.bitcoinaddressfinder.persistence.bloom.BloomFilterAccelerator}):
 *
 * <ul>
 *   <li>a filter <em>miss</em> is definitive — the address is absent and the delegate is never
 *       consulted (the fast, overwhelmingly common path during a key scan);</li>
 *   <li>a filter <em>hit</em> is only probable — the call falls through to the exact delegate
 *       to confirm the address or reject it as a false positive.</li>
 * </ul>
 *
 * <p>Because positives must be disambiguated against the delegate, {@link #requiresBackend()}
 * returns {@code true}: the backing storage (LMDB env + mmap) must stay open for the lifetime
 * of this accelerator and is <em>not</em> released after the filter is built. This is the only
 * difference from the exact self-contained snapshots ({@code HASHSET}, {@code TRUNCATED_LONG_64}),
 * whose answers are definitive so they can drop LMDB.
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
@ToString
public final class BinaryFuseAccelerator implements AddressPresence {

    private final @NonNull AddressPresence filter;
    private final @NonNull AddressPresence delegate;

    /**
     * Creates a new accelerator.
     *
     * @param filter   the pre-populated Binary Fuse filter consulted first
     * @param delegate the exact lookup consulted to disambiguate filter positives
     */
    public BinaryFuseAccelerator(@NonNull AddressPresence filter, @NonNull AddressPresence delegate) {
        this.filter = filter;
        this.delegate = delegate;
    }

    /**
     * Returns {@code true} only when the filter reports a hit <em>and</em> the exact delegate
     * confirms the address. A filter miss short-circuits and never touches the delegate.
     *
     * @param hash160 the address hash to look up; its position/limit are restored by both the
     *                filter and the delegate per the {@link AddressPresence} contract
     * @return {@code true} if the address is confirmed present by the delegate
     */
    @Override
    public boolean containsAddress(ByteBuffer hash160) {
        if (!filter.containsAddress(hash160)) {
            return false;
        }
        return delegate.containsAddress(hash160);
    }

    /**
     * A Binary Fuse filter is probabilistic: a filter hit may be a false positive that must be
     * disambiguated against the delegate, so the backing storage must remain open.
     *
     * @return {@code true} - the delegate must always be available
     */
    @Override
    public boolean requiresBackend() {
        return true;
    }

    /**
     * Returns the GPU VRAM-upload payload of the wrapped filter, if it is a
     * {@link BinaryFuse8AddressPresence}.
     *
     * @return the Binary Fuse 8 GPU-upload payload, or {@link Optional#empty()} if the wrapped
     *     filter is not a {@link BinaryFuse8AddressPresence} (e.g. the 16-bit variant, which has
     *     no GPU path)
     */
    public Optional<BinaryFuse8GpuFilterData> getGpuFilterData() {
        if (filter instanceof BinaryFuse8AddressPresence fuse8) {
            return Optional.of(fuse8.toGpuFilterData());
        }
        return Optional.empty();
    }
}
