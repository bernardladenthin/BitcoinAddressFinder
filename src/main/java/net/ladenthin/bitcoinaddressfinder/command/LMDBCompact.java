// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CCompactLMDB;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes a compacted copy of an existing LMDB database using LMDB's {@code MDB_CP_COMPACT}.
 *
 * <p>Intended as an optional post-import optimize step: the random-insert import leaves free/dead pages
 * behind, so a compacted copy is smaller and read-denser. The gain for random point lookups is largest
 * when the database exceeds RAM (a smaller file raises the OS page-cache hit rate). The source database
 * is opened read-only and left unchanged; the compacted copy is written to a separate directory as
 * {@code data.mdb}, which the finder can then read by pointing its {@code lmdbDirectory} at it.
 */
@ToString
public class LMDBCompact implements Runnable, Interruptable {

    private static final String LMDB_DATA_FILE = "data.mdb";

    private static final Logger LOGGER = LoggerFactory.getLogger(LMDBCompact.class);

    private final Network network = new NetworkParameterFactory().getNetwork();

    private final CCompactLMDB compactLMDB;

    // Lifecycle flag — the compaction itself is a single blocking LMDB call and cannot be interrupted
    // mid-copy, so this only prevents a not-yet-started run from proceeding.
    @ToString.Exclude
    private final AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * Creates a new compactor.
     *
     * @param compactLMDB the compaction configuration
     */
    public LMDBCompact(CCompactLMDB compactLMDB) {
        this.compactLMDB = compactLMDB;
    }

    @Override
    public void run() {
        String sourceDirectory = compactLMDB.lmdbConfigurationReadOnly.lmdbDirectory;
        File targetDirectory = new File(compactLMDB.targetDirectory);

        if (targetDirectory.getAbsolutePath().equals(new File(sourceDirectory).getAbsolutePath())) {
            throw new IllegalArgumentException(
                    "targetDirectory must differ from the source lmdbDirectory: " + targetDirectory);
        }
        if (!targetDirectory.mkdirs() && !targetDirectory.isDirectory()) {
            throw new IllegalStateException("Failed to create target directory: " + targetDirectory);
        }
        // MDB_CP_COMPACT writes data.mdb and fails if it already exists; replace a previous compaction.
        File targetDataFile = new File(targetDirectory, LMDB_DATA_FILE);
        if (targetDataFile.exists() && !targetDataFile.delete()) {
            throw new IllegalStateException("Failed to delete existing compacted database: " + targetDataFile);
        }

        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        try (LMDBPersistence persistence =
                new LMDBPersistence(compactLMDB.lmdbConfigurationReadOnly, persistenceUtils)) {
            persistence.init();

            File sourceDataFile = new File(sourceDirectory, LMDB_DATA_FILE);
            LOGGER.info("Compacting LMDB " + sourceDataFile + " -> " + targetDataFile + " (MDB_CP_COMPACT) ...");
            persistence.compactTo(targetDirectory);

            long sourceBytes = sourceDataFile.length();
            long targetBytes = targetDataFile.length();
            LOGGER.info("... compaction done. Source: " + mib(sourceBytes) + " MiB, compacted: " + mib(targetBytes)
                    + " MiB (" + reductionPercent(sourceBytes, targetBytes) + "% smaller). Point the finder's "
                    + "lmdbDirectory at " + targetDirectory + " to use it.");
        }
    }

    private static String mib(long bytes) {
        return String.format("%.1f", bytes / (1024.0 * 1024.0));
    }

    private static String reductionPercent(long sourceBytes, long targetBytes) {
        if (sourceBytes <= 0) {
            return "0.0";
        }
        return String.format("%.1f", (1.0 - (double) targetBytes / (double) sourceBytes) * 100.0);
    }

    @Override
    public void interrupt() {
        shouldRun.set(false);
    }
}
