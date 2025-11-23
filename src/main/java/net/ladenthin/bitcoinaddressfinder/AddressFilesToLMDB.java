// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.NonNull;

public class AddressFilesToLMDB implements Runnable, Interruptable {
    
    private final static long PROGRESS_LOG = 100_000;

    private final Logger logger = LoggerFactory.getLogger(AddressFilesToLMDB.class);

    private final @NonNull CAddressFilesToLMDB addressFilesToLMDB;

    private final AtomicLong addressCounter = new AtomicLong();

    private final ReadStatistic readStatistic = new ReadStatistic();

    @NonNull
    AtomicReference<AddressFile> currentAddressFile = new AtomicReference<>();
    
    protected final AtomicBoolean shouldRun = new AtomicBoolean(true);
    
    public AddressFilesToLMDB( @NonNull CAddressFilesToLMDB addressFilesToLMDB) {
        this.addressFilesToLMDB = addressFilesToLMDB;
    }

    @Override
    public void run() {
        final Network network = new NetworkParameterFactory().getNetwork();

        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        CLMDBConfigurationWrite lmdbConfigurationWrite = Objects.requireNonNull(addressFilesToLMDB.lmdbConfigurationWrite);
        try(LMDBPersistence persistence = new LMDBPersistence(lmdbConfigurationWrite, persistenceUtils)) {
            logger.info("Init LMDB ...");
            persistence.init();
            logger.info("... init LMDB done.");

            try {
                FileHelper fileHelper = new FileHelper();
                List<String> addressesFiles = Objects.requireNonNull(addressFilesToLMDB.addressesFiles);
                List<File> files = fileHelper.stringsToFiles(addressesFiles);
                fileHelper.assertFilesExists(files);

                java.util.function.Consumer<AddressToCoin> supported =
                        addressToCoin -> {
                            ByteBuffer hash160 = addressToCoin.hash160();
                            persistence.putNewAmount(hash160, addressToCoin.coin());
                            addressCounter.incrementAndGet();

                            if (addressCounter.get() % PROGRESS_LOG == 0) {
                                logProgress();
                            }
                        };

                java.util.function.Consumer<String> unsupported =
                        line -> {
                            if (readStatistic.unsupported % PROGRESS_LOG == 0) {
                                logProgress();
                            }
                        };

                logger.info("Iterate address files ...");
                for (File file : files) {
                    if (!shouldRun.get()) {
                        break;
                    }
                    AddressFile addressFile = new AddressFile(
                            file,
                            readStatistic,
                            network,
                            supported,
                            unsupported
                    );

                logger.info("process " + file.getAbsolutePath());
                currentAddressFile.set(addressFile);
                addressFile.readFile();
                currentAddressFile.set(null);
                logger.info("finished: " + file.getAbsolutePath());
                
                logProgress();
            }
            logProgress();
            logger.info("... iterate address files done.");

            for (String error : readStatistic.errors) {
                logger.info("Error in line: " + error);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void logProgress() {
        logger.info("Progress: " + addressCounter.get() + " addresses. Unsupported: " + readStatistic.unsupported + ". Errors: " + readStatistic.errors.size() + ". Current File progress: " + String.format("%.2f", readStatistic.currentFileProgress) + "%.");
    }

    @Override
    public void interrupt() {
        AddressFile addressFile = currentAddressFile.get();
        if (addressFile != null) {
            addressFile.interrupt();
        }
    }
}
