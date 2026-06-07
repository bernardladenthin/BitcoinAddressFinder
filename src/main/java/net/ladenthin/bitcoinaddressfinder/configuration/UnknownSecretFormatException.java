// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Thrown by {@link net.ladenthin.bitcoinaddressfinder.io.SecretsFile} when a
 * {@link CSecretFormat} value is encountered that is not handled by the
 * implementation. This indicates a programming error: a new enum constant was
 * added to {@link CSecretFormat} but the corresponding handling branch was not
 * implemented.
 */
public class UnknownSecretFormatException extends IllegalArgumentException {

    /** Prefix prepended to every constructed exception message. */
    private static final String MESSAGE_PREFIX = "Unknown secret format: ";

    /** The offending secret format. */
    private final CSecretFormat secretFormat;

    /**
     * Creates a new exception for the unhandled secret format.
     *
     * @param secretFormat the offending secret format; returned by {@link #getSecretFormat()}
     */
    public UnknownSecretFormatException(CSecretFormat secretFormat) {
        super(MESSAGE_PREFIX + secretFormat);
        this.secretFormat = secretFormat;
    }

    /**
     * Creates a new exception for the unhandled secret format, chaining a cause.
     *
     * @param secretFormat the offending secret format; returned by {@link #getSecretFormat()}
     * @param cause        the underlying cause
     */
    public UnknownSecretFormatException(CSecretFormat secretFormat, Throwable cause) {
        super(MESSAGE_PREFIX + secretFormat, cause);
        this.secretFormat = secretFormat;
    }

    /**
     * Returns the offending secret format.
     *
     * @return the offending secret format
     */
    public CSecretFormat getSecretFormat() {
        return secretFormat;
    }
}
