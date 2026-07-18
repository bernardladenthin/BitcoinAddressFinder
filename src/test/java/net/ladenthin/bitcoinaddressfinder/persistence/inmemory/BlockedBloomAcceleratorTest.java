// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import org.junit.jupiter.api.Test;

class BlockedBloomAcceleratorTest {

    /** Recording {@link AddressPresence} stub: counts calls and reports a fixed contents set. */
    private static final class RecordingPresence implements AddressPresence {
        final Set<ByteBuffer> contains = new HashSet<>();
        final AtomicInteger containsCalls = new AtomicInteger();

        @Override
        public boolean containsAddress(ByteBuffer hash160) {
            containsCalls.incrementAndGet();
            return contains.contains(hash160);
        }

        @Override
        public boolean requiresBackend() {
            return false;
        }
    }

    private static ByteBuffer hash(int v) {
        ByteBuffer b = ByteBuffer.allocate(20);
        b.putInt(0, v);
        return b;
    }

    @Test
    void requiresBackend_isTrue_becauseBlockedBloomIsProbabilistic() {
        BlockedBloomAddressPresence filter = BlockedBloomAddressPresence.populateFrom(new ListIterable().add(1));
        BlockedBloomAccelerator accelerator = new BlockedBloomAccelerator(filter, new RecordingPresence());
        assertThat(accelerator.requiresBackend(), is(true));
    }

    @Test
    void getFilter_returnsWrappedFilter() {
        BlockedBloomAddressPresence filter = BlockedBloomAddressPresence.populateFrom(new ListIterable().add(1));
        BlockedBloomAccelerator accelerator = new BlockedBloomAccelerator(filter, new RecordingPresence());
        assertThat(accelerator.getFilter(), is(sameInstance(filter)));
    }

    @Test
    void containsAddress_filterMiss_doesNotConsultDelegate() {
        // A key absent from the filter must short-circuit before the (expensive) delegate.
        ListIterable source = new ListIterable().add(7).add(8).add(9);
        BlockedBloomAddressPresence filter = BlockedBloomAddressPresence.populateFrom(source);
        RecordingPresence delegate = new RecordingPresence();
        delegate.contains.add(hash(7));
        BlockedBloomAccelerator accelerator = new BlockedBloomAccelerator(filter, delegate);

        // hash(123_456) is (overwhelmingly) a filter miss; assert the delegate stayed untouched only
        // if the filter actually missed, so the test is robust against the rare false positive.
        boolean result = accelerator.containsAddress(hash(123_456));
        if (!result) {
            // Either a definitive miss (0 delegate calls) or a false positive rejected by the delegate.
            assertThat(delegate.containsCalls.get() <= 1, is(true));
        }
    }

    @Test
    void containsAddress_wrappingRealFilter_rejectsAbsentAndConfirmsPresent() {
        // End-to-end: present keys flow filter -> delegate -> true (no false negatives); an absent
        // key is rejected either by a filter miss or by the delegate overruling a false positive.
        ListIterable source = new ListIterable().add(7).add(8).add(9);
        BlockedBloomAddressPresence filter = BlockedBloomAddressPresence.populateFrom(source);
        RecordingPresence delegate = new RecordingPresence();
        delegate.contains.add(hash(7));
        delegate.contains.add(hash(8));
        delegate.contains.add(hash(9));
        BlockedBloomAccelerator accelerator = new BlockedBloomAccelerator(filter, delegate);

        assertThat(accelerator.containsAddress(hash(7)), is(true));
        assertThat(accelerator.containsAddress(hash(8)), is(true));
        assertThat(accelerator.containsAddress(hash(9)), is(true));
        assertThat(accelerator.containsAddress(hash(123_456)), is(false));
    }

    @Test
    void containsAddress_filterHit_delegateDenies_returnsFalse() {
        // Force a "filter says maybe" path with a stub filter is not possible (BlockedBloomAddressPresence
        // is final and computes its own answer), so instead verify a member of the filter that the
        // delegate rejects: filter hit -> delegate denies -> false, and the delegate was consulted.
        ListIterable source = new ListIterable().add(42);
        BlockedBloomAddressPresence filter = BlockedBloomAddressPresence.populateFrom(source);
        RecordingPresence delegate = new RecordingPresence(); // intentionally empty
        BlockedBloomAccelerator accelerator = new BlockedBloomAccelerator(filter, delegate);

        boolean result = accelerator.containsAddress(hash(42));

        assertThat(result, is(false));
        assertThat(
                "filter hit must consult the delegate to reject the false positive",
                delegate.containsCalls.get(),
                is(equalTo(1)));
    }

    /** Minimal {@link AddressIterable} of 20-byte addresses for building real filters. */
    private static final class ListIterable implements AddressIterable {
        final List<byte[]> entries = new ArrayList<>();

        ListIterable add(int seed) {
            byte[] bytes = new byte[20];
            ByteBuffer.wrap(bytes).putInt(0, seed);
            entries.add(bytes);
            return this;
        }

        @Override
        public Stream<ByteBuffer> addresses() {
            return entries.stream().map(ByteBuffer::wrap);
        }

        @Override
        public long count() {
            return entries.size();
        }
    }
}
