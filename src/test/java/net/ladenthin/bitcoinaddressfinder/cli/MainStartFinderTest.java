// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.engine.Finder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The start sequence of the {@code Find} command and what it does when the start fails part-way. */
public class MainStartFinderTest {

    /** A finder whose start fails at the last step and whose teardown may fail too. */
    private static final class FailingFinder extends Finder {

        private final RuntimeException startFailure;
        private final @Nullable RuntimeException abortFailure;
        private final AtomicInteger aborts = new AtomicInteger();

        FailingFinder(RuntimeException startFailure, @Nullable RuntimeException abortFailure) {
            super(new CFinder());
            this.startFailure = startFailure;
            this.abortFailure = abortFailure;
        }

        @Override
        public void startKeyProducer() {}

        @Override
        public void startConsumer() {}

        @Override
        public void configureProducer() {}

        @Override
        public void initProducer() {}

        @Override
        public void startProducer() {
            throw startFailure;
        }

        @Override
        public void abortStart() {
            aborts.incrementAndGet();
            final RuntimeException localAbortFailure = abortFailure;
            if (localAbortFailure != null) {
                throw localAbortFailure;
            }
        }
    }

    @Test
    public void startFinder_startFails_abortsOnceAndRethrowsTheStartFailure() {
        // arrange
        final RuntimeException startFailure = new IllegalStateException("start failed");
        final FailingFinder finder = new FailingFinder(startFailure, null);

        // act
        final Exception thrown = assertThrows(Exception.class, () -> Main.startFinder(finder));

        // assert
        assertThat(thrown, is(sameInstance(startFailure)));
        assertThat(thrown.getSuppressed(), is(emptyArray()));
        assertThat(finder.aborts.get(), is(equalTo(1)));
    }

    /**
     * The teardown can fail as well (the producer releaser rethrows what a release threw). That must
     * not replace the start failure, which is the one the user needs to see: it stays the thrown
     * exception and the teardown failure is attached to it.
     */
    @Test
    public void startFinder_startAndAbortFail_theStartFailureIsThrownWithTheAbortFailureSuppressed() {
        // arrange
        final RuntimeException startFailure = new IllegalStateException("start failed");
        final RuntimeException abortFailure = new IllegalStateException("teardown failed");
        final FailingFinder finder = new FailingFinder(startFailure, abortFailure);

        // act
        final Exception thrown = assertThrows(Exception.class, () -> Main.startFinder(finder));

        // assert
        assertThat(thrown, is(sameInstance(startFailure)));
        assertThat(thrown.getSuppressed(), is(arrayContaining(abortFailure)));
    }
}
