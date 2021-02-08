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
package net.ladenthin.bitcoinaddressfinder.cli;

import com.google.gson.Gson;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import net.ladenthin.javacommons.StreamHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.AddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.LMDBToAddressFile;
import net.ladenthin.bitcoinaddressfinder.OpenCLInfo;
import net.ladenthin.bitcoinaddressfinder.Finder;

// VM option: -Dorg.slf4j.simpleLogger.defaultLogLevel=trace
public class Main implements Runnable {

    private final static Logger logger = LoggerFactory.getLogger(Main.class);

    private final CConfiguration configuration;
    
    public Main(CConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public static Main createFromConfigurationFile(File configFile) {
        try {
            return createFromConfigurationString(new StreamHelper().readFullyAsUTF8String(configFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static Main createFromConfigurationString(String configurationString) {
        Gson gson = new Gson();
        CConfiguration configuration = gson.fromJson(configurationString, CConfiguration.class);
        return new Main(configuration);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            logger.error("Invalid arguments. Pass path to configuration as first argument.");
            return;
        }
        Main main = createFromConfigurationFile(new File(args[0]));
        main.run();
    }

    @Override
    public void run() {
        logger.info(configuration.command.name());
        switch (configuration.command) {
            case Find:
                Finder finder = new Finder(configuration.finder);
                finder.startRunner();
                break;
            case LMDBToAddressFile:
                LMDBToAddressFile lmdbToAddressFile = new LMDBToAddressFile(configuration.lmdbToAddressFile);
                lmdbToAddressFile.run();
                break;
            case AddressFilesToLMDB:
                AddressFilesToLMDB addressFilesToLMDB = new AddressFilesToLMDB(configuration.addressFilesToLMDB);
                addressFilesToLMDB.run();
                break;
            case OpenCLInfo:
                OpenCLInfo.openClInfo();
                break;
            default:
                throw new UnsupportedOperationException("Command: " + configuration.command.name() + " currently not supported." );
        }
    }
}
