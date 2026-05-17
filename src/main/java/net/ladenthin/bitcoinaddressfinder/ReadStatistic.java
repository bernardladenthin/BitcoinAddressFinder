// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReadStatistic {

    public long successful = 0;
    public final Map<String, Long> unsupportedReasons = new LinkedHashMap<>();
    /**
     * In percent.
     */
    public double currentFileProgress;

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
