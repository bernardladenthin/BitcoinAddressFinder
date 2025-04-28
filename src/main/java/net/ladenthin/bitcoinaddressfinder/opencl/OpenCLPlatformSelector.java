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

import java.util.List;

public class OpenCLPlatformSelector {

    /**
     * Selects an OpenCLDevice based on the platform index and device type from the given list of platforms.
     *
     * @param platforms The list of available OpenCL platforms
     * @param platformIndex The index of the platform to select
     * @param deviceType The OpenCL device type (e.g., CL_DEVICE_TYPE_GPU)
     * @param deviceIndex The index of the device within the platform
     * @return A selected OpenCLDeviceSelection containing platform, device, and context properties
     */
    public static OpenCLDeviceSelection select(List<OpenCLPlatform> platforms, int platformIndex, long deviceType, int deviceIndex) {
        if (platformIndex < 0 || platformIndex >= platforms.size()) {
            throw new IllegalArgumentException("Invalid platform index: " + platformIndex);
        }
        
        OpenCLPlatform selectedPlatform = platforms.get(platformIndex);
        
        List<OpenCLDevice> matchingDevices = selectedPlatform.openCLDevices().stream()
            .filter(device -> (device.deviceType() & deviceType) != 0)
            .toList();

        if (deviceIndex < 0 || deviceIndex >= matchingDevices.size()) {
            throw new IllegalArgumentException("Invalid device index: " + deviceIndex + " for deviceType: " + deviceType);
        }
        
        OpenCLDevice selectedDevice = matchingDevices.get(deviceIndex);
        
        return new OpenCLDeviceSelection(selectedPlatform, selectedDevice, selectedPlatform.contextProperties());
    }
}
