// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link ClErrorCodeResolver}.
 *
 * <p>No OpenCL runtime is touched here: the resolver is a pure lookup over the error-code constants,
 * which is exactly why it is a separate class from {@link ClErrorChecker}.
 */
class ClErrorCodeResolverTest {

    /** An error code that is not defined by any OpenCL version, used for the fallback path. */
    private static final int UNDEFINED_ERROR_CODE = -9999;

    private final ClErrorCodeResolver resolver = new ClErrorCodeResolver();

    // <editor-fold defaultstate="collapsed" desc="describe">
    @ParameterizedTest
    @MethodSource("knownErrorCodes")
    void describe_knownErrorCode_containsSymbolicNameAndNumericCode(int errorCode, String expectedName) {
        // act
        final String description = resolver.describe(errorCode);

        // assert
        assertThat(description, containsString(expectedName));
        assertThat(description, containsString(Integer.toString(errorCode)));
    }

    @Test
    void describe_undefinedErrorCode_containsNumericCode() {
        // act
        final String description = resolver.describe(UNDEFINED_ERROR_CODE);

        // assert
        assertThat(description, containsString(Integer.toString(UNDEFINED_ERROR_CODE)));
    }

    @Test
    void describe_successCode_returnsSuccessName() {
        // act
        final String description = resolver.describe(ClErrorCodeResolver.CL_SUCCESS);

        // assert
        assertThat(description, containsString("CL_SUCCESS"));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isSuccess">
    @Test
    void isSuccess_successCode_returnsTrue() {
        // act, assert
        assertThat(resolver.isSuccess(ClErrorCodeResolver.CL_SUCCESS), is(true));
    }

    @Test
    void isSuccess_failureCode_returnsFalse() {
        // act, assert
        assertThat(resolver.isSuccess(UNDEFINED_ERROR_CODE), is(false));
    }
    // </editor-fold>

    /**
     * Error codes that must resolve to their symbolic OpenCL name. Deliberately a small, stable
     * selection covering the failures this project can realistically provoke: a bad argument, an
     * exhausted device, an exhausted host, and a kernel that fails to build.
     *
     * @return pairs of error code and expected symbolic name
     */
    static Stream<Arguments> knownErrorCodes() {
        return Stream.of(
                Arguments.of(-5, "CL_OUT_OF_RESOURCES"),
                Arguments.of(-6, "CL_OUT_OF_HOST_MEMORY"),
                Arguments.of(-11, "CL_BUILD_PROGRAM_FAILURE"),
                Arguments.of(-30, "CL_INVALID_VALUE"));
    }
}
