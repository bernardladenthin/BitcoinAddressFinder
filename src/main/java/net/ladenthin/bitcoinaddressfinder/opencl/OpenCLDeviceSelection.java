// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import org.jocl.cl_context_properties;

/**
 * Result of {@link OpenCLPlatformSelector#select}: the selected platform and device plus the
 * matching {@link cl_context_properties}.
 *
 * @param platform           the selected OpenCL platform
 * @param device             the selected OpenCL device on that platform
 * @param contextProperties  the context properties referencing the platform
 */
public record OpenCLDeviceSelection(
        OpenCLPlatform platform, OpenCLDevice device, cl_context_properties contextProperties) {}
