// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import org.jspecify.annotations.NonNull;

/**
 * Self-contained presence-only snapshot backed by a {@link HashSet} of read-only
 * {@link ByteBuffer} entries.
 *
 * <p>Once populated this class holds no reference to its source, so the source backend
 * (LMDB env + mmap) can be closed and garbage collected. {@link #requiresBackend()}
 * returns {@code false}.
 *
 * <p>Memory cost rough estimate: ~80 bytes per entry (HashSet node + ByteBuffer wrapper
 * + 20-byte payload). For comparison, a sorted-array snapshot reaches ~20 bytes/entry.
 * Choose this class for smallish address sets where O(1) average lookup latency
 * matters more than memory footprint.
 *
 * <p>Lookups are zero-allocation: {@link Set#contains} on a passed {@link ByteBuffer}
 * does not allocate. Mutation of stored buffers is prevented at construction time by
 * snapshotting each source buffer into a fresh read-only {@code ByteBuffer}.
 *
 * <p>The class is thread-safe for concurrent reads after construction. It is not
 * thread-safe for concurrent mutation (no mutation API is exposed).
 *
 * <p><b>equals/hashCode cost.</b> The Lombok-generated equals iterates the underlying
 * {@link Set#equals(Object) Set.equals}, which is O(N) over the stored entries (potentially
 * millions). The annotation is applied to satisfy the value-equality contract for any
 * future caller (e.g. test fixtures comparing two snapshots), not because production code
 * routinely compares two presence snapshots. Do NOT use instances of this class as Map
 * keys at scan time.
 */
@ToString
@EqualsAndHashCode
public final class HashSetAddressPresence implements AddressPresence {

    // Potentially millions of ByteBuffer entries — toString'ing the whole Set would be
    // log-killing. The size() getter is included instead (see @ToString.Include below).
    @ToString.Exclude
    private final @NonNull Set<ByteBuffer> entries;

    private HashSetAddressPresence(@NonNull Set<ByteBuffer> entries) {
        this.entries = entries;
    }

    /**
     * Builds a presence snapshot by streaming every address from {@code source} into a
     * {@link HashSet}. After this returns the source reference is dropped; callers may
     * close and discard the source backend so it becomes eligible for garbage
     * collection.
     *
     * @param source the address set to materialise
     * @return a fully populated, self-contained presence lookup
     */
    public static HashSetAddressPresence populateFrom(@NonNull AddressIterable source) {
        long expected = source.count();
        int initialCapacity = (int) Math.min(Integer.MAX_VALUE >> 1, Math.max(16L, expected));
        Set<ByteBuffer> set = new HashSet<>(initialCapacity);
        try (Stream<ByteBuffer> stream = source.addresses()) {
            stream.forEach(bb -> set.add(snapshot(bb)));
        }
        return new HashSetAddressPresence(set);
    }

    /**
     * Copies the remaining bytes of {@code src} into a fresh read-only {@link ByteBuffer}
     * so the stored entry cannot be mutated by callers that retain the source buffer.
     */
    private static ByteBuffer snapshot(@NonNull ByteBuffer src) {
        ByteBuffer copy = ByteBuffer.allocate(src.remaining());
        copy.put(src.duplicate());
        copy.flip();
        return copy.asReadOnlyBuffer();
    }

    @Override
    public boolean containsAddress(ByteBuffer hash160) {
        // Set.contains uses ByteBuffer.equals/hashCode which are defined over the
        // remaining region and do not mutate the buffer. Zero allocations here.
        return entries.contains(hash160);
    }

    @Override
    public boolean requiresBackend() {
        return false;
    }

    /**
     * Returns the number of distinct addresses in the snapshot.
     *
     * @return entry count
     */
    @ToString.Include
    public int size() {
        return entries.size();
    }
}
