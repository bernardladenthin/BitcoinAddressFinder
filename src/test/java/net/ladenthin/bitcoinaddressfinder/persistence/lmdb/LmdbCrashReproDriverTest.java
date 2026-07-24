// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.lmdb;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.LMDBPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in loop-until-crash driver for issue #50. A native {@code SIGSEGV} in {@code mdb_txn_renew0}
 * kills the JVM, so it can never be asserted from inside the test JVM — instead this driver
 * <b>forks many child JVMs</b>, each running {@link LmdbReaderSlotChurnStressTest#main(String[])}
 * (the prod-mode reader-slot churn against a shared read-only test LMDB), and counts how many die
 * abnormally. The output is a reproduction <em>rate</em> ("k crashes / N runs") — the number that
 * turns #50 from "sporadic and untestable" into "reproduced on demand", and the yardstick for
 * validating a fix.
 *
 * <p><b>It never fails on a crash</b> (a crash is the goal, and it is probabilistic — asserting
 * on it would be flaky). It only fails if the harness itself misbehaves: a child that could not be
 * launched, or a run that neither finished nor crashed within its timeout. The crash rate is
 * logged at {@code WARN}.
 *
 * <p><b>Enable and run:</b>
 *
 * <pre>
 * mvn test -Dtest=LmdbCrashReproDriverTest \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbCrashDriver=true \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbCrashDriver.runs=50 \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbCrashDriver.concurrency=128 \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbCrashDriver.seconds=8 \
 *     -Dnet.ladenthin.bitcoinaddressfinder.lmdbCrashDriver.lookupsPerThread=1
 * </pre>
 *
 * <p>Note: children are launched clean (no JaCoCo agent). Issue #50 comment 1 observes that
 * JaCoCo offline instrumentation widens the race window; to reproduce the CI conditions more
 * faithfully, a follow-up can attach the agent per child — left out here to keep each child
 * self-contained and to avoid concurrent writers to a single {@code jacoco.exec}.
 */
public class LmdbCrashReproDriverTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LmdbCrashReproDriverTest.class);

    /** System property that enables this opt-in driver. */
    public static final String PROP_ENABLE = "net.ladenthin.bitcoinaddressfinder.lmdbCrashDriver";
    /** Number of child JVMs to fork (default 30). */
    public static final String PROP_RUNS = PROP_ENABLE + ".runs";
    /** Per-child live-thread concurrency (default 128). */
    public static final String PROP_CONCURRENCY = PROP_ENABLE + ".concurrency";
    /** Per-child churn duration in seconds (default 8). */
    public static final String PROP_SECONDS = PROP_ENABLE + ".seconds";
    /** Per-child lookups-per-thread (default 1 → maximal thread churn). */
    public static final String PROP_LOOKUPS_PER_THREAD = PROP_ENABLE + ".lookupsPerThread";
    /** Optional directory for child JVM logs; defaults to a temp subdir that is auto-cleaned. */
    public static final String PROP_LOG_DIR = PROP_ENABLE + ".logDir";

    private static final int DEFAULT_RUNS = 30;
    private static final int DEFAULT_CONCURRENCY = 128;
    private static final int DEFAULT_SECONDS = 8;
    private static final int DEFAULT_LOOKUPS_PER_THREAD = 1;

    /**
     * Runtime module flags the child JVM needs so lmdbjava's off-heap {@code ByteBufferProxy} can
     * reflectively reach {@code sun.nio.ch.DirectBuffer} / {@code jdk.internal.ref.Cleaner}. Kept
     * in lockstep with {@code .mvn/jvm.config} (the surefire fork gets these via {@code argLine}).
     */
    private static final String[] CHILD_MODULE_FLAGS = {
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    };

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);

    /** Creates a new {@link LmdbCrashReproDriverTest}. */
    public LmdbCrashReproDriverTest() {}

    @Test
    public void forkedChildren_reportCrashRate() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean(PROP_ENABLE),
                "opt-in LMDB crash-repro driver; enable with -D" + PROP_ENABLE + "=true");
        new LMDBPlatformAssume().assumeLMDBExecution();

        final int runs = Integer.getInteger(PROP_RUNS, DEFAULT_RUNS);
        final int concurrency = Integer.getInteger(PROP_CONCURRENCY, DEFAULT_CONCURRENCY);
        final int seconds = Integer.getInteger(PROP_SECONDS, DEFAULT_SECONDS);
        final int lookupsPerThread = Integer.getInteger(PROP_LOOKUPS_PER_THREAD, DEFAULT_LOOKUPS_PER_THREAD);

        // Build the shared read-only test LMDB once; every child opens it read-only.
        File lmdbDirectory = LmdbReaderSlotChurnStressTest.buildTestLmdb(folder, persistenceUtils);
        String logDirOverride = System.getProperty(PROP_LOG_DIR);
        Path logDir = Files.createDirectories(
                logDirOverride != null ? Path.of(logDirOverride) : folder.resolve("child-logs"));
        LOGGER.info("child JVM logs: {}", logDir);

        int clean = 0;
        int nativeCrash = 0;
        int javaError = 0;
        int harnessFailure = 0;
        List<String> crashLogs = new ArrayList<>();

        for (int run = 0; run < runs; run++) {
            Path childLog = logDir.resolve("child-" + run + ".log");
            RunOutcome outcome = runChild(lmdbDirectory, childLog, concurrency, seconds, lookupsPerThread);
            switch (outcome.classification) {
                case CLEAN -> clean++;
                case JAVA_ERROR -> javaError++;
                case NATIVE_CRASH -> {
                    nativeCrash++;
                    crashLogs.add(childLog.toString());
                }
                case HARNESS_FAILURE -> harnessFailure++;
            }
            LOGGER.info(
                    "run {}/{}: exit={} class={}{}",
                    run + 1,
                    runs,
                    outcome.exitCode,
                    outcome.classification,
                    outcome.signature == null ? "" : " signature=\"" + outcome.signature + "\"");
        }

        LOGGER.warn(
                "issue #50 repro rate: {}/{} native crashes ({} clean, {} java-error, {} harness-failure).{}",
                nativeCrash,
                runs,
                clean,
                javaError,
                harnessFailure,
                crashLogs.isEmpty() ? "" : " Crash logs: " + crashLogs);

        // A crash is the GOAL, not a failure — do not assert on it (it is probabilistic).
        // Only the harness misbehaving is a real test failure.
        assertThat(
                "every child JVM must either finish or crash within its timeout (no harness failures)",
                harnessFailure,
                is(equalTo(0)));
    }

    private RunOutcome runChild(File lmdbDirectory, Path childLog, int concurrency, int seconds, int lookupsPerThread)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-Xmx512m");
        command.addAll(List.of(CHILD_MODULE_FLAGS));
        command.add("-cp");
        command.add(buildChildClasspath());
        command.add(LmdbReaderSlotChurnStressTest.class.getName());
        command.add(lmdbDirectory.getAbsolutePath());
        command.add(Integer.toString(concurrency));
        command.add(Integer.toString(seconds));
        command.add(Integer.toString(lookupsPerThread));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(childLog.toFile());

        Process process = pb.start();
        // Generous ceiling: child churn duration + JVM start/stop + fixture open.
        long timeoutSeconds = (long) seconds + 90L;
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(15, TimeUnit.SECONDS);
            return new RunOutcome(Classification.HARNESS_FAILURE, -1, "timeout after " + timeoutSeconds + "s");
        }
        int exit = process.exitValue();
        String signature = scanForCrashSignature(childLog);
        return new RunOutcome(classify(exit, signature), exit, signature);
    }

    /**
     * Classifies a child exit. {@code main} returns 0 (clean), 2 (usage — a harness bug), or 3
     * (Java-level throwable caught in the churn). Any other non-zero exit, or a fatal-error
     * signature in the log, is a native crash — the {@code mdb_txn_renew0} SIGSEGV we are hunting
     * (Linux SIGSEGV → 139, JVM abort → 134, Windows access violation → 0xC0000005).
     */
    private static Classification classify(int exit, @Nullable String signature) {
        if (signature != null) {
            return Classification.NATIVE_CRASH;
        }
        return switch (exit) {
            case 0 -> Classification.CLEAN;
            case 2 -> Classification.HARNESS_FAILURE;
            case 3 -> Classification.JAVA_ERROR;
            default -> Classification.NATIVE_CRASH;
        };
    }

    @Nullable
    private static String scanForCrashSignature(Path childLog) throws IOException {
        if (!Files.exists(childLog)) {
            return null;
        }
        String text = Files.readString(childLog, StandardCharsets.UTF_8);
        String[] signatures = {
            "mdb_txn_renew0", "SIGSEGV", "EXCEPTION_ACCESS_VIOLATION", "A fatal error has been detected",
        };
        for (String signature : signatures) {
            if (text.contains(signature)) {
                return signature;
            }
        }
        return null;
    }

    /**
     * Builds the child classpath robustly. Surefire commonly launches its fork through a
     * manifest-only booter jar, so the parent's {@code java.class.path} may not carry the plain
     * {@code target/classes} / {@code target/test-classes} output roots — which is exactly why a
     * naive {@code -cp java.class.path} child could load this (test) class yet fail on a main
     * class. So derive both output roots from the {@link java.security.CodeSource} of a known test
     * class and a known main class, then append {@code java.class.path} for the dependency jars.
     */
    private static String buildChildClasspath() {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        addCodeSourceRoot(entries, LmdbReaderSlotChurnStressTest.class); // target/test-classes
        addCodeSourceRoot(entries, NetworkParameterFactory.class); // target/classes
        String parentClasspath = System.getProperty("java.class.path");
        if (parentClasspath != null && !parentClasspath.isEmpty()) {
            for (String entry : parentClasspath.split(File.pathSeparator)) {
                if (!entry.isEmpty()) {
                    entries.add(entry);
                }
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    private static void addCodeSourceRoot(LinkedHashSet<String> entries, Class<?> anchor) {
        try {
            java.security.CodeSource codeSource = anchor.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return;
            }
            entries.add(Path.of(codeSource.getLocation().toURI()).toString());
        } catch (URISyntaxException | RuntimeException e) {
            LOGGER.warn("could not resolve classpath root for {}", anchor.getName(), e);
        }
    }

    private static String javaBinary() {
        boolean windows =
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java")
                .toString();
    }

    private enum Classification {
        CLEAN,
        JAVA_ERROR,
        NATIVE_CRASH,
        HARNESS_FAILURE,
    }

    private static final class RunOutcome {
        final Classification classification;
        final int exitCode;

        @Nullable
        final String signature;

        RunOutcome(Classification classification, int exitCode, @Nullable String signature) {
            this.classification = classification;
            this.exitCode = exitCode;
            this.signature = signature;
        }
    }
}
