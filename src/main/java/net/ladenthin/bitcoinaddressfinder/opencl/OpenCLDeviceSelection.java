// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import org.jocl.cl_context_properties;

public record OpenCLDeviceSelection(OpenCLPlatform platform, OpenCLDevice device,
                                    cl_context_properties contextProperties) {

}
