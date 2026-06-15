// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.vmlens;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.vmlens.api.AllInterleavings;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaReceiver;
import net.ladenthin.bitcoinaddressfinder.keyproducer.AbstractKeyProducerQueueBuffered;
import net.ladenthin.bitcoinaddressfinder.secret.NoMoreSecretsAvailableException;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;

/**
 * vmlens interleaving analysis of the shutdown hand-off in
 * {@link AbstractKeyProducerQueueBuffered}.
 *
 * <p>The base class coordinates three roles over a {@code BlockingQueue} with a
 * hand-rolled protocol: a consumer parked in {@code createSecrets(...)} (here on an
 * indefinite {@code take()}), and a shutdown caller that performs two separate
 * writes — set {@code volatile shouldStop = true}, then {@code offer} a
 * reference-identity {@code SHUTDOWN_SENTINEL}. The consumer in turn does two
 * separate reads — check {@code shouldStop} at the top of its loop, then block on
 * {@code take()}.</p>
 *
 * <p>The liveness invariant that must hold under <em>every</em> interleaving:
 * {@code signalShutdown()} always releases a parked consumer, which terminates with
 * {@link NoMoreSecretsAvailableException} — there is no schedule in which the
 * consumer is left blocked forever (lost wakeup). This is exactly the multi-write /
 * multi-read ordering class vmlens enumerates and that the trivial single-{@code
 * AtomicLong} smoke test cannot. Unlike the model-gated thread-pool tests in
 * {@code AbstractKeyProducerQueueBufferedTest}, vmlens drives this exhaustively.</p>
 *
 * <p>Raw {@link Thread} usage is intentional here (the production "use executor
 * services" convention does not apply): vmlens explores the interleavings of the
 * threads it directly manages.</p>
 */
public class KeyProducerQueueBufferedInterleavingTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));

    /** Minimal concrete receiver that blocks indefinitely and exposes the shutdown signal. */
    private static final class ShutdownProbe extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaReceiver> {
        ShutdownProbe(CKeyProducerJavaReceiver config, KeyUtility keyUtility) {
            super(config, keyUtility);
        }

        @Override
        protected int getReadTimeout() {
            return -1; // take(), block forever until a secret or the shutdown sentinel arrives
        }

        @Override
        public void interrupt() {
            signalShutdown();
        }

        void addForTest(byte[] secret) {
            addSecret(secret);
        }
    }

    /** secp256k1 private-key byte length; a real secret has this many bytes, the sentinel has zero. */
    private static final int PRIVATE_KEY_BYTES = 32;

    /**
     * Verifies that {@code signalShutdown()} always unblocks a consumer parked in
     * {@code createSecrets(...)} — it terminates with
     * {@link NoMoreSecretsAvailableException} in every interleaving (no lost wakeup).
     *
     * @throws InterruptedException if joining a worker thread is interrupted
     */
    @Test
    public void shutdownAlwaysUnblocksParkedConsumer() throws InterruptedException {
        try (AllInterleavings allInterleavings =
                new AllInterleavings("AbstractKeyProducerQueueBuffered.shutdownUnblocks")) {
            while (allInterleavings.hasNext()) {
                final ShutdownProbe producer = new ShutdownProbe(new CKeyProducerJavaReceiver(), keyUtility);
                final AtomicReference<Throwable> outcome = new AtomicReference<>();

                final Thread consumer = new Thread(() -> {
                    try {
                        final BigInteger[] secrets = producer.createSecrets(1, true);
                        outcome.compareAndSet(
                                null,
                                new AssertionError("createSecrets returned " + secrets.length
                                        + " secrets instead of terminating on shutdown"));
                    } catch (Throwable t) {
                        outcome.compareAndSet(null, t);
                    }
                });
                final Thread shutdown = new Thread(producer::interrupt);

                consumer.start();
                shutdown.start();
                consumer.join();
                shutdown.join();

                assertThat(outcome.get(), is(instanceOf(NoMoreSecretsAvailableException.class)));
            }
        }
    }

    /**
     * Verifies the drop-after-stop invariant: when {@code addSecret(...)} races
     * {@code signalShutdown()}, a subsequent consume yields exactly one of two
     * outcomes in every interleaving — the real key (it was enqueued ahead of the
     * sentinel) or a clean {@link NoMoreSecretsAvailableException} — and the
     * zero-length {@code SHUTDOWN_SENTINEL} is <em>never</em> decoded as a key.
     *
     * <p>This is a regression guard, not a bug reproduction: the protocol is correct
     * by construction (source-side drop via the {@code shouldStop} guard in
     * {@code addSecret}; the consumer always throws on the sentinel so it can never
     * advance to an element behind it; and a reference-identity check plus a
     * {@code length != 32} backstop keep the sentinel from being mistaken for a key).
     * vmlens proves it holds across <em>all</em> writer interleavings.</p>
     *
     * @throws InterruptedException if joining a worker thread is interrupted
     */
    @Test
    public void shutdownNeverLetsTheSentinelBeDecodedAsAKey() throws InterruptedException {
        try (AllInterleavings allInterleavings =
                new AllInterleavings("AbstractKeyProducerQueueBuffered.dropAfterStop")) {
            while (allInterleavings.hasNext()) {
                final ShutdownProbe producer = new ShutdownProbe(new CKeyProducerJavaReceiver(), keyUtility);
                final byte[] key = new byte[PRIVATE_KEY_BYTES];
                Arrays.fill(key, (byte) 0xAB);
                final BigInteger expectedKey = new BigInteger(1, key);
                final AtomicReference<Throwable> producerFailure = new AtomicReference<>();

                final Thread transport = new Thread(() -> {
                    try {
                        producer.addForTest(key);
                    } catch (Throwable t) {
                        producerFailure.compareAndSet(null, t);
                    }
                });
                final Thread shutdown = new Thread(producer::interrupt);

                transport.start();
                shutdown.start();
                transport.join();
                shutdown.join();
                assertThat(producerFailure.get(), is(nullValue()));

                // The sentinel is always enqueued (unbounded queue), so this consume
                // never blocks; it must resolve to the real key or a clean termination.
                try {
                    final BigInteger[] secrets = producer.createSecrets(1, true);
                    assertThat(secrets.length, is(1));
                    assertThat(
                            "only the real key may ever be decoded, never the sentinel", secrets[0], is(expectedKey));
                } catch (NoMoreSecretsAvailableException terminated) {
                    // Acceptable: the consumer observed the sentinel / shouldStop and stopped.
                }
            }
        }
    }
}
