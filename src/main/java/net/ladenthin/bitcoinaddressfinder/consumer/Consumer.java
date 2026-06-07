// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.consumer;

import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;

/**
 * Receives batches of derived public keys from producers and matches them against the database.
 */
public interface Consumer extends Interruptable {

    /**
     * Hands off a batch of public keys to the consumer.
     *
     * @param publicKeyBytes the batch of public keys derived by a producer
     * @throws InterruptedException if the calling thread is interrupted while enqueueing
     */
    void consumeKeys(PublicKeyBytes[] publicKeyBytes) throws InterruptedException;

    /**
     * Starts the internal consumer thread(s).
     */
    void startConsumer();
}
