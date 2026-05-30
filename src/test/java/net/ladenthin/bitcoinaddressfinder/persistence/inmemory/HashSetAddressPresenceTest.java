// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

import static net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.hash20;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.InMemoryTestSupport.ListIterable;
import org.junit.jupiter.api.Test;

class HashSetAddressPresenceTest {

    @Test
    void empty_doesNotContainAnything() {
        HashSetAddressPresence presence = HashSetAddressPresence.populateFrom(new ListIterable());
        assertThat(presence.containsAddress(hash20(0x42, 1)), is(false));
        assertThat(presence.size(), is(equalTo(0)));
    }

    @Test
    void populated_containsKnown_doesNotContainAbsent() {
        ListIterable src =
                new ListIterable().add(hash20(0x42, 1)).add(hash20(0x42, 2)).add(hash20(0xAA, 1));
        HashSetAddressPresence presence = HashSetAddressPresence.populateFrom(src);
        assertThat(presence.size(), is(equalTo(3)));
        assertThat(presence.containsAddress(hash20(0x42, 1)), is(true));
        assertThat(presence.containsAddress(hash20(0x42, 2)), is(true));
        assertThat(presence.containsAddress(hash20(0xAA, 1)), is(true));
        assertThat(presence.containsAddress(hash20(0x42, 999)), is(false));
        assertThat(presence.containsAddress(hash20(0x00, 1)), is(false));
    }

    @Test
    void requiresBackend_isFalse_soSourceCanBeReleasedAfterPopulation() {
        HashSetAddressPresence presence = HashSetAddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        assertThat(presence.requiresBackend(), is(false));
    }

    @Test
    void containsAddress_doesNotMutateCallerBuffer() {
        HashSetAddressPresence presence = HashSetAddressPresence.populateFrom(new ListIterable().add(hash20(0x10, 7)));
        ByteBuffer query = hash20(0x10, 7);
        int posBefore = query.position();
        int limitBefore = query.limit();
        presence.containsAddress(query);
        assertThat(query.position(), is(equalTo(posBefore)));
        assertThat(query.limit(), is(equalTo(limitBefore)));
    }

    @Test
    void populateFrom_duplicateEntries_storedOnce() {
        // Two identical entries in the source must collapse into one in the snapshot.
        ListIterable src = new ListIterable().add(hash20(0xAB, 42)).add(hash20(0xAB, 42));
        HashSetAddressPresence presence = HashSetAddressPresence.populateFrom(src);
        assertThat(presence.size(), is(equalTo(1)));
    }
}
