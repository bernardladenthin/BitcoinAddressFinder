// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;

/**
 * Shared test helpers for the in-memory presence backends.
 */
final class InMemoryTestSupport {

    private InMemoryTestSupport() {}

    /** Builds a deterministic 20-byte hash160 for testing - first byte from {@code firstByte}. */
    static ByteBuffer hash20(int firstByte, int tailSeed) {
        byte[] bytes = new byte[20];
        bytes[0] = (byte) firstByte;
        bytes[1] = (byte) (tailSeed >> 24);
        bytes[2] = (byte) (tailSeed >> 16);
        bytes[3] = (byte) (tailSeed >> 8);
        bytes[4] = (byte) tailSeed;
        return ByteBuffer.wrap(bytes);
    }

    /** A reusable in-memory {@link AddressIterable} backed by a list of byte snapshots. */
    static final class ListIterable implements AddressIterable {
        private final List<byte[]> entries = new ArrayList<>();

        ListIterable add(ByteBuffer bb) {
            byte[] copy = new byte[bb.remaining()];
            bb.duplicate().get(copy);
            entries.add(copy);
            return this;
        }

        @Override
        public Stream<ByteBuffer> addresses() {
            // Each call returns a fresh stream view; entries are immutable snapshots.
            return entries.stream().map(ByteBuffer::wrap);
        }

        @Override
        public long count() {
            return entries.size();
        }
    }
}
