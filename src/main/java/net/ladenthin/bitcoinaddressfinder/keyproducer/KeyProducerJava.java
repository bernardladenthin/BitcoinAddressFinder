// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJava;
import org.slf4j.Logger;

/**
 * Base class for Java-side {@link KeyProducer} implementations parameterised by their config type.
 *
 * @param <T> the configuration type for the concrete subclass
 */
public abstract class KeyProducerJava<T extends CKeyProducerJava> extends AbstractKeyProducer {

    /** Configuration for the concrete key producer. */
    protected final T cKeyProducerJava;

    /** SLF4J logger used by the concrete subclass. */
    protected final Logger logger;

    /**
     * Creates a new key producer with the given configuration and logger.
     *
     * @param cKeyProducerJava the configuration
     * @param logger           the logger
     */
    public KeyProducerJava(T cKeyProducerJava, Logger logger) {
        this.cKeyProducerJava = cKeyProducerJava;
        this.logger = logger;
    }

    /**
     * Validates that the requested work size is within {@code [0, maxWorkSize]}.
     *
     * @param overallWorkSize the requested number of secrets
     * @param maxWorkSize     the configured maximum work size
     * @throws NoMoreSecretsAvailableException never thrown directly but declared for subclass use
     * @throws IllegalArgumentException if {@code overallWorkSize} is outside the allowed range
     */
    public void verifyWorkSize(int overallWorkSize, int maxWorkSize) throws NoMoreSecretsAvailableException {
        if (overallWorkSize < 0 || overallWorkSize > maxWorkSize) {
            throw new IllegalArgumentException("Unreasonable work size: " + overallWorkSize);
        }
    }

    @Override
    public Logger getLogger() {
        return logger;
    }
}
