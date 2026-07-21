// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.statistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.io.AddressFormatNotAcceptedException;

/**
 * Mutable statistic populated while reading address or secret files.
 *
 * <h2>toString contract</h2>
 * <p>The {@link ToString} annotation includes every instance field — {@code successful},
 * {@code unsupportedReasons}, {@code currentFileProgress}, {@code errors} — plus the
 * derived {@link #getUnsupportedTotal()} getter (marked with {@link ToString.Include} so
 * Lombok also calls it). The getter is in the output so log lines can show the total
 * skip count without the reader having to sum {@code unsupportedReasons.values()}
 * themselves.
 *
 * <p>Lombok renders the getter under the {@code name = "unsupportedTotal"} label
 * (without that override Lombok would emit the raw method name {@code getUnsupportedTotal},
 * which is less natural for logs and breaks the property-name convention). Derived members
 * <em>after</em> the regular field set, so the rendered order is
 * {@code (successful=…, unsupportedReasons=…, currentFileProgress=…, errors=…,
 * unsupportedTotal=…)}. No test or downstream consumer depends on the field ordering;
 * if that ever changes, switch the per-member declarations to {@link ToString.Include}
 * with explicit {@code rank} values.
 */
@ToString
public class ReadStatistic {

    /** Creates a new {@link ReadStatistic}. */
    public ReadStatistic() {}

    /** Number of successfully parsed entries. */
    public long successful = 0;
    /** Counts of skipped lines grouped by rejection reason. */
    public final Map<String, Long> unsupportedReasons = new LinkedHashMap<>();
    /**
     * Progress of the currently processed file, in percent.
     *
     * <p>{@code volatile} so a separate progress-reporter thread can read a fresh value while the
     * reading thread updates it per line (see {@code AddressFilesToLMDB}).
     */
    public volatile double currentFileProgress;

    /** Lines that failed to parse with a logged error. */
    public final List<String> errors = new ArrayList<>();

    /**
     * Increments the counter for the given unsupported reason by one.
     * If the reason has not been seen before, it is added with a count of 1.
     *
     * @param reason the reason string from {@link AddressFormatNotAcceptedException#getReason()}
     */
    public void incrementUnsupported(String reason) {
        unsupportedReasons.merge(reason, 1L, Long::sum);
    }

    /**
     * Returns the total number of unsupported lines across all reasons.
     *
     * @return sum of all per-reason counts in {@link #unsupportedReasons}
     */
    @ToString.Include(name = "unsupportedTotal")
    public long getUnsupportedTotal() {
        return unsupportedReasons.values().stream().mapToLong(Long::longValue).sum();
    }
}
