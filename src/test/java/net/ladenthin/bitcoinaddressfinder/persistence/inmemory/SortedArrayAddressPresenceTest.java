// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.hash20;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.ListIterable;
import org.junit.jupiter.api.Test;

class SortedArrayAddressPresenceTest {

    @Test
    void empty_doesNotContainAnything() {
        SortedArrayAddressPresence presence = SortedArrayAddressPresence.populateFrom(new ListIterable());
        assertThat(presence.containsAddress(hash20(0x42, 1)), is(false));
        assertThat(presence.size(), is(equalTo(0L)));
    }

    @Test
    void populated_containsKnown_doesNotContainAbsent_acrossBuckets() {
        // Entries spread across three different first-byte buckets (0x00, 0x42, 0xFF)
        ListIterable src = new ListIterable()
                .add(hash20(0x00, 1))
                .add(hash20(0x00, 2))
                .add(hash20(0x42, 1))
                .add(hash20(0x42, 99))
                .add(hash20(0xFF, 7));
        SortedArrayAddressPresence presence = SortedArrayAddressPresence.populateFrom(src);

        assertThat(presence.size(), is(equalTo(5L)));
        assertThat(presence.containsAddress(hash20(0x00, 1)), is(true));
        assertThat(presence.containsAddress(hash20(0x00, 2)), is(true));
        assertThat(presence.containsAddress(hash20(0x42, 1)), is(true));
        assertThat(presence.containsAddress(hash20(0x42, 99)), is(true));
        assertThat(presence.containsAddress(hash20(0xFF, 7)), is(true));
        // Absent values in occupied and empty buckets
        assertThat(presence.containsAddress(hash20(0x00, 3)), is(false));
        assertThat(presence.containsAddress(hash20(0x42, 100)), is(false));
        assertThat(presence.containsAddress(hash20(0xAB, 1)), is(false));
    }

    @Test
    void requiresBackend_isFalse() {
        SortedArrayAddressPresence presence =
                SortedArrayAddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        assertThat(presence.requiresBackend(), is(false));
    }

    @Test
    void containsAddress_doesNotMutateCallerBuffer() {
        SortedArrayAddressPresence presence =
                SortedArrayAddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        ByteBuffer query = hash20(0x10, 7);
        int posBefore = query.position();
        int limitBefore = query.limit();
        presence.containsAddress(query);
        assertThat(query.position(), is(equalTo(posBefore)));
        assertThat(query.limit(), is(equalTo(limitBefore)));
    }

    @Test
    void wrongLengthBuffer_returnsFalse() {
        SortedArrayAddressPresence presence =
                SortedArrayAddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        ByteBuffer notHash160 = ByteBuffer.wrap(new byte[19]);
        assertThat(presence.containsAddress(notHash160), is(false));
    }

    @Test
    void singleEntryBucket_lookupHitsAndMisses() {
        // A bucket with only one entry exercises the binary-search base case.
        SortedArrayAddressPresence presence =
                SortedArrayAddressPresence.populateFrom(new ListIterable().add(hash20(0x80, 42)));
        assertThat(presence.containsAddress(hash20(0x80, 42)), is(true));
        assertThat(presence.containsAddress(hash20(0x80, 43)), is(false));
    }

    @Test
    void manyEntriesInOneBucket_remainSorted() {
        // Insert in shuffled order; lookup must still find each entry.
        ListIterable src = new ListIterable();
        int[] order = {15, 2, 9, 4, 11, 1, 13, 7, 6, 14, 3, 12, 8, 5, 10};
        for (int seed : order) {
            src.add(hash20(0x55, seed));
        }
        SortedArrayAddressPresence presence = SortedArrayAddressPresence.populateFrom(src);
        for (int seed = 1; seed <= 15; seed++) {
            assertThat("seed " + seed, presence.containsAddress(hash20(0x55, seed)), is(true));
        }
        assertThat(presence.containsAddress(hash20(0x55, 16)), is(false));
    }
}
