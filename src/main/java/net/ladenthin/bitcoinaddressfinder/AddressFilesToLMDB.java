// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imports one or more plaintext address files into an LMDB database.
 */
@ToString
public class AddressFilesToLMDB implements Runnable, Interruptable {

    private static final long PROGRESS_LOG = 100_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(AddressFilesToLMDB.class);

    private final @NonNull CAddressFilesToLMDB addressFilesToLMDB;

    private final AtomicLong addressCounter = new AtomicLong();

    private final ReadStatistic readStatistic = new ReadStatistic();

    // AtomicReference toString is identity-style and the wrapped value mutates per-file —
    // the per-file diagnostic belongs in the LOGGER lines, not in this aggregate's toString.
    @ToString.Exclude
    @NonNull
    AtomicReference<@Nullable AddressFile> currentAddressFile = new AtomicReference<>();

    /**
     * Flag controlling the main import loop; cleared via {@link #interrupt()}.
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
        try (LMDBPersistence persistence = new LMDBPersistence(lmdbConfigurationWrite, persistenceUtils)) {
            LOGGER.info("Init LMDB ...");
            persistence.init();
            LOGGER.info("... init LMDB done.");

            try {
                FileHelper fileHelper = new FileHelper();
                List<String> addressesFiles = Objects.requireNonNull(addressFilesToLMDB.addressesFiles);
                List<File> files = fileHelper.stringsToFiles(addressesFiles);
                fileHelper.assertFilesExists(files);

                java.util.function.Consumer<AddressToCoin> supported = addressToCoin -> {
                    ByteBuffer hash160 = addressToCoin.hash160();
                    persistence.putNewAmount(hash160, addressToCoin.coin());
                    addressCounter.incrementAndGet();

                    if (addressCounter.get() % PROGRESS_LOG == 0) {
                        logProgress();
                    }
                };

                java.util.function.Consumer<String> unsupported = line -> {
                    if (readStatistic.getUnsupportedTotal() % PROGRESS_LOG == 0) {
                        logProgress();
                    }
                };

                LOGGER.info("Iterate address files ...");
                for (File file : files) {
                    if (!shouldRun.get()) {
                        break;
                    }
                    AddressFile addressFile = new AddressFile(file, readStatistic, network, supported, unsupported);

                    LOGGER.info("process " + file.getAbsolutePath());
                    currentAddressFile.set(addressFile);
                    addressFile.readFile();
                    currentAddressFile.set(null);
                    LOGGER.info("finished: " + file.getAbsolutePath());

                    logProgress();
                }
                logProgress();
                LOGGER.info("... iterate address files done.");

                for (String error : readStatistic.errors) {
                    LOGGER.info("Error in line: " + error);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to import address file (file in flight: " + currentAddressFile.get() + ")", e);
            }
        }
    }

    private void logProgress() {
        LOGGER.info("Progress: " + addressCounter.get() + " addresses. Unsupported: "
                + readStatistic.getUnsupportedTotal() + ". Errors: " + readStatistic.errors.size()
                + ". Current File progress: " + String.format("%.2f", readStatistic.currentFileProgress) + "%.");
    }

    @Override
    public void interrupt() {
        AddressFile addressFile = currentAddressFile.get();
        if (addressFile != null) {
            addressFile.interrupt();
        }
    }
}
