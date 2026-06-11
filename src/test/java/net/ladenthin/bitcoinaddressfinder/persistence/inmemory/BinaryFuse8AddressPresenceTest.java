// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.hash20;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.ListIterable;
import org.junit.jupiter.api.Test;

class BinaryFuse8AddressPresenceTest {

    @Test
    void empty_doesNotContainAnything() {
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(new ListIterable());
        assertThat(presence.containsAddress(hash20(0x42, 1)), is(false));
        assertThat(presence.slotCount(), is(equalTo(0)));
    }

    @Test
    void populated_containsAllKnown_noFalseNegatives() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 50; i++) {
            src.add(hash20(0, i));
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);
        for (int i = 1; i <= 50; i++) {
            assertThat("seed " + i, presence.containsAddress(hash20(0, i)), is(true));
        }
    }

    @Test
    void populated_falsePositiveRateBelowTwoPercent() {
        ListIterable src = new ListIterable();
        for (int firstByte = 0; firstByte < 8; firstByte++) {
            for (int seed = 1; seed <= 250; seed++) {
                src.add(hash20(firstByte, seed));
            }
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);

        int falsePositives = 0;
        int queries = 20000;
        for (int i = 0; i < queries; i++) {
            int firstByte = i % 8;
            int seed = 10001 + (i / 8);
            if (presence.containsAddress(hash20(firstByte, seed))) {
                falsePositives++;
            }
        }
        assertThat(falsePositives, is(lessThan((int) (queries * 0.02))));
    }

    @Test
    void requiresBackend_isFalse() {
        BinaryFuse8AddressPresence presence =
                BinaryFuse8AddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        assertThat(presence.requiresBackend(), is(false));
    }

    @Test
    void containsAddress_doesNotMutateCallerBuffer() {
        BinaryFuse8AddressPresence presence =
                BinaryFuse8AddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        ByteBuffer query = hash20(0x10, 7);
        int posBefore = query.position();
        int limitBefore = query.limit();
        presence.containsAddress(query);
        assertThat(query.position(), is(equalTo(posBefore)));
        assertThat(query.limit(), is(equalTo(limitBefore)));
    }

    @Test
    void wrongLengthBuffer_returnsFalse() {
        BinaryFuse8AddressPresence presence =
                BinaryFuse8AddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        ByteBuffer notHash160 = ByteBuffer.wrap(new byte[19]);
        assertThat(presence.containsAddress(notHash160), is(false));
    }

    @Test
    void singleEntry_hitsAndMisses() {
        BinaryFuse8AddressPresence presence =
                BinaryFuse8AddressPresence.populateFrom(new ListIterable().add(hash20(0x80, 42)));
        assertThat(presence.containsAddress(hash20(0x80, 42)), is(true));
        assertThat(presence.containsAddress(hash20(0x80, 43)), is(false));
    }

    @Test
    void manyEntriesInSingleBucket_allFound() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 20; i++) {
            src.add(hash20(0x55, i));
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);
        for (int i = 1; i <= 20; i++) {
            assertThat("seed " + i, presence.containsAddress(hash20(0x55, i)), is(true));
        }
    }

    @Test
    void hash64_isDistributed() {
        long h1 = BinaryFuse8AddressPresence.hash64(0x1234567890ABCDEFL, 0L);
        long h2 = BinaryFuse8AddressPresence.hash64(0x1234567890ABCDEFL, 1L);
        assertThat(h1 == h2, is(false));
        long h3 = BinaryFuse8AddressPresence.hash64(0L, 0L);
        long h4 = BinaryFuse8AddressPresence.hash64(1L, 0L);
        assertThat(h3 == h4, is(false));
    }

    @Test
    void reduce_mapsUniformly() {
        assertThat(BinaryFuse8AddressPresence.reduce(0, 100), is(equalTo(0)));
        assertThat(BinaryFuse8AddressPresence.reduce(Integer.MAX_VALUE, 100), is(lessThan(100)));
        assertThat(BinaryFuse8AddressPresence.reduce(-1, 100), is(lessThan(100)));
    }

    @Test
    void fingerprint8_knownValues() {
        // h=0: (byte)(0 ^ 0) = 0
        assertThat(BinaryFuse8AddressPresence.fingerprint8(0L), is(equalTo((byte) 0)));
        // h=0x0000000100000002L: h ^ (h>>>32) = 3L; (byte)3 = 3
        assertThat(BinaryFuse8AddressPresence.fingerprint8(0x0000000100000002L), is(equalTo((byte) 3)));
        // h=0x0000000100000100L: h ^ (h>>>32) = 0x0000000100000101L; (byte) = 1
        // fingerprint16 of same input gives (short)0x0101 = 257 — verifies the narrowing cast matters
        assertThat(BinaryFuse8AddressPresence.fingerprint8(0x0000000100000100L), is(equalTo((byte) 1)));
    }

    @Test
    void largePopulation_allFound() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 200; i++) {
            src.add(hash20(i % 256, i));
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);
        for (int i = 1; i <= 200; i++) {
            assertThat("entry " + i, presence.containsAddress(hash20(i % 256, i)), is(true));
        }
    }

    @Test
    void exactlyThreeEntries_allFound() {
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(
                new ListIterable().add(hash20(0xAA, 1)).add(hash20(0xBB, 2)).add(hash20(0xCC, 3)));
        assertThat(presence.containsAddress(hash20(0xAA, 1)), is(true));
        assertThat(presence.containsAddress(hash20(0xBB, 2)), is(true));
        assertThat(presence.containsAddress(hash20(0xCC, 3)), is(true));
    }

    @Test
    void slotCount_isAtLeastN() {
        int n = 50;
        ListIterable src = new ListIterable();
        for (int i = 1; i <= n; i++) {
            src.add(hash20(i % 256, i));
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);
        assertThat(presence.slotCount(), is(greaterThanOrEqualTo(n)));
    }

    @Test
    void twoIndependentFilters_doNotShareState() {
        BinaryFuse8AddressPresence a = BinaryFuse8AddressPresence.populateFrom(new ListIterable().add(hash20(0x01, 1)));
        BinaryFuse8AddressPresence b = BinaryFuse8AddressPresence.populateFrom(new ListIterable().add(hash20(0x02, 2)));
        assertThat(a.containsAddress(hash20(0x01, 1)), is(true));
        assertThat(a.containsAddress(hash20(0x02, 2)), is(false));
        assertThat(b.containsAddress(hash20(0x02, 2)), is(true));
        assertThat(b.containsAddress(hash20(0x01, 1)), is(false));
    }

    @Test
    void idempotentLookup_sameAnswerOnRepeatedQuery() {
        BinaryFuse8AddressPresence presence =
                BinaryFuse8AddressPresence.populateFrom(new ListIterable().add(hash20(0x33, 99)));
        boolean first = presence.containsAddress(hash20(0x33, 99));
        boolean second = presence.containsAddress(hash20(0x33, 99));
        assertThat(first, is(true));
        assertThat(second, is(equalTo(first)));
    }

    @Test
    void containsAddress_emptyByteBuffer_returnsFalse() {
        BinaryFuse8AddressPresence presence =
                BinaryFuse8AddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        assertThat(presence.containsAddress(ByteBuffer.wrap(new byte[0])), is(false));
    }
}
