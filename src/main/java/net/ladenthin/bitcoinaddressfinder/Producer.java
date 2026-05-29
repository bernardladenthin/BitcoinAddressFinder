// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.util.Random;

/**
 * Generates candidate private keys and forwards them to the {@link Consumer} via the shared queue.
 */
public interface Producer extends Runnable, Interruptable, ProducerStateProvider {

    /**
     * Initialize the producer to procue keys with
     * {@link #produceKeys()} continuously.
     *
     * @throws Exception if initialisation fails for any reason
     */
    void initProducer() throws Exception;

    /**
     * Release the producer.
     */
    void releaseProducer();

    /**
     * Create multiple keys for a specific bit length using {@link Random} and
     * push them to the {@link Consumer}.
     *
     * Specifically, any 256-bit number between {@code 0x1} and {@link PublicKeyBytes#MAX_PRIVATE_KEY} is a valid private key.
     *
     * @throws Exception if key production fails for any reason
     */
    void produceKeys() throws Exception;

    /**
     * Processes a provided secret base, which may be used for key generation or other cryptographic
     * operations in the implementation of the producer.
     *
     * @param secretBase the secret base value to be processed, represented as a {@link BigInteger}
     */
    void processSecretBase(BigInteger secretBase);

    /**
     * Processes an array of secrets represented as BigInteger values.
     *
     * @param secrets an array of BigInteger objects representing the secrets to be processed
     */
    void processSecrets(BigInteger[] secrets);

    /**
     * Blocks till the producer is not running anymore.
     */
    void waitTillProducerNotRunning();
}
