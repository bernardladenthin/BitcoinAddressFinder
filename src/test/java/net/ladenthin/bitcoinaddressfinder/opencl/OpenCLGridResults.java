// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;

/**
 * Builds {@link OpenCLGridResult}s for tests outside this package, whose constructors are
 * package-private: a result reader can then be driven without a device.
 */
public class OpenCLGridResults {

    /** Bytes of the count header the kernel writes first; a buffer of just that is a grid with no entry. */
    private static final int COUNT_HEADER_BYTES = Integer.BYTES;

    /** Creates a new {@link OpenCLGridResults}. */
    public OpenCLGridResults() {}

    /**
     * Returns a grid result without a single key: its count header reads zero in the kernel's word
     * order, so {@link OpenCLGridResult#getPublicKeyBytes()} is an empty array and the reader hands the
     * consumer an empty batch at once. Its buffer is not pooled, so {@link OpenCLGridResult#close()} is a
     * no-op.
     *
     * @param secretKeyBase the base secret the result reports for its batch
     * @return a result with zero entries
     */
    public OpenCLGridResult empty(BigInteger secretKeyBase) {
        ByteBuffer countHeaderOnly =
                ByteBuffer.allocate(COUNT_HEADER_BYTES).order(OpenClKernelConstants.GPU_NATIVE_WORD_ORDER);
        return new OpenCLGridResult(secretKeyBase, 0, countHeaderOnly);
    }
}
