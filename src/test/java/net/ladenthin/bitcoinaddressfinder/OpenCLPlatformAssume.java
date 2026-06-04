// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.util.List;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.junit.jupiter.api.Assumptions;

public class OpenCLPlatformAssume implements PlatformAssume {

    public void assumeOpenClLibraryAvailable() {
        Assumptions.assumeTrue(OpenCLBuilder.isOpenClNativeLibraryLoaded(), "OpenCL library not available");
    }

    public void assumeOneOpenCL2_0OrGreaterDeviceAvailable(List<OpenCLPlatform> openCLPlatforms) {
        Assumptions.assumeTrue(
                OpenCLBuilder.isOneOpenCL2_0OrGreaterDeviceAvailable(openCLPlatforms),
                "One OpenCL 2.0 or greater device available");
    }

    public void assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable() {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailable();
        OpenCLBuilder openCLBuilder = new OpenCLBuilder();
        List<OpenCLPlatform> openCLPlatforms = openCLBuilder.build();
        new OpenCLPlatformAssume().assumeOneOpenCL2_0OrGreaterDeviceAvailable(openCLPlatforms);
    }
}
