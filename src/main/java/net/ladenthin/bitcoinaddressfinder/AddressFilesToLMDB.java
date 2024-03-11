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

import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;

public class AddressFilesToLMDB implements Runnable, Interruptable {

    private final Logger logger = LoggerFactory.getLogger(AddressFilesToLMDB.class);

    private final CAddressFilesToLMDB addressFilesToLMDB;

    private LMDBPersistence persistence;

    private final AtomicLong addressCounter = new AtomicLong();

    private final ReadStatistic readStatistic = new ReadStatistic();
    
    private final static long PROGRESS_LOG = 100_000;
    
    private final AtomicBoolean shouldRun;

    public AddressFilesToLMDB(CAddressFilesToLMDB addressFilesToLMDB, AtomicBoolean shouldRun) {
        this.addressFilesToLMDB = addressFilesToLMDB;
        this.shouldRun = shouldRun;
    }

    @Override
    public void run() {
        final NetworkParameters networkParameters = new NetworkParameterFactory().getOrCreate();

        PersistenceUtils persistenceUtils = new PersistenceUtils(networkParameters);
        persistence = new LMDBPersistence(addressFilesToLMDB.lmdbConfigurationWrite, persistenceUtils);
        logger.info("Init LMDB ...");
        persistence.init();
        logger.info("... init LMDB done.");

        try {
            FileHelper fileHelper = new FileHelper();
            List<File> files = fileHelper.stringsToFiles(addressFilesToLMDB.addressesFiles);
            fileHelper.assertFilesExists(files);
            
            logger.info("Iterate address files ...");
            for (File file : files) {
                AddressFile addressFile = new AddressFile(
                    file,
                    readStatistic,
                    networkParameters,
                    this::supported,
                    this::unsupported,
                    shouldRun
                );
                
                logger.info("process " + file.getAbsolutePath());
                addressFile.readFile();
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
        } finally {
            persistence.close();
        }
    }
    
    private void supported(AddressToCoin addressToCoin) {
        ByteBuffer hash160 = addressToCoin.getHash160();
        persistence.putNewAmount(hash160, addressToCoin.getCoin());
        addressCounter.incrementAndGet();

        if (addressCounter.get() % PROGRESS_LOG == 0) {
            logProgress();
        }
    }

    private void unsupported(String line) {
        if (readStatistic.unsupported % PROGRESS_LOG == 0) {
            logProgress();
        }
    }

    private void logProgress() {
        logger.info("Progress: " + addressCounter.get() + " addresses. Unsupported: " + readStatistic.unsupported + ". Errors: " + readStatistic.errors.size() + ". Current File progress: " + String.format("%.2f", readStatistic.currentFileProgress) + "%.");
    }

    @Override
    public void interrupt() {
    }
}
