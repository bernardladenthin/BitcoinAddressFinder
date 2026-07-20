// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

/**
 * Bounded progress reporter for the O(n) phases of any in-memory address-lookup construction.
 *
 * <p>Populating a backend over a large address set (tens or hundreds of millions of entries, up to
 * the billion-entry tier) is a single-threaded, multi-second-to-multi-minute — sometimes
 * multi-<em>hour</em> — and otherwise <em>silent</em> computation. Without output the process looks
 * hung, which is exactly the confusion this reporter removes: it emits a small, bounded number of
 * lines per phase (roughly one per {@code 100 / }{@link #SEGMENTS}&nbsp;% of progress) so a user
 * watching the log can see the build is alive and roughly how far along it is.
 *
 * <p>This helper is shared by <em>every</em> backend with a non-trivial build — the Binary Fuse
 * filters, the blocked Bloom filter, the Bloom accelerator, and the self-contained {@code HashSet} /
 * truncated-{@code long} snapshots — so that no backend can be silent for minutes on a large
 * database. It lives in the parent {@code persistence} package rather than in
 * {@code persistence.inmemory} precisely so the {@code persistence.bloom} side can use it too.
 *
 * <p>Each line carries percent complete, absolute counts, current throughput, an <b>ETA</b>, and
 * elapsed time — for example:
 * <pre>{@code
 * Blocked Bloom filter: inserting addresses: 40% (550991404/1377478516), 2.5M/s, ETA 5m30s, elapsed 3m18s.
 * }</pre>
 * The total is known up front (the backends take it from {@code AddressIterable.count()}, which is
 * O(1) on LMDB), so a meaningful ETA is available from the very first milestone.
 *
 * <p><b>Rate and ETA are computed over the most recent segment, not cumulatively.</b> Build
 * throughput is not stationary: while the source walk stays inside the OS page cache it runs several
 * times faster than once it leaves it (measured ~7&times; on the billion-entry tier, after which it
 * plateaus). A cumulative average would remain anchored to the fast early regime and badly
 * under-predict the remaining time; a recent-window rate follows the transition.
 *
 * <p>Interim percentage lines are suppressed for small inputs (fewer than {@link #MIN_ENTRIES}
 * units); callers still emit their own start/end lines, so tiny databases and unit-test runs stay
 * quiet instead of spamming a line per entry.
 *
 * <p>Output is delivered through a caller-supplied {@link LogSink} (typically the owning class's
 * {@code LOGGER::info}); this keeps the helper free of a {@code Logger} field and lets each backend
 * log under its own category.
 *
 * <p><strong>Not thread-safe.</strong> Each instance is advanced by the single construction thread.
 */
public final class FilterBuildProgress {

    /**
     * Destination for a single, fully rendered progress line. Kept as a minimal functional type so
     * this helper does not hold a logger reference of its own.
     */
    @FunctionalInterface
    public interface LogSink {
        /**
         * Emits one rendered progress line.
         *
         * @param message the complete, human-readable progress message
         */
        void log(String message);
    }

    /** Monotonic nanosecond time source; injectable so progress output is deterministic in tests. */
    @FunctionalInterface
    public interface NanoClock {
        /**
         * Returns the current value of the monotonic clock.
         *
         * @return nanoseconds from an arbitrary origin, as {@link System#nanoTime()}
         */
        long nanoTime();
    }

    /** Approximate number of interim percentage lines emitted per phase for large inputs. */
    public static final int SEGMENTS = 10;

    /** Inputs smaller than this (in units) emit no interim percentage lines. */
    public static final long MIN_ENTRIES = 1_000_000L;

    private final LogSink sink;
    private final String phase;
    private final long total;
    private final boolean interimEnabled;
    private final long step;
    private final NanoClock clock;
    private final long startNanos;
    private long nextThreshold;
    private long lastNanos;
    private long lastDone;

    /**
     * Creates a reporter for a single construction phase, timed by {@link System#nanoTime()}.
     *
     * @param sink  the destination for rendered progress lines
     * @param phase a human-readable phase label, e.g. {@code "Binary Fuse8 filter: peeling"}
     * @param total the total number of units this phase will process; interim lines are emitted
     *              only when this is at least {@link #MIN_ENTRIES}
     */
    public FilterBuildProgress(LogSink sink, String phase, long total) {
        this(sink, phase, total, System::nanoTime);
    }

    /**
     * Creates a reporter with an explicit clock, so tests can assert on the rendered rate and ETA.
     *
     * @param sink  the destination for rendered progress lines
     * @param phase a human-readable phase label
     * @param total the total number of units this phase will process
     * @param clock the monotonic time source used for rate and ETA
     */
    public FilterBuildProgress(LogSink sink, String phase, long total, NanoClock clock) {
        this.sink = sink;
        this.phase = phase;
        this.total = Math.max(0L, total);
        this.interimEnabled = this.total >= MIN_ENTRIES;
        this.step = Math.max(1L, this.total / SEGMENTS);
        this.nextThreshold = this.step;
        this.clock = clock;
        this.startNanos = clock.nanoTime();
        this.lastNanos = this.startNanos;
        this.lastDone = 0L;
    }

    /**
     * Reports that {@code done} units of this phase are complete. Emits at most one line per
     * {@code 100 / }{@link #SEGMENTS}&nbsp;% milestone crossed; calls below the next milestone, and
     * all calls for small inputs, are cheap no-ops.
     *
     * @param done the number of units completed so far (monotonically non-decreasing)
     */
    public void report(long done) {
        if (!interimEnabled || done < nextThreshold) {
            return;
        }
        long now = clock.nanoTime();
        long percent = total == 0L ? 100L : Math.min(100L, done * 100L / total);

        StringBuilder message = new StringBuilder()
                .append(phase)
                .append(": ")
                .append(percent)
                .append("% (")
                .append(done)
                .append('/')
                .append(total)
                .append(')');

        // Rate and ETA are derived from the MOST RECENT segment, never from the cumulative average.
        // Throughput here is not stationary: populating from a database larger than free RAM runs at
        // page-cache speed until the walk leaves the cached region, then drops sharply (measured ~7x
        // on the Full DB) and plateaus. A cumulative-average ETA stays anchored to the fast early
        // regime and badly under-predicts; a recent-window rate tracks the change.
        long segmentNanos = now - lastNanos;
        long segmentDone = done - lastDone;
        if (segmentNanos > 0L && segmentDone > 0L) {
            double perSecond = segmentDone * (double) NANOS_PER_SECOND / segmentNanos;
            message.append(", ").append(formatRate(perSecond));
            long remaining = total - done;
            if (remaining > 0L) {
                message.append(", ETA ").append(formatDuration((long) (remaining / perSecond)));
            }
        }
        message.append(", elapsed ")
                .append(formatDuration((now - startNanos) / NANOS_PER_SECOND))
                .append('.');

        sink.log(message.toString());

        lastNanos = now;
        lastDone = done;
        do {
            nextThreshold += step;
        } while (nextThreshold <= done);
    }

    /** Nanoseconds in one second. */
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /**
     * Renders a throughput with a compact magnitude suffix, e.g. {@code "2.9M/s"}.
     *
     * @param perSecond units processed per second
     * @return the rendered rate
     */
    private static String formatRate(double perSecond) {
        if (perSecond >= 1_000_000.0) {
            return String.format(java.util.Locale.ROOT, "%.1fM/s", perSecond / 1_000_000.0);
        }
        if (perSecond >= 1_000.0) {
            return String.format(java.util.Locale.ROOT, "%.0fk/s", perSecond / 1_000.0);
        }
        return String.format(java.util.Locale.ROOT, "%.0f/s", perSecond);
    }

    /**
     * Renders a duration compactly: {@code "45s"}, {@code "5m30s"}, {@code "1h05m"}.
     *
     * @param seconds the duration in seconds (negative values are clamped to zero)
     * @return the rendered duration
     */
    private static String formatDuration(long seconds) {
        long s = Math.max(0L, seconds);
        if (s >= 3600L) {
            return String.format(java.util.Locale.ROOT, "%dh%02dm", s / 3600L, (s % 3600L) / 60L);
        }
        if (s >= 60L) {
            return String.format(java.util.Locale.ROOT, "%dm%02ds", s / 60L, s % 60L);
        }
        return s + "s";
    }
}
