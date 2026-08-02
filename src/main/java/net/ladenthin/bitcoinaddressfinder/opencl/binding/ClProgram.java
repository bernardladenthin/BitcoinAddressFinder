// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import com.google.errorprone.annotations.Immutable;

/**
 * Handle to a compiled OpenCL program, the container of one or more kernels.
 *
 * <p>Corresponds to the OpenCL C type {@code cl_program}.
 *
 * @param handle the native handle value
 */
@Immutable
public record ClProgram(long handle) implements ClHandle {}
