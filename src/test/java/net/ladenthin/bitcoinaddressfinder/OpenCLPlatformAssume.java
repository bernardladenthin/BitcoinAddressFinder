// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static java.lang.Boolean.TRUE;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.junit.Assume;
import static org.hamcrest.Matchers.is;

public class OpenCLPlatformAssume implements PlatformAssume {
    
    public void assumeOpenCLLibraryLoadable() {
        Assume.assumeThat("OpenCL library loadable", OpenCLBuilder.isOpenCLnativeLibraryLoadable(), is(TRUE));
    }
    
    public void assumeOneOpenCL2_0OrGreaterDeviceAvailable(List<OpenCLPlatform> openCLPlatforms) {
        Assume.assumeThat("One OpenCL 2.0 or greater device available", OpenCLBuilder.isOneOpenCL2_0OrGreaterDeviceAvailable(openCLPlatforms), is(TRUE));
    }
    
    public void assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable() {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadable();
        OpenCLBuilder openCLBuilder = new OpenCLBuilder();
        List<OpenCLPlatform> openCLPlatforms = openCLBuilder.build();
        new OpenCLPlatformAssume().assumeOneOpenCL2_0OrGreaterDeviceAvailable(openCLPlatforms);
    }
}
