// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.engine;

/**
 * Marker interface for components supporting an explicit, blocking shutdown call.
 */
public interface Shutdown {
    /**
     * Requests an orderly shutdown of this component.
     */
    void shutdown();
}
