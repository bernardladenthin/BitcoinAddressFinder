// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

/**
 * Thrown when the native OpenCL library cannot be loaded under any of the known library names.
 *
 * <p>Distinct from {@link OpenClCallFailedException} on purpose: that one reports a call that
 * reached the OpenCL runtime and was rejected, while this one reports that no runtime could be
 * reached at all. The two lead to different conclusions — a driver problem on the host versus a
 * misuse of the API — and callers that want to degrade gracefully (falling back to a CPU producer,
 * for instance) only care about the latter.
 */
public class OpenClLibraryUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception describing a failed library load.
     *
     * @param message the human-readable failure description, naming the attempted library names
     * @param cause   the last {@link UnsatisfiedLinkError} encountered while trying the candidates
     */
    public OpenClLibraryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
