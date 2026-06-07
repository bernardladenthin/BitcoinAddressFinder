// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

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
}
