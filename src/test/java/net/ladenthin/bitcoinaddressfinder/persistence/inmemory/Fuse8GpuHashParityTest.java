// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the exact key-extraction + Binary Fuse 8 hash formula that the OpenCL kernel must
 * reproduce, as a runnable Java test written <b>before</b> any OpenCL code.
 *
 * <p>The GPU kernel checks each derived hash160 against the uploaded filter inline. If the
 * kernel's key extraction (the first 8 bytes of the hash160 read as a big-endian {@code ulong})
 * or its Murmur/reduce/fingerprint arithmetic diverges by even one bit from
 * {@link BinaryFuse8AddressPresence#containsAddress(ByteBuffer)}, the kernel produces silent
 * false negatives &mdash; an address that IS in the database is never flagged, so a real
 * balance hit is missed.
 *
 * <p>{@link #gpuStyleContains(BinaryFuse8AddressPresence, byte[])} below is an independent
 * re-implementation of the lookup, written exactly as the OpenCL C will be written:
 * {@code key = bytes[0..7] big-endian}, {@code hash = murmur64(key + seed)},
 * {@code base = unsignedMulHigh(hash, segCountLen)}, {@code h0 = base},
 * {@code h1 = base + segLen ^ ((hash >>> 18) & segMask)},
 * {@code h2 = base + 2*segLen ^ (hash & segMask)},
 * {@code hit = (fp[h0] ^ fp[h1] ^ fp[h2]) == fingerprint8(hash)}. The test asserts
 * this agrees with {@code containsAddress} on every one of 1 000 distinct hash160 inputs (members
 * and non-members alike). If the formula ever drifts, this test fails.
 */
class Fuse8GpuHashParityTest {

    private static final int INSERTED = 200;
    private static final int PROBES = 1000;

    /**
     * Builds a deterministic distinct 20-byte hash160 whose first 8 bytes encode {@code i}, so
     * the extracted 64-bit key differs for every {@code i}.
     */
    private static byte[] hash160For(int i) {
        byte[] h = new byte[20];
        long key = 0x9E3779B97F4A7C15L * (i + 1); // golden-ratio mix for spread distinct keys
        for (int b = 0; b < 8; b++) {
            h[b] = (byte) (key >>> (56 - 8 * b)); // big-endian into the first 8 bytes
        }
        // fill the remaining bytes too, so the addresses are fully distinct
        h[8] = (byte) i;
        h[9] = (byte) (i >>> 8);
        h[19] = (byte) 0xA5;
        return h;
    }

    /** One fused position, computed inline exactly as the OpenCL kernel will compute it. */
    private static int gpuStylePosition(int index, long hash, int segCountLen, int segLen, int segMask) {
        long h = Math.unsignedMultiplyHigh(hash, Integer.toUnsignedLong(segCountLen));
        h += (long) index * segLen;
        if (index == 1) {
            h ^= (hash >>> 18) & Integer.toUnsignedLong(segMask);
        } else if (index == 2) {
            h ^= hash & Integer.toUnsignedLong(segMask);
        }
        return (int) h;
    }

    /** Independent re-implementation of the lookup, written exactly as the OpenCL kernel will be. */
    private static boolean gpuStyleContains(BinaryFuse8AddressPresence filter, byte[] hash160) {
        byte[] fp = filter.getFingerprints();
        int segCountLen = filter.getSegmentCountLength();
        if (fp.length == 0) {
            return false;
        }
        long seed = filter.getSeed();
        int segLen = filter.getSegmentLength();
        int segMask = filter.getSegmentLengthMask();

        // Key extraction: first 8 bytes of hash160 as a big-endian uint64. This is exactly what
        // ByteBuffer.wrap(hash160).getLong(0) yields, and exactly what the kernel computes from
        // the two little-endian RIPEMD-160 output words via byte-swap.
        long key = ByteBuffer.wrap(hash160).getLong(0);

        // Single mix per key: murmur64(key + seed). Positions come from bit-windows of this hash.
        long hash = BinaryFuse8AddressPresence.murmur64(key + seed);
        int h0 = gpuStylePosition(0, hash, segCountLen, segLen, segMask);
        int h1 = gpuStylePosition(1, hash, segCountLen, segLen, segMask);
        int h2 = gpuStylePosition(2, hash, segCountLen, segLen, segMask);
        byte f8 = BinaryFuse8AddressPresence.fingerprint8(hash);
        return (byte) (fp[h0] ^ fp[h1] ^ fp[h2]) == f8;
    }

    @Test
    void gpuStyleLookup_agreesWithContainsAddress_onEveryProbe() {
        // arrange: build a filter whose members are the first INSERTED probes.
        InMemoryTestSupport.ListIterable src = new InMemoryTestSupport.ListIterable();
        List<byte[]> probes = new ArrayList<>(PROBES);
        for (int i = 0; i < PROBES; i++) {
            byte[] h = hash160For(i);
            probes.add(h);
            if (i < INSERTED) {
                src.add(ByteBuffer.wrap(h));
            }
        }
        BinaryFuse8AddressPresence filter = BinaryFuse8AddressPresence.populateFrom(src);

        // assert: the GPU-style reimplementation agrees with containsAddress on every probe,
        // and every inserted member is found (no false negatives).
        int agreements = 0;
        for (int i = 0; i < PROBES; i++) {
            byte[] h = probes.get(i);
            boolean java = filter.containsAddress(ByteBuffer.wrap(h));
            boolean gpu = gpuStyleContains(filter, h);
            assertThat("probe " + i + " java=" + java + " gpu=" + gpu, gpu, is(java));
            if (i < INSERTED) {
                assertThat("inserted member " + i + " must be a hit", java, is(true));
            }
            agreements++;
        }
        assertThat(agreements, is(PROBES));
    }

    @Test
    void gpuStyleLookup_emptyFilter_neverHits() {
        BinaryFuse8AddressPresence empty =
                BinaryFuse8AddressPresence.populateFrom(new InMemoryTestSupport.ListIterable());
        for (int i = 0; i < 100; i++) {
            assertThat(gpuStyleContains(empty, hash160For(i)), is(false));
        }
    }
}
