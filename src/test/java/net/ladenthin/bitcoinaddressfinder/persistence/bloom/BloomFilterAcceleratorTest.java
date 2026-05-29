// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.bloom;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressLookup;
import org.bitcoinj.base.Coin;
import org.junit.jupiter.api.Test;

class BloomFilterAcceleratorTest {

    /** Recording {@link AddressLookup} stub: counts delegate calls and reports a fixed contents set. */
    private static final class RecordingLookup implements AddressLookup {
        final Set<ByteBuffer> contains = new HashSet<>();
        final Map<ByteBuffer, Coin> amounts = new HashMap<>();
        final AtomicInteger containsCalls = new AtomicInteger();
        final AtomicInteger amountCalls = new AtomicInteger();

        @Override
        public boolean containsAddress(ByteBuffer hash160) {
            containsCalls.incrementAndGet();
            return contains.contains(hash160);
        }

        @Override
        public Coin getAmount(ByteBuffer hash160) {
            amountCalls.incrementAndGet();
            return amounts.getOrDefault(hash160, Coin.ZERO);
        }
    }

    private static ByteBuffer hash(int v) {
        ByteBuffer b = ByteBuffer.allocate(20);
        b.putInt(0, v);
        return b;
    }

    private static BloomFilter<byte[]> bloomWith(int... values) {
        BloomFilter<byte[]> bloom = BloomFilter.create(Funnels.byteArrayFunnel(), Math.max(values.length, 1), 0.01);
        for (int v : values) {
            byte[] bytes = new byte[20];
            ByteBuffer.wrap(bytes).putInt(0, v);
            bloom.put(bytes);
        }
        return bloom;
    }

    @Test
    void containsAddress_bloomSaysAbsent_returnsFalseAndDoesNotCallDelegate() {
        RecordingLookup delegate = new RecordingLookup();
        BloomFilter<byte[]> bloom = bloomWith(42, 43);
        BloomFilterAccelerator accelerator = new BloomFilterAccelerator(bloom, delegate);

        boolean result = accelerator.containsAddress(hash(999));

        assertThat(result, is(false));
        assertThat("Bloom 'no' must short-circuit", delegate.containsCalls.get(), is(equalTo(0)));
    }

    @Test
    void containsAddress_bloomSaysMaybe_delegatesToBackend_andReturnsBackendAnswer() {
        RecordingLookup delegate = new RecordingLookup();
        delegate.contains.add(hash(42));
        BloomFilter<byte[]> bloom = bloomWith(42);
        BloomFilterAccelerator accelerator = new BloomFilterAccelerator(bloom, delegate);

        boolean result = accelerator.containsAddress(hash(42));

        assertThat(result, is(true));
        assertThat("Bloom 'maybe' must consult the backend", delegate.containsCalls.get(), is(equalTo(1)));
    }

    @Test
    void containsAddress_bloomSaysMaybe_backendSaysNo_returnsFalse() {
        // A Bloom-filter false positive: the bloom contains the value, but the backend does not.
        RecordingLookup delegate = new RecordingLookup();
        // delegate.contains intentionally empty
        BloomFilter<byte[]> bloom = bloomWith(42);
        BloomFilterAccelerator accelerator = new BloomFilterAccelerator(bloom, delegate);

        boolean result = accelerator.containsAddress(hash(42));

        assertThat(result, is(false));
        assertThat(
                "Backend must be consulted to disambiguate the false positive",
                delegate.containsCalls.get(),
                is(equalTo(1)));
    }

    @Test
    void containsAddress_bufferPositionRestoredAfterCall() {
        // The accelerator drains the input ByteBuffer to extract bytes for Bloom;
        // it must rewind so the same buffer is reusable by the caller.
        RecordingLookup delegate = new RecordingLookup();
        BloomFilter<byte[]> bloom = bloomWith();
        BloomFilterAccelerator accelerator = new BloomFilterAccelerator(bloom, delegate);

        ByteBuffer buf = hash(7);
        int initialPos = buf.position();
        accelerator.containsAddress(buf);

        assertThat("Caller's buffer position must survive the call", buf.position(), is(equalTo(initialPos)));
    }

    @Test
    void getAmount_alwaysDelegates_evenWhenBloomLacksAddress() {
        // Bloom filters carry no value information, so getAmount must always consult the delegate.
        RecordingLookup delegate = new RecordingLookup();
        delegate.amounts.put(hash(42), Coin.valueOf(12345));
        BloomFilter<byte[]> bloom = bloomWith();
        BloomFilterAccelerator accelerator = new BloomFilterAccelerator(bloom, delegate);

        Coin amount = accelerator.getAmount(hash(42));

        assertThat(amount, is(equalTo(Coin.valueOf(12345))));
        assertThat(delegate.amountCalls.get(), is(equalTo(1)));
    }
}
