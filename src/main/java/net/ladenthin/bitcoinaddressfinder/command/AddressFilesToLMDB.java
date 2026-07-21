// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.io.AddressFormatNotAcceptedException;
import net.ladenthin.bitcoinaddressfinder.io.AddressTxtLine;
import net.ladenthin.bitcoinaddressfinder.io.FileHelper;
import net.ladenthin.bitcoinaddressfinder.model.AddressToCoin;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.statistics.SlidingWindowRate;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imports one or more plaintext address files into an LMDB database.
 *
 * <h2>Pipeline</h2>
 * Files are processed <b>sequentially, in list order</b>. For the whole import a single reader (this
 * thread) reads the current file line by line into a bounded {@link BlockingQueue}; {@code threads}
 * parser workers take lines and decode them into {@link AddressToCoin} entries on a second bounded
 * queue; and a <b>single writer</b> drains that queue and writes to LMDB in <b>batches</b> (one write
 * transaction per {@code writeBatchSize} entries, default 32768). LMDB is a single-writer store and a
 * commit per address is the dominant cost of a bulk import, so batching the writes — not the reading —
 * is the main
 * speedup. Reading files one at a time keeps all workers busy on the current file and never has several
 * threads reading different whole files at once.
 *
 * <h2>Ordering</h2>
 * With {@code threads == 1} there is one parser draining a FIFO queue and one writer, so entries are
 * written in the original file/line order — deterministic. This matters when {@code useStaticAmount} is
 * {@code false}: for an address present more than once the last write wins. With {@code threads >= 2}
 * lines are parsed in parallel, so the write order — and thus the winning amount for duplicates —
 * becomes non-deterministic; a warning is logged unless {@code useStaticAmount} is {@code true}, which
 * stores the same amount regardless of order. The set of imported addresses is the same either way.
 */
@ToString
public class AddressFilesToLMDB implements Runnable, Interruptable {

    /** Log a write-progress line every this many written addresses. */
    private static final long PROGRESS_LOG = 100_000;

    /** Poll timeout used when draining the queues so the "upstream done and queue empty" exit is re-checked. */
    private static final long QUEUE_POLL_MILLIS = 50;

    /** Minimum interval between per-file read-progress log lines. */
    private static final long PROGRESS_REPORT_MILLIS = 30_000L;

    /** Interval at which the reader feeds a byte sample into the trailing-rate window for the ETA. */
    private static final long SAMPLE_INTERVAL_MILLIS = 1_000L;

    /** Trailing window over which the read throughput (for the ETA) is averaged. */
    private static final long RATE_WINDOW_MILLIS = 60_000L;

    private static final Logger LOGGER = LoggerFactory.getLogger(AddressFilesToLMDB.class);

    private final @NonNull CAddressFilesToLMDB addressFilesToLMDB;

    /** Count of addresses actually written to LMDB (incremented by the single writer). */
    private final AtomicLong addressCounter = new AtomicLong();

    /** Count of lines successfully decoded into an address (incremented by the parser workers). */
    private final AtomicLong parsedCounter = new AtomicLong();

    /** Count of lines rejected as unsupported/unparseable (incremented by the parser workers). */
    private final AtomicLong unsupportedCounter = new AtomicLong();

    /** Number of address files fully read so far (for "X/Y files" progress). */
    private final AtomicLong filesProcessed = new AtomicLong();

    /** Lines that threw an unexpected error while parsing; logged in the final summary. */
    private final Queue<String> errors = new ConcurrentLinkedQueue<>();

    /**
     * Flag controlling the import; cleared via {@link #interrupt()}.
     *
     * <p>Excluded from {@link ToString} — uninformative lifecycle flag.
     */
    @ToString.Exclude
    protected final AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * Creates a new importer.
     *
     * @param addressFilesToLMDB configuration with the LMDB target and source files
     */
    public AddressFilesToLMDB(@NonNull CAddressFilesToLMDB addressFilesToLMDB) {
        this.addressFilesToLMDB = addressFilesToLMDB;
    }

    @Override
    public void run() {
        final Network network = new NetworkParameterFactory().getNetwork();

        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        CLMDBConfigurationWrite lmdbConfigurationWrite =
                Objects.requireNonNull(addressFilesToLMDB.lmdbConfigurationWrite);
        final int threads = Math.max(1, addressFilesToLMDB.threads);
        final int writeBatchSize = Math.max(1, addressFilesToLMDB.writeBatchSize);
        final int queueCapacity = Math.max(1, addressFilesToLMDB.queueCapacity);

        if (threads > 1 && !lmdbConfigurationWrite.useStaticAmount) {
            LOGGER.warn(
                    "threads={} with useStaticAmount=false: address files are parsed in parallel, so the LMDB write "
                            + "order is non-deterministic. For an address that appears more than once the last write "
                            + "wins, so the stored amount for such duplicates is not deterministic across runs. Use "
                            + "threads=1 for a deterministic amount, or useStaticAmount=true (order-safe with any "
                            + "thread count). The set of imported addresses is unaffected either way.",
                    threads);
        }

        try (LMDBPersistence persistence = new LMDBPersistence(lmdbConfigurationWrite, persistenceUtils)) {
            LOGGER.info("Init LMDB ...");
            persistence.init();
            LOGGER.info("... init LMDB done.");

            try {
                FileHelper fileHelper = new FileHelper();
                List<String> addressesFiles = Objects.requireNonNull(addressFilesToLMDB.addressesFiles);
                List<File> files = fileHelper.stringsToFiles(addressesFiles);
                fileHelper.assertFilesExists(files);

                LOGGER.info("Import " + files.size() + " address file(s) with " + threads + " parser thread(s), "
                        + "batched writes of " + writeBatchSize + ", queue capacity " + queueCapacity + " ...");
                importFiles(network, persistence, files, threads, writeBatchSize, queueCapacity);
                logSummary();
                LOGGER.info("... import done.");
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to import " + addressFilesToLMDB.addressesFiles.size() + " address file(s)", e);
            }
        }
    }

    /**
     * Runs the reader (this thread) → line queue → parser pool → entry queue → single batched writer
     * pipeline and blocks until every file is imported.
     */
    private void importFiles(
            @NonNull Network network,
            @NonNull LMDBPersistence persistence,
            @NonNull List<File> files,
            int threads,
            int writeBatchSize,
            int queueCapacity)
            throws IOException {
        // All files are already verified to exist, so summing their sizes is a cheap up-front stat and
        // gives the denominator for overall progress + ETA. Owned by the reader (this) thread only.
        final ReaderProgress progress =
                new ReaderProgress(files.stream().mapToLong(File::length).sum(), System.currentTimeMillis());

        final BlockingQueue<String> lineQueue = new LinkedBlockingQueue<>(queueCapacity);
        final BlockingQueue<AddressToCoin> entryQueue = new LinkedBlockingQueue<>(queueCapacity);
        final AtomicBoolean readingDone = new AtomicBoolean(false);
        final AtomicBoolean parsingDone = new AtomicBoolean(false);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final ExecutorService writerExecutor = Executors.newSingleThreadExecutor();
        final Future<?> writerFuture =
                writerExecutor.submit(() -> runWriter(persistence, entryQueue, parsingDone, failure, writeBatchSize));

        final ExecutorService parserPool = Executors.newFixedThreadPool(threads);
        final List<Future<?>> parserFutures = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            parserFutures.add(parserPool.submit(() -> runParser(network, lineQueue, entryQueue, readingDone, failure)));
        }
        parserPool.shutdown();

        try {
            // The reader is this thread: read every file, in order, into the line queue.
            readAllFiles(files, lineQueue, failure, progress);
            readingDone.set(true);
            // Parsers drain the remaining lines, then the writer drains the remaining entries.
            awaitTermination(parserPool);
            parsingDone.set(true);
            writerFuture.get();
            for (Future<?> parserFuture : parserFutures) {
                parserFuture.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while importing " + files.size() + " address file(s)", e);
        } catch (ExecutionException e) {
            // Chain the full ExecutionException so the worker's original failure keeps its stack trace.
            throw new IOException("Failed while importing " + files.size() + " address file(s)", e);
        } finally {
            readingDone.set(true);
            parsingDone.set(true);
            parserPool.shutdownNow();
            writerExecutor.shutdownNow();
        }

        Throwable failed = failure.get();
        if (failed != null) {
            throw new IOException("LMDB writer failed after " + addressCounter.get() + " address(es) written", failed);
        }
    }

    /** Reads every file, in list order, pushing each line onto the shared line queue. */
    private void readAllFiles(
            @NonNull List<File> files,
            @NonNull BlockingQueue<String> lineQueue,
            @NonNull AtomicReference<Throwable> failure,
            @NonNull ReaderProgress progress)
            throws IOException {
        int totalFiles = files.size();
        for (File file : files) {
            if (!shouldRun.get() || failure.get() != null) {
                break;
            }
            String path = file.getAbsolutePath();
            LOGGER.info("process " + path);
            readFileLines(file, lineQueue, failure, progress);
            progress.fileFinished(file.length());
            LOGGER.info("finished (" + filesProcessed.incrementAndGet() + "/" + totalFiles + " files): " + path);
        }
    }

    /**
     * Reads one file line by line onto the line queue, logging a throttled progress line.
     *
     * <p>Uses a {@link BufferedReader} (block reads) rather than {@code RandomAccessFile.readLine()},
     * which reads one byte at a time and made this single reader the import bottleneck. The
     * {@link InputStreamReader} decodes UTF-8 leniently (malformed bytes are replaced, not thrown),
     * matching the previous behaviour. Progress percent is by decoded characters over file bytes —
     * exact for the ASCII address files, approximate otherwise. Each line also feeds {@code progress},
     * which samples the running byte count (~1/s) and, on the throttled interval, prints overall
     * progress and an ETA. All reader-side, so nothing here is shared across threads.
     */
    private void readFileLines(
            @NonNull File file,
            @NonNull BlockingQueue<String> lineQueue,
            @NonNull AtomicReference<Throwable> failure,
            @NonNull ReaderProgress progress)
            throws IOException {
        long length = Math.max(file.length(), 1L);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            long charsRead = 0;
            String line;
            while (shouldRun.get() && failure.get() == null && (line = reader.readLine()) != null) {
                charsRead += line.length() + 1L;
                if (!enqueue(lineQueue, line, failure)) {
                    return;
                }
                progress.onLineRead(file, charsRead, length, System.currentTimeMillis());
            }
        }
    }

    /**
     * Reader-thread-only overall-progress and ETA tracker. All state lives here and only the single
     * reader thread ever calls into it, so no field is shared across threads and none needs
     * synchronization. Samples the running byte count (~1/s) into a trailing-rate window and, on the
     * throttled interval, logs overall percent plus an ETA derived from that rate.
     */
    private class ReaderProgress {

        private final long totalBytes;
        private final SlidingWindowRate byteRate = new SlidingWindowRate(RATE_WINDOW_MILLIS);
        private long bytesReadInFinishedFiles;
        private long lastSampleMillis;
        private long lastProgressLogMillis;

        ReaderProgress(long totalBytes, long nowMillis) {
            this.totalBytes = Math.max(totalBytes, 1L);
            this.lastSampleMillis = nowMillis;
            this.lastProgressLogMillis = nowMillis;
        }

        /** Adds a fully-read file's exact size to the running total. */
        void fileFinished(long fileBytes) {
            bytesReadInFinishedFiles += fileBytes;
        }

        /** Per line: samples the byte rate (throttled) and emits the progress+ETA line (throttled). */
        void onLineRead(File file, long charsRead, long fileLength, long nowMillis) {
            long overallBytes = bytesReadInFinishedFiles + charsRead;
            if (nowMillis - lastSampleMillis >= SAMPLE_INTERVAL_MILLIS) {
                lastSampleMillis = nowMillis;
                byteRate.sample(nowMillis, overallBytes);
            }
            if (nowMillis - lastProgressLogMillis >= PROGRESS_REPORT_MILLIS) {
                lastProgressLogMillis = nowMillis;
                double filePercent = (double) charsRead / (double) fileLength * 100.0d;
                double overallPercent = (double) overallBytes / (double) totalBytes * 100.0d;
                double bytesPerSecond = byteRate.ratePerSecond(nowMillis, overallBytes);
                String eta = bytesPerSecond > 0.0
                        ? formatDuration((long) ((totalBytes - overallBytes) / bytesPerSecond))
                        : "?";
                LOGGER.info(String.format(
                        "reading %s: %.2f%% (%d written) | overall %.2f%% of %s, %s/s, ETA %s",
                        file.getName(),
                        filePercent,
                        addressCounter.get(),
                        overallPercent,
                        humanBytes(totalBytes),
                        humanBytes((long) bytesPerSecond),
                        eta));
            }
        }
    }

    /** Formats a byte count as a human-readable KiB/MiB/GiB string. */
    @VisibleForTesting
    String humanBytes(long bytes) {
        if (bytes >= 1L << 30) {
            return String.format("%.1f GiB", bytes / (double) (1L << 30));
        }
        if (bytes >= 1L << 20) {
            return String.format("%.1f MiB", bytes / (double) (1L << 20));
        }
        if (bytes >= 1L << 10) {
            return String.format("%.1f KiB", bytes / (double) (1L << 10));
        }
        return bytes + " B";
    }

    /** Formats a duration in seconds as a compact {@code Hh Mm} / {@code Mm Ss} / {@code Ss} string. */
    @VisibleForTesting
    String formatDuration(long totalSeconds) {
        if (totalSeconds < 0L) {
            return "?";
        }
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /** One parser worker: take lines, decode them, and hand the parsed entries to the writer queue. */
    private Void runParser(
            @NonNull Network network,
            @NonNull BlockingQueue<String> lineQueue,
            @NonNull BlockingQueue<AddressToCoin> entryQueue,
            @NonNull AtomicBoolean readingDone,
            @NonNull AtomicReference<Throwable> failure)
            throws InterruptedException {
        // Both are stateless/immutable and reused for every line this worker parses.
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(true));
        AddressTxtLine addressTxtLine = new AddressTxtLine();
        while (failure.get() == null && shouldRun.get() && !(readingDone.get() && lineQueue.isEmpty())) {
            String line = lineQueue.poll(QUEUE_POLL_MILLIS, TimeUnit.MILLISECONDS);
            if (line == null) {
                continue;
            }
            try {
                AddressToCoin entry = addressTxtLine.fromLine(line, keyUtility);
                parsedCounter.incrementAndGet();
                if (!enqueue(entryQueue, entry, failure)) {
                    return null;
                }
            } catch (AddressFormatNotAcceptedException e) {
                unsupportedCounter.incrementAndGet();
            } catch (RuntimeException e) {
                errors.add(line);
                LOGGER.error("Error in line: {}", line, e);
            }
        }
        return null;
    }

    /** The single LMDB writer: drains parsed entries and writes them in batches (one transaction each). */
    private Void runWriter(
            @NonNull LMDBPersistence persistence,
            @NonNull BlockingQueue<AddressToCoin> entryQueue,
            @NonNull AtomicBoolean parsingDone,
            @NonNull AtomicReference<Throwable> failure,
            int writeBatchSize)
            throws InterruptedException {
        List<ByteBuffer> hash160Batch = new ArrayList<>(writeBatchSize);
        List<Coin> amountBatch = new ArrayList<>(writeBatchSize);
        try {
            while (failure.get() == null && !(parsingDone.get() && entryQueue.isEmpty())) {
                AddressToCoin entry = entryQueue.poll(QUEUE_POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (entry == null) {
                    continue;
                }
                hash160Batch.add(entry.hash160());
                amountBatch.add(entry.coin());
                if (hash160Batch.size() >= writeBatchSize) {
                    flushBatch(persistence, hash160Batch, amountBatch);
                }
            }
            if (!hash160Batch.isEmpty()) {
                flushBatch(persistence, hash160Batch, amountBatch);
            }
        } catch (RuntimeException e) {
            // Record the failure and keep draining so parsers/reader blocked on a full queue can finish.
            // Only this single writer thread ever sets failure, so an unconditional set is correct.
            failure.set(e);
            drainEntries(entryQueue, parsingDone);
        }
        return null;
    }

    /** Writes one batch in a single transaction and logs write progress when a 100k boundary is crossed. */
    private void flushBatch(
            @NonNull LMDBPersistence persistence,
            @NonNull List<ByteBuffer> hash160Batch,
            @NonNull List<Coin> amountBatch) {
        int count = hash160Batch.size();
        persistence.putNewAmounts(hash160Batch, amountBatch);
        long before = addressCounter.getAndAdd(count);
        long after = before + count;
        if (before / PROGRESS_LOG != after / PROGRESS_LOG) {
            LOGGER.info("Progress: " + after + " addresses written.");
        }
        hash160Batch.clear();
        amountBatch.clear();
    }

    private static void drainEntries(
            @NonNull BlockingQueue<AddressToCoin> entryQueue, @NonNull AtomicBoolean parsingDone)
            throws InterruptedException {
        while (!(parsingDone.get() && entryQueue.isEmpty())) {
            // Discard the drained entries: the writer has already failed, this only unblocks upstream.
            entryQueue.poll(QUEUE_POLL_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Offers an item to a bounded queue, retrying until it fits or the import is aborting (interrupt,
     * {@link #shouldRun} cleared, or a recorded {@code failure}).
     *
     * @return {@code true} if the item was enqueued, {@code false} if the import is aborting
     */
    private <T extends @NonNull Object> boolean enqueue(
            @NonNull BlockingQueue<T> queue, @NonNull T item, @NonNull AtomicReference<Throwable> failure) {
        try {
            while (shouldRun.get() && failure.get() == null) {
                if (queue.offer(item, QUEUE_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void awaitTermination(@NonNull ExecutorService executor) throws InterruptedException {
        while (!executor.awaitTermination(1, TimeUnit.DAYS)) {
            // wait for all workers to finish
        }
    }

    private void logSummary() {
        LOGGER.info("Import summary: " + addressCounter.get() + " written, " + parsedCounter.get() + " parsed, "
                + unsupportedCounter.get() + " unsupported, " + errors.size() + " error(s).");
        for (String error : errors) {
            LOGGER.info("Error in line: " + error);
        }
    }

    @Override
    public void interrupt() {
        shouldRun.set(false);
    }
}
