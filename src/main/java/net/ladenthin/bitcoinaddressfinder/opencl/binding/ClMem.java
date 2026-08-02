// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import com.google.errorprone.annotations.Immutable;

/**
 * Handle to an OpenCL memory object, i.e. a device-side buffer.
 *
 * <p>Corresponds to the OpenCL C type {@code cl_mem}.
 *
 * @param handle the native handle value
 */
@Immutable
public record ClMem(long handle) implements ClHandle {}
