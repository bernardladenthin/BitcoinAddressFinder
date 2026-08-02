// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

/**
 * A handle to a native OpenCL object.
 *
 * <p>The OpenCL C API identifies every object — platforms, devices, contexts, queues, programs,
 * kernels, buffers, events — by an opaque pointer, and the LWJGL binding surfaces all of them as a
 * bare {@code long}. To the compiler, a command queue and a buffer are then the same thing, so
 * swapping two arguments produces code that builds cleanly and fails inside the driver with an error
 * that points nowhere useful.
 *
 * <p>The implementations of this interface restore the per-object types that JOCL provided through
 * its {@code cl_mem} / {@code cl_kernel} / ... classes, at the cost of one small object per handle.
 * They are deliberately plain value carriers: whether a handle is valid is decided when it is
 * created, by {@link ClErrorChecker}, not by the record.
 *
 * <p>Sealed because the set of OpenCL object types is fixed by the specification, not by this
 * project.
 */
public sealed interface ClHandle
        permits ClCommandQueue, ClContext, ClDeviceId, ClEvent, ClKernel, ClMem, ClPlatformId, ClProgram {

    /**
     * Returns the native handle value.
     *
     * @return the opaque pointer value as passed to and from the OpenCL runtime
     */
    long handle();
}
