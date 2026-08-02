// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.ToString;
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
     * resources of a producer that is still running would pull them out from under it.
     *
     * @param producers the producers to release; ones already released are skipped
     */
    public synchronized void release(Collection<Producer> producers) {
        for (Producer producer : producers) {
            if (!released.add(producer)) {
                LOGGER.debug("Producer already released, skipping: {}", producer);
                continue;
            }
            LOGGER.info("Interrupt Producer: " + producer.toString());
            producer.interrupt();
            LOGGER.info("waitTillProducerNotRunning ...");
            producer.waitTillProducerNotRunning();
            producer.releaseProducer();
        }
    }
}
