// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.engine;

/**
 * The one thing the orchestrator needs from a result broadcaster, regardless of its transport.
 *
 * <p>Announcing the grid cannot happen when a broadcaster is built: the producers do not exist yet
 * at that point, and only they know the size a base is expanded to. This contract exists so the
 * orchestrator can come back later and tell every broadcaster at once, without knowing whether it
 * speaks WebSocket, ZeroMQ or plain TCP.
 *
 * <p>How a transport gets that message to a client differs and is its own business: a WebSocket can
 * greet on connect and catch up whoever was already there, a ZeroMQ publisher cannot see its
 * subscribers at all and has to republish, and a TCP server greets on accept.
 */
@FunctionalInterface
interface ResultBroadcaster {

    /**
     * Publishes the grid configuration a client needs in order to send usable ranges.
     *
     * @param batchSizeInBits             the grid size each base is expanded to
     * @param batchUsePrivateKeyIncrement whether one base yields a whole grid
     */
    void announceConfiguration(int batchSizeInBits, boolean batchUsePrivateKeyIncrement);
}
