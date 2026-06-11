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

class BinaryFuse16AddressPresenceTest {

    @Test
    void empty_doesNotContainAnything() {
        BinaryFuse16AddressPresence presence =
                BinaryFuse16AddressPresence.populateFrom(new ListIterable());
        assertThat(presence.containsAddress(hash20(0x42, 1)), is(false));
        assertThat(presence.slotCount(), is(equalTo(0)));
    }

    @Test
    void populated_containsAllKnown_noFalseNegatives() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 50; i++) {
            src.add(hash20(0, i));
        }
        BinaryFuse16AddressPresence presence = BinaryFuse16AddressPresence.populateFrom(src);
        for (int i = 1; i <= 50; i++) {
            assertThat("seed " + i, presence.containsAddress(hash20(0, i)), is(true));
        }
    }

    @Test
    void populated_falsePositiveRateBelowZeroPointOnePercent() {
        ListIterable src = new ListIterable();
        for (int firstByte = 0; firstByte < 8; firstByte++) {
            for (int seed = 1; seed <= 250; seed++) {
                src.add(hash20(firstByte, seed));
            }
        }
        BinaryFuse16AddressPresence presence = BinaryFuse16AddressPresence.populateFrom(src);

        int falsePositives = 0;
        int queries = 20000;
        for (int i = 0; i < queries; i++) {
            int firstByte = i % 8;
            int seed = 10001 + (i / 8);
            if (presence.containsAddress(hash20(firstByte, seed))) {
                falsePositives++;
            }
        }
        assertThat(falsePositives, is(lessThan((int) (queries * 0.001))));
    }

    @Test
    void requiresBackend_isFalse() {
        BinaryFuse16AddressPresence presence =
                BinaryFuse16AddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        assertThat(presence.requiresBackend(), is(false));
    }

    @Test
    void containsAddress_doesNotMutateCallerBuffer() {
        BinaryFuse16AddressPresence presence =
                BinaryFuse16AddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        ByteBuffer query = hash20(0x10, 7);
        int posBefore = query.position();
        int limitBefore = query.limit();
        presence.containsAddress(query);
        assertThat(query.position(), is(equalTo(posBefore)));
        assertThat(query.limit(), is(equalTo(limitBefore)));
    }

    @Test
    void wrongLengthBuffer_returnsFalse() {
        BinaryFuse16AddressPresence presence =
                BinaryFuse16AddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        ByteBuffer notHash160 = ByteBuffer.wrap(new byte[19]);
        assertThat(presence.containsAddress(notHash160), is(false));
    }

    @Test
    void singleEntry_hitsAndMisses() {
        BinaryFuse16AddressPresence presence =
                BinaryFuse16AddressPresence.populateFrom(new ListIterable().add(hash20(0x80, 42)));
        assertThat(presence.containsAddress(hash20(0x80, 42)), is(true));
        assertThat(presence.containsAddress(hash20(0x80, 43)), is(false));
    }

    @Test
    void manyEntriesInSingleBucket_allFound() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 20; i++) {
            src.add(hash20(0x55, i));
        }
        BinaryFuse16AddressPresence presence = BinaryFuse16AddressPresence.populateFrom(src);
        for (int i = 1; i <= 20; i++) {
            assertThat("seed " + i, presence.containsAddress(hash20(0x55, i)), is(true));
        }
    }

    @Test
    void fingerprint16_knownValues() {
        // h=0: 0L ^ (0L >>> 32) = 0L; (short) 0L = 0
        assertThat(BinaryFuse16AddressPresence.fingerprint16(0L), is(equalTo((short) 0)));

        // h=0x0000000100000002L: high=1, low=2; h^(h>>>32) = 0x0000000100000002L ^ 0x01L = 0x0000000100000003L
        // (short) of that = (short) 3
        assertThat(BinaryFuse16AddressPresence.fingerprint16(0x0000000100000002L), is(equalTo((short) 3)));

        // Verify fingerprint16 produces a different result than casting fingerprint8 for the same input
        // fingerprint8(x) = (byte)(x ^ (x>>>32)); fingerprint16(x) = (short)(x ^ (x>>>32))
        // For x=0x0000000100000002L: byte cast = (byte)3 = 3; short cast = (short)3 = 3 — same low bits here
        // Use a value where the upper byte of the short differs from fingerprint8's single byte
        // x = 0x0000000100000100L: h ^ (h>>>32) = 0x0000000100000100L ^ 0x1L = 0x0000000100000101L; (short)=0x0101=257
        // (byte)0x0101 = 1; so fingerprint8 gives 1, fingerprint16 gives 257
        short fp16 = BinaryFuse16AddressPresence.fingerprint16(0x0000000100000100L);
        assertThat(fp16, is(equalTo((short) 0x0101)));
    }

    @Test
    void reduce_mapsUniformly() {
        assertThat(BinaryFuse16AddressPresence.reduce(0, 100), is(equalTo(0)));
        assertThat(BinaryFuse16AddressPresence.reduce(Integer.MAX_VALUE, 100), is(lessThan(100)));
        assertThat(BinaryFuse16AddressPresence.reduce(-1, 100), is(lessThan(100)));
    }

    @Test
    void hash64_isDistributed() {
        long h1 = BinaryFuse16AddressPresence.hash64(0x1234567890ABCDEFL, 0L);
        long h2 = BinaryFuse16AddressPresence.hash64(0x1234567890ABCDEFL, 1L);
        assertThat(h1 == h2, is(false));
        long h3 = BinaryFuse16AddressPresence.hash64(0L, 0L);
        long h4 = BinaryFuse16AddressPresence.hash64(1L, 0L);
        assertThat(h3 == h4, is(false));
    }

    @Test
    void largePopulation_allFound() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 200; i++) {
            src.add(hash20(i % 256, i));
        }
        BinaryFuse16AddressPresence presence = BinaryFuse16AddressPresence.populateFrom(src);
        for (int i = 1; i <= 200; i++) {
            assertThat("entry " + i, presence.containsAddress(hash20(i % 256, i)), is(true));
        }
    }

    @Test
    void exactlyThreeEntries_allFound() {
        BinaryFuse16AddressPresence presence = BinaryFuse16AddressPresence.populateFrom(
                new ListIterable()
                        .add(hash20(0xAA, 1))
                        .add(hash20(0xBB, 2))
                        .add(hash20(0xCC, 3)));
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
        BinaryFuse16AddressPresence presence = BinaryFuse16AddressPresence.populateFrom(src);
        assertThat(presence.slotCount(), is(greaterThanOrEqualTo(n)));
    }

    @Test
    void twoIndependentFilters_doNotShareState() {
        BinaryFuse16AddressPresence a = BinaryFuse16AddressPresence.populateFrom(
                new ListIterable().add(hash20(0x01, 1)));
        BinaryFuse16AddressPresence b = BinaryFuse16AddressPresence.populateFrom(
                new ListIterable().add(hash20(0x02, 2)));
        assertThat(a.containsAddress(hash20(0x01, 1)), is(true));
        assertThat(a.containsAddress(hash20(0x02, 2)), is(false));
        assertThat(b.containsAddress(hash20(0x02, 2)), is(true));
        assertThat(b.containsAddress(hash20(0x01, 1)), is(false));
    }

    @Test
    void idempotentLookup_sameAnswerOnRepeatedQuery() {
        BinaryFuse16AddressPresence presence = BinaryFuse16AddressPresence.populateFrom(
                new ListIterable().add(hash20(0x33, 99)));
        boolean first = presence.containsAddress(hash20(0x33, 99));
        boolean second = presence.containsAddress(hash20(0x33, 99));
        assertThat(first, is(true));
        assertThat(second, is(equalTo(first)));
    }

    @Test
    void containsAddress_emptyByteBuffer_returnsFalse() {
        BinaryFuse16AddressPresence presence = BinaryFuse16AddressPresence.populateFrom(
                new ListIterable().add(hash20(0x10, 7)));
        assertThat(presence.containsAddress(ByteBuffer.wrap(new byte[0])), is(false));
    }
}
