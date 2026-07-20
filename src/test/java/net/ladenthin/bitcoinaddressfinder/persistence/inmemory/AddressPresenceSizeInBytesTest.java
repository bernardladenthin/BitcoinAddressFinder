// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.hash20;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.ListIterable;
import org.junit.jupiter.api.Test;

/**
 * Cross-backend coverage for {@link AddressPresence#sizeInBytes()}.
 *
 * <p><b>Why this exists.</b> {@code sizeInBytes()} is not diagnostic output — it is the memory term
 * of the filter-selection recommendation. Every "bytes per entry" figure in the filter
 * documentation, and therefore every "does this filter fit in RAM / under the device allocation
 * limit" decision, is read off these numbers. A backend that under-reports its footprint does not
 * fail anything: it silently makes an oversized filter look affordable, and the operator finds out
 * when the process dies or the GPU upload is rejected. Nothing else in the suite pins the reported
 * size against the backing arrays, so a refactor that changed a backend's storage without updating
 * its accounting would go unnoticed.
 *
 * <p>Two things are asserted per backend: the <b>exact</b> byte count derived from the backing
 * array (so an accounting bug is caught precisely), and the <b>per-entry ratio</b> against the
 * documented constant with a tolerance (so a change in the sizing policy itself — segment rounding,
 * default density — is caught even when the accounting stays self-consistent).
 */
class AddressPresenceSizeInBytesTest {

    /**
     * Entry count for the ratio assertions. Large enough that the fuse filters' fixed segment
     * rounding is amortised into the ~1.125 slots/entry asymptote, small enough to build fast.
     */
    private static final int ENTRIES = 200_000;

    /** Documented Binary Fuse 8 footprint, bytes per entry (one 8-bit slot per ~1.126 entries). */
    private static final double FUSE_8_BYTES_PER_ENTRY = 1.126;

    /** Documented Binary Fuse 16 footprint, bytes per entry — exactly twice {@link #FUSE_8_BYTES_PER_ENTRY}. */
    private static final double FUSE_16_BYTES_PER_ENTRY = 2.252;

    /** Documented blocked Bloom footprint at the default 11 bits/entry: 11/8 bytes per entry. */
    private static final double BLOCKED_BLOOM_BYTES_PER_ENTRY = 1.375;

    /** Truncated 64-bit keys are stored verbatim in {@code long[]} buckets: 8 bytes per entry. */
    private static final double TRUNCATED_LONG_64_BYTES_PER_ENTRY = 8.0;

    /**
     * Tolerance on the fuse filters' per-entry ratios, as a fraction of the documented constant.
     *
     * <p>The documented 1.126 / 2.252 are the asymptotic slot overhead; at a finite entry count the
     * construction rounds up to whole segments, which costs a few percent (measured ~1.167 B/entry
     * for Fuse-8 at {@link #ENTRIES}). 10 % absorbs that rounding while still failing hard on the
     * realistic regression — a slot width reported at half or double its real value.
     */
    private static final double FUSE_RATIO_RELATIVE_TOLERANCE = 0.10;

    /**
     * Tolerance on the blocked Bloom's per-entry ratio. Tighter than the fuse filters': fastrange
     * block sizing makes the requested density literal, so only the final partial block deviates.
     */
    private static final double BLOCKED_BLOOM_RATIO_TOLERANCE = 0.01;

    private static ListIterable source(int entries) {
        ListIterable src = new ListIterable();
        for (int i = 0; i < entries; i++) {
            src.add(hash20(i % 256, i));
        }
        return src;
    }

    @Test
    void binaryFuse8_sizeInBytes_isOneBytePerSlot_andMatchesDocumentedRatio() {
        BinaryFuse8AddressPresence presence = BinaryFuse8AddressPresence.populateFrom(source(ENTRIES));

        // exact: one byte per fingerprint slot, nothing else retained
        assertThat(presence.sizeInBytes(), is(equalTo((long) presence.slotCount())));

        double bytesPerEntry = (double) presence.sizeInBytes() / ENTRIES;
        assertThat(
                bytesPerEntry,
                is(closeTo(FUSE_8_BYTES_PER_ENTRY, FUSE_8_BYTES_PER_ENTRY * FUSE_RATIO_RELATIVE_TOLERANCE)));
    }

    @Test
    void binaryFuse16_sizeInBytes_isTwoBytesPerSlot_andMatchesDocumentedRatio() {
        BinaryFuse16AddressPresence presence = BinaryFuse16AddressPresence.populateFrom(source(ENTRIES));

        // exact: two bytes per fingerprint slot. Reporting the slot count (as the 8-bit filter
        // legitimately does) would halve the reported footprint of the widest filter offered.
        assertThat(presence.sizeInBytes(), is(equalTo((long) presence.slotCount() * Short.BYTES)));

        double bytesPerEntry = (double) presence.sizeInBytes() / ENTRIES;
        assertThat(
                bytesPerEntry,
                is(closeTo(FUSE_16_BYTES_PER_ENTRY, FUSE_16_BYTES_PER_ENTRY * FUSE_RATIO_RELATIVE_TOLERANCE)));
    }

    @Test
    void blockedBloom_sizeInBytes_isTheBitArray_andMatchesDefaultDensity() {
        BlockedBloomAddressPresence presence = BlockedBloomAddressPresence.populateFrom(source(ENTRIES));

        // exact: the long[] bit array and nothing else
        assertThat(presence.sizeInBytes(), is(equalTo((long) presence.getWords().length * Long.BYTES)));
        // ...which is exactly numBlocks x 512 bits
        assertThat(
                presence.sizeInBytes(),
                is(equalTo((long) presence.getNumBlocks() * BlockedBloomAddressPresence.BLOCK_BITS / Byte.SIZE)));

        // The default density is the number the memory recommendation quotes; the fastrange block
        // sizing is what makes it literal (power-of-two rounding used to deliver up to 2x this).
        double bytesPerEntry = (double) presence.sizeInBytes() / ENTRIES;
        assertThat(bytesPerEntry, is(closeTo(BLOCKED_BLOOM_BYTES_PER_ENTRY, BLOCKED_BLOOM_RATIO_TOLERANCE)));
    }

    @Test
    void blockedBloom_sizeInBytes_tracksConfiguredDensity() {
        // A non-default density must move the reported size proportionally: the size knob and the
        // reported footprint have to stay the same number, otherwise a tuned deployment is sized
        // against the default's figure.
        int bitsPerEntry = 22;
        BlockedBloomAddressPresence presence = BlockedBloomAddressPresence.populateFrom(
                source(ENTRIES), BlockedBloomAddressPresence.DEFAULT_K, bitsPerEntry);

        double bytesPerEntry = (double) presence.sizeInBytes() / ENTRIES;
        assertThat(bytesPerEntry, is(closeTo(bitsPerEntry / (double) Byte.SIZE, BLOCKED_BLOOM_RATIO_TOLERANCE)));
    }

    @Test
    void truncatedLong64_sizeInBytes_isEightBytesPerStoredEntry() {
        TruncatedLong64SortedArrayPresence presence = TruncatedLong64SortedArrayPresence.populateFrom(source(ENTRIES));

        // Exact and self-consistent: the 256 sorted buckets hold one long per stored entry. This is
        // the exact backend, so its footprint is the baseline the probabilistic filters are
        // compared against - an error here misprices every other option in the table.
        assertThat(presence.sizeInBytes(), is(equalTo(presence.size() * Long.BYTES)));

        double bytesPerEntry = (double) presence.sizeInBytes() / presence.size();
        assertThat(bytesPerEntry, is(closeTo(TRUNCATED_LONG_64_BYTES_PER_ENTRY, 0.0)));
    }

    @Test
    void nonReportingBackend_usesInterfaceDefault_ofMinusOne() {
        // HashSetAddressPresence deliberately does not override sizeInBytes: its footprint is
        // dominated by per-object overhead, so it reports "unknown" rather than a guess. The
        // contract is that a caller can distinguish "unknown" from a real measurement - if the
        // default ever returned 0 instead, a HashSet backend would advertise itself as free.
        AddressPresence presence = HashSetAddressPresence.populateFrom(source(16));

        assertThat(presence.sizeInBytes(), is(equalTo(-1L)));
    }

    @Test
    void emptyFilters_reportNonNegativeSizes() {
        // Degenerate input must not produce a negative size that a caller would mistake for the
        // "unknown" sentinel (-1) and silently exclude from a memory budget.
        assertThat(BinaryFuse8AddressPresence.populateFrom(new ListIterable()).sizeInBytes(), is(equalTo(0L)));
        assertThat(BinaryFuse16AddressPresence.populateFrom(new ListIterable()).sizeInBytes(), is(equalTo(0L)));
        assertThat(
                TruncatedLong64SortedArrayPresence.populateFrom(new ListIterable())
                        .sizeInBytes(),
                is(equalTo(0L)));
        // The blocked Bloom always allocates at least one block, so it is positive rather than 0.
        assertThat(
                BlockedBloomAddressPresence.populateFrom(new ListIterable()).sizeInBytes(),
                is(equalTo((long) BlockedBloomAddressPresence.LONGS_PER_BLOCK * Long.BYTES)));
    }
}
