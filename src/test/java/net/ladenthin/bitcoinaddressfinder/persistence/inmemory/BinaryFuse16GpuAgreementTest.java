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

import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.ListIterable;
import org.junit.jupiter.api.Test;

/**
 * Pins the OpenCL {@code fuse16_contains} formula against the Java implementation.
 *
 * <h2>Why this test carries more weight than a typical unit test</h2>
 * A divergence between the device probe and the Java filter does not throw, does not corrupt
 * anything, and does not show up in a throughput number. It silently produces <b>false
 * negatives</b> — a funded address that the GPU pre-filter drops before it ever reaches the
 * consumer. The scan keeps running and reports nothing, which is indistinguishable from a scan
 * that simply found nothing. That is the one failure mode this project cannot tolerate, so the
 * device formula is re-implemented here independently and checked against the real filter over
 * every probe rather than spot-checked.
 *
 * <p>The sibling test {@code BlockedBloomAddressPresenceTest#gpuStyleLookup_agreesWithContainsAddress}
 * does the same for the blocked Bloom probe.
 *
 * <h2>What "independently" means here</h2>
 * {@link #gpuStyleContains} is written from the OpenCL source, not by calling into the production
 * lookup: it recomputes the mix, the three positions and the 16-bit fingerprint the way the kernel
 * does. Sharing the production code path would make the test pass by construction and prove
 * nothing about the kernel.
 */
class BinaryFuse16GpuAgreementTest {

    /**
     * Re-implementation of {@code fuse16_contains} exactly as the OpenCL kernel computes it.
     *
     * <p>Geometry is shared with Fuse-8 — same {@code murmur64(key + seed)} mix, same three fused
     * positions — so only the fingerprint width differs: {@code (short)(hash ^ (hash >>> 32))}
     * against Fuse-8's {@code (byte)(...)}. Narrowing to 16 bits is what makes this a different
     * filter, and widening it by accident is precisely the bug this test exists to catch.
     */
    private static boolean gpuStyleContains(BinaryFuse16GpuFilterData data, byte[] hash160) {
        if (data.segmentCountLength() == 0) {
            return false;
        }
        long key = ByteBuffer.wrap(hash160).getLong(0);
        long hash = murmur64(key + data.seed());
        int h0 = position(0, hash, data);
        int h1 = position(1, hash, data);
        int h2 = position(2, hash, data);
        short[] fp = data.fingerprints();
        short expected = (short) (hash ^ (hash >>> 32));
        return (short) (fp[h0] ^ fp[h1] ^ fp[h2]) == expected;
    }

    /** The kernel's {@code fuse8_position}, reused unchanged by the 16-bit probe. */
    private static int position(int index, long hash, BinaryFuse16GpuFilterData data) {
        long h = Math.unsignedMultiplyHigh(hash, Integer.toUnsignedLong(data.segmentCountLength()));
        h += Integer.toUnsignedLong(index * data.segmentLength());
        if (index == 1) {
            h ^= (hash >>> 18) & Integer.toUnsignedLong(data.segmentLengthMask());
        } else if (index == 2) {
            h ^= hash & Integer.toUnsignedLong(data.segmentLengthMask());
        }
        return (int) h;
    }

    /** The kernel's {@code fuse8_murmur64}. */
    private static long murmur64(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    @Test
    void gpuStyleLookup_agreesWithContainsAddress_onEveryProbe() {
        int members = 20_000;
        int probes = 40_000;

        ListIterable src = new ListIterable();
        byte[][] all = new byte[probes][];
        for (int i = 0; i < probes; i++) {
            byte[] h = new byte[20];
            long key = 0x9E3779B97F4A7C15L * (i + 1);
            for (int b = 0; b < 8; b++) {
                h[b] = (byte) (key >>> (56 - 8 * b));
            }
            h[8] = (byte) i;
            h[19] = (byte) 0xA5;
            all[i] = h;
            if (i < members) {
                src.add(ByteBuffer.wrap(h));
            }
        }

        BinaryFuse16AddressPresence filter = BinaryFuse16AddressPresence.populateFrom(src);
        BinaryFuse16GpuFilterData data = filter.toGpuFilterData();

        int agreements = 0;
        for (int i = 0; i < probes; i++) {
            boolean java = filter.containsAddress(ByteBuffer.wrap(all[i]));
            boolean gpu = gpuStyleContains(data, all[i]);
            assertThat("probe " + i + " must agree between Java and the kernel formula", gpu, is(java));
            agreements++;
            if (i < members) {
                assertThat("member " + i + " must never be missed", java, is(true));
            }
        }
        assertThat(agreements, is(greaterThan(0)));
    }

    /**
     * Guards the property the whole cascade rests on: a 16-bit fingerprint is <em>not</em> an 8-bit
     * one. If {@code fuse16_fingerprint} were ever widened or truncated to a byte, the filter would
     * still work — just as a much worse one — and every agreement test above would still pass,
     * because both sides would be wrong together. This checks the fingerprint values themselves
     * actually span more than 8 bits.
     */
    @Test
    void fingerprints_spanMoreThanEightBits_soThisIsNotSecretlyFuse8() {
        ListIterable src = new ListIterable();
        for (int i = 0; i < 50_000; i++) {
            src.add(hash20(i % 256, i));
        }
        BinaryFuse16GpuFilterData data =
                BinaryFuse16AddressPresence.populateFrom(src).toGpuFilterData();

        int highByteSet = 0;
        for (short slot : data.fingerprints()) {
            if (((slot >>> 8) & 0xFF) != 0) {
                highByteSet++;
            }
        }
        // With 16-bit fingerprints roughly 255/256 of non-empty slots carry a non-zero high byte.
        // An accidental narrowing to 8 bits would drive this to zero.
        assertThat(
                "high fingerprint byte must be in use", highByteSet, is(greaterThan(data.fingerprints().length / 4)));
    }

    /**
     * The payload must report what it will actually occupy in VRAM, because that number is checked
     * against {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE} — a limit that is a quarter of total VRAM on
     * NVIDIA and 85 % on AMD. Reporting slot count instead of bytes would understate it 2&times;
     * and turn a hard allocation failure into a surprise at run time.
     */
    @Test
    void sizeInBytes_countsTwoBytesPerSlot() {
        ListIterable src = new ListIterable();
        for (int i = 0; i < 10_000; i++) {
            src.add(hash20(i % 256, i));
        }
        BinaryFuse16GpuFilterData data =
                BinaryFuse16AddressPresence.populateFrom(src).toGpuFilterData();
        assertThat(data.sizeInBytes(), is((long) data.fingerprints().length * Short.BYTES));
    }
}
