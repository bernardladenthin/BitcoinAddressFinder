// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.io.AddressFile;
import net.ladenthin.bitcoinaddressfinder.io.FileHelper;
import net.ladenthin.bitcoinaddressfinder.model.AddressToCoin;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.statistics.ReadStatistic;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imports one or more plaintext address files into an LMDB database.
 *
 * <h2>Reading is decoupled from writing</h2>
 * File reading and address parsing (CPU/IO-bound) run on {@code threads} reader threads; each parsed
 * {@link AddressToCoin} is handed over a bounded {@link BlockingQueue} to a <b>single writer</b> (the
 * sink), because LMDB is a single-writer store. This parallelises only the slow, single-threaded read
 * side without touching the write path.
 *
 * <h2>Ordering</h2>
 * With {@code threads == 1} there is one reader and one writer draining a FIFO queue, so writes happen
 * in exactly the original file/line order — identical to the previous single-threaded importer. This
 * matters when {@code useStaticAmount} is {@code false}: for an address present in several files the
 * last write wins. With {@code threads >= 2} files are read in parallel, so that order (and thus the
 * winning amount for duplicates) is non-deterministic; a warning is logged unless
 * {@code useStaticAmount} is {@code true}, which stores the same amount regardless of order. The set
 * of imported addresses is the same either way.
 */
@ToString
public class AddressFilesToLMDB implements Runnable, Interruptable {

    private static final long PROGRESS_LOG = 100_000;

    /**
     * Bound on the reader-to-writer hand-off queue. Provides back-pressure so fast readers cannot
     * outrun the single LMDB writer and exhaust the heap on a huge import.
     */
    private static final int QUEUE_CAPACITY = 200_000;

    /** Sink poll timeout: how often the writer re-checks the "readers done and queue drained" exit. */
    private static final long SINK_POLL_MILLIS = 50;

    private static final Logger LOGGER = LoggerFactory.getLogger(AddressFilesToLMDB.class);

    private final @NonNull CAddressFilesToLMDB addressFilesToLMDB;

    /** Count of addresses actually written to LMDB (incremented by the single sink). */
    private final AtomicLong addressCounter = new AtomicLong();

    /** Number of address files fully read so far, across all reader threads (for "X/Y files" progress). */
    private final AtomicLong filesProcessed = new AtomicLong();

    /**
     * Aggregate read statistic. Each reader thread keeps its own {@link ReadStatistic} (which is not
     * thread-safe) and they are merged into this one after every reader has finished — so the final
     * summary is race-free.
     */
    private final ReadStatistic readStatistic = new ReadStatistic();

    /**
     * Address files currently being read, so {@link #interrupt()} can stop every in-flight reader.
     *
     * <p>Excluded from {@link ToString} — a transient, per-run set of readers, not configuration.
     */
    @ToString.Exclude
    private final Set<AddressFile> activeFiles = ConcurrentHashMap.newKeySet();

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

        if (threads > 1 && !lmdbConfigurationWrite.useStaticAmount) {
            LOGGER.warn(
                    "threads={} with useStaticAmount=false: address files are read in parallel, so the LMDB write "
                            + "order is non-deterministic. For an address that appears in more than one file the "
                            + "last write wins, so the stored amount for such duplicates is not deterministic across "
                            + "runs. Use threads=1 for a deterministic amount, or useStaticAmount=true (order-safe "
                            + "with any thread count). The set of imported addresses is unaffected either way.",
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

                LOGGER.info("Iterate address files with " + threads + " reader thread(s) ...");
                importFiles(network, persistence, files, threads);
                logProgress();
                LOGGER.info("... iterate address files done.");

                for (String error : readStatistic.errors) {
                    LOGGER.info("Error in line: " + error);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to import " + addressFilesToLMDB.addressesFiles.size() + " address file(s)", e);
            }
        }
    }

    /**
     * Runs the reader pool → queue → single writer pipeline and blocks until every file is imported.
     */
    private void importFiles(
            @NonNull Network network, @NonNull LMDBPersistence persistence, @NonNull List<File> files, int threads)
            throws IOException {
        final BlockingQueue<AddressToCoin> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        final ConcurrentLinkedQueue<File> fileQueue = new ConcurrentLinkedQueue<>(files);
        final AtomicBoolean readersDone = new AtomicBoolean(false);
        final AtomicReference<Throwable> sinkError = new AtomicReference<>();

        final ExecutorService sinkExecutor = Executors.newSingleThreadExecutor();
        final Future<?> sinkFuture = sinkExecutor.submit(() -> runSink(persistence, queue, readersDone, sinkError));

        final int totalFiles = files.size();
        final ExecutorService readerPool = Executors.newFixedThreadPool(threads);
        final List<Future<ReadStatistic>> readerFutures = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            readerFutures.add(readerPool.submit(reader(network, queue, fileQueue, sinkError, totalFiles)));
        }
        readerPool.shutdown();

        try {
            awaitTermination(readerPool);
            // Readers are done; let the sink drain the remainder and stop.
            readersDone.set(true);
            sinkFuture.get();
            for (Future<ReadStatistic> future : readerFutures) {
                merge(readStatistic, future.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while importing " + files.size() + " address file(s)", e);
        } catch (ExecutionException e) {
            // Chain the full ExecutionException so the reader's original failure keeps its stack trace.
            throw new IOException("Failed while importing " + files.size() + " address file(s)", e);
        } finally {
            readersDone.set(true);
            sinkExecutor.shutdownNow();
            readerPool.shutdownNow();
        }

        Throwable failed = sinkError.get();
        if (failed != null) {
            throw new IOException("LMDB writer failed after " + addressCounter.get() + " address(es) written", failed);
        }
    }

    /** Builds one reader task: pull files from the shared queue, parse them, enqueue parsed entries. */
    private Callable<ReadStatistic> reader(
            @NonNull Network network,
            @NonNull BlockingQueue<AddressToCoin> queue,
            @NonNull ConcurrentLinkedQueue<File> fileQueue,
            @NonNull AtomicReference<Throwable> sinkError,
            int totalFiles) {
        return () -> {
            ReadStatistic localStatistic = new ReadStatistic();
            Consumer<AddressToCoin> toQueue = addressToCoin -> putOrFail(queue, addressToCoin);
            Consumer<String> unsupported = line -> {};

            File file;
            while (shouldRun.get() && sinkError.get() == null && (file = fileQueue.poll()) != null) {
                AddressFile addressFile = new AddressFile(file, localStatistic, network, toQueue, unsupported);
                activeFiles.add(addressFile);
                String filePath = file.getAbsolutePath();
                LOGGER.info("process " + filePath);
                try {
                    addressFile.readFile();
                } finally {
                    activeFiles.remove(addressFile);
                }
                LOGGER.info(
                        "finished (" + filesProcessed.incrementAndGet() + "/" + totalFiles + " files): " + filePath);
            }
            return localStatistic;
        };
    }

    /** The single LMDB writer: drains the queue and writes until readers are done and the queue is empty. */
    private Void runSink(
            @NonNull LMDBPersistence persistence,
            @NonNull BlockingQueue<AddressToCoin> queue,
            @NonNull AtomicBoolean readersDone,
            @NonNull AtomicReference<Throwable> sinkError)
            throws InterruptedException {
        try {
            while (!(readersDone.get() && queue.isEmpty())) {
                AddressToCoin entry = queue.poll(SINK_POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    ByteBuffer hash160 = entry.hash160();
                    persistence.putNewAmount(hash160, entry.coin());
                    long written = addressCounter.incrementAndGet();
                    if (written % PROGRESS_LOG == 0) {
                        LOGGER.info("Progress: " + written + " addresses written.");
                    }
                }
            }
        } catch (RuntimeException e) {
            // Record the failure and keep draining so readers blocked on a full queue can finish; the
            // recorded error is rethrown by importFiles once everything has stopped.
            sinkError.set(e);
            drainRemaining(queue, readersDone);
        }
        return null;
    }

    private static void drainRemaining(@NonNull BlockingQueue<AddressToCoin> queue, @NonNull AtomicBoolean readersDone)
            throws InterruptedException {
        while (!(readersDone.get() && queue.isEmpty())) {
            // Discard the drained entries: the writer has already failed, this only unblocks readers.
            queue.poll(SINK_POLL_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private static void putOrFail(@NonNull BlockingQueue<AddressToCoin> queue, @NonNull AddressToCoin entry) {
        try {
            queue.put(entry);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // A reader lambda hands entries to the writer through a Consumer that cannot throw checked
            // exceptions, so an interrupt while queueing is surfaced as an unchecked exception.
            throw new IllegalStateException(
                    "Interrupted while queueing a parsed address for the LMDB writer on thread "
                            + Thread.currentThread().getName(),
                    e);
        }
    }

    private static void awaitTermination(@NonNull ExecutorService executor) throws InterruptedException {
        while (!executor.awaitTermination(1, TimeUnit.DAYS)) {
            // wait for all readers to finish
        }
    }

    /** Merges a reader's per-thread statistic into the aggregate; {@code target} is only touched here. */
    private static void merge(@NonNull ReadStatistic target, @NonNull ReadStatistic source) {
        target.successful += source.successful;
        source.unsupportedReasons.forEach((reason, count) -> target.unsupportedReasons.merge(reason, count, Long::sum));
        target.errors.addAll(source.errors);
        target.currentFileProgress = source.currentFileProgress;
    }

    private void logProgress() {
        LOGGER.info("Progress: " + addressCounter.get() + " addresses. Unsupported: "
                + readStatistic.getUnsupportedTotal() + ". Errors: " + readStatistic.errors.size()
                + ". Current File progress: " + String.format("%.2f", readStatistic.currentFileProgress) + "%.");
    }

    @Override
    public void interrupt() {
        shouldRun.set(false);
        for (AddressFile addressFile : activeFiles) {
            addressFile.interrupt();
        }
    }
}
