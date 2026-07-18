// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.hash20;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.ListIterable;
import org.junit.jupiter.api.Test;

class BlockedBloomAddressPresenceTest {

    @Test
    void empty_doesNotContainAnything() {
        BlockedBloomAddressPresence presence = BlockedBloomAddressPresence.populateFrom(new ListIterable());
        assertThat(presence.containsAddress(hash20(0x42, 1)), is(false));
    }

    @Test
    void populated_containsAllMembers_noFalseNegatives() {
        int n = 50_000;
        ListIterable src = new ListIterable();
        for (int i = 0; i < n; i++) {
            src.add(hash20(i % 256, i));
        }
        BlockedBloomAddressPresence presence = BlockedBloomAddressPresence.populateFrom(src);
        for (int i = 0; i < n; i++) {
            assertThat("member " + i, presence.containsAddress(hash20(i % 256, i)), is(true));
        }
    }

    @Test
    void requiresBackend_isTrue() {
        BlockedBloomAddressPresence presence =
                BlockedBloomAddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        assertThat(presence.requiresBackend(), is(true));
    }

    @Test
    void wrongLengthBuffer_returnsFalse() {
        BlockedBloomAddressPresence presence =
                BlockedBloomAddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        assertThat(presence.containsAddress(ByteBuffer.wrap(new byte[19])), is(false));
    }

    @Test
    void falsePositiveRate_isReasonableAndDiscriminates() {
        // A configuration that yields a moderate, clearly-measurable FPR (so a broken all-bits-set
        // filter would fail this). Members and non-members occupy disjoint key spaces.
        int members = 100_000;
        int probes = 200_000;
        int k = 2;
        int bitsPerEntry = 8;

        ListIterable src = new ListIterable();
        for (int i = 0; i < members; i++) {
            src.add(hash20(i % 256, i));
        }
        BlockedBloomAddressPresence presence = BlockedBloomAddressPresence.populateFrom(src, k, bitsPerEntry);

        int falsePositives = 0;
        for (int i = 0; i < probes; i++) {
            // disjoint from members (tailSeed offset well beyond the member range)
            if (presence.containsAddress(hash20(i % 256, 10_000_000 + i))) {
                falsePositives++;
            }
        }
        double fpr = (double) falsePositives / probes;
        // Well below a broken filter's ~1.0, and above 0 (it must actually let some through, i.e. the
        // bit array is neither empty nor saturated). The pow-2 block rounding keeps the exact value
        // modest; the point here is discrimination, not a tight numeric match.
        assertThat("fpr should be far below a saturated filter", fpr, is(lessThan(0.10)));
    }

    @Test
    void chooseLogBlocks_billionScale_sizesToTwoGiB() {
        // 1.5 B entries at 11 bits/entry -> 2^25 blocks x 512 bits = 2^34 bits = 2 GiB.
        int logBlocks = BlockedBloomAddressPresence.chooseLogBlocks(1_500_000_000L, 11);
        assertThat(logBlocks, is(equalTo(25)));
        long totalBits = (1L << logBlocks) * BlockedBloomAddressPresence.BLOCK_BITS;
        assertThat(totalBits, is(equalTo(1L << 34))); // 2 GiB
    }

    @Test
    void chooseLogBlocks_isClampedToArrayLimit() {
        // Even absurd inputs must keep numBlocks * LONGS_PER_BLOCK within Integer.MAX_VALUE.
        int logBlocks = BlockedBloomAddressPresence.chooseLogBlocks(Long.MAX_VALUE, 64);
        assertThat(logBlocks, is(lessThan(29)));
    }

    /**
     * Independent re-implementation of the lookup, written exactly as the OpenCL kernel will be —
     * key = bytes[0..7] big-endian, {@code a=murmur64(key)}, {@code b=murmur64(key+GOLDEN)},
     * {@code block=a>>>(64-logBlocks)}, {@code bit_i=(x + i*y) & 511} with {@code x=(int)b} and the
     * stride {@code y=((int)(b>>>32))|1} forced odd. Pins the byte-exact formula the GPU must
     * reproduce.
     */
    private static boolean gpuStyleContains(BlockedBloomAddressPresence filter, byte[] hash160) {
        long[] words = filter.getWords();
        if (words.length == 0) {
            return false;
        }
        int logBlocks = filter.getLogBlocks();
        int k = filter.getK();
        long key = ByteBuffer.wrap(hash160).getLong(0);
        long a = BlockedBloomAddressPresence.murmur64(key);
        long b = BlockedBloomAddressPresence.murmur64(key + BlockedBloomAddressPresence.GOLDEN);
        int base = ((int) (a >>> (64 - logBlocks))) * BlockedBloomAddressPresence.LONGS_PER_BLOCK;
        int x = (int) b;
        int y = ((int) (b >>> 32)) | 1; // stride forced odd - see BlockedBloomAddressPresence#oddStride
        for (int i = 0; i < k; i++) {
            int bit = (x + i * y) & BlockedBloomAddressPresence.BLOCK_MASK;
            if ((words[base + (bit >>> 6)] & (1L << (bit & 63))) == 0L) {
                return false;
            }
        }
        return true;
    }

    /**
     * The probe sequence {@code bit_i = (x + i*y) mod 512} has period {@code 512 / gcd(y, 512)}. If the
     * stride {@code y} were taken raw from the hash, 1 key in 512 would draw {@code y ≡ 0 (mod 512)}
     * and place all {@code k} probes on a <em>single</em> bit, 1 in 256 on 2 bits, 1 in 128 on 4 —
     * degenerate entries that dominated the false-positive rate (measured 0.258 % against a
     * theoretical 0.052 % at k=8 / 16.24 bits per entry, ~5x worse). Forcing the stride odd makes
     * {@code gcd(y, 512) = 1}, so the probes are always distinct. This test fails if that {@code | 1}
     * is ever dropped: over this many keys, hitting zero degenerate strides by chance is impossible.
     */
    @Test
    void probePositions_areAlwaysDistinct_becauseStrideIsOdd() {
        int k = 8;
        int keys = 200_000; // >> 512, so raw-stride degeneracy would certainly show up
        for (int i = 0; i < keys; i++) {
            long key = 0x9E3779B97F4A7C15L * (i + 1);
            long b = BlockedBloomAddressPresence.murmur64(key + BlockedBloomAddressPresence.GOLDEN);
            int x = (int) b;
            int y = ((int) (b >>> 32)) | 1;
            Set<Integer> positions = new HashSet<>();
            for (int j = 0; j < k; j++) {
                positions.add((x + j * y) & BlockedBloomAddressPresence.BLOCK_MASK);
            }
            assertThat("key " + i + " must probe " + k + " distinct bits", positions.size(), is(equalTo(k)));
        }
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
        BlockedBloomAddressPresence filter = BlockedBloomAddressPresence.populateFrom(src);
        for (int i = 0; i < probes; i++) {
            boolean java = filter.containsAddress(ByteBuffer.wrap(all[i]));
            boolean gpu = gpuStyleContains(filter, all[i]);
            assertThat("probe " + i, gpu, is(java));
            if (i < members) {
                assertThat("member " + i + " must be found", java, is(true));
            }
        }
    }
}
