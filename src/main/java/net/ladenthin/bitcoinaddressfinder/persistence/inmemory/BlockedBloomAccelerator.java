// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.nio.ByteBuffer;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.persistence.AbstractFilterAccelerator;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import org.jspecify.annotations.NonNull;

/**
 * Read-only accelerator that places a {@link BlockedBloomAddressPresence} in front of an exact
 * {@link AddressPresence} delegate (typically the LMDB read store).
 *
 * <p>Like the Bloom and Binary Fuse accelerators, the blocked Bloom filter has <em>no false
 * negatives</em> but a non-negligible <em>false-positive</em> rate (measured 0.18&nbsp;% at the
 * Light DB tier, 0.49&nbsp;% at the Full DB tier), so a filter positive must be verified against the exact delegate. The verify-on-hit
 * behaviour and the {@code requiresBackend() == true} contract are inherited from
 * {@link AbstractFilterAccelerator}; this class only supplies the filter probe.
 *
 * <p>The blocked Bloom filter's advantage over the Binary Fuse filters is that it is built in a
 * single streaming pass (peak build memory ≈ the filter itself — measured 2.0&nbsp;GiB for
 * 1.377&nbsp;B entries), so it scales to the full database tier where the fuse construction's ~42&nbsp;GB peak does not fit.
 *
 * <h2>Concurrency</h2>
 * Thread-safe for concurrent reads as long as the wrapped filter and delegate are.
 */
@ToString(callSuper = true)
public final class BlockedBloomAccelerator extends AbstractFilterAccelerator<AddressPresence> {

    private final @NonNull BlockedBloomAddressPresence filter;

    /**
     * Creates a new accelerator.
     *
     * @param filter   the pre-populated blocked Bloom filter consulted first
     * @param delegate the exact lookup consulted to disambiguate filter positives
     */
    public BlockedBloomAccelerator(@NonNull BlockedBloomAddressPresence filter, @NonNull AddressPresence delegate) {
        super(delegate);
        this.filter = filter;
    }

    /**
     * Probes the blocked Bloom filter; no false negatives, so a miss is definitive.
     *
     * @param hash160 the address hash to test (position/limit preserved by the filter)
     * @return {@code true} if the filter reports the address as possibly present
     */
    @Override
    protected boolean mightContain(ByteBuffer hash160) {
        return filter.containsAddress(hash160);
    }

    /**
     * Returns the wrapped blocked Bloom filter (for GPU VRAM upload and tests).
     *
     * @return the wrapped filter
     */
    public BlockedBloomAddressPresence getFilter() {
        return filter;
    }
}
