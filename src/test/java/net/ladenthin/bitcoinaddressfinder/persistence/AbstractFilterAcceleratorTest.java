// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests the shared {@link AbstractFilterAccelerator} contract directly, independently of the Bloom
 * and Binary Fuse concrete subclasses: a filter miss short-circuits without consulting the
 * delegate, a filter hit is verified against the delegate, and {@link
 * AbstractFilterAccelerator#requiresBackend()} is always {@code true}.
 */
class AbstractFilterAcceleratorTest {

    /** Recording delegate: counts calls and reports a fixed contents set. */
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

    /** Minimal accelerator whose filter probe is driven by a fixed set, for contract testing. */
    private static final class FixedFilterAccelerator extends AbstractFilterAccelerator<AddressPresence> {
        private final Set<ByteBuffer> filterHits;
        private final AtomicInteger mightContainCalls = new AtomicInteger();

        FixedFilterAccelerator(Set<ByteBuffer> filterHits, AddressPresence delegate) {
            super(delegate);
            this.filterHits = filterHits;
        }

        @Override
        protected boolean mightContain(ByteBuffer hash160) {
            mightContainCalls.incrementAndGet();
            return filterHits.contains(hash160);
        }
    }

    private static ByteBuffer hash(int v) {
        ByteBuffer b = ByteBuffer.allocate(20);
        b.putInt(0, v);
        return b;
    }

    @Test
    void containsAddress_filterMiss_shortCircuitsWithoutDelegate() {
        RecordingPresence delegate = new RecordingPresence();
        FixedFilterAccelerator accelerator = new FixedFilterAccelerator(new HashSet<>(), delegate);

        boolean result = accelerator.containsAddress(hash(1));

        assertThat(result, is(false));
        assertThat("a filter miss must not consult the delegate", delegate.containsCalls.get(), is(equalTo(0)));
    }

    @Test
    void containsAddress_filterHit_delegateConfirms_returnsTrue() {
        RecordingPresence delegate = new RecordingPresence();
        delegate.contains.add(hash(1));
        Set<ByteBuffer> filterHits = new HashSet<>();
        filterHits.add(hash(1));
        FixedFilterAccelerator accelerator = new FixedFilterAccelerator(filterHits, delegate);

        boolean result = accelerator.containsAddress(hash(1));

        assertThat(result, is(true));
        assertThat("a filter hit must be verified against the delegate", delegate.containsCalls.get(), is(equalTo(1)));
    }

    @Test
    void containsAddress_filterHit_delegateRejects_returnsFalse() {
        RecordingPresence delegate = new RecordingPresence(); // empty -> rejects
        Set<ByteBuffer> filterHits = new HashSet<>();
        filterHits.add(hash(1));
        FixedFilterAccelerator accelerator = new FixedFilterAccelerator(filterHits, delegate);

        boolean result = accelerator.containsAddress(hash(1));

        assertThat("the delegate rejects the false positive", result, is(false));
        assertThat(delegate.containsCalls.get(), is(equalTo(1)));
    }

    @Test
    void requiresBackend_isAlwaysTrue() {
        FixedFilterAccelerator accelerator = new FixedFilterAccelerator(new HashSet<>(), new RecordingPresence());
        assertThat(accelerator.requiresBackend(), is(true));
    }
}
