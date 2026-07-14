// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.inmemory;

/**
 * Bounded progress reporter for the O(n) phases of Binary Fuse filter construction.
 *
 * <p>Building a filter over a large address set (tens or hundreds of millions of entries) is a
 * single-threaded, multi-second-to-multi-minute, otherwise <em>silent</em> computation. Without
 * any output the process looks hung — which is exactly the confusion this reporter removes: it
 * emits a small, bounded number of lines per phase (roughly one per {@code 100 / }{@link #SEGMENTS}
 * &nbsp;% of progress) so a user watching the log can see the build is alive and roughly how far
 * along it is.
 *
 * <p>Interim percentage lines are suppressed for small inputs (fewer than {@link #MIN_ENTRIES}
 * units); callers still emit their own start/end lines, so tiny databases and unit-test runs stay
 * quiet instead of spamming a line per entry.
 *
 * <p>Output is delivered through a caller-supplied {@link LogSink} (typically the owning filter
 * class's {@code LOGGER::info}); this keeps the helper free of a {@code Logger} field and lets each
 * filter log under its own category.
 *
 * <p><strong>Not thread-safe.</strong> Each instance is advanced by the single construction thread.
 */
final class FilterBuildProgress {

    /**
     * Destination for a single, fully rendered progress line. Kept as a minimal functional type so
     * this helper does not hold a logger reference of its own.
     */
    @FunctionalInterface
    interface LogSink {
        /**
         * Emits one rendered progress line.
         *
         * @param message the complete, human-readable progress message
         */
        void log(String message);
    }

    /** Approximate number of interim percentage lines emitted per phase for large inputs. */
    static final int SEGMENTS = 10;

    /** Inputs smaller than this (in units) emit no interim percentage lines. */
    static final long MIN_ENTRIES = 1_000_000L;

    private final LogSink sink;
    private final String phase;
    private final long total;
    private final boolean interimEnabled;
    private final long step;
    private long nextThreshold;

    /**
     * Creates a reporter for a single construction phase.
     *
     * @param sink  the destination for rendered progress lines
     * @param phase a human-readable phase label, e.g. {@code "Binary Fuse8 filter: peeling"}
     * @param total the total number of units this phase will process; interim lines are emitted
     *              only when this is at least {@link #MIN_ENTRIES}
     */
    FilterBuildProgress(LogSink sink, String phase, long total) {
        this.sink = sink;
        this.phase = phase;
        this.total = Math.max(0L, total);
        this.interimEnabled = this.total >= MIN_ENTRIES;
        this.step = Math.max(1L, this.total / SEGMENTS);
        this.nextThreshold = this.step;
    }

    /**
     * Reports that {@code done} units of this phase are complete. Emits at most one line per
     * {@code 100 / }{@link #SEGMENTS}&nbsp;% milestone crossed; calls below the next milestone, and
     * all calls for small inputs, are cheap no-ops.
     *
     * @param done the number of units completed so far (monotonically non-decreasing)
     */
    void report(long done) {
        if (!interimEnabled || done < nextThreshold) {
            return;
        }
        long percent = total == 0L ? 100L : Math.min(100L, done * 100L / total);
        sink.log(phase + ": " + percent + "% (" + done + "/" + total + ").");
        do {
            nextThreshold += step;
        } while (nextThreshold <= done);
    }
}
