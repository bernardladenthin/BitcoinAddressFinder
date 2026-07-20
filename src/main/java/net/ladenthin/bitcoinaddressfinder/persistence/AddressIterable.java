// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Optional capability declared by backends that can stream their full address set, used
 * once by accelerator factories to populate self-contained in-memory snapshots
 * (HashSet, sorted-array). Decoupled from {@link AddressLookup} so accelerators stay
 * backend-agnostic.
 *
 * <p>{@link #addresses()} returns a fresh {@link Stream} on each call. Implementations
 * MUST attach a close hook that releases their underlying iteration resources (e.g.
 * LMDB read transaction + cursor) when the stream is closed; callers MUST use
 * try-with-resources around the returned stream.
 *
 * <p>Each emitted {@link ByteBuffer} is logically a snapshot of one hash160 entry. The
 * underlying buffer may be a view that the cursor reuses for the next iteration, so
 * consumers MUST copy the bytes out before advancing the stream.
 *
 * <p>{@link #addresses()} may be called multiple times - this is essential for the
 * sorted-array accelerator which does one pass for per-bucket counting and a second
 * pass for filling.
 */
public interface AddressIterable {

    /**
     * Streams all hash160 entries in the backing storage. The stream must be closed by
     * the caller (try-with-resources); closing releases the underlying iteration
     * resources.
     *
     * <p>Returned buffers may be cursor-owned views; copy them before advancing the
     * stream.
     *
     * @return a fresh stream over all stored hash160 entries
     */
    Stream<ByteBuffer> addresses();

    /**
     * Returns the number of entries the backing storage currently holds. Used to size
     * the in-memory snapshot allocated by the accelerator factory.
     *
     * @return the entry count
     */
    long count();

    /**
     * Applies {@code action} to every hash160 entry. The default implementation streams via
     * {@link #addresses()}; backends with a lower-overhead bulk iteration (e.g. a raw LMDB cursor,
     * avoiding the {@code Stream}/{@code Spliterator}/{@code Iterator} per-entry dispatch) should
     * override this for large-database throughput.
     *
     * <p>Each supplied {@link ByteBuffer} may be a cursor-owned view valid only until {@code action}
     * returns — read what you need immediately, do not retain the buffer.
     *
     * @param action the per-entry action
     */
    default void forEachAddress(Consumer<ByteBuffer> action) {
        try (Stream<ByteBuffer> stream = addresses()) {
            stream.forEach(action);
        }
    }
}
