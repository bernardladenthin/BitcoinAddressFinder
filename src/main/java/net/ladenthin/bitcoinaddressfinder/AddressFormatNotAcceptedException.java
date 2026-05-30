// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

/**
 * Thrown by {@link AddressTxtLine#fromLine} when an address line cannot be
 * accepted because its format is unsupported or malformed.
 * The exception message includes a human-readable reason describing why the
 * format was rejected.
 */
public class AddressFormatNotAcceptedException extends Exception {

    /** The rejection reason supplied at construction time. */
    private final String reason;

    /**
     * Creates a new exception with a human-readable reason.
     *
     * @param reason a short description of why the format was rejected
     */
    public AddressFormatNotAcceptedException(String reason) {
        super("Address format not accepted: " + reason);
        this.reason = reason;
    }

    /**
     * Creates a new exception with a human-readable reason and a chained cause.
     *
     * @param reason a short description of why the format was rejected
     * @param cause  the original exception that led to the rejection
     */
    public AddressFormatNotAcceptedException(String reason, Throwable cause) {
        super("Address format not accepted: " + reason, cause);
        this.reason = reason;
    }

    /**
     * Returns the human-readable rejection reason.
     *
     * @return the human-readable rejection reason
     */
    public String getReason() {
        return reason;
    }
}
