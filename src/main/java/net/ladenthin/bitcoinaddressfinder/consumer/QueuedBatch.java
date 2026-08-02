// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.consumer;

import java.math.BigInteger;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import org.jspecify.annotations.Nullable;

/**
 * One producer batch on its way through the consumer queue.
 *
 * <p>Exists so the batch keeps its identity all the way to the check. A producer already hands over
 * exactly one array per batch, so the boundary was always there — it simply had nothing attached to
 * it, which is why the outcome could not be reported per range.
 *
 * <p>Deliberately a plain class rather than a record: the candidate array holds up to
 * {@code 2^batchSizeInBits} entries, and a record would either compare it by reference (misleading)
 * or force a defensive copy of a multi-megabyte array on the hot path. This type is only ever moved
 * through a queue and never compared, so identity semantics are exactly right.
 */
@ToString
final class QueuedBatch {

    @ToString.Exclude
    private final PublicKeyBytes[] keys;

    private final @Nullable BigInteger secretBase;

    /**
     * Creates a queued batch.
     *
     * @param keys       the candidates of this batch, taken over as-is
     * @param secretBase the aligned start of the swept grid, or {@code null} when the batch is a set
     *                   of independent secrets rather than one expanded range
     */
    QueuedBatch(PublicKeyBytes[] keys, @Nullable BigInteger secretBase) {
        this.keys = keys;
        this.secretBase = secretBase;
    }

    /**
     * Returns the candidates of this batch.
     *
     * @return the candidate array, not copied
     */
    PublicKeyBytes[] keys() {
        return keys;
    }

    /**
     * Returns the aligned start of the swept grid.
     *
     * @return the base, or {@code null} when this batch has none
     */
    @Nullable
    BigInteger secretBase() {
        return secretBase;
    }
}
