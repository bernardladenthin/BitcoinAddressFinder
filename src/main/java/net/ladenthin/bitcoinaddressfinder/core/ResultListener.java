// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.core;

/**
 * Receives the outcome of every checked batch.
 *
 * <p>This is the seam that lets results leave the process by some route other than the log. It
 * carries no transport of its own on purpose: the consumer knows only this interface, and whatever
 * forwards the results — a socket, a WebSocket, ZeroMQ — is wired in from above. That keeps the
 * network libraries out of the consumer entirely.
 *
 * <p><b>Implementations must not block and must not throw.</b> They are invoked on the consumer's
 * worker threads, directly in the path that drains the key queue; a slow or failing listener would
 * stall or break the scan itself. Buffer, drop, or log — but return promptly.
 */
public interface ResultListener {

    /**
     * Called once per batch, after it has been checked against the database.
     *
     * @param batchResult what the batch contained and what was found in it, possibly nothing
     */
    void onBatchChecked(BatchResult batchResult);
}
