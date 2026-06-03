// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip and syntax-probe tests for {@link Main#loadConfiguration(java.nio.file.Path)}.
 *
 * <p>Each round-trip serialises a known {@link CConfiguration} to disk using the matching
 * Jackson serializer, writes the file under a temporary directory with one of the supported
 * extensions ({@link Main#FILE_EXTENSION_JSON}, {@link Main#FILE_EXTENSION_JS},
 * {@link Main#FILE_EXTENSION_YAML}, {@link Main#FILE_EXTENSION_YML}), probes the written
 * content to confirm the correct format was emitted (JSON content starts with {@code &#123;};
 * YAML content does not), then loads it back via {@code loadConfiguration} and asserts the
 * loaded object matches the original.
 *
 * <p>Co-located with {@link Main} in the {@code cli} package so the package-private
 * {@code @VisibleForTesting} {@code FILE_EXTENSION_*} constants are directly referenceable.
 */
public class MainConfigurationLoadingTest {

    /** Minimal JSON configuration for the round-trip — only requires the {@code command} field. */
    private static final String OPEN_CL_INFO_JSON_STRING = "{\"command\":\"OpenCLInfo\"}";

    /** Minimal YAML configuration for the round-trip — equivalent of {@link #OPEN_CL_INFO_JSON_STRING}. */
    private static final String OPEN_CL_INFO_YAML_STRING = "command: OpenCLInfo\n";

    /** Temporary directory for the on-disk round-trip; provided per-test by JUnit Jupiter. */
    @TempDir
    public Path folder;

    /** Default constructor used by JUnit Jupiter. */
    public MainConfigurationLoadingTest() {
        // no-op
    }

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
        assertRoundTripJsonExtension(Main.FILE_EXTENSION_JSON);
    }

    @Test
    public void loadConfiguration_jsExtension_roundTripsAndWritesJsonContent() throws IOException {
        assertRoundTripJsonExtension(Main.FILE_EXTENSION_JS);
    }

    @Test
    public void loadConfiguration_yamlExtension_roundTripsAndWritesYamlContent() throws IOException {
        assertRoundTripYamlExtension(Main.FILE_EXTENSION_YAML);
    }

    @Test
    public void loadConfiguration_ymlExtension_roundTripsAndWritesYamlContent() throws IOException {
        assertRoundTripYamlExtension(Main.FILE_EXTENSION_YML);
    }

    @Test
    public void loadConfiguration_uppercaseExtension_isCaseInsensitive() throws IOException {
        // arrange — caller might pass /tmp/config.JSON
        CConfiguration original = Main.fromJson(OPEN_CL_INFO_JSON_STRING);
        Path file = folder.resolve("config" + Main.FILE_EXTENSION_JSON.toUpperCase(java.util.Locale.ROOT));
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
}
