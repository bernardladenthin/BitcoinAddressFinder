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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.AddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.Finder;
import net.ladenthin.bitcoinaddressfinder.Interruptable;
import net.ladenthin.bitcoinaddressfinder.LMDBToAddressFile;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBToAddressFile;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// VM option: -Dorg.slf4j.simpleLogger.defaultLogLevel=trace
public class Main implements Runnable, Interruptable {

    /**
     * File extension for JavaScript configuration files; treated identically to {@link #FILE_EXTENSION_JSON}.
     */
    @VisibleForTesting
    static final String FILE_EXTENSION_JS = ".js";

    /**
     * Standard file extension for JSON configuration files.
     *
     * @see #FILE_EXTENSION_JS
     */
    @VisibleForTesting
    static final String FILE_EXTENSION_JSON = ".json";

    /**
     * Standard long-form file extension for YAML configuration files.
     *
     * @see #FILE_EXTENSION_YML
     */
    @VisibleForTesting
    static final String FILE_EXTENSION_YAML = ".yaml";

    /**
     * Short-form file extension for YAML configuration files; treated identically to {@link #FILE_EXTENSION_YAML}.
     */
    @VisibleForTesting
    static final String FILE_EXTENSION_YML = ".yml";

    @VisibleForTesting
    public static Logger logger = LoggerFactory.getLogger(Main.class);

    private final List<Interruptable> interruptables = new ArrayList<>();

    private final CConfiguration configuration;
    
    CountDownLatch runLatch = new CountDownLatch(1);
    
    public Main(CConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public static String readString(Path path) {
        try {
            String content = Files.readString(path, Charset.defaultCharset());
            return content;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static CConfiguration fromJson(String configurationString) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(configurationString, CConfiguration.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static CConfiguration fromYaml(String configurationString) {
        try {
            YAMLMapper mapper = new YAMLMapper();
            return mapper.readValue(configurationString, CConfiguration.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String configurationToJson(CConfiguration configuration) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(configuration);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String configurationToYAML(CConfiguration configuration) {
        try {
            YAMLMapper mapper = new YAMLMapper();
            return mapper.writeValueAsString(configuration);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            logger.error("Invalid arguments. Pass path to configuration as first argument.");
            return;
        }
        final Path configurationPath = Path.of(args[0]);
        String configurationAsString = readString(configurationPath);
        final CConfiguration configuration;
        String lowerPath = configurationPath.toString().toLowerCase();
        if (lowerPath.endsWith(FILE_EXTENSION_JS) || lowerPath.endsWith(FILE_EXTENSION_JSON)) {
            configuration = fromJson(configurationAsString);
        } else if (lowerPath.endsWith(FILE_EXTENSION_YAML) || lowerPath.endsWith(FILE_EXTENSION_YML)) {
            configuration = fromYaml(configurationAsString);
        } else {
            throw new IllegalArgumentException("Unknown file ending for: " + configurationPath);
        }
        Main main = new Main(configuration);
        main.logConfigurationTransformation();
        main.run();
    }

    public void logConfigurationTransformation() {
        String json = configurationToJson(configuration);
        String yaml = configurationToYAML(configuration);
        logger.info(
                "Please review the transformed configuration to ensure it aligns with your expectations and requirements before proceeding.:\n" +
                        "########## BEGIN transformed JSON configuration ##########\n" +
                        json + "\n" +
                        "########## END   transformed JSON configuration ##########\n" +
                        "\n" + 
                        "########## BEGIN transformed YAML configuration ##########\n" +
                        yaml + "\n" +
                        "########## END   transformed YAML configuration ##########\n"
        );
    }

    @Override
    public void run() {
        logger.info(configuration.command.name());
        
        addSchutdownHook();
        
        switch (configuration.command) {
            case Find:
                CFinder cFinder = Objects.requireNonNull(configuration.finder);
                Finder finder = new Finder(cFinder);
                interruptables.add(finder);
                // key producer first
                finder.startKeyProducer();
                
                // consumer second
                finder.startConsumer();
                
                // producer last
                finder.configureProducer();
                finder.initProducer();
                finder.startProducer();
                finder.shutdownAndAwaitTermination();
                break;
            case LMDBToAddressFile:
                CLMDBToAddressFile cLMDBToAddressFile = Objects.requireNonNull(configuration.lmdbToAddressFile);
                LMDBToAddressFile lmdbToAddressFile = new LMDBToAddressFile(cLMDBToAddressFile);
                interruptables.add(lmdbToAddressFile);
                lmdbToAddressFile.run();
                break;
            case AddressFilesToLMDB:
                CAddressFilesToLMDB cAddressFilesToLMDB = Objects.requireNonNull(configuration.addressFilesToLMDB);
                AddressFilesToLMDB addressFilesToLMDB = new AddressFilesToLMDB(cAddressFilesToLMDB);
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
        runLatch.countDown();
        
        if (false) {
            printAllStackTracesWithDelay(2_000L, true);
        }
    }

    public static void printAllStackTracesWithDelay(long delayMillis, boolean includeDaemons) {
        try { Thread.sleep(delayMillis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();

        List<Map.Entry<Thread, StackTraceElement[]>> entries = new ArrayList<>(all.entrySet());
        entries.sort(Comparator
                .comparing((Map.Entry<Thread, StackTraceElement[]> e) -> {
                    String n = e.getKey().getName();
                    return n == null ? "" : n;
                })
                .thenComparingLong(e -> e.getKey().getId()));

        boolean printedAny = false;
        for (Map.Entry<Thread, StackTraceElement[]> e : entries) {
            Thread t = e.getKey();
            if (!includeDaemons && t.isDaemon()) continue; // previous behavior

            printedAny = true;
            System.out.println("##################################################");
            System.out.println("# Thread: " + t + " | state=" + t.getState() + " | daemon=" + t.isDaemon());
            for (StackTraceElement el : e.getValue()) System.out.println("  at " + el);
        }

        if (!printedAny) {
            System.out.println(includeDaemons
                    ? "No threads to print."
                    : "No non-daemon threads found. JVM should be able to exit normally.");
        }
    }
    
    private void addSchutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown received via hook.");
            interrupt();
            try {
                logger.info("runLatch await");
                runLatch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            logger.info("Finish shutdown hook.");
        }));
    }
    
    @Override
    public void interrupt() {
        for (Interruptable interruptable : interruptables) {
            interruptable.interrupt();
        }
    }
}
