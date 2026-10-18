// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.ToString;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stops and releases producers, guaranteeing that each one is released exactly once.
 *
 * <p>The guarantee is the whole point of this class. Teardown is reached from two directions — the
 * ordinary completion path once a run has finished, and the JVM shutdown hook — and nothing keeps
 * those apart: a {@code Ctrl+C} arriving while a finished run is releasing its producers is enough to
 * have both running at the same moment. Previously each caller took its own snapshot of the producer
 * list before either had cleared it, so both released the same producer. Releasing an OpenCL producer
 * twice is rejected by the driver with {@code CL_INVALID_MEM_OBJECT}, which ended an otherwise
 * successful run with a fatal error.
 *
 * <p>Producers are tracked by identity, not by {@code equals}: two distinct producers configured
 * identically are separate native resources and must each be released, while the same instance handed
 * in twice must not be.
 *
 * <p>An instance class rather than a static utility, per the project convention on helper classes:
 * the owner holds one and the release history lives with it.
 */
@ToString
public class ProducerReleaser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerReleaser.class);

    /**
     * Producers already released by this instance, compared by identity. Guarded by the instance
     * lock together with the release itself, so a second caller cannot slip between the check and
     * the release.
     */
    @ToString.Exclude
    private final Set<Producer> released = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Creates a new {@link ProducerReleaser}. */
    public ProducerReleaser() {}

    /**
     * Stops and releases every producer that this releaser has not released before.
     *
     * <p>Each producer is interrupted, awaited and then released, in that order: releasing the
     * resources of a producer that is still running would pull them out from under it. A producer
     * that is still running once the wait returns (it timed out after
     * {@code shutdownTimeoutSeconds}, or was interrupted) is therefore not released at all; an error
     * is logged and its resources are left to the process exit.
     *
     * <p>A producer whose teardown throws does not stop the others: every producer is handled, and
     * then the first failure is rethrown with later ones attached as suppressed exceptions.
     *
     * @param producers the producers to release; ones already released are skipped
     * @throws RuntimeException the first failure of a producer's teardown, after all were handled
     */
    public synchronized void release(Collection<Producer> producers) {
        // One producer that fails to release (an OpenCL release call raises on a driver error) must not keep
        // the others running; the first failure is rethrown once every producer was handled.
        @Nullable RuntimeException firstFailure = null;
        for (Producer producer : producers) {
            if (!released.add(producer)) {
                LOGGER.debug("Producer already released, skipping: {}", producer);
                continue;
            }
            try {
                LOGGER.info("Interrupt Producer: " + producer.toString());
                producer.interrupt();
                LOGGER.info("waitTillProducerNotRunning ...");
                producer.waitTillProducerNotRunning();
                if (producer.getState() == ProducerState.RUNNING) {
                    // The wait timed out or was interrupted. Its resources (an OpenCL kernel, queue,
                    // buffers) would be freed while the producer may still use them inside the driver;
                    // leaking them at shutdown is the lesser harm.
                    LOGGER.error(
                            "Producer is still running after the wait; its resources are not released: {}", producer);
                    continue;
                }
                producer.releaseProducer();
            } catch (RuntimeException e) {
                LOGGER.error("Releasing producer failed, releasing the others: " + producer, e);
                if (firstFailure == null) {
                    firstFailure = e;
                } else {
                    firstFailure.addSuppressed(e);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
