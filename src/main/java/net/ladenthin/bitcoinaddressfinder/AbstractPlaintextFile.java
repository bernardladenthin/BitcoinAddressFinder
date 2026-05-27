// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.NonNull;
import org.lmdbjava.LmdbException;

/**
 * Base class for line-by-line readers of plaintext files that update a {@link ReadStatistic}
 * and can be interrupted gracefully.
 */
public abstract class AbstractPlaintextFile implements Interruptable {

    /** The file being read. */
    @NonNull
    protected final File file;
    /** Statistic updated while the file is being processed. */
    @NonNull
    protected final ReadStatistic readStatistic;
    @NonNull
    private final AtomicBoolean shouldRun = new AtomicBoolean(true);

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
        return ((double)(Math.max(raf.getFilePointer(),1)) / (double)raf.length()) * 100.0d;
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
            while(shouldRun.get()) {
                String line = raf.readLine();
                if (line == null) {
                    return;
                }
                String utf8 = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                readStatistic.currentFileProgress = calculateFileProgress(raf);
                try {
                    processLine(utf8);
                } catch(LmdbException e) {
                    // do not catch expections from LMDB (e. g. MapFullException).
                    throw e;
                } catch (Exception e) {
                    System.err.println("Error in line: " + utf8);
                    e.printStackTrace();
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
