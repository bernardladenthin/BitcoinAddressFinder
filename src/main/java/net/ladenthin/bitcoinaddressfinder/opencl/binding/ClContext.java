// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import com.google.errorprone.annotations.Immutable;

/**
 * Handle to an OpenCL context, the scope that owns queues, buffers and programs.
 *
 * <p>Corresponds to the OpenCL C type {@code cl_context}.
 *
 * @param handle the native handle value
 */
@Immutable
public record ClContext(long handle) implements ClHandle {}
