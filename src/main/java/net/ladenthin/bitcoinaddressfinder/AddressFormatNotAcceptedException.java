// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
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

    private final String reason;

    public AddressFormatNotAcceptedException(String reason) {
        super("Address format not accepted: " + reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
