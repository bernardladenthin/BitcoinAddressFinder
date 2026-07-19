// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.nio.ByteBuffer;

/**
 * Minimal "is this address present?" contract.
 *
 * <p>This is the smallest stable seam used by the scan/consumer hot path. Lookups that
 * carry value information additionally implement {@link AddressLookup}; the scan path
 * never needs values, so a presence-only structure (e.g. a HashSet or sorted-array
 * snapshot of the address set) is sufficient and significantly cheaper than a full
 * key&#x2192;value backend.
 *
 * <p>Two flavours of {@code AddressPresence} exist:
 *
 * <ol>
 *   <li><b>Decorators</b> that keep a reference to a backing delegate and consult it
 *       when their own structure cannot answer definitively. Bloom filters are the
 *       canonical example: {@code mightContain == true} can be a false positive, so the
 *       call must fall through to the backend. Decorators return {@code true} from
 *       {@link #requiresBackend()}.
 *   <li><b>Self-contained replacements</b> that have been populated from a source once
 *       and dropped the reference. HashSet and sorted-array snapshots are exact, so a
 *       contains-answer is definitive. The original backend can be closed and garbage
 *       collected after population. Replacements return {@code false} from
 *       {@link #requiresBackend()}.
 * </ol>
 *
 * <p>The orchestrator that wires the chain consults {@link #requiresBackend()} after
 * construction to decide whether the backing storage (LMDB env + mmap) can be released
 * immediately. This is the only mechanism that lets the on-disk store be unloaded
 * once a self-contained in-memory snapshot has been built.
 */
public interface AddressPresence {

    /**
     * Checks whether the given address is present.
     *
     * @param hash160 the address hash to look up; the caller's position/limit is restored
     *                before this method returns
     * @return {@code true} if the address is definitely present
     */
    boolean containsAddress(ByteBuffer hash160);

    /**
     * Indicates whether this presence lookup needs its backing storage to remain open.
     *
     * <p>Decorators (e.g. {@code BloomFilterAccelerator}) return {@code true}: the
     * backend must be available to disambiguate false positives.
     *
     * <p>Self-contained replacements (HashSet, sorted-array, full in-memory copy) return
     * {@code false}: they answer definitively from their own structure and the source
     * may be closed and garbage collected.
     *
     * @return {@code true} if this lookup must keep its backing delegate open
     */
    boolean requiresBackend();

    /**
     * Exact size of the in-memory structure in bytes, or {@code -1} when this backend cannot report
     * one.
     *
     * <p>Reported by the backend itself rather than estimated, because the obvious alternative —
     * measuring used heap around construction — is unreliable at the sizes that matter: it also
     * captures whatever else the process holds, and has been observed reporting 26&nbsp;MiB for a
     * 16&nbsp;MiB array. Choosing a filter is a trade between memory, lookup speed and
     * false-positive rate, so the memory term has to be exact for the comparison to mean anything.
     *
     * <p>Backends whose footprint is dominated by per-object overhead rather than a flat array (for
     * example a {@code HashSet} of boxed buffers) return {@code -1} instead of guessing.
     *
     * @return the structure size in bytes, or {@code -1} if unknown
     */
    default long sizeInBytes() {
        return -1L;
    }
}
