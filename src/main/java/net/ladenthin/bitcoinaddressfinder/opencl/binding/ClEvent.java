// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import com.google.errorprone.annotations.Immutable;

/**
 * Handle to an OpenCL event, used to await and to profile an enqueued command.
 *
 * <p>Corresponds to the OpenCL C type {@code cl_event}.
 *
 * @param handle the native handle value
 */
@Immutable
public record ClEvent(long handle) implements ClHandle {}
