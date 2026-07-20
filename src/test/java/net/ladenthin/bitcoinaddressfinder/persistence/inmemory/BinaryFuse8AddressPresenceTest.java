// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.hash20;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
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
    void mix_isDistributed() {
        long h1 = BinaryFuse8AddressPresence.mix(0x1234567890ABCDEFL, 0L);
        long h2 = BinaryFuse8AddressPresence.mix(0x1234567890ABCDEFL, 1L);
        assertThat(h1 == h2, is(false));
        long h3 = BinaryFuse8AddressPresence.mix(0L, 0L);
        long h4 = BinaryFuse8AddressPresence.mix(1L, 0L);
        assertThat(h3 == h4, is(false));
    }

    @Test
    void hashPosition_positionsAreDistinctAndInRange() {
        // A one-segment layout: segmentCount = 1, so arrayLength = (1 + 2) * segmentLength.
        int segLen = 32;
        int segMask = segLen - 1;
        int segCountLen = segLen; // segmentCount * segmentLength = 1 * 32
        int arrayLength = (1 + 2) * segLen;
        long hash = BinaryFuse8AddressPresence.mix(0xABCDEF12345L, BinaryFuse8AddressPresence.INITIAL_SEED);
        int h0 = BinaryFuse8AddressPresence.hashPosition(0, hash, segCountLen, segLen, segMask);
        int h1 = BinaryFuse8AddressPresence.hashPosition(1, hash, segCountLen, segLen, segMask);
        int h2 = BinaryFuse8AddressPresence.hashPosition(2, hash, segCountLen, segLen, segMask);
        assertThat("positions must be distinct", h0 != h1 && h1 != h2 && h0 != h2, is(true));
        for (int h : new int[] {h0, h1, h2}) {
            assertThat(h, is(greaterThanOrEqualTo(0)));
            assertThat(h, is(lessThan(arrayLength)));
        }
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

    /**
     * Two <em>distinct</em> 20-byte addresses that share their first 8 bytes collapse to the same
     * 64-bit key (the filter keys on the first 8 bytes only). A binary fuse cannot place two
     * identical keys, so {@code populateFrom} de-duplicates the truncated keys before construction.
     * The build therefore succeeds, and both addresses are reported as present (they share the one
     * surviving key). This is lossless for a presence pre-filter, since every hit is verified
     * against the exact backend anyway.
     */
    @Test
    void firstEightBytesCollide_deduplicated_buildsAndBothAddressesFound() {
        // both addresses: identical first 8 bytes {1..8}; distinct tails (index 8 differs)
        byte[] a = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] b = {1, 2, 3, 4, 5, 6, 7, 8, 99, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        ListIterable src = new ListIterable().add(ByteBuffer.wrap(a)).add(ByteBuffer.wrap(b));

        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);

        assertThat(presence.containsAddress(ByteBuffer.wrap(a)), is(true));
        assertThat(presence.containsAddress(ByteBuffer.wrap(b)), is(true));
    }

    /**
     * Directly pins the queue-sizing fix at billion-entry scale (arithmetic only, no allocation).
     * The old {@code arrayLength + 3 * size} sizing overflowed {@code int} here — {@code 3 * size}
     * wraps past {@link Integer#MAX_VALUE} to a negative value — producing a capacity <em>smaller</em>
     * than {@code arrayLength} (an undersized queue). The corrected {@code arrayLength} sizing stays
     * positive and sufficient. This is the red-without-the-fix, green-with-it guard; the real
     * billion-entry build cannot be exercised in a unit test (tens of GB of arrays).
     */
    @Test
    void peelingQueueLength_billionScale_isSufficientAndDoesNotOverflow() {
        // Full-DB scale (~1.377 B keys): arrayLength ~1.55 B (< Integer.MAX_VALUE), size ~1.38 B.
        int arrayLength = 1_549_000_000;
        int capacity = BinaryFuse8AddressPresence.peelingQueueLength(arrayLength);
        assertThat("capacity must not overflow to a non-positive value", capacity, is(greaterThan(0)));
        assertThat(
                "capacity must cover every position (>= arrayLength)", capacity, is(greaterThanOrEqualTo(arrayLength)));
    }

    /**
     * Regression guard for the peeling-queue sizing at moderate scale. The queue is sized exactly
     * {@code arrayLength} because counts only decrease during peeling, so each position is enqueued
     * at most once. A queue smaller than that would throw {@link ArrayIndexOutOfBoundsException}
     * during peeling. This builds a filter large enough to exercise peeling near the queue capacity
     * and asserts it completes with no exception and no false negatives.
     */
    @Test
    void peelingQueue_largePopulation_buildsWithoutOverflowAndFindsAll() {
        int n = 200_000;
        ListIterable src = new ListIterable();
        for (int i = 0; i < n; i++) {
            src.add(hash20(i % 256, i));
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);
        for (int i = 0; i < n; i++) {
            assertThat("member " + i, presence.containsAddress(hash20(i % 256, i)), is(true));
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

    // <editor-fold defaultstate="collapsed" desc="GPU-upload getters (Step C)">
    @Test
    void getSeed_returnsInitialSeedForFirstSuccessfulBuild() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 10; i++) {
            src.add(hash20(0x20, i));
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);

        // A small filter builds on the first attempt, so the seed is the initial seed.
        assertThat(presence.getSeed(), is(equalTo(BinaryFuse8AddressPresence.INITIAL_SEED)));
        assertThat(presence.getSeed() != 0L, is(true));

        // Cross-check: the seed the getter exposes is the same one containsAddress uses
        // internally. Rebuild one inserted key's XOR invariant by hand with getSeed() and
        // assert the three fingerprint slots XOR to the key's fingerprint (i.e. it is a hit).
        long seed = presence.getSeed();
        int segLen = presence.getSegmentLength();
        int segMask = presence.getSegmentLengthMask();
        int segCountLen = presence.getSegmentCountLength();
        byte[] fp = presence.getFingerprints();
        ByteBuffer member = hash20(0x20, 5);
        long key = member.getLong(member.position());
        long hash = BinaryFuse8AddressPresence.mix(key, seed);
        int h0 = BinaryFuse8AddressPresence.hashPosition(0, hash, segCountLen, segLen, segMask);
        int h1 = BinaryFuse8AddressPresence.hashPosition(1, hash, segCountLen, segLen, segMask);
        int h2 = BinaryFuse8AddressPresence.hashPosition(2, hash, segCountLen, segLen, segMask);
        byte expectedFp = BinaryFuse8AddressPresence.fingerprint8(hash);
        assertThat((byte) (fp[h0] ^ fp[h1] ^ fp[h2]), is(equalTo(expectedFp)));
    }

    @Test
    void getSegmentLengthMask_isSegmentLengthMinusOne() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 30; i++) {
            src.add(hash20(0x30, i));
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);
        assertThat(presence.getSegmentLengthMask(), is(equalTo(presence.getSegmentLength() - 1)));
    }

    @Test
    void getFingerprints_lengthEqualsSlotCount() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 40; i++) {
            src.add(hash20(0x40, i));
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);
        assertThat(presence.getFingerprints().length, is(equalTo(presence.slotCount())));
        // segmentCountLength is the base-position bound (segmentCount * segmentLength); the full
        // fingerprint array carries two extra segments of headroom for the fused offsets.
        assertThat(
                presence.getSegmentCountLength(), is(equalTo(presence.slotCount() - 2 * presence.getSegmentLength())));
    }

    @Test
    void getters_doNotMutateFilter() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 25; i++) {
            src.add(hash20(0x50, i));
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);

        // Capture the answers before touching the getters.
        boolean[] before = new boolean[25];
        for (int i = 0; i < 25; i++) {
            before[i] = presence.containsAddress(hash20(0x50, i + 1));
        }

        // Touch every getter.
        presence.getSeed();
        presence.getSegmentLength();
        presence.getSegmentLengthMask();
        presence.getSegmentCountLength();
        presence.getFingerprints();

        // Answers are unchanged.
        for (int i = 0; i < 25; i++) {
            assertThat("entry " + (i + 1), presence.containsAddress(hash20(0x50, i + 1)), is(equalTo(before[i])));
        }
    }

    @Test
    void gpuFilterData_hasArrayAwareValueSemantics() {
        byte[] fp = {1, 2, 3, 4};
        BinaryFuse8GpuFilterData a = new BinaryFuse8GpuFilterData(fp.clone(), 7L, 2, 1, 4);
        BinaryFuse8GpuFilterData equal = new BinaryFuse8GpuFilterData(fp.clone(), 7L, 2, 1, 4);
        BinaryFuse8GpuFilterData differentArray = new BinaryFuse8GpuFilterData(new byte[] {1, 2, 3, 9}, 7L, 2, 1, 4);
        BinaryFuse8GpuFilterData differentSeed = new BinaryFuse8GpuFilterData(fp.clone(), 8L, 2, 1, 4);

        // value semantics: equal content (different array instances) are equal with equal hashCode
        assertThat(a.equals(equal), is(true));
        assertThat(a.hashCode() == equal.hashCode(), is(true));
        // differing array content or scalar makes them unequal
        assertThat(a.equals(differentArray), is(false));
        assertThat(a.equals(differentSeed), is(false));
        // toString prints the length, not the raw array content
        assertThat(a.toString().contains("fingerprints.length=4"), is(true));
    }

    @Test
    void toGpuFilterData_mirrorsGettersForGpuUpload() {
        ListIterable src = new ListIterable();
        for (int i = 1; i <= 15; i++) {
            src.add(hash20(0x60, i));
        }
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(src);

        BinaryFuse8GpuFilterData data = presence.toGpuFilterData();
        assertThat(data.fingerprints(), is(equalTo(presence.getFingerprints())));
        assertThat(data.seed(), is(equalTo(presence.getSeed())));
        assertThat(data.segmentLength(), is(equalTo(presence.getSegmentLength())));
        assertThat(data.segmentLengthMask(), is(equalTo(presence.getSegmentLengthMask())));
        assertThat(data.segmentCountLength(), is(equalTo(presence.getSegmentCountLength())));
    }

    @Test
    void toGpuFilterData_emptyFilter_hasEmptyFingerprintsAndZeroCount() {
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(new ListIterable());
        BinaryFuse8GpuFilterData data = presence.toGpuFilterData();
        assertThat(data.fingerprints().length, is(equalTo(0)));
        assertThat(data.segmentCountLength(), is(equalTo(0)));
    }
    // </editor-fold>
}
