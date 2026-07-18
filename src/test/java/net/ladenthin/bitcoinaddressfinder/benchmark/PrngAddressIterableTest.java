// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.benchmark;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the properties {@link FilterLookupBenchmark} relies on: the synthetic source must be
 * reproducible, must agree between its two iteration paths, and must produce uniform, distinct
 * addresses — otherwise a measured false-positive rate would reflect the generator, not the filter.
 */
class PrngAddressIterableTest {

    private static List<byte[]> collectViaForEach(PrngAddressIterable source) {
        List<byte[]> out = new ArrayList<>();
        source.forEachAddress(bb -> {
            byte[] copy = new byte[bb.remaining()];
            bb.get(bb.position(), copy);
            out.add(copy);
        });
        return out;
    }

    @Test
    void sameSeed_producesIdenticalSequence() {
        List<byte[]> first = collectViaForEach(new PrngAddressIterable(42L, 1_000));
        List<byte[]> second = collectViaForEach(new PrngAddressIterable(42L, 1_000));

        assertThat(first.size(), is(equalTo(1_000)));
        for (int i = 0; i < first.size(); i++) {
            assertThat("index " + i, second.get(i), is(equalTo(first.get(i))));
        }
    }

    @Test
    void differentSeeds_produceDisjointSets() {
        // Member and probe sets must not overlap, or the benchmark's "non-member" probes would be
        // members and every backend would report a spurious 100 % hit rate.
        Set<ByteBuffer> members = new HashSet<>();
        new PrngAddressIterable(1L, 20_000).addresses().forEach(bb -> members.add(ByteBuffer.wrap(toArray(bb))));

        long overlap = new PrngAddressIterable(2L, 20_000)
                .addresses()
                .filter(bb -> members.contains(ByteBuffer.wrap(toArray(bb))))
                .count();

        assertThat(overlap, is(equalTo(0L)));
    }

    @Test
    void streamAndForEach_agree() {
        // The filters build via forEachAddress, some helpers via addresses(); they must not diverge.
        PrngAddressIterable source = new PrngAddressIterable(7L, 500);
        List<byte[]> viaForEach = collectViaForEach(source);
        List<byte[]> viaStream;
        try (Stream<ByteBuffer> stream = source.addresses()) {
            viaStream = stream.map(PrngAddressIterableTest::toArray).toList();
        }

        assertThat(viaStream.size(), is(equalTo(viaForEach.size())));
        for (int i = 0; i < viaStream.size(); i++) {
            assertThat("index " + i, viaStream.get(i), is(equalTo(viaForEach.get(i))));
        }
    }

    @Test
    void addressAt_isPureFunctionOfSeedAndIndex() {
        assertThat(PrngAddressIterable.addressAt(9L, 123), is(equalTo(PrngAddressIterable.addressAt(9L, 123))));
        assertThat(PrngAddressIterable.addressAt(9L, 124), is(not(PrngAddressIterable.addressAt(9L, 123))));
    }

    private static org.hamcrest.Matcher<byte[]> not(byte[] unwanted) {
        return org.hamcrest.Matchers.not(equalTo(unwanted));
    }

    @Test
    void addresses_areDistinctAndCorrectLength() {
        int n = 50_000;
        Set<ByteBuffer> distinct = new HashSet<>();
        for (byte[] address : collectViaForEach(new PrngAddressIterable(3L, n))) {
            assertThat(address.length, is(equalTo(PrngAddressIterable.BYTES_PER_ADDRESS)));
            distinct.add(ByteBuffer.wrap(address));
        }
        // A weak generator that collided would silently deflate every filter's measured FPR.
        assertThat(distinct.size(), is(equalTo(n)));
    }

    @Test
    void leadingLongIsUniformlyDistributed() {
        // Every filter derives its 64-bit key from bytes 0..7, so those bytes must be well spread.
        // A biased generator would make blocked-Bloom block selection non-uniform and distort FPR.
        int n = 100_000;
        int[] topByteHistogram = new int[256];
        for (byte[] address : collectViaForEach(new PrngAddressIterable(11L, n))) {
            topByteHistogram[address[0] & 0xFF]++;
        }
        int expected = n / 256; // ~390
        for (int bucket = 0; bucket < 256; bucket++) {
            assertThat("bucket " + bucket, topByteHistogram[bucket], is(greaterThan(expected / 2)));
            assertThat("bucket " + bucket, topByteHistogram[bucket], is(lessThan(expected * 2)));
        }
    }

    @Test
    void count_isReportedAndClampedAtZero() {
        assertThat(new PrngAddressIterable(1L, 1234).count(), is(equalTo(1234L)));
        assertThat(new PrngAddressIterable(1L, -5).count(), is(equalTo(0L)));
    }

    private static byte[] toArray(ByteBuffer bb) {
        byte[] copy = new byte[bb.remaining()];
        bb.get(bb.position(), copy);
        return copy;
    }
}
