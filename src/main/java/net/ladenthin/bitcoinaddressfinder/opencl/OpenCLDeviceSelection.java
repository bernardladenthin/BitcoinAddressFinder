// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.opencl;

import org.jocl.cl_context_properties;

public class OpenCLDeviceSelection {
    
    private final OpenCLPlatform platform;
    private final OpenCLDevice device;
    private final cl_context_properties contextProperties;

    public OpenCLDeviceSelection(OpenCLPlatform platform, OpenCLDevice device, cl_context_properties contextProperties) {
        this.platform = platform;
        this.device = device;
        this.contextProperties = contextProperties;
    }

    public OpenCLPlatform getPlatform() {
        return platform;
    }

    public OpenCLDevice getDevice() {
        return device;
    }

    public cl_context_properties getContextProperties() {
        return contextProperties;
    }
}
