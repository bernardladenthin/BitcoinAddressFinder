// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import zmq.Ctx;
import zmq.poll.IPollEvents;
import zmq.poll.Poller;

/**
 * Deterministic, socket-free reproduction of a race in jeromq 0.6.0's I/O poller, kept here as
 * the finding behind the flaky {@link ZmqResultBroadcasterTest} and as the guard for its
 * workaround. <b>This class asserts that the bug is present.</b> It goes red on the day a jeromq
 * build fixes it, which is the signal to drop {@code HANDSHAKE_IVL_MILLIS} from
 * {@link ZmqResultBroadcasterTest}. It contains no fix; the fix belongs in jeromq.
 *
 * <h2>Symptom</h2>
 * A freshly connected jeromq TCP socket (SUB, PULL in connect mode, any connecting type)
 * completes the TCP connect but never the ZMTP handshake: no greeting is ever sent, the peer
 * waits, and a PUB peer drops every message because no subscription arrives. jeromq only
 * recovers through its handshake timer ({@code ZMQ_HANDSHAKE_IVL}, default 30 s), after which it
 * reconnects and the second attempt practically always works. {@link ZmqResultBroadcasterTest}
 * had a 15 s budget, so it failed about once in twenty fresh forks with the whole budget elapsed
 * and nothing received, on Linux locally and in the ubuntu CI jobs (runs of 2026-08-26 and
 * 2026-09-20). Measured on the real PUB/SUB pattern (fresh JVM, bind, connect, subscribe,
 * publish every 100 ms): about 1 stalled connection in 100, rising under CPU load.
 *
 * <h2>Mechanism (jeromq-core {@code zmq.poll.Poller#run}, identical in 0.6.0 and on master)</h2>
 * Every loop iteration starts with one "retired" pass over {@code fdTable}: for each handle it
 * looks up {@code handle.fd.keyFor(selector)}; a cancelled handle gets its key cancelled and is
 * removed; a live handle whose key is {@code null} is registered; a live handle whose key is
 * valid gets {@code interestOps} updated. Then {@code retired} is reset and the loop selects.
 * The gap: a {@code SelectionKey} that was cancelled is deregistered only by the <i>next</i>
 * selection operation, so within the same pass {@code keyFor} still returns it for a new handle
 * on the same channel. That key is neither {@code null} nor valid, so nothing is registered, and
 * if the new handle is visited before the old one, the pass updates the interest ops of the
 * doomed key instead. Either way the new handle sits in {@code fdTable} with its ops set and no
 * selector key, and nothing ever revisits it until something else sets {@code retired} again.
 *
 * <h2>How the real code reaches it</h2>
 * {@code TcpConnecter.connectEvent} retires its poll handle and sends an ATTACH command carrying
 * the connected channel to the session; {@code StreamEngine.plug} then adds a handle for the
 * <b>same channel</b> and sets poll-in/poll-out. Normally the ATTACH is drained one iteration
 * later, after the pass that cancels the connecter's key and the select that deregisters it. In
 * the failing runs the select before the race returned the connecter's key and the I/O thread's
 * mailbox key <i>together</i>, the connecter was dispatched first, and the mailbox drain in the
 * same iteration processed the ATTACH, so both handles reached one pass. Captured nine times
 * with an instrumented poller: every time {@code lastSelected = rc=2: IOObject/OP_CONNECT
 * IOThread/OP_READ}, and the stalled engine handle showed {@code ops=5, key=NONE} with no pending
 * commands. Only the connecting side has this hand-off; the accept path registers a fresh channel
 * and never raced in any run. What makes the mailbox readable at exactly that moment is
 * <b>not</b> established (the obvious candidate, the signaler counting its wake-up byte after
 * writing it, was tested and refuted); it does not matter for the reproduction below, which
 * forces the two handles into one pass directly.
 *
 * <h2>Why a socket-free test</h2>
 * The socket-level trigger is a timing coincidence and cannot be forced through the public
 * socket API. The poller, however, is a public class, and the race is fully described by "retire
 * a handle and add a new handle for the same channel before the poller's next pass". A handler
 * that does exactly that from inside its own event callback lands both changes in one pass by
 * construction, so the reproduction is deterministic (20 of 20 iterations on 0.6.0).
 *
 * <h2>What a fix looks like and how the next step verifies it</h2>
 * Retire first, flush, then register: cancel every retired handle's key, call
 * {@code selector.selectNow()} once (and clear its selected set) so the cancellations are
 * deregistered, and only then register or update the live handles. With that change in a local
 * copy of {@code Poller.java}: this reproduction passes 50 of 50, and the real PUB/SUB harness
 * ran 300 fresh JVMs (900 connections) with no handshake cap and zero stalls, against 14 stalls
 * per ~1200 connections unpatched. Verification plan: fork jeromq, apply the fix in
 * {@code jeromq-core/src/main/java/zmq/poll/Poller.java}, port
 * {@link #handleReplacedInSamePass_replacementNeverReceivesEvents_bugStillPresent} inverted into
 * {@code jeromq-core/src/test/java/zmq/poll/} as a regression test, build, point BAF at the
 * fork, and expect this class to go red and {@link ZmqResultBroadcasterTest} to stay green with
 * {@code HANDSHAKE_IVL_MILLIS} removed. Upstream: github.com/zeromq/jeromq, branch
 * {@code master}, no release after 0.6.0 (Maven Central, 2024-02).
 *
 * <h2>Production exposure</h2>
 * {@link ZmqResultBroadcaster} binds and is not affected. {@code KeyProducerJavaZmq} in
 * {@code CONNECT} mode is: a hit delays the first key by up to 30 s and then self-heals through
 * jeromq's reconnect; nothing is lost because {@code PUSH}/{@code PULL} queue rather than drop.
 */
class JeromqPollerHandOffRaceTest {

    /** Repetitions of the same-pass sequence; the race is deterministic, so this is a margin. */
    private static final int ITERATIONS = 20;

    /** How long a handler that is never registered is given to prove it never fires. */
    private static final long NEVER_MILLIS = 300;

    /** How long a correctly registered handler may take to see an event. */
    private static final long SOON_SECONDS = 2;

    /** Delay before the second event; lets the poller run its registration pass first. */
    private static final long AFTER_PASS_MILLIS = 20;

    /** Timer that adds the replacement only after the poller has run a pass on its own. */
    private static final long LATER_TIMER_MILLIS = 10;

    /** Timer that registers an unrelated handle, which forces a pass and heals the stuck one. */
    private static final long HEAL_TIMER_MILLIS = 400;

    private static final int TIMER_ID = 1;

    // <editor-fold defaultstate="collapsed" desc="same pass: the bug">
    /**
     * The retire and the add for one channel land in the same pass: the replacement never gets a
     * selector key and never sees the second byte. Green means the bug is present.
     */
    @Test
    void handleReplacedInSamePass_replacementNeverReceivesEvents_bugStillPresent() throws Exception {
        int stalled = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            try (Fixture fixture = new Fixture("same-pass-" + i)) {
                // arrange: the retiring handler swaps itself for the replacement inside its callback
                fixture.startWithRetiringHandler(fixture::retireAndAddReplacementNow);

                // act
                fixture.writeByte();
                assertThat(
                        "retiring handler ran", fixture.retiringSeen.await(SOON_SECONDS, TimeUnit.SECONDS), is(true));
                Thread.sleep(AFTER_PASS_MILLIS);
                fixture.writeByte();

                // assert
                if (!fixture.replacementSeen.await(NEVER_MILLIS, TimeUnit.MILLISECONDS)) {
                    stalled++;
                }
            }
        }
        assertThat(
                "iterations whose replacement handle was never registered (jeromq 0.6.0 poller race)",
                stalled,
                is(ITERATIONS));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="separate passes: the control">
    /**
     * Same channel, same handlers, but the replacement is added from a timer that fires after the
     * poller has run a pass and a select on its own, so the cancelled key is already gone. This
     * is the normal (non-racing) order of the connecter-to-engine hand-off and works on 0.6.0.
     */
    @Test
    void handleReplacedInLaterPass_replacementReceivesEvents() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            try (Fixture fixture = new Fixture("later-pass-" + i)) {
                // arrange: retire now, add the replacement from a timer a few milliseconds later
                fixture.startWithRetiringHandler(fixture::retireNowAndAddReplacementLater);

                // act
                fixture.writeByte();
                assertThat(
                        "retiring handler ran", fixture.retiringSeen.await(SOON_SECONDS, TimeUnit.SECONDS), is(true));
                Thread.sleep(LATER_TIMER_MILLIS + AFTER_PASS_MILLIS);
                fixture.writeByte();

                // assert
                assertThat(
                        "replacement registered when added in a later pass",
                        fixture.replacementSeen.await(SOON_SECONDS, TimeUnit.SECONDS),
                        is(true));
            }
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="a later pass heals: why the workaround works">
    /**
     * After the race, any change that sets {@code retired} again makes the next pass find the
     * stuck handle with {@code keyFor == null} and register it; the pending byte is then
     * delivered. This is the path jeromq's handshake timer takes (engine error, handle removed,
     * reconnect), and the reason capping {@code ZMQ_HANDSHAKE_IVL} is a valid workaround.
     */
    @Test
    void stuckHandle_registeredByAnyLaterPass_thenReceivesThePendingEvent() throws Exception {
        try (Fixture fixture = new Fixture("heal")) {
            // arrange: race as above, plus a timer that later registers a handle on another channel
            fixture.startWithRetiringHandler(fixture::retireAndAddReplacementNowThenHealLater);

            // act
            fixture.writeByte();
            assertThat("retiring handler ran", fixture.retiringSeen.await(SOON_SECONDS, TimeUnit.SECONDS), is(true));
            Thread.sleep(AFTER_PASS_MILLIS);
            fixture.writeByte();

            // pre-assert: stuck until the healing pass
            assertThat(
                    "replacement stuck before the healing pass",
                    fixture.replacementSeen.await(NEVER_MILLIS, TimeUnit.MILLISECONDS),
                    is(false));

            // assert: the unrelated registration forced a pass, the stuck handle got its key
            assertThat(
                    "replacement delivered after an unrelated handle forced a pass",
                    fixture.replacementSeen.await(SOON_SECONDS, TimeUnit.SECONDS),
                    is(true));
        }
    }
    // </editor-fold>

    /** One poller, one pipe, and the two handlers that fight over the pipe's read end. */
    private static final class Fixture implements AutoCloseable {

        private final Ctx ctx = new Ctx();
        private final Poller poller;
        private final Pipe pipe;
        private final CountDownLatch retiringSeen = new CountDownLatch(1);
        private final CountDownLatch replacementSeen = new CountDownLatch(1);
        private final AtomicReference<Poller.Handle> retiringHandle = new AtomicReference<>();

        private final IPollEvents replacement = new IPollEvents() {
            @Override
            public void inEvent() {
                drain();
                replacementSeen.countDown();
            }
        };

        private Fixture(String name) throws IOException {
            poller = new Poller(ctx, name);
            pipe = Pipe.open();
            pipe.source().configureBlocking(false);
            pipe.sink().configureBlocking(false);
        }

        /** Registers a handler that runs the action (on the poller thread) at its first event, then polls. */
        void startWithRetiringHandler(Runnable actionOnWorkerThread) {
            final IPollEvents retiring = new IPollEvents() {
                @Override
                public void inEvent() {
                    drain();
                    actionOnWorkerThread.run();
                    retiringSeen.countDown();
                }
            };
            retiringHandle.set(poller.addHandle(pipe.source(), retiring));
            poller.setPollIn(retiringHandle.get());
            poller.start();
        }

        /** What {@code TcpConnecter.connectEvent} + {@code StreamEngine.plug} do when they share a pass. */
        void retireAndAddReplacementNow() {
            poller.removeHandle(retiringHandle.get());
            final Poller.Handle handle = poller.addHandle(pipe.source(), replacement);
            poller.setPollIn(handle);
        }

        /** The same hand-off with a pass and a select in between: the normal order. */
        void retireNowAndAddReplacementLater() {
            poller.removeHandle(retiringHandle.get());
            poller.addTimer(
                    LATER_TIMER_MILLIS,
                    new IPollEvents() {
                        @Override
                        public void timerEvent(int id) {
                            final Poller.Handle handle = poller.addHandle(pipe.source(), replacement);
                            poller.setPollIn(handle);
                        }
                    },
                    TIMER_ID);
        }

        /** The race, followed by an unrelated registration that forces a healing pass. */
        void retireAndAddReplacementNowThenHealLater() {
            retireAndAddReplacementNow();
            poller.addTimer(
                    HEAL_TIMER_MILLIS,
                    new IPollEvents() {
                        @Override
                        public void timerEvent(int id) {
                            try {
                                final Pipe unrelated = Pipe.open();
                                unrelated.source().configureBlocking(false);
                                poller.setPollIn(poller.addHandle(unrelated.source(), new IPollEvents() {}));
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                    },
                    TIMER_ID);
        }

        void writeByte() throws IOException {
            pipe.sink().write(ByteBuffer.wrap(new byte[] {1}));
        }

        private void drain() {
            try {
                final ByteBuffer buffer = ByteBuffer.allocate(16);
                while (pipe.source().read(buffer) > 0) {
                    buffer.clear();
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() throws IOException {
            poller.destroy();
            pipe.sink().close();
            pipe.source().close();
            ctx.terminate();
        }
    }
}
