// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import org.jspecify.annotations.Nullable;

/**
 * Provides read access to a producer's current life-cycle state.
 */
public interface ProducerStateProvider {
    /**
     * Returns the current life-cycle state.
     *
     * @return the current {@link ProducerState}
     */
    ProducerState getState();

    /**
     * Returns the most recent failure this producer hit, whether or not it survived it.
     *
     * <p>Producers fail in two ways and <b>neither reaches the thread that started them</b>:
     *
     * <ul>
     *   <li><b>Fatal</b> — an exception escapes {@code produceKeys()}; {@code run()} catches it on
     *       the executor thread, logs it and leaves the loop. {@link ProducerState#NOT_RUNNING}
     *       cannot then distinguish "asked to stop" from "died".
     *   <li><b>Swallowed</b> — a per-secret failure is caught, logged and the loop continues, which
     *       is what {@code ProducerOpenCL.processSecretBase} does with a {@code CLException}. The
     *       producer stays alive and looks entirely healthy from outside while producing nothing.
     * </ul>
     *
     * <p>The second is the one that misled a real measurement: an Intel Arc threw
     * {@code CL_OUT_OF_RESOURCES} 40 times inside one 20 s window without the producer ever dying,
     * and the arm was reported as {@code 1,022,288 candidates/s}. Both kinds are recorded here so a
     * caller can ask "did anything go wrong during this window" rather than having to infer it from
     * a suspiciously shaped throughput number.
     *
     * @return the most recent failure, or {@code null} if the producer has not failed
     */
    @Nullable
    Throwable getLastFailure();
}
