// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether to warn that the JVM runs an OpenCL driver without the JDK's signal chaining.
 *
 * <h2>Why</h2>
 * HotSpot raises {@code SIGSEGV} on purpose (implicit null checks, safepoint polls of compiled loops)
 * and handles it itself. An OpenCL driver that compiles kernels in-process with LLVM can install its own
 * handlers for {@code SIGSEGV}, {@code SIGBUS}, {@code SIGFPE} and {@code SIGILL}: measured with pocl
 * 5.0, whose {@code libLLVM-16.so.1} takes them over while the kernel is built, with
 * {@code SA_RESETHAND}, with Intel's CPU OpenCL runtime 2026.2 ({@code libintelocl.so}, already at
 * context creation) and with AMD ROCm 7.14 and 10.1 (where no crash was observed). A single fault is still forwarded to the JVM, but when several threads fault at
 * once, as they do at a safepoint, one of them meets the default handler and the process dies with
 * {@code SIGSEGV} &#x2014; without an {@code hs_err} file, because the JVM never sees the signal. Loading
 * the JDK's {@code libjsig} first ({@code LD_PRELOAD}) keeps the JVM's handlers in front and chains the
 * driver's behind them, which removes the crash.
 *
 * <h2>What is checked</h2>
 * The decision is taken from what the dynamic linker actually mapped into the process
 * ({@code /proc/self/maps}), not from the {@code LD_PRELOAD} string: {@code ld.so} skips a preload entry
 * it cannot open with "cannot be preloaded ... ignored" and starts the JVM anyway, so a wrong path
 * (for example {@code $JAVA_HOME/lib/libjsig.so} with {@code JAVA_HOME} unset) names {@code libjsig}
 * without loading it. The mapping also covers {@code /etc/ld.so.preload} and launchers that link
 * {@code libjsig} in. When the mapping cannot be read, the decision falls back to the
 * {@code LD_PRELOAD} string and the warning says so.
 *
 * <p>Only Linux is affected: Windows has no POSIX signals, and the warning is not raised for macOS,
 * where it has not been observed.
 */
public final class SignalChainingCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalChainingCheck.class);

    /** File name stem of the JDK's signal-chaining library. */
    static final String LIBJSIG = "libjsig";

    /** The marker of a {@code libjsig} mapping in a {@code /proc/self/maps} line (the path's last segment). */
    static final String LIBJSIG_MAPPING_MARKER = "/" + LIBJSIG + ".";

    /** The Linux file listing the current process's memory mappings. */
    static final String PROC_SELF_MAPS = "/proc/self/maps";

    /** Advice appended to every warning: what to preload and that the shipped launchers do it. */
    private static final String START_WITH = " Start with LD_PRELOAD=";

    /** Creates a check. */
    public SignalChainingCheck() {}

    /**
     * Returns the warning to log before the OpenCL program is built, if any, deciding from
     * {@code /proc/self/maps} of this process.
     *
     * @param osName the value of the {@code os.name} system property
     * @param ldPreload the {@code LD_PRELOAD} environment variable, or {@code null} when unset
     * @param javaHome the value of the {@code java.home} system property
     * @return the warning, or empty when the platform is not Linux or {@code libjsig} is loaded
     */
    public Optional<String> warning(String osName, @Nullable String ldPreload, String javaHome) {
        return warning(osName, ldPreload, javaHome, () -> libjsigMapped(Paths.get(PROC_SELF_MAPS)));
    }

    /**
     * Returns the warning to log before the OpenCL program is built, if any.
     *
     * @param osName the value of the {@code os.name} system property
     * @param ldPreload the {@code LD_PRELOAD} environment variable, or {@code null} when unset
     * @param javaHome the value of the {@code java.home} system property
     * @param libjsigMapped whether {@code libjsig} is mapped into the process; empty when that could not
     *     be determined. Consulted on Linux only.
     * @return the warning, or empty when the platform is not Linux or {@code libjsig} is loaded
     */
    public Optional<String> warning(
            String osName, @Nullable String ldPreload, String javaHome, Supplier<Optional<Boolean>> libjsigMapped) {
        if (!osName.toLowerCase(Locale.ROOT).startsWith("linux")) {
            return Optional.empty();
        }
        final boolean namedInLdPreload = ldPreload != null && ldPreload.contains(LIBJSIG);
        final String libraryToPreload = javaHome + "/lib/" + LIBJSIG + ".so";
        final Optional<Boolean> mapped = libjsigMapped.get();
        if (mapped.isEmpty()) {
            if (namedInLdPreload) {
                return Optional.empty();
            }
            return Optional.of(notPreloadedWarning(libraryToPreload) + " (" + PROC_SELF_MAPS
                    + " could not be read, so this was decided from LD_PRELOAD alone.)");
        }
        if (mapped.get()) {
            return Optional.empty();
        }
        if (namedInLdPreload) {
            return Optional.of("LD_PRELOAD names " + LIBJSIG + " (" + ldPreload + "), but it was not loaded: the"
                    + " dynamic linker printed \"cannot be preloaded ... ignored\" at JVM start, so the JDK's"
                    + " signal chaining is off. An OpenCL driver that compiles kernels in-process with LLVM can"
                    + " then kill the JVM without an hs_err file." + START_WITH + libraryToPreload
                    + " (the library of the JVM that runs, " + javaHome + ").");
        }
        return Optional.of(notPreloadedWarning(libraryToPreload));
    }

    /**
     * Reads a {@code /proc/<pid>/maps} file and tells whether {@code libjsig} is mapped.
     *
     * @param maps the maps file to read
     * @return {@code true} or {@code false}; empty when the file cannot be read (logged at DEBUG)
     */
    static Optional<Boolean> libjsigMapped(Path maps) {
        try (Stream<String> lines = Files.lines(maps, StandardCharsets.ISO_8859_1)) {
            return Optional.of(lines.anyMatch(line -> line.contains(LIBJSIG_MAPPING_MARKER)));
        } catch (IOException | UncheckedIOException e) {
            LOGGER.debug("Could not read {} to check whether {} is loaded.", maps, LIBJSIG, e);
            return Optional.empty();
        }
    }

    private static String notPreloadedWarning(String libraryToPreload) {
        return "The JDK's signal chaining (libjsig) is not preloaded. An OpenCL driver that compiles"
                + " kernels in-process with LLVM (measured: pocl, Intel CPU runtime, AMD ROCm) replaces the JVM's SIGSEGV handler, and"
                + " concurrent safepoints can then kill the JVM without an hs_err file." + START_WITH
                + libraryToPreload + " (the examples/run_*OpenCL*.sh scripts do).";
    }
}
