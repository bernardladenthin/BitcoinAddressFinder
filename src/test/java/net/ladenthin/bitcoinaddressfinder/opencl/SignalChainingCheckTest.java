// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SignalChainingCheckTest {

    private static final String JAVA_HOME = "/usr/lib/jvm/java-21";

    /** What the README's {@code LD_PRELOAD="$JAVA_HOME/lib/libjsig.so"} expands to with JAVA_HOME unset. */
    private static final String UNLOADABLE_LIBJSIG_PRELOAD = "/lib/libjsig.so";

    private static final Supplier<Optional<Boolean>> MAPPED = () -> Optional.of(true);

    private static final Supplier<Optional<Boolean>> NOT_MAPPED = () -> Optional.of(false);

    private static final Supplier<Optional<Boolean>> MAPPING_UNKNOWN = Optional::empty;

    private static final Supplier<Optional<Boolean>> MUST_NOT_BE_CONSULTED = () -> {
        throw new AssertionError("the mapping must not be read off Linux");
    };

    @TempDir
    Path tempDir;

    private final SignalChainingCheck check = new SignalChainingCheck();

    // <editor-fold defaultstate="collapsed" desc="warning">
    @Test
    void warning_linuxWithoutPreload_warnsAndNamesTheLibraryToPreload() {
        Optional<String> warning = check.warning("Linux", null, JAVA_HOME, NOT_MAPPED);

        assertThat(warning.isPresent(), is(true));
        assertThat(warning.get(), containsString("LD_PRELOAD=/usr/lib/jvm/java-21/lib/libjsig.so"));
    }

    @Test
    void warning_linuxWithAnotherPreload_warns() {
        assertThat(
                check.warning("Linux", "/usr/lib/libfoo.so", JAVA_HOME, NOT_MAPPED)
                        .isPresent(),
                is(true));
    }

    @Test
    void warning_linuxWithLibjsigPreloaded_isEmpty() {
        assertThat(
                check.warning("Linux", "/usr/lib/libfoo.so:/usr/lib/jvm/java-21/lib/libjsig.so", JAVA_HOME, MAPPED)
                        .isPresent(),
                is(false));
    }

    @Test
    void warning_linuxLibjsigNamedInLdPreloadButNotMapped_warns() {
        // ld.so ignores a preload entry it cannot open ("cannot be preloaded ... ignored") and starts the
        // JVM without signal chaining; the string alone must not silence the warning
        Optional<String> warning = check.warning("Linux", UNLOADABLE_LIBJSIG_PRELOAD, JAVA_HOME, NOT_MAPPED);

        assertThat(warning.isPresent(), is(true));
        assertThat(warning.get(), containsString("was not loaded"));
        assertThat(warning.get(), containsString("cannot be preloaded"));
        assertThat(warning.get(), containsString("LD_PRELOAD=/usr/lib/jvm/java-21/lib/libjsig.so"));
    }

    @Test
    void warning_linuxLibjsigMappedWithoutLdPreload_isEmpty() {
        // e.g. /etc/ld.so.preload or a launcher that links libjsig in: chained, although LD_PRELOAD is unset
        assertThat(check.warning("Linux", null, JAVA_HOME, MAPPED).isPresent(), is(false));
    }

    @Test
    void warning_linuxMappingUnreadable_fallsBackToLdPreload() {
        assertThat(
                check.warning("Linux", "/usr/lib/jvm/java-21/lib/libjsig.so", JAVA_HOME, MAPPING_UNKNOWN)
                        .isPresent(),
                is(false));

        Optional<String> warning = check.warning("Linux", null, JAVA_HOME, MAPPING_UNKNOWN);
        assertThat(warning.isPresent(), is(true));
        assertThat(warning.get(), containsString("could not be read"));
        assertThat(warning.get(), containsString("LD_PRELOAD=/usr/lib/jvm/java-21/lib/libjsig.so"));
    }

    @Test
    void warning_windows_isEmpty() {
        assertThat(
                check.warning("Windows 11", null, JAVA_HOME, MUST_NOT_BE_CONSULTED)
                        .isPresent(),
                is(false));
    }

    @Test
    void warning_macOs_isEmpty() {
        assertThat(
                check.warning("Mac OS X", null, JAVA_HOME, MUST_NOT_BE_CONSULTED)
                        .isPresent(),
                is(false));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="warningOnce">
    @Test
    void warningOnce_linuxWithoutPreload_secondCallIsEmpty() {
        // the tuner builds a kernel per arm, i.e. calls this once per arm; the condition cannot change
        final SignalChainingCheck gate = new SignalChainingCheck();

        Optional<String> first = gate.warningOnce("Linux", null, JAVA_HOME, NOT_MAPPED);
        Optional<String> second = gate.warningOnce("Linux", null, JAVA_HOME, NOT_MAPPED);

        assertThat(first.isPresent(), is(true));
        assertThat(second.isPresent(), is(false));
    }

    @Test
    void warningOnce_libjsigLoaded_doesNotConsumeTheGate() {
        final SignalChainingCheck gate = new SignalChainingCheck();

        Optional<String> whileLoaded = gate.warningOnce("Linux", null, JAVA_HOME, MAPPED);
        Optional<String> afterwards = gate.warningOnce("Linux", null, JAVA_HOME, NOT_MAPPED);

        assertThat(whileLoaded.isPresent(), is(false));
        assertThat(afterwards.isPresent(), is(true));
    }

    @Test
    void warning_afterWarningOnceOfTheSameInstance_stillAnswers() {
        // the gate is separate: warning(...) stays a pure decision
        final SignalChainingCheck gate = new SignalChainingCheck();
        gate.warningOnce("Linux", null, JAVA_HOME, NOT_MAPPED);

        assertThat(gate.warning("Linux", null, JAVA_HOME, NOT_MAPPED).isPresent(), is(true));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="libjsigMapped">
    @Test
    void libjsigMapped_mapsListLibjsig_isTrue() throws IOException {
        Path maps = writeMaps(
                "7f1c2a000000-7f1c2a001000 r--p 00000000 08:01 1234 /usr/lib/jvm/java-21/lib/libjsig.so",
                "7f1c2b000000-7f1c2b100000 r-xp 00000000 08:01 5678 /usr/lib/jvm/java-21/lib/server/libjvm.so");

        assertThat(SignalChainingCheck.libjsigMapped(maps), is(equalTo(Optional.of(true))));
    }

    @Test
    void libjsigMapped_mapsWithoutLibjsig_isFalse() throws IOException {
        Path maps = writeMaps(
                "7f1c2b000000-7f1c2b100000 r-xp 00000000 08:01 5678 /usr/lib/jvm/java-21/lib/server/libjvm.so",
                "7ffd4a000000-7ffd4a021000 rw-p 00000000 00:00 0 [stack]");

        assertThat(SignalChainingCheck.libjsigMapped(maps), is(equalTo(Optional.of(false))));
    }

    @Test
    void libjsigMapped_unreadableMaps_isUnknown() {
        assertThat(SignalChainingCheck.libjsigMapped(tempDir.resolve("no-such-maps")), is(equalTo(Optional.empty())));
    }
    // </editor-fold>

    private Path writeMaps(String... lines) throws IOException {
        Path maps = tempDir.resolve("maps");
        Files.write(maps, List.of(lines), StandardCharsets.US_ASCII);
        return maps;
    }
}
