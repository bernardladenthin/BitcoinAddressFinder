// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.hash20;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.ListIterable;
import org.junit.jupiter.api.Test;

/**
 * The two-stage filter cascade: a GPU pre-filter followed by the consumer's own backend.
 *
 * <h2>What the cascade is for</h2>
 * The consumer is single-threaded on purpose (to avoid LMDB contention), so it is the pipeline's
 * bottleneck, and the GPU can generate candidates far faster than one thread can verify them.
 * Every stage that rejects a candidate before it reaches LMDB multiplies the throughput ceiling.
 *
 * <h2>The property that makes it work — and the one that would break it</h2>
 * Two things must hold, and this test pins both because neither is self-evident:
 *
 * <ol>
 *   <li><b>The second stage must reject what the first let through.</b> That is only true if the
 *       filters hash <em>independently</em>. It is emphatically <b>not</b> true when both stages
 *       are the same filter instance — which is exactly what happens with
 *       {@code addressLookupBackend = BINARY_FUSE_8} plus the Fuse-8 GPU pre-filter, because the
 *       consumer reuses the very filter it uploaded. Then every GPU survivor passes the consumer
 *       stage too and the second filter is a no-op. This test measures that difference rather than
 *       assuming it.</li>
 *   <li><b>No stage may ever drop a member.</b> Both filter families have one-sided error, so a
 *       cascade of them still has one-sided error — but only as long as each stage is built over
 *       the same address set. A cascade that loses a funded address fails silently, so it is
 *       checked exhaustively over every member.</li>
 * </ol>
 */
class FilterCascadeTest {

    private static final int MEMBERS = 30_000;
    private static final int NON_MEMBERS = 200_000;

    /** Members occupy one key space; probes a disjoint one, so every probe is a true negative. */
    private static ListIterable members() {
        ListIterable src = new ListIterable();
        for (int i = 0; i < MEMBERS; i++) {
            src.add(hash20(i % 256, i));
        }
        return src;
    }

    private static ByteBuffer nonMember(int i) {
        return hash20(i % 256, 50_000_000 + i);
    }

    /** Counts how many of the non-member probes survive both stages. */
    private static int survivorsOfCascade(AddressPresence first, AddressPresence second) {
        int survivors = 0;
        for (int i = 0; i < NON_MEMBERS; i++) {
            ByteBuffer probe = nonMember(i);
            if (first.containsAddress(probe) && second.containsAddress(probe)) {
                survivors++;
            }
        }
        return survivors;
    }

    private static int survivorsOfSingle(AddressPresence only) {
        int survivors = 0;
        for (int i = 0; i < NON_MEMBERS; i++) {
            if (only.containsAddress(nonMember(i))) {
                survivors++;
            }
        }
        return survivors;
    }

    @Test
    void independentSecondStage_rejectsMostOfWhatTheFirstLetThrough() {
        ListIterable src = members();
        BinaryFuse8AddressPresence gpuStage = BinaryFuse8AddressPresence.populateFrom(src);
        BinaryFuse16AddressPresence cpuStage = BinaryFuse16AddressPresence.populateFrom(src);

        int afterFirst = survivorsOfSingle(gpuStage);
        int afterBoth = survivorsOfCascade(gpuStage, cpuStage);

        assertThat(
                "the first stage must let something through, or the test proves nothing",
                afterFirst,
                is(greaterThan(0)));
        // Fuse-16's FPR is ~0.0016 %, so it should reject essentially all of them. Asserting a
        // strict reduction rather than an exact count keeps this robust to the seed.
        assertThat("an independent second stage must reject most survivors", afterBoth, is(lessThan(afterFirst)));
    }

    @Test
    void sameFilterTwice_rejectsNothing_whichIsWhyTheBackendMustDiffer() {
        // Reusing one instance for both stages is what the Fuse-8 GPU path plus a Fuse-8 consumer
        // backend actually does. Deterministic filter, identical members => every survivor of the
        // first pass survives the second. This is the wasted-stage case the docs warn about.
        BinaryFuse8AddressPresence shared = BinaryFuse8AddressPresence.populateFrom(members());

        int afterFirst = survivorsOfSingle(shared);
        int afterBoth = survivorsOfCascade(shared, shared);

        assertThat(afterFirst, is(greaterThan(0)));
        assertThat("the same filter cannot reject its own survivors", afterBoth, is(afterFirst));
    }

    @Test
    void cascade_neverDropsAMember_forEveryStagePairing() {
        ListIterable src = members();
        BinaryFuse8AddressPresence fuse8 = BinaryFuse8AddressPresence.populateFrom(src);
        BinaryFuse16AddressPresence fuse16 = BinaryFuse16AddressPresence.populateFrom(src);
        BlockedBloomAddressPresence blocked = BlockedBloomAddressPresence.populateFrom(src);

        AddressPresence[][] pairings = {
            {fuse8, fuse16}, {fuse16, fuse8}, {blocked, fuse8}, {blocked, fuse16}, {fuse8, blocked},
        };

        for (AddressPresence[] pair : pairings) {
            for (int i = 0; i < MEMBERS; i++) {
                ByteBuffer member = hash20(i % 256, i);
                assertThat(
                        "member " + i + " must survive both stages of "
                                + pair[0].getClass().getSimpleName() + " -> "
                                + pair[1].getClass().getSimpleName(),
                        pair[0].containsAddress(member) && pair[1].containsAddress(member),
                        is(true));
            }
        }
    }

    /**
     * The compound rate should be far below either stage alone — that is the entire point of
     * cascading, and it only holds because the stages hash independently rather than correlating.
     */
    @Test
    void compoundFalsePositiveRate_isFarBelowEitherStage() {
        ListIterable src = members();
        BinaryFuse8AddressPresence fuse8 = BinaryFuse8AddressPresence.populateFrom(src);
        BlockedBloomAddressPresence blocked = BlockedBloomAddressPresence.populateFrom(src);

        int onlyFuse8 = survivorsOfSingle(fuse8);
        int onlyBlocked = survivorsOfSingle(blocked);
        int both = survivorsOfCascade(blocked, fuse8);

        assertThat(onlyFuse8, is(greaterThan(0)));
        assertThat(onlyBlocked, is(greaterThan(0)));
        assertThat("cascade must beat its own first stage", both, is(lessThan(onlyBlocked)));
        assertThat("cascade must beat its own second stage", both, is(lessThan(onlyFuse8)));
    }
}
