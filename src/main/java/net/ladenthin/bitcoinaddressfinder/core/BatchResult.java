// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.core;

import java.math.BigInteger;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of one checked batch — reported whether or not anything was found.
 *
 * <p>Reporting the empty case is the point. A client sweeping a range needs to distinguish "checked,
 * nothing there" from "never checked"; if only hits were reported the two would look identical, and
 * a range dropped when the process stopped would silently be recorded as clean. Because this is
 * emitted after the batch has been checked against the database, it is also an exact completion
 * signal: no dispatch-ahead lag that a client would have to compensate for.
 *
 * @param secretBase   the aligned start of the swept grid, or {@code null} when the batch was a set
 *                     of independent secrets rather than one expanded range
 * @param checkedCount how many candidates this batch carried
 * @param hits         everything found, empty when nothing matched
 */
public record BatchResult(@Nullable BigInteger secretBase, int checkedCount, List<Hit> hits) {

    /**
     * Creates a batch result with an unmodifiable hit list.
     *
     * @param secretBase   the aligned start of the swept grid, or {@code null}
     * @param checkedCount how many candidates this batch carried
     * @param hits         everything found, empty when nothing matched
     */
    public BatchResult {
        hits = List.copyOf(hits);
    }
}
