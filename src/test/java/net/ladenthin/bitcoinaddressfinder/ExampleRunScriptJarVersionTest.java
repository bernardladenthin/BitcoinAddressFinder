// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Hard gate: every {@code examples/run_*.bat} and {@code examples/run_*.sh} launcher must reference
 * the fat jar of the <em>current</em> project version. A build from source produces
 * {@code bitcoinaddressfinder-<project.version>-jar-with-dependencies.jar}; a launcher naming a stale
 * version fails at run time with "jar not found", which is exactly the kind of silent drift that
 * survives a version bump because nothing else references those file names.
 *
 * <p>The single source of truth is the first {@code <version>} in {@code pom.xml} (the project
 * coordinate; there is no parent POM). This test extracts every
 * {@code bitcoinaddressfinder-<v>-jar-with-dependencies.jar} reference from the launchers <em>and</em>
 * the {@code docs/*.md} guides and asserts each {@code <v>} equals that version. The jar-name pattern
 * never matches a plain version mention (for example a changelog entry), so scanning docs is safe.
 */
public class ExampleRunScriptJarVersionTest {

    /** Captures the version in a {@code bitcoinaddressfinder-<version>-jar-with-dependencies.jar} name. */
    private static final Pattern JAR_REFERENCE =
            Pattern.compile("bitcoinaddressfinder-([0-9A-Za-z.\\-]+)-jar-with-dependencies\\.jar");

    /** Extracts the project version from the first {@code <version>} element in {@code pom.xml}. */
    private static final Pattern POM_VERSION = Pattern.compile("<version>([^<]+)</version>");

    /** Creates a new {@link ExampleRunScriptJarVersionTest}. */
    public ExampleRunScriptJarVersionTest() {}

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    private static String projectVersion() {
        Matcher matcher = POM_VERSION.matcher(read(Path.of("pom.xml")));
        assertThat("pom.xml must declare a <version>", matcher.find(), is(true));
        return matcher.group(1);
    }

    private static List<Path> filesUnder(Path directory, String suffixOne, String suffixTwo, String namePrefix) {
        try (Stream<Path> entries = Files.list(directory)) {
            List<Path> matched = new ArrayList<>();
            entries.forEach(path -> {
                String name = path.getFileName().toString();
                boolean prefixOk = namePrefix.isEmpty() || name.startsWith(namePrefix);
                if (prefixOk && (name.endsWith(suffixOne) || name.endsWith(suffixTwo))) {
                    matched.add(path);
                }
            });
            return matched;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + directory, e);
        }
    }

    private static List<Path> sourcesReferencingTheJar() {
        List<Path> sources = new ArrayList<>();
        sources.addAll(filesUnder(Path.of("examples"), ".bat", ".sh", "run_"));
        sources.addAll(filesUnder(Path.of("docs"), ".md", ".md", ""));
        return sources;
    }

    @Test
    public void everyRunnableJarReferenceUsesTheCurrentProjectVersion() {
        String projectVersion = projectVersion();
        List<Path> sources = sourcesReferencingTheJar();
        assertThat("no examples/run_* launchers were found", sources, is(not(empty())));

        List<String> mismatches = new ArrayList<>();
        for (Path source : sources) {
            Matcher matcher = JAR_REFERENCE.matcher(read(source));
            while (matcher.find()) {
                String referencedVersion = matcher.group(1);
                if (!referencedVersion.equals(projectVersion)) {
                    mismatches.add(source + " references " + referencedVersion + " but the project version is "
                            + projectVersion);
                }
            }
        }

        assertThat(mismatches, is(empty()));
    }
}
