// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.nio.ByteBuffer;
import lombok.ToString;
import org.jspecify.annotations.NonNull;

/**
 * Base class for read-only accelerators that place a <em>probabilistic</em> filter in front of an
 * exact {@link AddressPresence} delegate (typically the LMDB read store).
 *
 * <p>A probabilistic filter (Bloom, Binary Fuse, …) has <em>no false negatives</em> but a non-zero
 * <em>false-positive</em> rate, so a filter "maybe present" answer can never be reported as a final
 * hit on the CPU — it must be verified against the exact delegate. This base hard-codes that
 * contract so every concrete accelerator gets it for free and cannot get it wrong:
 *
 * <ul>
 *   <li>a filter <em>miss</em> is definitive — the address is absent and the delegate is never
 *       consulted (the fast, overwhelmingly common path during a key scan);</li>
 *   <li>a filter <em>hit</em> is only probable — the call falls through to the exact delegate to
 *       confirm the address or reject it as a false positive.</li>
 * </ul>
 *
 * <p>Because positives must be disambiguated against the delegate, {@link #requiresBackend()}
 * always returns {@code true}: the backing storage must stay open for the lifetime of the
 * accelerator. Both {@link #containsAddress(ByteBuffer)} and {@link #requiresBackend()} are
 * {@code final} — a subclass only supplies the filter probe via {@link #mightContain(ByteBuffer)},
 * so it is structurally impossible for a probabilistic accelerator to (a) report an unverified hit
 * or (b) claim it does not need its backend.
 *
 * @param <D> the delegate type; {@link AddressPresence} is enough for presence-only filters, while
 *     value-carrying accelerators (e.g. a Bloom filter that also answers {@code getAmount}) use a
 *     wider type such as {@link AddressLookup} and reach it via {@link #delegate()}
 */
@ToString
public abstract class AbstractFilterAccelerator<D extends AddressPresence> implements AddressPresence {

    private final @NonNull D delegate;

    /**
     * Creates an accelerator wrapping the given exact delegate.
     *
     * @param delegate the exact lookup consulted to disambiguate filter positives
     */
    protected AbstractFilterAccelerator(@NonNull D delegate) {
        this.delegate = delegate;
    }

    /**
     * Returns the exact delegate, for subclasses that expose additional delegate-backed operations
     * (e.g. value lookups).
     *
     * @return the wrapped delegate
     */
    protected final @NonNull D delegate() {
        return delegate;
    }

    /**
     * Probes the probabilistic filter. Implementations must not consult the delegate here.
     *
     * @param hash160 the address hash to test; its position/limit must be restored before return
     * @return {@code true} if the filter reports the address as <em>possibly</em> present
     */
    protected abstract boolean mightContain(ByteBuffer hash160);

    /**
     * Returns {@code true} only when the filter reports a hit <em>and</em> the exact delegate
     * confirms the address. A filter miss short-circuits and never touches the delegate.
     *
     * @param hash160 the address hash to look up; its position/limit are restored by both the
     *                filter probe and the delegate per the {@link AddressPresence} contract
     * @return {@code true} if the address is confirmed present by the delegate
     */
    @Override
    public final boolean containsAddress(ByteBuffer hash160) {
        if (!mightContain(hash160)) {
            return false;
        }
        return delegate.containsAddress(hash160);
    }

    /**
     * A probabilistic filter hit may be a false positive that must be disambiguated against the
     * delegate, so the backing storage must remain open.
     *
     * @return {@code true} - the delegate must always be available
     */
    @Override
    public final boolean requiresBackend() {
        return true;
    }
}
