// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.consumer;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import org.jspecify.annotations.Nullable;

/**
 * Receives batches of derived public keys from producers and matches them against the database.
 */
public interface Consumer extends Interruptable {

    /**
     * Hands off a batch of public keys to the consumer.
     *
     * <p>The batch travels as one unit all the way to the check, which is what allows the consumer
     * to report the outcome per batch. The secret base is carried along for that report: it
     * identifies the swept range to whoever submitted it.
     *
     * @param publicKeyBytes the batch of public keys derived by a producer
     * @param secretBase     the aligned start of the swept grid, or {@code null} when the batch is a
     *                       set of independent secrets rather than one expanded range
     * @throws InterruptedException if the calling thread is interrupted while enqueueing
     */
    void consumeKeys(PublicKeyBytes[] publicKeyBytes, @Nullable BigInteger secretBase) throws InterruptedException;

    /**
     * Starts the internal consumer thread(s).
     */
    void startConsumer();
}
