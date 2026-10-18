// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether to warn that the JVM runs an OpenCL driver without the JDK's signal chaining.
 *
 * <h2>Why</h2>
 * HotSpot raises {@code SIGSEGV} on purpose (implicit null checks, safepoint polls of compiled loops)
 * and handles it itself. An OpenCL driver that compiles kernels in-process with LLVM can install its own
 * handlers for {@code SIGSEGV}, {@code SIGBUS}, {@code SIGFPE} and {@code SIGILL}: measured with pocl
 * 5.0, whose {@code libLLVM-16.so.1} takes them over while the kernel is built, with
 * {@code SA_RESETHAND}. A single fault is still forwarded to the JVM, but when several threads fault at
 * once, as they do at a safepoint, one of them meets the default handler and the process dies with
 * {@code SIGSEGV} &#x2014; without an {@code hs_err} file, because the JVM never sees the signal. Loading
 * the JDK's {@code libjsig} first ({@code LD_PRELOAD}) keeps the JVM's handlers in front and chains the
 * driver's behind them, which removes the crash.
 *
 * <p>Only Linux is affected: Windows has no POSIX signals, and the warning is not raised for macOS,
 * where it has not been observed.
 */
public final class SignalChainingCheck {

    /** File name stem of the JDK's signal-chaining library. */
    static final String LIBJSIG = "libjsig";

    /** Creates a check. */
    public SignalChainingCheck() {}

    /**
     * Returns the warning to log before the OpenCL program is built, if any.
     *
     * @param osName the value of the {@code os.name} system property
     * @param ldPreload the {@code LD_PRELOAD} environment variable, or {@code null} when unset
     * @param javaHome the value of the {@code java.home} system property
     * @return the warning, or empty when the platform is not Linux or {@code libjsig} is preloaded
     */
    public Optional<String> warning(String osName, @Nullable String ldPreload, String javaHome) {
        if (!osName.toLowerCase(Locale.ROOT).startsWith("linux")) {
            return Optional.empty();
        }
        if (ldPreload != null && ldPreload.contains(LIBJSIG)) {
            return Optional.empty();
        }
        return Optional.of("The JDK's signal chaining (libjsig) is not preloaded. An OpenCL driver that compiles"
                + " kernels in-process with LLVM (measured: pocl) replaces the JVM's SIGSEGV handler, and"
                + " concurrent safepoints can then kill the JVM without an hs_err file. Start with"
                + " LD_PRELOAD=" + javaHome + "/lib/" + LIBJSIG + ".so (the examples/run_*OpenCL*.sh scripts do).");
    }
}
