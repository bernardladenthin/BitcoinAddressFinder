// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBDelta;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.statistics.SlidingWindowRate;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes every address that is present in at least one of the "other" databases but <b>not</b> in the
 * reference database to a plaintext file — one Base58 P2PKH ({@code 1...}) address per line.
 *
 * <h2>Cursor merge — near-zero memory</h2>
 * LMDB stores entries sorted by hash160, so each database can be streamed in key order via a single
 * cursor. The reference and all other databases are walked simultaneously as a k-way sorted merge: for
 * the smallest hash160 across the other cursors, it is emitted iff it does not appear in the reference,
 * then every cursor sitting on that key is advanced (which deduplicates across the other databases).
 * The output is streamed directly to the sink, so memory is just the per-cursor head — independent of
 * the delta size — and the whole pass is sequential I/O rather than billions of random lookups.
 *
 * <p>hash160 is re-encoded as a mainnet P2PKH {@code 1...} address. This is round-trip-safe: re-importing
 * decodes back to the same hash160 (the tool keys on hash160), even though a hash160 that originally came
 * from a P2SH/altcoin/bech32 address is written as {@code 1...}.
 *
 * <h2>Provenance and progress</h2>
 * Because a delta address may live in several of the "other" databases at once, the main delta file stays
 * a pure re-importable address list, and the origin is written separately:
 * <ul>
 *   <li>a sidecar TSV ({@code <deltaFile>.provenance.tsv}) with one {@code address\tsource1,source2,...}
 *       line per delta address — if a hash160 was in more than one source database, all are listed;</li>
 *   <li>a per-source summary logged at the end (how many delta addresses each source database contained);</li>
 *   <li>periodic progress lines (delta written so far, position in the reference key space, rate, ETA).</li>
 * </ul>
 */
@ToString
public class LMDBDelta implements Runnable, Interruptable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LMDBDelta.class);

    private static final String PROVENANCE_FILE_SUFFIX = ".provenance.tsv";

    private static final long PROGRESS_REPORT_MILLIS = 30_000L;
    private static final long RATE_WINDOW_MILLIS = 60_000L;
    private static final long SAMPLE_INTERVAL_MILLIS = 1_000L;
    // Reading the wall clock on every merged key would add measurable overhead over hundreds of millions
    // of iterations, so the clock is only sampled once every 65,536 keys.
    private static final long PROGRESS_CHECK_MASK = 0xFFFFL;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(true));

    private final CLMDBDelta lmdbDelta;

    // Lifecycle flag — uninformative in aggregate toString.
    @ToString.Exclude
    private final AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * Creates a new delta writer.
     *
     * @param lmdbDelta the delta configuration
     */
    public LMDBDelta(CLMDBDelta lmdbDelta) {
        this.lmdbDelta = lmdbDelta;
    }

    @Override
    public void run() {
        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        List<LMDBPersistence> opened = new ArrayList<>();
        try {
            LMDBPersistence reference =
                    new LMDBPersistence(lmdbDelta.referenceLmdbConfigurationReadOnly, persistenceUtils);
            reference.init();
            opened.add(reference);

            List<AddressIterable> others = new ArrayList<>(lmdbDelta.lmdbConfigurationReadOnlyList.size());
            List<String> otherLabels = new ArrayList<>(lmdbDelta.lmdbConfigurationReadOnlyList.size());
            for (CLMDBConfigurationReadOnly otherConfig : lmdbDelta.lmdbConfigurationReadOnlyList) {
                LMDBPersistence other = new LMDBPersistence(otherConfig, persistenceUtils);
                other.init();
                opened.add(other);
                others.add(other);
                otherLabels.add(otherConfig.lmdbDirectory);
            }

            Path deltaFile = Path.of(lmdbDelta.deltaAddressesFile);
            Path deltaFileName = Objects.requireNonNull(deltaFile.getFileName(), "deltaAddressesFile has no file name");
            Path provenanceFile = deltaFile.resolveSibling(deltaFileName + PROVENANCE_FILE_SUFFIX);
            LOGGER.info("Writing delta of " + others.size() + " database(s) against the reference to " + deltaFile
                    + " (provenance: " + provenanceFile + ") ...");

            DeltaResult result;
            try (Writer writer = Files.newBufferedWriter(deltaFile, StandardCharsets.UTF_8);
                    Writer provenanceWriter = Files.newBufferedWriter(provenanceFile, StandardCharsets.UTF_8)) {
                result = writeDelta(reference, others, otherLabels, writer, provenanceWriter);
            }
            LOGGER.info("... delta done: " + result.written() + " address(es) written to " + deltaFile + ".");
            logSourceSummary(result.perSourceContained(), otherLabels);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write delta to " + lmdbDelta.deltaAddressesFile, e);
        } finally {
            for (LMDBPersistence persistence : opened) {
                persistence.close();
            }
        }
    }

    /**
     * Streams every hash160 present in one of {@code others} but not in {@code reference} to {@code out}
     * as a Base58 P2PKH address line, using a k-way sorted merge over the (key-ordered) cursors. Takes
     * plain {@link Appendable}s rather than files so it is trivially unit-testable in memory.
     *
     * @param reference     the reference address set (excluded from the delta)
     * @param others        the other address sets to diff against the reference
     * @param otherLabels   a display label per {@code others} entry (same size/order), used for provenance
     * @param out           the sink for one {@code 1...} address per delta hash160, newline-separated
     * @param provenanceOut optional sink for one {@code address\tsource1,source2,...} line per delta
     *                      address; {@code null} disables provenance output
     * @return the delta result: the number of addresses written and the per-source contained counts
     * @throws IOException if appending to a sink fails
     */
    @VisibleForTesting
    DeltaResult writeDelta(
            AddressIterable reference,
            List<? extends AddressIterable> others,
            List<String> otherLabels,
            Appendable out,
            @Nullable Appendable provenanceOut)
            throws IOException {
        long[] perSourceContained = new long[others.size()];
        long referenceTotal = reference.count();
        List<Stream<ByteBuffer>> openStreams = new ArrayList<>();
        try {
            Stream<ByteBuffer> referenceStream = reference.addresses();
            openStreams.add(referenceStream);
            Peeker referencePeeker = new Peeker(referenceStream.iterator());

            List<Peeker> otherPeekers = new ArrayList<>(others.size());
            for (AddressIterable other : others) {
                Stream<ByteBuffer> otherStream = other.addresses();
                openStreams.add(otherStream);
                otherPeekers.add(new Peeker(otherStream.iterator()));
            }

            long written = 0;
            long referenceConsumed = 0;
            long iterationCounter = 0;
            long startMillis = System.currentTimeMillis();
            long lastSampleMillis = startMillis;
            long lastProgressMillis = startMillis;
            SlidingWindowRate referenceRate = new SlidingWindowRate(RATE_WINDOW_MILLIS);
            referenceRate.sample(startMillis, 0);

            while (shouldRun.get()) {
                byte @Nullable [] smallest = smallestHead(otherPeekers);
                if (smallest == null) {
                    break; // every other database exhausted
                }
                // Advance the reference to the first key >= smallest (its keys are also ascending).
                byte @Nullable [] referenceHead = referencePeeker.peek();
                while (referenceHead != null && Arrays.compareUnsigned(referenceHead, smallest) < 0) {
                    referencePeeker.advance();
                    referenceConsumed++;
                    referenceHead = referencePeeker.peek();
                }
                boolean inReference = referenceHead != null && Arrays.equals(referenceHead, smallest);

                // Consume `smallest` from every other database (deduplicates across them); for a delta key,
                // record which sources held it and count them.
                StringBuilder sources = new StringBuilder();
                for (int i = 0; i < otherPeekers.size(); i++) {
                    Peeker peeker = otherPeekers.get(i);
                    byte @Nullable [] head = peeker.peek();
                    if (head != null && Arrays.equals(head, smallest)) {
                        if (!inReference) {
                            perSourceContained[i]++;
                            if (provenanceOut != null) {
                                if (sources.length() > 0) {
                                    sources.append(',');
                                }
                                sources.append(otherLabels.get(i));
                            }
                        }
                        peeker.advance();
                    }
                }

                if (!inReference) {
                    String address = keyUtility.toBase58(smallest);
                    out.append(address).append('\n');
                    if (provenanceOut != null) {
                        provenanceOut
                                .append(address)
                                .append('\t')
                                .append(sources)
                                .append('\n');
                    }
                    written++;
                }

                if ((++iterationCounter & PROGRESS_CHECK_MASK) == 0L) {
                    long now = System.currentTimeMillis();
                    if (now - lastSampleMillis >= SAMPLE_INTERVAL_MILLIS) {
                        referenceRate.sample(now, referenceConsumed);
                        lastSampleMillis = now;
                    }
                    if (now - lastProgressMillis >= PROGRESS_REPORT_MILLIS) {
                        logProgress(now, written, referenceConsumed, referenceTotal, referenceRate);
                        lastProgressMillis = now;
                    }
                }
            }
            return new DeltaResult(written, perSourceContained);
        } finally {
            for (Stream<ByteBuffer> stream : openStreams) {
                stream.close();
            }
        }
    }

    /** Returns the unsigned-smallest current head across the peekers, or {@code null} if all are exhausted. */
    private byte @Nullable [] smallestHead(Iterable<Peeker> peekers) {
        byte[] smallest = null;
        for (Peeker peeker : peekers) {
            byte @Nullable [] head = peeker.peek();
            if (head != null && (smallest == null || Arrays.compareUnsigned(head, smallest) < 0)) {
                smallest = head;
            }
        }
        return smallest;
    }

    private void logProgress(
            long now, long written, long referenceConsumed, long referenceTotal, SlidingWindowRate referenceRate) {
        double rate = referenceRate.ratePerSecond(now, referenceConsumed);
        double percent = referenceTotal > 0L ? referenceConsumed * 100.0 / referenceTotal : 0.0;
        String eta = (rate > 0.0 && referenceTotal > referenceConsumed)
                ? formatDuration((long) ((referenceTotal - referenceConsumed) / rate * 1_000.0))
                : "?";
        LOGGER.info(String.format(
                "delta progress: %,d written | reference %,d/%,d (%.2f%%) | %,.0f keys/s | ETA %s",
                written, referenceConsumed, referenceTotal, percent, rate, eta));
    }

    private void logSourceSummary(long[] perSourceContained, List<String> otherLabels) {
        LOGGER.info("--- delta source summary (addresses present in the source but not in the reference) ---");
        List<Integer> order = new ArrayList<>(otherLabels.size());
        for (int i = 0; i < otherLabels.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Long.compare(perSourceContained[b], perSourceContained[a]));
        for (int i : order) {
            LOGGER.info(String.format("%,15d  %s", perSourceContained[i], otherLabels.get(i)));
        }
    }

    @VisibleForTesting
    String formatDuration(long millis) {
        long totalSeconds = millis / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void interrupt() {
        shouldRun.set(false);
    }

    /**
     * Result of a delta pass: the number of addresses written and, per "other" database (in input order),
     * how many delta addresses it contained.
     */
    static final class DeltaResult {

        private final long written;
        private final long[] perSourceContained;

        DeltaResult(long written, long[] perSourceContained) {
            this.written = written;
            this.perSourceContained = perSourceContained.clone();
        }

        long written() {
            return written;
        }

        long[] perSourceContained() {
            return perSourceContained.clone();
        }
    }

    /**
     * One-element look-ahead over a key-ordered hash160 stream. Each head is copied out of the (possibly
     * cursor-owned, reused) {@link ByteBuffer} so it stays valid after the stream advances. Static nested:
     * holds no reference to the enclosing instance.
     */
    private static class Peeker {

        private final Iterator<ByteBuffer> iterator;

        private byte @Nullable [] head;

        Peeker(Iterator<ByteBuffer> iterator) {
            this.iterator = iterator;
            this.head = nextOrNull(iterator);
        }

        byte @Nullable [] peek() {
            return head;
        }

        void advance() {
            head = nextOrNull(iterator);
        }

        private static byte @Nullable [] nextOrNull(Iterator<ByteBuffer> iterator) {
            if (iterator.hasNext()) {
                ByteBuffer buffer = iterator.next();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                return bytes;
            }
            return null;
        }
    }
}
