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
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBToAddressFile;

public class LMDBToAddressFile implements Runnable, Interruptable {

    private final Logger logger = LoggerFactory.getLogger(LMDBToAddressFile.class);

    private final CLMDBToAddressFile lmdbToAddressFile;
    
    private final Stoppable stoppable;

    private LMDBPersistence persistence;

    public LMDBToAddressFile(CLMDBToAddressFile lmdbToAddressFile, Stoppable stoppable) {
        this.lmdbToAddressFile = lmdbToAddressFile;
        this.stoppable = stoppable;
    }

    @Override
    public void run() {
        final NetworkParameters networkParameters = new NetworkParameterFactory().getOrCreate();
        
        PersistenceUtils persistenceUtils = new PersistenceUtils(networkParameters);
        persistence = new LMDBPersistence(lmdbToAddressFile.lmdbConfigurationReadOnly, persistenceUtils);
        persistence.init();
        try {
            logger.info("writeAllAmounts ...");
            File addressesFile = new File(lmdbToAddressFile.addressesFile);
            // delete before write all addresses
            addressesFile.delete();
            persistence.writeAllAmountsToAddressFile(addressesFile, lmdbToAddressFile.addressFileOutputFormat, stoppable);
            logger.info("writeAllAmounts done");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            persistence.close();
        }
    }

    @Override
    public void interrupt() {
    }
}
