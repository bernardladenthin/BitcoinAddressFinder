// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Thrown by {@link net.ladenthin.bitcoinaddressfinder.SecretsFile} when a
 * {@link CSecretFormat} value is encountered that is not handled by the
 * implementation. This indicates a programming error: a new enum constant was
 * added to {@link CSecretFormat} but the corresponding handling branch was not
 * implemented.
 */
public class UnknownSecretFormatException extends IllegalArgumentException {

    /** The offending secret format. */
    private final CSecretFormat secretFormat;

    /**
     * Creates a new exception for the unhandled secret format.
     *
     * @param secretFormat the offending secret format
     */
    public UnknownSecretFormatException(CSecretFormat secretFormat) {
        super("Unknown secret format: " + secretFormat);
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
