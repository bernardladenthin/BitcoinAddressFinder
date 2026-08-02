// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

/**
 * Tests the typed OpenCL handle records.
 *
 * <p>The property under test is the one the migration would otherwise lose. JOCL modelled every
 * OpenCL object as its own class ({@code cl_mem}, {@code cl_kernel}, ...), so passing a buffer where
 * a kernel was expected could not compile. LWJGL represents all of them as a bare {@code long},
 * which makes that mistake invisible to the compiler and turns it into an undiagnosable runtime
 * fault deep inside the driver. These records restore the distinction.
 */
class ClHandleTest {

    /** An arbitrary non-zero value standing in for a native OpenCL object address. */
    private static final long HANDLE_VALUE = 0x7F00_1234L;

    /** A second, different address used to check that value equality actually compares the handle. */
    private static final long OTHER_HANDLE_VALUE = 0x7F00_5678L;

    // <editor-fold defaultstate="collapsed" desc="handle">
    @Test
    void handle_created_returnsTheWrappedValue() {
        // act, assert
        assertThat(new ClMem(HANDLE_VALUE).handle(), is(equalTo(HANDLE_VALUE)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="equals">
    @Test
    void equals_sameTypeAndSameHandle_areEqual() {
        // act, assert
        assertThat(new ClMem(HANDLE_VALUE), is(equalTo(new ClMem(HANDLE_VALUE))));
    }

    @Test
    void equals_sameTypeAndDifferentHandle_areNotEqual() {
        // act, assert
        assertThat(new ClMem(HANDLE_VALUE), is(not(equalTo(new ClMem(OTHER_HANDLE_VALUE)))));
    }

    @Test
    void equals_differentTypeAndSameHandle_areNotEqual() {
        // arrange
        final ClMem memory = new ClMem(HANDLE_VALUE);
        final ClKernel kernel = new ClKernel(HANDLE_VALUE);

        // act, assert
        assertThat(memory, is(not(equalTo((Object) kernel))));
    }
    // </editor-fold>
}
