// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

/**
 * Marker interface for components that can be interrupted to support graceful shutdown.
 */
public interface Interruptable {
    /**
     * Requests that this component stop running as soon as possible.
     */
    void interrupt();
}
