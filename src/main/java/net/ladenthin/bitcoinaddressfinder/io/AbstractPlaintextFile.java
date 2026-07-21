// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.io;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.statistics.ReadStatistic;
import org.jspecify.annotations.NonNull;
import org.lmdbjava.LmdbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for line-by-line readers of plaintext files that update a {@link ReadStatistic}
 * and can be interrupted gracefully.
 */
@ToString
public abstract class AbstractPlaintextFile implements Interruptable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPlaintextFile.class);

    /** The file being read. */
    protected final @NonNull File file;
    /** Statistic updated while the file is being processed. */
    protected final @NonNull ReadStatistic readStatistic;

    // Lifecycle AtomicBoolean — its value flips on shutdown and the toString form is uninformative.
    @ToString.Exclude
    private final @NonNull AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * Creates a new reader for the given file.
     *
     * @param file          the file to read
     * @param readStatistic the statistic to update while reading
     */
    public AbstractPlaintextFile(@NonNull File file, @NonNull ReadStatistic readStatistic) {
        this.file = file;
        this.readStatistic = readStatistic;
    }

    /**
     * Calculates the current read progress as a percentage of the file size.
     *
     * @param raf the currently open random-access file
     * @return the progress in percent (0 to 100)
     * @throws IOException if the file pointer or length cannot be read
     */
    protected double calculateFileProgress(@NonNull RandomAccessFile raf) throws IOException {
        return ((double) Math.max(raf.getFilePointer(), 1) / (double) raf.length()) * 100.0d;
    }

    /**
     * Returns the file being read.
     *
     * @return the file being read
     */
    public @NonNull File getFile() {
        return file;
    }

    /**
     * Returns the current read progress of this file in percent (byte offset over file size), as last
     * updated by {@link #readFile()}. Safe to call from another thread for progress reporting.
     *
     * @return the current read progress in percent (0 to 100)
     */
    public double getReadProgressInPercent() {
        return readStatistic.currentFileProgress;
    }

    /**
     * Processes a single line read from the file.
     *
     * @param line the line content (UTF-8 decoded)
     */
    protected abstract void processLine(String line);

    /**
     * Reads the file line by line and dispatches each line to {@link #processLine(String)}.
     *
     * @throws IOException if reading fails
     */
    public void readFile() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            while (shouldRun.get()) {
                String line = raf.readLine();
                if (line == null) {
                    return;
                }
                String utf8 = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                readStatistic.currentFileProgress = calculateFileProgress(raf);
                try {
                    processLine(utf8);
                } catch (LmdbException e) {
                    // do not catch expections from LMDB (e. g. MapFullException).
                    throw e;
                } catch (Exception e) {
                    LOGGER.error("Error in line: {}", utf8, e);
                    readStatistic.errors.add(utf8);
                }
            }
        }
    }

    @Override
    public void interrupt() {
        shouldRun.set(false);
    }
}
