// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.cli;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.command.AddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.command.LMDBCompact;
import net.ladenthin.bitcoinaddressfinder.command.LMDBDelta;
import net.ladenthin.bitcoinaddressfinder.command.LMDBToAddressFile;
import net.ladenthin.bitcoinaddressfinder.command.TuneConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.CAddressFilesToLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CCompactLMDB;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBDelta;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBToAddressFile;
import net.ladenthin.bitcoinaddressfinder.configuration.CTuneConfiguration;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.core.InterruptedRuntimeException;
import net.ladenthin.bitcoinaddressfinder.engine.Finder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI entry point: loads the configuration file and dispatches to the configured command.
 * <p>VM option: {@code -Dorg.slf4j.simpleLogger.defaultLogLevel=trace}
 */
@ToString
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

    /** SLF4J logger for the CLI entry point. */
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    // List of registered Interruptables — mutable list of stateful coordinators, recursive/heavy.
    @ToString.Exclude
    private final List<Interruptable> interruptables = new ArrayList<>();

    private final CConfiguration configuration;

    // CountDownLatch toString is uninformative lifecycle state.
    @ToString.Exclude
    private final CountDownLatch runLatch = new CountDownLatch(1);

    /**
     * Returns the run-completion latch.
     *
     * <p>Exposed for tests so they can assert that {@link #run()} counted down the
     * latch on both success and failure paths.
     *
     * @return the run-completion latch
     */
    @VisibleForTesting
    public CountDownLatch getRunLatch() {
        return runLatch;
    }

    /**
     * Creates a new main instance for the given configuration.
     *
     * @param configuration the loaded configuration
     */
    public Main(CConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Reads the entire contents of {@code path} as a string using UTF-8.
     *
     * @param path the file to read
     * @return the file contents
     * @throws IOException if the file cannot be read
     */
    public static String readString(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * Parses a JSON configuration string.
     *
     * @param configurationString the JSON document
     * @return the parsed {@link CConfiguration}
     * @throws IOException if the JSON cannot be deserialised
     */
    public static CConfiguration fromJson(String configurationString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(configurationString, CConfiguration.class);
    }

    /**
     * Parses a YAML configuration string.
     *
     * @param configurationString the YAML document
     * @return the parsed {@link CConfiguration}
     * @throws IOException if the YAML cannot be deserialised
     */
    public static CConfiguration fromYaml(String configurationString) throws IOException {
        YAMLMapper mapper = new YAMLMapper();
        return mapper.readValue(configurationString, CConfiguration.class);
    }

    /**
     * Serialises a {@link CConfiguration} as indented JSON.
     *
     * @param configuration the configuration to serialise
     * @return the JSON representation
     * @throws IOException if the configuration cannot be serialised
     */
    public static String configurationToJson(CConfiguration configuration) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        applyFieldOnlyVisibility(mapper);
        return mapper.writeValueAsString(configuration);
    }

    /**
     * Restricts serialisation to public fields, never getters.
     *
     * <p>The configuration POJOs are plain field carriers, but {@code CProducer} also exposes the
     * derived {@code getOverallWorkSize()}. With Jackson's default visibility that getter is written
     * out as an {@code "overallWorkSize"} property, and reading the file back fails with
     * {@code UnrecognizedPropertyException} because no such field exists — so a config the tool
     * itself printed could not be loaded by the tool. Every serialisation path here feeds output a
     * user may paste back in, so all of them must round-trip.
     *
     * @param mapper the mapper to configure in place
     */
    private static void applyFieldOnlyVisibility(ObjectMapper mapper) {
        mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.PUBLIC_ONLY);
    }

    /**
     * Serialises a {@link CConfiguration} as YAML.
     *
     * @param configuration the configuration to serialise
     * @return the YAML representation
     * @throws IOException if the configuration cannot be serialised
     */
    public static String configurationToYAML(CConfiguration configuration) throws IOException {
        YAMLMapper mapper = new YAMLMapper();
        applyFieldOnlyVisibility(mapper);
        return mapper.writeValueAsString(configuration);
    }

    /**
     * Loads a configuration file from disk, picking the parser based on the file extension.
     *
     * <p>Supported extensions (case-insensitive):
     * <ul>
     *   <li>{@link #FILE_EXTENSION_JSON} / {@link #FILE_EXTENSION_JS} &#x2192; parsed as JSON via {@link #fromJson(String)}</li>
     *   <li>{@link #FILE_EXTENSION_YAML} / {@link #FILE_EXTENSION_YML} &#x2192; parsed as YAML via {@link #fromYaml(String)}</li>
     * </ul>
     *
     * @param configurationPath the configuration file
     * @return the parsed configuration
     * @throws IOException if the file cannot be read or its content cannot be deserialised
     * @throws IllegalArgumentException if {@code configurationPath} does not end with a supported extension
     */
    public static CConfiguration loadConfiguration(Path configurationPath) throws IOException {
        String configurationAsString = readString(configurationPath);
        String lowerPath = configurationPath.toString().toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(FILE_EXTENSION_JS) || lowerPath.endsWith(FILE_EXTENSION_JSON)) {
            return fromJson(configurationAsString);
        } else if (lowerPath.endsWith(FILE_EXTENSION_YAML) || lowerPath.endsWith(FILE_EXTENSION_YML)) {
            return fromYaml(configurationAsString);
        } else {
            throw new IllegalArgumentException("Unknown file ending for: " + configurationPath);
        }
    }

    /**
     * Java entry point. Loads the configuration file passed as the first argument and runs the tool.
     *
     * @param args the program arguments; expects a single path to a JSON or YAML configuration file
     * @throws IOException if the configuration file cannot be read or deserialised
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            LOGGER.error("Invalid arguments. Pass path to configuration as first argument.");
            return;
        }
        final Path configurationPath = Path.of(args[0]);
        final CConfiguration configuration = loadConfiguration(configurationPath);
        Main main = new Main(configuration);
        main.logConfigurationTransformation();
        main.run();
    }

    /**
     * Logs the JSON and YAML representations of the loaded configuration for review.
     *
     * @throws IOException if the configuration cannot be serialised
     */
    public void logConfigurationTransformation() throws IOException {
        String json = configurationToJson(configuration);
        String yaml = configurationToYAML(configuration);
        LOGGER.info(
                "Please review the transformed configuration to ensure it aligns with your expectations and requirements before proceeding.:\n"
                        + "########## BEGIN transformed JSON configuration ##########\n"
                        + json
                        + "\n" + "########## END   transformed JSON configuration ##########\n"
                        + "\n"
                        + "########## BEGIN transformed YAML configuration ##########\n"
                        + yaml
                        + "\n" + "########## END   transformed YAML configuration ##########\n");
    }

    @Override
    public void run() {
        LOGGER.info(configuration.command.name());

        addSchutdownHook();

        try {
            switch (configuration.command) {
                case Find -> {
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
                }
                case LMDBToAddressFile -> {
                    CLMDBToAddressFile cLMDBToAddressFile = Objects.requireNonNull(configuration.lmdbToAddressFile);
                    LMDBToAddressFile lmdbToAddressFile = new LMDBToAddressFile(cLMDBToAddressFile);
                    interruptables.add(lmdbToAddressFile);
                    lmdbToAddressFile.run();
                }
                case AddressFilesToLMDB -> {
                    CAddressFilesToLMDB cAddressFilesToLMDB = Objects.requireNonNull(configuration.addressFilesToLMDB);
                    AddressFilesToLMDB addressFilesToLMDB = new AddressFilesToLMDB(cAddressFilesToLMDB);
                    interruptables.add(addressFilesToLMDB);
                    addressFilesToLMDB.run();
                }
                case TuneConfiguration -> {
                    CTuneConfiguration cTuneConfiguration = Objects.requireNonNull(configuration.tuneConfiguration);
                    TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
                    interruptables.add(tuneConfiguration);
                    tuneConfiguration.run();
                }
                case CompactLMDB -> {
                    CCompactLMDB cCompactLMDB = Objects.requireNonNull(configuration.compactLMDB);
                    LMDBCompact lmdbCompact = new LMDBCompact(cCompactLMDB);
                    interruptables.add(lmdbCompact);
                    lmdbCompact.run();
                }
                case LMDBDelta -> {
                    CLMDBDelta cLMDBDelta = Objects.requireNonNull(configuration.lmdbDelta);
                    LMDBDelta lmdbDelta = new LMDBDelta(cLMDBDelta);
                    interruptables.add(lmdbDelta);
                    lmdbDelta.run();
                }
                case OpenCLInfo -> {
                    OpenCLBuilder openCLBuilder = new OpenCLBuilder();
                    List<OpenCLPlatform> openCLPlatforms = openCLBuilder.build();
                    for (OpenCLPlatform platform : openCLPlatforms) {
                        System.out.println("=== OpenCL platform: " + platform.platformName() + " ===");
                        for (OpenCLDevice device : platform.openCLDevices()) {
                            System.out.println(device.toStringPretty());
                        }
                    }
                }
                default ->
                    throw new UnsupportedOperationException(
                            "Command: " + configuration.command.name() + " currently not supported.");
            }
            LOGGER.info("Main#run end.");
        } catch (Exception e) {
            LOGGER.error("Fatal error during Main.run; triggering shutdown of all registered components.", e);
            interrupt();
            throw new IllegalStateException(
                    "Main.run() failed on thread " + Thread.currentThread().getName() + "; shutdown triggered", e);
        } finally {
            runLatch.countDown();
        }

        if (false) {
            printAllStackTracesWithDelay(2_000L, true);
        }
    }

    /**
     * Prints all live thread stack traces after waiting for the given delay.
     *
     * <p><b>Developer-debug helper only.</b> The sole production call site is
     * gated by {@code if (false) { ... }} near the end of {@link #run()} — an
     * "uncomment for local debugging" hook to dump the thread state at the end
     * of a run. Kept (rather than deleted with the dead {@code if}) so the
     * debug hook survives for future use. Not part of any public API.
     *
     * @param delayMillis     how long to wait before sampling, in milliseconds
     * @param includeDaemons  whether daemon threads should be included in the output
     */
    public static void printAllStackTracesWithDelay(long delayMillis, boolean includeDaemons) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();

        List<Map.Entry<Thread, StackTraceElement[]>> entries = new ArrayList<>(all.entrySet());
        entries.sort(Comparator.comparing(
                        (Map.Entry<Thread, StackTraceElement[]> e) -> e.getKey().getName())
                .thenComparingLong(e -> e.getKey().threadId()));

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
            System.out.println(
                    includeDaemons
                            ? "No threads to print."
                            : "No non-daemon threads found. JVM should be able to exit normally.");
        }
    }

    private void addSchutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown received via hook.");
            interrupt();
            try {
                LOGGER.info("runLatch await");
                if (!runLatch.await(30, TimeUnit.SECONDS)) {
                    LOGGER.warn("runLatch did not complete within 30s shutdown timeout; "
                            + "remaining tasks may not have finished cleanly.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedRuntimeException(
                        "Interrupted while awaiting runLatch during shutdown-hook (30s timeout)", e);
            }
            LOGGER.info("Finish shutdown hook.");
        }));
    }

    @Override
    public void interrupt() {
        for (Interruptable interruptable : interruptables) {
            interruptable.interrupt();
        }
    }
}
