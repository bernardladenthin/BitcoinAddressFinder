// @formatter:off
/**
 * Copyright 2022 Bernard Ladenthin bernard.ladenthin@gmail.com
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
