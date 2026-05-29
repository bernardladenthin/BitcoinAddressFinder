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

class TruncatedLong64SortedArrayPresenceTest {

    @Test
    void empty_doesNotContainAnything() {
        TruncatedLong64SortedArrayPresence presence =
                TruncatedLong64SortedArrayPresence.populateFrom(new ListIterable());
        assertThat(presence.containsAddress(hash20(0x42, 1)), is(false));
        assertThat(presence.size(), is(equalTo(0L)));
    }

    @Test
    void populated_containsKnown_doesNotContainAbsent_acrossBuckets() {
        ListIterable src = new ListIterable()
                .add(hash20(0x00, 1))
                .add(hash20(0x00, 2))
                .add(hash20(0x42, 1))
                .add(hash20(0x42, 99))
                .add(hash20(0xFF, 7));
        TruncatedLong64SortedArrayPresence presence = TruncatedLong64SortedArrayPresence.populateFrom(src);

        assertThat(presence.size(), is(equalTo(5L)));
        assertThat(presence.containsAddress(hash20(0x00, 1)), is(true));
        assertThat(presence.containsAddress(hash20(0x00, 2)), is(true));
        assertThat(presence.containsAddress(hash20(0x42, 1)), is(true));
        assertThat(presence.containsAddress(hash20(0x42, 99)), is(true));
        assertThat(presence.containsAddress(hash20(0xFF, 7)), is(true));

        // Different tail in occupied bucket
        assertThat(presence.containsAddress(hash20(0x00, 3)), is(false));
        assertThat(presence.containsAddress(hash20(0x42, 100)), is(false));
        // Empty bucket
        assertThat(presence.containsAddress(hash20(0xAB, 1)), is(false));
    }

    @Test
    void requiresBackend_isFalse() {
        TruncatedLong64SortedArrayPresence presence =
                TruncatedLong64SortedArrayPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        assertThat(presence.requiresBackend(), is(false));
    }

    @Test
    void containsAddress_doesNotMutateCallerBuffer() {
        TruncatedLong64SortedArrayPresence presence =
                TruncatedLong64SortedArrayPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        ByteBuffer query = hash20(0x10, 7);
        int posBefore = query.position();
        int limitBefore = query.limit();
        presence.containsAddress(query);
        assertThat(query.position(), is(equalTo(posBefore)));
        assertThat(query.limit(), is(equalTo(limitBefore)));
    }

    @Test
    void wrongLengthBuffer_returnsFalse() {
        TruncatedLong64SortedArrayPresence presence =
                TruncatedLong64SortedArrayPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        ByteBuffer notHash160 = ByteBuffer.wrap(new byte[19]);
        assertThat(presence.containsAddress(notHash160), is(false));
    }

    @Test
    void singleEntryBucket_hitsAndMisses() {
        TruncatedLong64SortedArrayPresence presence =
                TruncatedLong64SortedArrayPresence.populateFrom(new ListIterable().add(hash20(0x80, 42)));
        assertThat(presence.containsAddress(hash20(0x80, 42)), is(true));
        assertThat(presence.containsAddress(hash20(0x80, 43)), is(false));
    }

    @Test
    void manyEntriesInOneBucket_orderingIsRobust() {
        // Insert in shuffled order; binary search must still find each entry.
        ListIterable src = new ListIterable();
        int[] order = {15, 2, 9, 4, 11, 1, 13, 7, 6, 14, 3, 12, 8, 5, 10};
        for (int seed : order) {
            src.add(hash20(0x55, seed));
        }
        TruncatedLong64SortedArrayPresence presence = TruncatedLong64SortedArrayPresence.populateFrom(src);
        for (int seed = 1; seed <= 15; seed++) {
            assertThat("seed " + seed, presence.containsAddress(hash20(0x55, seed)), is(true));
        }
        assertThat(presence.containsAddress(hash20(0x55, 16)), is(false));
    }

    @Test
    void differentFirstByteWithSameTailBits_distinguishedByBucket() {
        // Two hash160s that share the same bytes[1..8] but differ in byte[0] must land in
        // different buckets, so each is found only via its own bucket.
        ListIterable src = new ListIterable().add(hash20(0x10, 123)).add(hash20(0x20, 123));
        TruncatedLong64SortedArrayPresence presence = TruncatedLong64SortedArrayPresence.populateFrom(src);
        assertThat(presence.containsAddress(hash20(0x10, 123)), is(true));
        assertThat(presence.containsAddress(hash20(0x20, 123)), is(true));
        // A first byte that was never inserted misses, even if the tail collides
        assertThat(presence.containsAddress(hash20(0x99, 123)), is(false));
    }
}
