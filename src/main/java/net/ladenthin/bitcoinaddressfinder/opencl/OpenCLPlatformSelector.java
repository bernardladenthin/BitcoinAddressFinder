// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import java.util.List;

/**
 * Selects an OpenCL device within a list of platforms based on the configured indices and device type mask.
 */
public class OpenCLPlatformSelector {

    /** Creates a new {@link OpenCLPlatformSelector}. */
    public OpenCLPlatformSelector() {
    }


    /**
     * Selects an OpenCLDevice based on the platform index and device type from the given list of platforms.
     *
     * @param platforms The list of available OpenCL platforms
     * @param platformIndex The index of the platform to select
     * @param deviceType The OpenCL device type (e.g., CL_DEVICE_TYPE_GPU)
     * @param deviceIndex The index of the device within the platform
     * @return A selected OpenCLDeviceSelection containing platform, device, and context properties
     */
    public OpenCLDeviceSelection select(List<OpenCLPlatform> platforms, int platformIndex, long deviceType, int deviceIndex) {
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
