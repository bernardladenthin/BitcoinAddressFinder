// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import lombok.ToString;

/**
 * Turns the OpenCL C error-code contract into Java exceptions.
 *
 * <p>Every OpenCL entry point reports failure through a returned {@code cl_int} (or, for the
 * create-style calls, by returning a NULL handle and writing the code into an out-parameter). The
 * LWJGL binding forwards that contract verbatim and never throws by itself, so a call whose result
 * is not inspected fails silently and only surfaces later as corrupted state or a confusing
 * follow-up error. Routing every call through this checker makes that impossible.
 *
 * <p>An instance class rather than a static utility, per the project convention on helper classes:
 * collaborators receive it through the constructor, which keeps the dependency explicit and lets
 * tests substitute it.
 */
@ToString
public class ClErrorChecker {

    /** The OpenCL NULL handle, returned by create-style entry points when the object was not created. */
    private static final long NULL_HANDLE = 0L;

    /**
     * Error code reported for a NULL handle when the failing call did not surface a code of its own.
     * {@link ClErrorCodeResolver#CL_SUCCESS} would be misleading here, so the generic
     * {@code CL_INVALID_VALUE} is used to keep {@link OpenClCallFailedException#getErrorCode()}
     * meaningful in every case.
     */
    private static final int NULL_HANDLE_ERROR_CODE = org.lwjgl.opencl.CL10.CL_INVALID_VALUE;

    private final ClErrorCodeResolver errorCodeResolver;

    /**
     * Creates a new checker.
     *
     * @param errorCodeResolver resolves numeric error codes to their symbolic OpenCL names
     */
    public ClErrorChecker(ClErrorCodeResolver errorCodeResolver) {
        this.errorCodeResolver = errorCodeResolver;
    }

    /**
     * Verifies that an OpenCL entry point reported success.
     *
     * @param errorCode          the {@code cl_int} returned by the entry point
     * @param openClFunctionName name of the entry point, used to attribute the failure
     * @throws OpenClCallFailedException if {@code errorCode} does not denote success
     */
    public void checkSuccess(int errorCode, String openClFunctionName) {
        if (errorCodeResolver.isSuccess(errorCode)) {
            return;
        }
        final String message =
                String.format("OpenCL call %s failed: %s", openClFunctionName, errorCodeResolver.describe(errorCode));
        throw new OpenClCallFailedException(message, errorCode, openClFunctionName);
    }

    /**
     * Verifies that a create-style OpenCL entry point actually produced an object.
     *
     * @param handle             the handle returned by the entry point
     * @param openClFunctionName name of the entry point, used to attribute the failure
     * @return {@code handle} unchanged, so the call can be used inline
     * @throws OpenClCallFailedException if {@code handle} is the NULL handle
     */
    public long requireCreated(long handle, String openClFunctionName) {
        if (handle != NULL_HANDLE) {
            return handle;
        }
        final String message = String.format("OpenCL call %s returned a NULL handle", openClFunctionName);
        throw new OpenClCallFailedException(message, NULL_HANDLE_ERROR_CODE, openClFunctionName);
    }
}
