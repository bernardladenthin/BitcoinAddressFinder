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
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.AddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.Finder;
import net.ladenthin.bitcoinaddressfinder.Interruptable;
import net.ladenthin.bitcoinaddressfinder.LMDBToAddressFile;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// VM option: -Dorg.slf4j.simpleLogger.defaultLogLevel=trace
public class Main implements Runnable, Interruptable {

    private final static Logger logger = LoggerFactory.getLogger(Main.class);

    private final List<Interruptable> interruptables = new ArrayList<>();

    private final CConfiguration configuration;
    
    public Main(CConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public static Main createFromConfigurationFile(Path path) {
        try {
            String content = Files.readString(path, Charset.defaultCharset());
            return createFromConfigurationString(content);
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
        Main main = createFromConfigurationFile(Path.of(args[0]));
        main.run();
    }

    @Override
    public void run() {
        logger.info(configuration.command.name());
        
        addSchutdownHook();
        
        switch (configuration.command) {
            case Find:
                Finder finder = new Finder(configuration.finder);
                interruptables.add(finder);
                finder.startConsumer();
                finder.configureProducer();
                finder.initProducer();
                finder.startProducer();
                finder.shutdownAndAwaitTermination();
                break;
            case LMDBToAddressFile:
                LMDBToAddressFile lmdbToAddressFile = new LMDBToAddressFile(configuration.lmdbToAddressFile);
                interruptables.add(lmdbToAddressFile);
                lmdbToAddressFile.run();
                break;
            case AddressFilesToLMDB:
                AddressFilesToLMDB addressFilesToLMDB = new AddressFilesToLMDB(configuration.addressFilesToLMDB);
                interruptables.add(addressFilesToLMDB);
                addressFilesToLMDB.run();
                break;
            case OpenCLInfo:
                OpenCLBuilder openCLBuilder = new OpenCLBuilder();
                List<OpenCLPlatform> openCLPlatforms = openCLBuilder.build();
                System.out.println(openCLPlatforms);
                break;
            default:
                throw new UnsupportedOperationException("Command: " + configuration.command.name() + " currently not supported." );
        }
        logger.info("Main#run end.");
    }
    
    private void addSchutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            interrupt();
        }));
    }
    
    @Override
    public void interrupt() {
        for (Interruptable interruptable : interruptables) {
            interruptable.interrupt();
        }
    }
}
