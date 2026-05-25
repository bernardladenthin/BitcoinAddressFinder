// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.util.List;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.junit.jupiter.api.Assumptions;

public class OpenCLPlatformAssume implements PlatformAssume {

    public void assumeOpenCLLibraryLoadable() {
        Assumptions.assumeTrue(OpenCLBuilder.isOpenCLnativeLibraryLoadable(), "OpenCL library loadable");
    }

    public void assumeOneOpenCL2_0OrGreaterDeviceAvailable(List<OpenCLPlatform> openCLPlatforms) {
        Assumptions.assumeTrue(OpenCLBuilder.isOneOpenCL2_0OrGreaterDeviceAvailable(openCLPlatforms), "One OpenCL 2.0 or greater device available");
    }
    
    public void assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable() {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadable();
        OpenCLBuilder openCLBuilder = new OpenCLBuilder();
        List<OpenCLPlatform> openCLPlatforms = openCLBuilder.build();
        new OpenCLPlatformAssume().assumeOneOpenCL2_0OrGreaterDeviceAvailable(openCLPlatforms);
    }
}
