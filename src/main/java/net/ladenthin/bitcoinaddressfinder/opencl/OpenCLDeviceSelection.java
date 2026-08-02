// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

/**
 * Result of {@link OpenCLPlatformSelector#select}: the selected platform and the device chosen on it.
 *
 * <p>The platform is kept alongside the device because creating a context needs both — the device
 * handle alone does not say which platform it came from.
 *
 * @param platform the selected OpenCL platform
 * @param device   the selected OpenCL device on that platform
 */
public record OpenCLDeviceSelection(OpenCLPlatform platform, OpenCLDevice device) {}
