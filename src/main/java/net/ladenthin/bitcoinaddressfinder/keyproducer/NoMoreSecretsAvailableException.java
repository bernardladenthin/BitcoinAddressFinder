// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.keyproducer;

/**
 * Exception thrown when no more secrets are available.
 * 
 * This class extends RuntimeException because it is used with methods such as
 * {@link java.util.Random#nextBytes(byte[])} and {@link java.math.BigInteger#BigInteger(int, java.util.Random)},
 * which require a {@code Random} instance that only throws unchecked exceptions.
 */
public class NoMoreSecretsAvailableException extends RuntimeException {
    
    public NoMoreSecretsAvailableException() {
        super();
    }

    public NoMoreSecretsAvailableException(String message) {
        super(message);
    }

    public NoMoreSecretsAvailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoMoreSecretsAvailableException(Throwable cause) {
        super(cause);
    }
}
