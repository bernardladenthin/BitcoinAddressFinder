// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Hard gate: the JVM module-access flag list ({@code --add-opens} / {@code --add-exports})
 * must be <b>byte-for-byte identical</b> (same flags, same order) in every place it is
 * declared, because a missing or reordered flag silently breaks lmdbjava's native off-heap
 * access (and the Error&nbsp;Prone compiler) at runtime.
 *
 * <p>The single source of truth is {@code .mvn/jvm.config}; this test asserts every other
 * declaration matches it:
 * <ul>
 *   <li>{@code examples/run_*.bat} and {@code examples/run_*.sh} launchers,</li>
 *   <li>the JMH {@code @Fork} {@code jvmArgsAppend} lists in {@code benchmark/*.java},</li>
 *   <li>the three {@code pom.xml} master copies: {@code <argLine>}, the javadoc
 *       {@code additionalJOption}s, and the {@code module-info-compile} {@code <arg>} block
 *       (the Error&nbsp;Prone {@code -J--add-*} compiler-fork args are a separate, smaller
 *       {@code jdk.compiler}-only set and are excluded here).</li>
 * </ul>
 */
public class JvmModuleFlagConsistencyTest {

    /**
     * Matches one {@code --add-opens}/{@code --add-exports} flag in any of the forms used
     * across the project ({@code --add-opens=X}, {@code --add-opens X}, quoted JMH strings,
     * and the {@code <arg>--add-opens</arg><arg>X</arg>} XML pair), capturing the directive
     * and the {@code module/package} target.
     */
    private static final Pattern FLAG =
            Pattern.compile("--add-(opens|exports)[\\s=\"</arg>]*([A-Za-z0-9_.]+/[A-Za-z0-9_.]+)=ALL-UNNAMED");

    /** Number of flags in the canonical set (9 {@code --add-opens} + 15 {@code --add-exports}). */
    private static final int EXPECTED_FLAG_COUNT = 24;

    /** Creates a new {@link JvmModuleFlagConsistencyTest}. */
    public JvmModuleFlagConsistencyTest() {}

    private static List<String> extract(String text) {
        List<String> out = new ArrayList<>();
        Matcher matcher = FLAG.matcher(text);
        while (matcher.find()) {
            out.add(matcher.group(1) + " " + matcher.group(2));
        }
        return out;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    private static List<String> master() {
        List<String> master = extract(read(Path.of(".mvn", "jvm.config")));
        assertThat(
                ".mvn/jvm.config must declare the canonical " + EXPECTED_FLAG_COUNT + " module-access flags",
                master.size(),
                is(equalTo(EXPECTED_FLAG_COUNT)));
        return master;
    }

    @Test
    public void exampleLaunchers_matchTheMasterFlagSet() throws IOException {
        List<String> master = master();
        List<Path> launchers;
        try (Stream<Path> stream = Files.list(Path.of("examples"))) {
            launchers = stream.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("run_") && (name.endsWith(".bat") || name.endsWith(".sh"));
                    })
                    .sorted()
                    .collect(Collectors.toList());
        }
        assertThat("expected run_*.bat / run_*.sh launchers under examples/", launchers.isEmpty(), is(equalTo(false)));
        for (Path launcher : launchers) {
            assertThat(
                    launcher + " JVM flags must equal the .mvn/jvm.config master set (same flags, same order)",
                    extract(read(launcher)),
                    is(equalTo(master)));
        }
    }

    @Test
    public void jmhBenchmarks_matchTheMasterFlagSet() {
        List<String> master = master();
        for (String file : new String[] {
            "src/test/java/net/ladenthin/bitcoinaddressfinder/benchmark/GridSizeSweepBenchmark.java",
            "src/test/java/net/ladenthin/bitcoinaddressfinder/benchmark/AddressLookupBenchmark.java"
        }) {
            assertThat(
                    file + " @Fork jvmArgsAppend must equal the .mvn/jvm.config master set (same flags, same order)",
                    extract(read(Path.of(file))),
                    is(equalTo(master)));
        }
    }

    @Test
    public void pomMasterSections_eachMatchTheMasterFlagSet() {
        List<String> master = master();
        // Drop the Error Prone "-J--add-*" compiler-fork args (a separate jdk.compiler-only set)
        // so only the three master copies remain.
        String pomWithoutErrorProneArgs = Stream.of(read(Path.of("pom.xml")).split("\n"))
                .filter(line -> !line.contains("-J--add"))
                .collect(Collectors.joining("\n"));
        List<String> flags = extract(pomWithoutErrorProneArgs);
        assertThat(
                "pom.xml must declare the master list exactly 3 times (<argLine>, javadoc additionalJOptions, module-info <arg> block)",
                flags.size(),
                is(equalTo(EXPECTED_FLAG_COUNT * 3)));
        for (int i = 0; i < flags.size(); i += EXPECTED_FLAG_COUNT) {
            assertThat(
                    "pom.xml master section starting at flag index " + i + " must equal the .mvn/jvm.config master",
                    flags.subList(i, i + EXPECTED_FLAG_COUNT),
                    is(equalTo(master)));
        }
    }
}
