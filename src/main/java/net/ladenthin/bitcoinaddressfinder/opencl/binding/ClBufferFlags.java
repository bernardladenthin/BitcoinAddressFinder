// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import org.lwjgl.opencl.CL10;

/**
 * The {@code cl_mem_flags} values this project uses when creating device buffers.
 *
 * <p>Collected here so the layers above the binding can express intent — read-only input, write-only
 * output, host-backed — without importing the binding library's constants, which is what keeps the
 * {@code org.lwjgl} dependency confined to this package.
 *
 * <p>{@code CL_MEM_COPY_HOST_PTR} is deliberately absent. Every host-to-device transfer in this
 * project goes through an explicit chunked write instead; see
 * {@link ClApi#writeBufferChunked} for why.
 */
public final class ClBufferFlags {

    /** Device buffer the kernel only reads. */
    public static final long READ_ONLY = CL10.CL_MEM_READ_ONLY;

    /** Device buffer the kernel only writes. */
    public static final long WRITE_ONLY = CL10.CL_MEM_WRITE_ONLY;

    /** Device buffer the kernel both reads and writes. */
    public static final long READ_WRITE = CL10.CL_MEM_READ_WRITE;

    /** Device buffer backed by a caller-owned host allocation that must stay alive and pinned. */
    public static final long USE_HOST_PTR = CL10.CL_MEM_USE_HOST_PTR;

    private ClBufferFlags() {
        // constant holder
    }
}
