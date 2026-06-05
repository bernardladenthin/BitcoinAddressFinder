// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

/**
 * Thrown by {@link AddressTxtLine#fromLine} when an address line cannot be
 * accepted because its format is unsupported or malformed.
 *
 * <p>The exception message includes a human-readable reason describing why the
 * format was rejected. The {@link #getReason()} accessor returns just the
 * reason category (one of the {@code REASON_*} constants defined on
 * {@link AddressTxtLine}); it is used by callers (e.g. {@code AddressFile}
 * via {@code readStatistic.incrementUnsupported(e.getReason())}) to aggregate
 * rejection counters by category, so {@code reason} must remain a small
 * bounded vocabulary even when the human-readable {@link #getMessage()}
 * carries variable runtime detail.</p>
 *
 * <h2>Constructor matrix</h2>
 * <ul>
 *   <li>{@code (reason)} — plain rejection with no runtime detail.</li>
 *   <li>{@code (reason, cause)} — rejection wrapping a lower-level parser exception.</li>
 *   <li>{@code (reason, detail)} — rejection enriched with the offending input
 *       (or other in-scope state) so a stack trace alone identifies what was
 *       rejected. {@code getReason()} still returns just the category.</li>
 *   <li>{@code (reason, detail, cause)} — both of the above combined.</li>
 * </ul>
 */
public class AddressFormatNotAcceptedException extends Exception {

    /** Prefix prepended to every constructed exception message. */
    private static final String MESSAGE_PREFIX = "Address format not accepted: ";

    /** The rejection reason category supplied at construction time. */
    private final String reason;

    /**
     * Creates a new exception with a reason category only.
     *
     * @param reason a short, bounded-vocabulary description of why the format
     *               was rejected; returned verbatim by {@link #getReason()}
     */
    public AddressFormatNotAcceptedException(String reason) {
        super(MESSAGE_PREFIX + reason);
        this.reason = reason;
    }

    /**
     * Creates a new exception with a reason category and a chained cause.
     *
     * @param reason a short, bounded-vocabulary description of why the format
     *               was rejected; returned verbatim by {@link #getReason()}
     * @param cause  the original exception that led to the rejection
     */
    public AddressFormatNotAcceptedException(String reason, Throwable cause) {
        super(MESSAGE_PREFIX + reason, cause);
        this.reason = reason;
    }

    /**
     * Creates a new exception with a reason category enriched with runtime detail.
     *
     * <p>The detail (typically the offending input line or address) is
     * appended to {@link #getMessage()} as "{@code (input: <detail>)}" so a
     * stack trace alone identifies what was rejected. {@link #getReason()}
     * still returns just the bounded-vocabulary category — the detail does
     * not leak into the aggregation key.</p>
     *
     * @param reason a short, bounded-vocabulary description of why the format
     *               was rejected; returned verbatim by {@link #getReason()}
     * @param detail runtime detail (offending input or in-scope state) appended
     *               to the human-readable message
     */
    public AddressFormatNotAcceptedException(String reason, String detail) {
        super(MESSAGE_PREFIX + reason + " (input: " + detail + ")");
        this.reason = reason;
    }

    /**
     * Creates a new exception with a reason category, runtime detail, and a chained cause.
     *
     * <p>Same enrichment semantics as
     * {@link #AddressFormatNotAcceptedException(String, String)}; the cause is
     * chained for stack-trace propagation.</p>
     *
     * @param reason a short, bounded-vocabulary description of why the format
     *               was rejected; returned verbatim by {@link #getReason()}
     * @param detail runtime detail (offending input or in-scope state) appended
     *               to the human-readable message
     * @param cause  the original exception that led to the rejection
     */
    public AddressFormatNotAcceptedException(String reason, String detail, Throwable cause) {
        super(MESSAGE_PREFIX + reason + " (input: " + detail + ")", cause);
        this.reason = reason;
    }

    /**
     * Returns the bounded-vocabulary rejection reason category.
     *
     * <p>This is intentionally NOT the full human-readable message — callers
     * that aggregate rejection counts (e.g.
     * {@code readStatistic.incrementUnsupported(e.getReason())}) rely on the
     * returned string being one of a small fixed set (the {@code REASON_*}
     * constants defined on {@link AddressTxtLine}).</p>
     *
     * @return the bounded-vocabulary rejection reason category
     */
    public String getReason() {
        return reason;
    }
}
