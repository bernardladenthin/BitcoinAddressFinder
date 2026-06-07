// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBToAddressFile;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports the LMDB database to a plaintext address file in one of the supported formats.
 */
@ToString
public class LMDBToAddressFile implements Runnable, Interruptable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LMDBToAddressFile.class);

    private final Network network = new NetworkParameterFactory().getNetwork();

    private final CLMDBToAddressFile lmdbToAddressFile;

    // Lifecycle flag — uninformative in aggregate toString.
    @ToString.Exclude
    private final AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * Creates a new exporter.
     *
     * @param lmdbToAddressFile the export configuration
     */
    public LMDBToAddressFile(CLMDBToAddressFile lmdbToAddressFile) {
        this.lmdbToAddressFile = lmdbToAddressFile;
    }

    @Override
    public void run() {
        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        try (LMDBPersistence persistence =
                new LMDBPersistence(lmdbToAddressFile.lmdbConfigurationReadOnly, persistenceUtils)) {
            persistence.init();
            LOGGER.info("writeAllAmounts ...");
            File addressesFile = new File(lmdbToAddressFile.addressesFile);
            // delete before write all addresses
            boolean deleted = addressesFile.delete();
            if (deleted) {
                LOGGER.info("deleted existing address file " + addressesFile);
            }
            persistence.writeAllAmountsToAddressFile(
                    addressesFile, lmdbToAddressFile.addressFileOutputFormat, shouldRun);
            LOGGER.info("writeAllAmounts done");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write LMDB dump to " + lmdbToAddressFile.addressesFile, e);
        }
    }

    @Override
    public void interrupt() {
        shouldRun.set(false);
    }
}
