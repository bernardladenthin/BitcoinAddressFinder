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
     * Returns the throwable that terminated this producer's run loop, if it ended in failure.
     *
     * <p>{@link ProducerState#NOT_RUNNING} alone cannot distinguish "asked to stop" from "died", and
     * the exception itself never reaches the thread that started the producer: {@code run()} catches
     * it on the executor thread and logs it. An orchestrator waiting out a measurement window sees
     * only a producer that stopped producing, which is why {@code TuneConfiguration} reported the
     * partial throughput of a crashed arm as a valid measurement.
     *
     * @return the throwable that ended the run loop, or {@code null} if the producer has not run,
     *         is still running, or stopped without error
     */
    @Nullable
    Throwable getTerminalFailure();
}
