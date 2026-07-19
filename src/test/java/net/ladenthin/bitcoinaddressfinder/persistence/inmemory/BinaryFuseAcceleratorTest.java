// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

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

class BinaryFuseAcceleratorTest {

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
    void containsAddress_filterSaysAbsent_returnsFalseAndDoesNotConsultDelegate() {
        RecordingPresence filter = new RecordingPresence(); // empty -> miss
        RecordingPresence delegate = new RecordingPresence();
        BinaryFuseAccelerator accelerator = new BinaryFuseAccelerator(filter, delegate);

        boolean result = accelerator.containsAddress(hash(999));

        assertThat(result, is(false));
        assertThat("filter 'no' must short-circuit", delegate.containsCalls.get(), is(equalTo(0)));
    }

    @Test
    void containsAddress_filterHit_delegateConfirms_returnsTrue() {
        RecordingPresence filter = new RecordingPresence();
        filter.contains.add(hash(42));
        RecordingPresence delegate = new RecordingPresence();
        delegate.contains.add(hash(42));
        BinaryFuseAccelerator accelerator = new BinaryFuseAccelerator(filter, delegate);

        boolean result = accelerator.containsAddress(hash(42));

        assertThat(result, is(true));
        assertThat("filter hit must consult the delegate", delegate.containsCalls.get(), is(equalTo(1)));
    }

    @Test
    void containsAddress_filterHit_delegateDenies_returnsFalse() {
        // A filter false positive: the filter reports present, but the exact delegate does not.
        RecordingPresence filter = new RecordingPresence();
        filter.contains.add(hash(42));
        RecordingPresence delegate = new RecordingPresence(); // intentionally empty
        BinaryFuseAccelerator accelerator = new BinaryFuseAccelerator(filter, delegate);

        boolean result = accelerator.containsAddress(hash(42));

        assertThat(result, is(false));
        assertThat(
                "delegate must be consulted to reject the false positive",
                delegate.containsCalls.get(),
                is(equalTo(1)));
    }

    @Test
    void requiresBackend_isTrue_becauseFuseIsProbabilistic() {
        BinaryFuseAccelerator accelerator = new BinaryFuseAccelerator(new RecordingPresence(), new RecordingPresence());
        assertThat(accelerator.requiresBackend(), is(true));
    }

    @Test
    void getGpuFilterData_fuse8Wrapped_returnsPayload() {
        ListIterable source = new ListIterable().add(7).add(8).add(9);
        BinaryFuse8AddressPresence filter = BinaryFuse8AddressPresence.populateFrom(source);
        BinaryFuseAccelerator accelerator = new BinaryFuseAccelerator(filter, new RecordingPresence());

        assertThat(accelerator.getGpuFilterData().isPresent(), is(true));
        assertThat(accelerator.getGpuFilterData().get().fingerprints().length > 0, is(true));
    }

    @Test
    void getGpuFilterData_fuse16Wrapped_returnsEmpty() {
        ListIterable source = new ListIterable().add(7).add(8).add(9);
        BinaryFuse16AddressPresence filter = BinaryFuse16AddressPresence.populateFrom(source);
        BinaryFuseAccelerator accelerator = new BinaryFuseAccelerator(filter, new RecordingPresence());

        // Fuse-16 does have a GPU path — but through getGpuFilterData16(), not this accessor. The
        // two widths stay type-distinct on purpose: handing a 16-bit payload to the 8-bit upload
        // would have the device read two adjacent slots as one fingerprint, matching almost nothing
        // and silently dropping funded addresses. Returning empty here is what forces the caller to
        // pick the right one.
        assertThat(
                "the 8-bit accessor must not answer for a 16-bit filter",
                accelerator.getGpuFilterData().isPresent(),
                is(false));
    }

    @Test
    void getGpuFilterData16_fuse16Wrapped_returnsPayload() {
        ListIterable source = new ListIterable().add(7).add(8).add(9);
        BinaryFuse16AddressPresence filter = BinaryFuse16AddressPresence.populateFrom(source);
        BinaryFuseAccelerator accelerator = new BinaryFuseAccelerator(filter, new RecordingPresence());

        assertThat(accelerator.getGpuFilterData16().isPresent(), is(true));
        assertThat(accelerator.getGpuFilterData16().get().fingerprints().length > 0, is(true));
    }

    @Test
    void getGpuFilterData16_fuse8Wrapped_returnsEmpty() {
        ListIterable source = new ListIterable().add(7).add(8).add(9);
        BinaryFuse8AddressPresence filter = BinaryFuse8AddressPresence.populateFrom(source);
        BinaryFuseAccelerator accelerator = new BinaryFuseAccelerator(filter, new RecordingPresence());

        // The symmetric guard. Without it a Fuse-8 filter could supply a payload to the 16-bit
        // upload path, which is the exact direction that produces silent false negatives.
        assertThat(
                "the 16-bit accessor must not answer for an 8-bit filter",
                accelerator.getGpuFilterData16().isPresent(),
                is(false));
    }

    @Test
    void bothAccessors_neverAnswerForTheSameFilter() {
        // Whatever is wrapped, at most one width may claim it. If both ever answered, the caller's
        // choice would decide how the device interprets the buffer, with no error either way.
        ListIterable source = new ListIterable().add(7).add(8).add(9);
        for (AddressPresence filter : new AddressPresence[] {
            BinaryFuse8AddressPresence.populateFrom(source), BinaryFuse16AddressPresence.populateFrom(source)
        }) {
            BinaryFuseAccelerator accelerator = new BinaryFuseAccelerator(filter, new RecordingPresence());
            boolean eight = accelerator.getGpuFilterData().isPresent();
            boolean sixteen = accelerator.getGpuFilterData16().isPresent();
            assertThat("exactly one width must claim " + filter.getClass().getSimpleName(), eight ^ sixteen, is(true));
        }
    }

    @Test
    void containsAddress_wrappingRealFuse8_rejectsAbsentAndConfirmsPresent() {
        // End-to-end through a real Fuse-8 filter: present keys flow filter->delegate->true;
        // a key the delegate does not hold is rejected even if the filter false-positives.
        ListIterable source = new ListIterable().add(7).add(8).add(9);
        BinaryFuse8AddressPresence filter = BinaryFuse8AddressPresence.populateFrom(source);
        RecordingPresence delegate = new RecordingPresence();
        delegate.contains.add(hash(7));
        delegate.contains.add(hash(8));
        delegate.contains.add(hash(9));
        BinaryFuseAccelerator accelerator = new BinaryFuseAccelerator(filter, delegate);

        // No false negatives: every inserted key passes the filter and is confirmed by the delegate.
        assertThat(accelerator.containsAddress(hash(7)), is(true));
        assertThat(accelerator.containsAddress(hash(8)), is(true));
        assertThat(accelerator.containsAddress(hash(9)), is(true));
        // An absent key: filter miss -> false, or (rarely) filter hit -> delegate rejects -> false.
        assertThat(accelerator.containsAddress(hash(123_456)), is(false));
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
