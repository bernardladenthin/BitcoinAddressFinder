// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import org.junit.jupiter.api.Test;

/** Pure (no-GPU) unit tests for the {@link OpenClConfigSuggestion} heuristic. */
public class OpenClConfigSuggestionTest {

    private static final long MIB = 1024L * 1024L;

    @Test
    public void suggest_rtx3070LikeDevice_returnsHighEndConfig() {
        // arrange: 40 compute units, ~2047 MiB max alloc (RTX 3070 Laptop)
        // act
        OpenClConfigSuggestion s = OpenClConfigSuggestion.suggest(40, 2047 * MIB);

        // assert: memory allows the high-end batch cap, and ~200 work-items/CU -> 256 keys/work-item
        assertThat(s.batchSizeInBits(), is(OpenClConfigSuggestion.MAX_BATCH_SIZE_IN_BITS)); // 21
        assertThat(s.keysPerWorkItem(), is(256));
    }

    @Test
    public void suggest_smallMemoryDevice_lowersBatchSizeToFitMemory() {
        // arrange: tiny 64 MiB max alloc, 4 compute units
        // act
        OpenClConfigSuggestion s = OpenClConfigSuggestion.suggest(4, 64 * MIB);

        // assert: batch is memory-bounded below the high-end cap
        assertThat(s.batchSizeInBits(), is(17));
        assertThat(s.keysPerWorkItem(), is(128));
    }

    @Test
    public void suggest_manyComputeUnits_lowersKeysPerWorkItemToKeepGridLarge() {
        // arrange: a very wide GPU needs more work-items -> fewer keys per work-item
        // act
        OpenClConfigSuggestion wide = OpenClConfigSuggestion.suggest(512, 8192 * MIB);
        OpenClConfigSuggestion narrow = OpenClConfigSuggestion.suggest(8, 8192 * MIB);

        // assert: same (cap-bound) batch, but the wider device gets a smaller keysPerWorkItem
        assertThat(wide.batchSizeInBits(), is(OpenClConfigSuggestion.MAX_BATCH_SIZE_IN_BITS));
        assertThat(narrow.batchSizeInBits(), is(OpenClConfigSuggestion.MAX_BATCH_SIZE_IN_BITS));
        assertThat(wide.keysPerWorkItem(), lessThanOrEqualTo(narrow.keysPerWorkItem()));
    }

    @Test
    public void suggest_anyDevice_staysWithinValidatedRanges() {
        for (int cu : new int[] {1, 4, 16, 40, 128, 1024}) {
            for (long memMib : new long[] {16, 64, 256, 2047, 16384}) {
                OpenClConfigSuggestion s = OpenClConfigSuggestion.suggest(cu, memMib * MIB);

                assertThat(s.batchSizeInBits(), greaterThanOrEqualTo(OpenClConfigSuggestion.MIN_BATCH_SIZE_IN_BITS));
                assertThat(s.batchSizeInBits(), lessThanOrEqualTo(OpenClConfigSuggestion.MAX_BATCH_SIZE_IN_BITS));
                assertThat(s.keysPerWorkItem(), greaterThanOrEqualTo(1));
                assertThat(s.keysPerWorkItem(), lessThanOrEqualTo(OpenClConfigSuggestion.MAX_KEYS_PER_WORK_ITEM));
                // keysPerWorkItem must be a power of two (kernel requirement)
                assertThat(Integer.bitCount(s.keysPerWorkItem()), is(1));
            }
        }
    }

    @Test
    public void suggest_zeroComputeUnits_doesNotThrowAndClampsToOne() {
        // act: degenerate device report -> treated as 1 compute unit
        OpenClConfigSuggestion s = OpenClConfigSuggestion.suggest(0, 2047 * MIB);

        // assert
        assertThat(s.keysPerWorkItem(), is(OpenClConfigSuggestion.MAX_KEYS_PER_WORK_ITEM));
    }
}
