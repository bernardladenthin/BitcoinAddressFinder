// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

/**
 * Thrown when a call into the OpenCL runtime reports a failure.
 *
 * <p>The OpenCL C API signals failures through {@code cl_int} return values rather than through any
 * exception mechanism, and the LWJGL binding passes that contract through unchanged. This exception
 * is what converts such a return value into a normal Java failure, carrying both the numeric error
 * code and the name of the entry point that produced it so a stack trace identifies the offending
 * call without further instrumentation.
 *
 * @see ClErrorChecker
 */
public class OpenClCallFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The {@code cl_int} error code reported by the OpenCL runtime. */
    private final int errorCode;

    /** Name of the OpenCL entry point that reported the failure, e.g. {@code clCreateBuffer}. */
    private final String openClFunctionName;

    /**
     * Creates a new exception describing a failed OpenCL call.
     *
     * @param message            the human-readable failure description
     * @param errorCode          the {@code cl_int} error code reported by the runtime
     * @param openClFunctionName name of the OpenCL entry point that failed
     */
    public OpenClCallFailedException(String message, int errorCode, String openClFunctionName) {
        super(message);
        this.errorCode = errorCode;
        this.openClFunctionName = openClFunctionName;
    }

    /**
     * Returns the {@code cl_int} error code reported by the OpenCL runtime.
     *
     * @return the numeric OpenCL error code
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the name of the OpenCL entry point that reported the failure.
     *
     * @return the OpenCL function name, e.g. {@code clCreateBuffer}
     */
    public String getOpenClFunctionName() {
        return openClFunctionName;
    }
}
