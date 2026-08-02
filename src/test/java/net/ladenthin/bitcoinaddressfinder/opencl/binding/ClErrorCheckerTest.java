// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ClErrorChecker}.
 *
 * <p>This class exists because of a behavioural difference between the two OpenCL bindings that is
 * easy to overlook: JOCL raised a {@code CLException} on its own once exceptions were enabled, while
 * LWJGL only ever <em>returns</em> an {@code cl_int} error code. Every LWJGL call therefore has to be
 * checked explicitly, and an unchecked call fails silently and corrupts state later. These tests pin
 * that the checker turns a non-success code into a loud, attributable failure.
 *
 * <p>No OpenCL runtime is involved — the checker operates on plain error codes and handles.
 */
class ClErrorCheckerTest {

    /** Name of the OpenCL entry point used in the failure assertions. */
    private static final String FUNCTION_NAME = "clCreateBuffer";

    /** An arbitrary non-zero value standing in for a successfully created OpenCL object. */
    private static final long VALID_HANDLE = 0x7F00_1234L;

    /** The OpenCL NULL handle: what a failed create call returns. */
    private static final long NULL_HANDLE = 0L;

    private final ClErrorChecker checker = new ClErrorChecker(new ClErrorCodeResolver());

    // <editor-fold defaultstate="collapsed" desc="checkSuccess">
    @Test
    void checkSuccess_successCode_noExceptionThrown() {
        // act, assert
        assertDoesNotThrow(() -> checker.checkSuccess(ClErrorCodeResolver.CL_SUCCESS, FUNCTION_NAME));
    }

    @Test
    void checkSuccess_failureCode_throwsException() {
        // act, assert
        assertThrows(OpenClCallFailedException.class, () -> checker.checkSuccess(-30, FUNCTION_NAME));
    }

    @Test
    void checkSuccess_failureCode_exceptionCarriesErrorCodeAndFunctionName() {
        // act
        final OpenClCallFailedException e =
                assertThrows(OpenClCallFailedException.class, () -> checker.checkSuccess(-30, FUNCTION_NAME));

        // assert
        assertThat(e.getErrorCode(), is(equalTo(-30)));
        assertThat(e.getOpenClFunctionName(), is(equalTo(FUNCTION_NAME)));
    }

    @Test
    void checkSuccess_failureCode_exceptionMessageNamesFunctionAndSymbolicError() {
        // act
        final OpenClCallFailedException e =
                assertThrows(OpenClCallFailedException.class, () -> checker.checkSuccess(-30, FUNCTION_NAME));

        // assert
        assertThat(e.getMessage(), containsString(FUNCTION_NAME));
        assertThat(e.getMessage(), containsString("CL_INVALID_VALUE"));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="requireCreated">
    @Test
    void requireCreated_nonNullHandle_returnsHandleUnchanged() {
        // act
        final long created = checker.requireCreated(VALID_HANDLE, FUNCTION_NAME);

        // assert
        assertThat(created, is(equalTo(VALID_HANDLE)));
    }

    @Test
    void requireCreated_nullHandle_throwsException() {
        // act, assert
        assertThrows(OpenClCallFailedException.class, () -> checker.requireCreated(NULL_HANDLE, FUNCTION_NAME));
    }

    @Test
    void requireCreated_nullHandle_exceptionMessageNamesFunction() {
        // act
        final OpenClCallFailedException e =
                assertThrows(OpenClCallFailedException.class, () -> checker.requireCreated(NULL_HANDLE, FUNCTION_NAME));

        // assert
        assertThat(e.getMessage(), containsString(FUNCTION_NAME));
    }
    // </editor-fold>
}
