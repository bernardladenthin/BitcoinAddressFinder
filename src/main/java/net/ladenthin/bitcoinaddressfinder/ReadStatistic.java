// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable statistic populated while reading address or secret files.
 */
public class ReadStatistic {

    /** Creates a new {@link ReadStatistic}. */
    public ReadStatistic() {
    }

    /** Number of successfully parsed entries. */
    public long successful = 0;
    /** Counts of skipped lines grouped by rejection reason. */
    public final Map<String, Long> unsupportedReasons = new LinkedHashMap<>();
    /**
     * Progress of the currently processed file, in percent.
     */
    public double currentFileProgress;

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
    public long getUnsupportedTotal() {
        return unsupportedReasons.values().stream().mapToLong(Long::longValue).sum();
    }

    @Override
    public String toString() {
        return "ReadStatistic{" + "successful=" + successful + ", unsupportedTotal=" + getUnsupportedTotal() + ", unsupportedReasons=" + unsupportedReasons + ", currentFileProgress=" + currentFileProgress + ", errors=" + errors + '}';
    }
}
