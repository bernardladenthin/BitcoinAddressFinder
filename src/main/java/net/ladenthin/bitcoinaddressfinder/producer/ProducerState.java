// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

/**
 * Life-cycle states reported by a {@link Producer}.
 */
public enum ProducerState {
    /** The producer instance has not yet been initialised. */
    UNINITIALIZED,
    /** {@link Producer#initProducer()} has completed successfully. */
    INITIALIZED,
    /** The producer's main loop is currently running. */
    RUNNING,
    /** The producer has stopped after running and is no longer producing. */
    NOT_RUNNING
}
