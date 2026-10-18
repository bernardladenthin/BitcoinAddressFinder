// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SignalChainingCheckTest {

    private static final String JAVA_HOME = "/usr/lib/jvm/java-21";

    private final SignalChainingCheck check = new SignalChainingCheck();

    @Test
    void warning_linuxWithoutPreload_warnsAndNamesTheLibraryToPreload() {
        Optional<String> warning = check.warning("Linux", null, JAVA_HOME);

        assertThat(warning.isPresent(), is(true));
        assertThat(warning.get(), containsString("LD_PRELOAD=/usr/lib/jvm/java-21/lib/libjsig.so"));
    }

    @Test
    void warning_linuxWithAnotherPreload_warns() {
        assertThat(check.warning("Linux", "/usr/lib/libfoo.so", JAVA_HOME).isPresent(), is(true));
    }

    @Test
    void warning_linuxWithLibjsigPreloaded_isEmpty() {
        assertThat(
                check.warning("Linux", "/usr/lib/libfoo.so:/usr/lib/jvm/java-21/lib/libjsig.so", JAVA_HOME)
                        .isPresent(),
                is(false));
    }

    @Test
    void warning_windows_isEmpty() {
        assertThat(check.warning("Windows 11", null, JAVA_HOME).isPresent(), is(false));
    }

    @Test
    void warning_macOs_isEmpty() {
        assertThat(check.warning("Mac OS X", null, JAVA_HOME).isPresent(), is(false));
    }
}
