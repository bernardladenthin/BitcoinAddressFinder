// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.vmlens;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import com.vmlens.api.AllInterleavings;
import java.math.BigInteger;
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
    }

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
}
