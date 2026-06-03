// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static net.ladenthin.bitcoinaddressfinder.cli.Main.printAllStackTracesWithDelay;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.cli.Main;
import net.ladenthin.bitcoinaddressfinder.configuration.CCommand;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MainTest {

    @TempDir
    public Path folder;

    private final Path resourceDirectory = Path.of("src", "test", "resources");
    private final Path testRoundtripDirectory = resourceDirectory.resolve("testRoundtrip");
    private final Path testOpenCLInfoDirectory = resourceDirectory.resolve("testOpenCLInfo");

    private final Path config_AddressFilesToLMDB_json =
            testRoundtripDirectory.resolve("config_AddressFilesToLMDB.json");
    private final Path config_LMDBToAddressFile_json = testRoundtripDirectory.resolve("config_LMDBToAddressFile.json");
    private final Path config_Find_SecretsFile_json = testRoundtripDirectory.resolve("config_Find_SecretsFile.json");

    private final Path config_Find_1OpenCLDevice_json =
            testRoundtripDirectory.resolve("config_Find_1OpenCLDevice.json");

    private final Path config_OpenCLInfo_json = testOpenCLInfoDirectory.resolve("config_OpenCLInfo.json");
    private final Path config_OpenCLInfo_yaml = testOpenCLInfoDirectory.resolve("config_OpenCLInfo.yaml");
    private final Path config_OpenCLInfo_yml = testOpenCLInfoDirectory.resolve("config_OpenCLInfo.yml");
    private final Path config_OpenCLInfo_js = testOpenCLInfoDirectory.resolve("config_OpenCLInfo.js");

    /** Minimal JSON string representing an OpenCLInfo configuration for unit tests. */
    private static final String OPEN_CL_INFO_JSON_STRING = "{\"command\":\"OpenCLInfo\"}";
    /** Minimal YAML string representing an OpenCLInfo configuration for unit tests. */
    private static final String OPEN_CL_INFO_YAML_STRING = "command: OpenCLInfo\n";

    private static final long DEFAULT_INTERRUPT_DELAY_SECONDS = 10;

    private void interruptAfterDelay(Main main, long delaySeconds) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(
                () -> {
                    main.interrupt();
                },
                delaySeconds,
                TimeUnit.SECONDS);
    }

    // <editor-fold defaultstate="collapsed" desc="testRoundtrip">
    @Test
    public void testRoundtrip_configurationsGiven_lmdbCreatedExportedAndRunFindSecretsFile()
            throws IOException, InterruptedException {
        // arrange, act, assert
        Main.main(new String[] {config_AddressFilesToLMDB_json.toAbsolutePath().toString()});
        Main.main(new String[] {config_LMDBToAddressFile_json.toAbsolutePath().toString()});
        Main.main(new String[] {config_Find_SecretsFile_json.toAbsolutePath().toString()});
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="testRoundtrip OpenCL producer">
    @Test
    @OpenCLTest
    public void testRoundtripOpenCLProducer_configurationsGiven_lmdbCreatedAndRunFindOpenCLDevice()
            throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        Main mainAddressFilesToLMDB = new Main(Main.fromJson(Main.readString(config_AddressFilesToLMDB_json)));
        mainAddressFilesToLMDB.logConfigurationTransformation();
        mainAddressFilesToLMDB.run();

        new LogLevelChange().setLevel(Level.DEBUG);

        Main mainFind_1OpenCLDevice = new Main(Main.fromJson(Main.readString(config_Find_1OpenCLDevice_json)));

        // interrupt the act after 10 seconds
        interruptAfterDelay(mainFind_1OpenCLDevice, DEFAULT_INTERRUPT_DELAY_SECONDS);

        // act
        mainFind_1OpenCLDevice.logConfigurationTransformation();
        mainFind_1OpenCLDevice.run();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="testRoundtrip">
    @Test
    @OpenCLTest
    public void testOpenCLInfo_configurationGiven_noExceptionThrown() throws IOException, InterruptedException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        Main mainFind_SecretsFile = new Main(Main.fromJson(Main.readString(config_OpenCLInfo_json)));
        mainFind_SecretsFile.logConfigurationTransformation();
        mainFind_SecretsFile.run();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="invalidArgument">
    @Test
    public void main_noArgumentGiven_errorLogged() throws IOException, InterruptedException {
        try (LogCaptor logCaptor = LogCaptor.forClass(Main.class)) {
            // act
            Main.main(new String[0]);

            // assert
            assertThat(
                    logCaptor.getErrorLogs(),
                    hasItem(equalTo("Invalid arguments. Pass path to configuration as first argument.")));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="printAllStackTracesWithDelay">
    @Test
    public void printAllStackTracesWithDelay_includeDaemonsTrue_noExceptionThrown() {
        // act
        printAllStackTracesWithDelay(0, true);
    }

    @Test
    public void printAllStackTracesWithDelay_includeDaemonsFalse_noExceptionThrown() {
        // act
        printAllStackTracesWithDelay(0, false);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="fromJson">
    @Test
    public void fromJson_validJsonString_returnsExpectedConfiguration() throws IOException {
        // act
        CConfiguration configuration = Main.fromJson(OPEN_CL_INFO_JSON_STRING);

        // pre-assert
        assertThat(configuration, is(notNullValue()));

        // assert
        assertThat(configuration.command, is(equalTo(CCommand.OpenCLInfo)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="fromYaml">
    @Test
    public void fromYaml_validYamlString_returnsExpectedConfiguration() throws IOException {
        // act
        CConfiguration configuration = Main.fromYaml(OPEN_CL_INFO_YAML_STRING);

        // pre-assert
        assertThat(configuration, is(notNullValue()));

        // assert
        assertThat(configuration.command, is(equalTo(CCommand.OpenCLInfo)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="loadConfiguration extension dispatch (round-trip + syntax probe)">
    /**
     * Round-trip: serialise via {@code configurationToJson}, write to disk with the given extension,
     * read back via {@link Main#loadConfiguration(Path)}, assert the loaded configuration matches
     * the original on the {@code command} field. Also probes the written file content to confirm the
     * correct serializer was used (JSON content starts with {@code &#123;}; YAML does not).
     */
    private void assertRoundTripJsonExtension(String extension) throws IOException {
        // arrange
        CConfiguration original = Main.fromJson(OPEN_CL_INFO_JSON_STRING);
        String serialized = Main.configurationToJson(original);
        Path file = folder.resolve("config" + extension);
        Files.writeString(file, serialized, StandardCharsets.UTF_8);

        // syntax probe — JSON content must start with '{'
        String onDisk = Files.readString(file, StandardCharsets.UTF_8).trim();
        assertThat(onDisk, startsWith("{"));

        // act
        CConfiguration loaded = Main.loadConfiguration(file);

        // assert
        assertThat(loaded, is(notNullValue()));
        assertThat(loaded.command, is(equalTo(original.command)));
    }

    private void assertRoundTripYamlExtension(String extension) throws IOException {
        // arrange
        CConfiguration original = Main.fromYaml(OPEN_CL_INFO_YAML_STRING);
        String serialized = Main.configurationToYAML(original);
        Path file = folder.resolve("config" + extension);
        Files.writeString(file, serialized, StandardCharsets.UTF_8);

        // syntax probe — YAML content must NOT start with '{' (would indicate JSON serializer ran)
        String onDisk = Files.readString(file, StandardCharsets.UTF_8).trim();
        assertThat(onDisk, not(startsWith("{")));
        assertThat(onDisk, containsString("command"));

        // act
        CConfiguration loaded = Main.loadConfiguration(file);

        // assert
        assertThat(loaded, is(notNullValue()));
        assertThat(loaded.command, is(equalTo(original.command)));
    }

    @Test
    public void loadConfiguration_jsonExtension_roundTripsAndWritesJsonContent() throws IOException {
        assertRoundTripJsonExtension(".json");
    }

    @Test
    public void loadConfiguration_jsExtension_roundTripsAndWritesJsonContent() throws IOException {
        assertRoundTripJsonExtension(".js");
    }

    @Test
    public void loadConfiguration_yamlExtension_roundTripsAndWritesYamlContent() throws IOException {
        assertRoundTripYamlExtension(".yaml");
    }

    @Test
    public void loadConfiguration_ymlExtension_roundTripsAndWritesYamlContent() throws IOException {
        assertRoundTripYamlExtension(".yml");
    }

    @Test
    public void loadConfiguration_uppercaseExtension_isCaseInsensitive() throws IOException {
        // arrange — caller might pass /tmp/config.JSON
        CConfiguration original = Main.fromJson(OPEN_CL_INFO_JSON_STRING);
        Path file = folder.resolve("config.JSON");
        Files.writeString(file, Main.configurationToJson(original), StandardCharsets.UTF_8);

        // act
        CConfiguration loaded = Main.loadConfiguration(file);

        // assert
        assertThat(loaded.command, is(equalTo(original.command)));
    }

    @Test
    public void loadConfiguration_unknownExtension_throwsIllegalArgumentException() throws IOException {
        // arrange
        Path file = folder.resolve("config.txt");
        Files.writeString(file, OPEN_CL_INFO_JSON_STRING, StandardCharsets.UTF_8);

        // act + assert
        assertThrows(IllegalArgumentException.class, () -> Main.loadConfiguration(file));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="main format detection">
    @Test
    @OpenCLTest
    public void main_jsonExtensionPath_parsesAndRunsConfiguration() throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // act
        Main.main(new String[] {config_OpenCLInfo_json.toAbsolutePath().toString()});
    }

    @Test
    @OpenCLTest
    public void main_jsExtensionPath_parsesAndRunsConfiguration() throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // act
        Main.main(new String[] {config_OpenCLInfo_js.toAbsolutePath().toString()});
    }

    @Test
    @OpenCLTest
    public void main_yamlExtensionPath_parsesAndRunsConfiguration() throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // act
        Main.main(new String[] {config_OpenCLInfo_yaml.toAbsolutePath().toString()});
    }

    @Test
    @OpenCLTest
    public void main_ymlExtensionPath_parsesAndRunsConfiguration() throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // act
        Main.main(new String[] {config_OpenCLInfo_yml.toAbsolutePath().toString()});
    }

    @Test
    public void main_unknownExtensionPath_throwsIllegalArgumentException() throws IOException {
        // arrange
        File tempFile = Files.createFile(folder.resolve("config.txt")).toFile();
        Files.writeString(tempFile.toPath(), OPEN_CL_INFO_JSON_STRING, StandardCharsets.UTF_8);

        // act
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] {tempFile.getAbsolutePath()}));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="run() error path: catch + interrupt + countDown + rethrow">
    @Test
    public void run_finderFailsDuringStartup_runLatchCountedDownAndExceptionRethrown() {
        // arrange: Find with finder.consumerJava = null triggers NPE inside
        // Finder.startConsumer's Objects.requireNonNull. That exception propagates
        // through Main.run's switch body and must hit the new catch/finally.
        CConfiguration configuration = new CConfiguration();
        configuration.command = CCommand.Find;
        configuration.finder = new CFinder();
        // intentionally leave configuration.finder.consumerJava = null
        Main main = new Main(configuration);

        try (LogCaptor logCaptor = LogCaptor.forClass(Main.class)) {
            // act + assert: Main.run must rethrow the wrapped exception
            RuntimeException thrown = assertThrows(RuntimeException.class, main::run);
            assertThat(thrown.getCause(), is(notNullValue()));

            // assert: fatal-error log was emitted
            assertThat(logCaptor.getErrorLogs(), hasItem(containsString("Fatal error during Main.run")));

            // assert: finally block ran -> runLatch counted down (shutdown hook would not hang)
            assertThat(main.getRunLatch().getCount(), is(equalTo(0L)));
        }
    }
    // </editor-fold>
}
