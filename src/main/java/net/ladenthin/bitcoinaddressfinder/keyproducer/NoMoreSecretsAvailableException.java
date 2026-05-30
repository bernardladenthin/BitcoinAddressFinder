// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

/**
 * Exception thrown when no more secrets are available.
 *
 * This class extends RuntimeException because it is used with methods such as
 * {@link java.util.Random#nextBytes(byte[])} and {@link java.math.BigInteger#BigInteger(int, java.util.Random)},
 * which require a {@code Random} instance that only throws unchecked exceptions.
 */
public class NoMoreSecretsAvailableException extends RuntimeException {

    /** Creates a new exception with no message or cause. */
    public NoMoreSecretsAvailableException() {
        super();
    }

    /**
     * Creates a new exception with the given message.
     *
     * @param message detail message
     */
    public NoMoreSecretsAvailableException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message detail message
     * @param cause   the underlying cause
     */
    public NoMoreSecretsAvailableException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception with the given cause.
     *
     * @param cause the underlying cause
     */
    public NoMoreSecretsAvailableException(Throwable cause) {
        super(cause);
    }
}
