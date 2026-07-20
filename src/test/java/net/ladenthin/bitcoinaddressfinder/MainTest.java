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
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
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
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
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
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
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

    // <editor-fold defaultstate="collapsed" desc="main format detection">
    @Test
    @OpenCLTest
    public void main_jsonExtensionPath_parsesAndRunsConfiguration() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // act
        Main.main(new String[] {config_OpenCLInfo_json.toAbsolutePath().toString()});
    }

    @Test
    @OpenCLTest
    public void main_jsExtensionPath_parsesAndRunsConfiguration() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // act
        Main.main(new String[] {config_OpenCLInfo_js.toAbsolutePath().toString()});
    }

    @Test
    @OpenCLTest
    public void main_yamlExtensionPath_parsesAndRunsConfiguration() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // act
        Main.main(new String[] {config_OpenCLInfo_yaml.toAbsolutePath().toString()});
    }

    @Test
    @OpenCLTest
    public void main_ymlExtensionPath_parsesAndRunsConfiguration() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
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

    // <editor-fold defaultstate="collapsed" desc="serialisation round-trips">
    /**
     * A configuration the tool serialises must be readable by the tool. {@code CProducer} exposes a
     * derived {@code getOverallWorkSize()}; under Jackson's default getter visibility that is written
     * as an {@code "overallWorkSize"} property with no matching field, so loading the output back
     * throws {@code UnrecognizedPropertyException}. This is not hypothetical — it broke the config
     * printed by {@code TuneConfiguration} and logged by {@code Main}. The whole value of that output
     * is that a user can paste it and run it, so it must round-trip. This fails if the field-only
     * visibility is dropped from either serialiser.
     */
    @Test
    public void configurationToJson_withOpenCLProducer_roundTripsBackThroughFromJson() throws IOException {
        CConfiguration configuration = new CConfiguration();
        configuration.command = CCommand.Find;
        configuration.finder = new CFinder();
        configuration.finder.producerOpenCL.add(new CProducerOpenCL());

        String json = Main.configurationToJson(configuration);
        assertThat("the derived getter must not leak into the output", json, not(containsString("overallWorkSize")));

        // The load path that a pasted file would take; it must not throw.
        CConfiguration reloaded = Main.fromJson(json);
        assertThat(reloaded.command, is(equalTo(CCommand.Find)));
        assertThat(reloaded.finder.producerOpenCL.size(), is(equalTo(1)));
    }

    @Test
    public void configurationToYAML_withOpenCLProducer_roundTripsBackThroughFromYaml() throws IOException {
        CConfiguration configuration = new CConfiguration();
        configuration.command = CCommand.Find;
        configuration.finder = new CFinder();
        configuration.finder.producerOpenCL.add(new CProducerOpenCL());

        String yaml = Main.configurationToYAML(configuration);
        assertThat(yaml, not(containsString("overallWorkSize")));

        CConfiguration reloaded = Main.fromYaml(yaml);
        assertThat(reloaded.command, is(equalTo(CCommand.Find)));
        assertThat(reloaded.finder.producerOpenCL.size(), is(equalTo(1)));
    }
    // </editor-fold>
}
