// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Hard gate: every {@code examples/run_*.sh} launcher that starts an OpenCL run preloads the JDK's
 * signal chaining ({@code libjsig}), and no other launcher does (F148, F210). A run is an OpenCL run
 * when the configuration the launcher names has the {@code command} {@code OpenCLInfo} or
 * {@code TuneConfiguration} (the tuner builds a kernel per arm when it tunes an OpenCL producer), or a
 * non-empty {@code finder.producerOpenCL}. The configuration is parsed, not searched: the CPU-only
 * {@code Find} configurations carry an empty {@code "producerOpenCL": []}. {@code run_TuneConfiguration.sh}
 * went without the preload until F210; this test keeps the next OpenCL example from doing the same.
 *
 * <p>Each launcher must name exactly one {@code config_*.json} that exists, so a launcher without a
 * configuration fails here and has to be classified deliberately. The {@code run_*.bat} launchers are
 * not checked: {@code libjsig} is a Linux mechanism, and Windows is not affected.
 */
public class ExampleRunScriptSignalChainingTest {

    private static final Path EXAMPLES = Path.of("examples");

    /** The configuration file a launcher passes to the jar. */
    private static final Pattern CONFIG_REFERENCE = Pattern.compile("config_[A-Za-z0-9_]+\\.json");

    /** The preload line of the OpenCL launchers: {@code export LD_PRELOAD="$JSIG...}. */
    private static final Pattern PRELOAD_EXPORT =
            Pattern.compile("^\\s*export LD_PRELOAD=\"\\$JSIG", Pattern.MULTILINE);

    /** The JDK's signal-chaining library the preload names. */
    private static final String LIBJSIG = "libjsig.so";

    /** Commands that build an OpenCL kernel whatever the producers are. */
    private static final Set<String> OPENCL_COMMANDS = Set.of("OpenCLInfo", "TuneConfiguration");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Creates a new {@link ExampleRunScriptSignalChainingTest}. */
    public ExampleRunScriptSignalChainingTest() {}

    @Test
    public void openClLaunchersPreloadLibjsig_andTheOthersDoNot() {
        final List<Path> launchers = shellLaunchers();
        assertThat("no examples/run_*.sh launchers were found", launchers, is(not(empty())));

        final Set<String> openClLaunchers = new TreeSet<>();
        final List<String> mismatches = new ArrayList<>();
        for (Path launcher : launchers) {
            final String script = read(launcher);
            final boolean openClRun = isOpenClRun(configOf(launcher, script));
            final boolean preloads =
                    script.contains(LIBJSIG) && PRELOAD_EXPORT.matcher(script).find();
            final boolean mentionsThePreload = script.contains("libjsig") || script.contains("LD_PRELOAD");
            if (openClRun) {
                openClLaunchers.add(launcher.getFileName().toString());
                if (!preloads) {
                    mismatches.add(launcher + " starts an OpenCL run but does not preload " + LIBJSIG);
                }
            } else if (mentionsThePreload) {
                mismatches.add(launcher + " starts a run without OpenCL but preloads libjsig");
            }
        }

        assertThat(mismatches, is(empty()));
        // The classification itself: a parser that missed every OpenCL run would pass the loop above.
        assertThat(
                openClLaunchers,
                is(new TreeSet<>(Set.of(
                        "run_Find_1OpenCLDevice.sh",
                        "run_Find_1OpenCLDeviceAnd2CPUProducer.sh",
                        "run_OpenCLInfo.sh",
                        "run_TuneConfiguration.sh"))));
    }

    private boolean isOpenClRun(Path config) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(config.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot parse " + config, e);
        }
        if (OPENCL_COMMANDS.contains(root.path("command").asText())) {
            return true;
        }
        final JsonNode producers = root.path("finder").path("producerOpenCL");
        return producers.isArray() && !producers.isEmpty();
    }

    private static Path configOf(Path launcher, String script) {
        final Set<String> configs = new TreeSet<>();
        final Matcher matcher = CONFIG_REFERENCE.matcher(script);
        while (matcher.find()) {
            configs.add(matcher.group());
        }
        assertThat(launcher + " must name exactly one configuration, named " + configs, configs, hasSize(1));
        final Path config = EXAMPLES.resolve(configs.iterator().next());
        assertThat(launcher + " names " + config + ", which does not exist", Files.isRegularFile(config), is(true));
        return config;
    }

    private static List<Path> shellLaunchers() {
        try (Stream<Path> entries = Files.list(EXAMPLES)) {
            return entries.filter(path -> {
                        final String name = path.getFileName().toString();
                        return name.startsWith("run_") && name.endsWith(".sh");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + EXAMPLES, e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }
}
