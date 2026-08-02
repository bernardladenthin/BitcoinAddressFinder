// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import com.google.errorprone.annotations.Immutable;

/**
 * Handle to an OpenCL command queue, through which work is submitted to one device.
 *
 * <p>Corresponds to the OpenCL C type {@code cl_command_queue}.
 *
 * @param handle the native handle value
 */
@Immutable
public record ClCommandQueue(long handle) implements ClHandle {}
