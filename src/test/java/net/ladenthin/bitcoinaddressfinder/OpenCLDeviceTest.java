// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
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

import java.io.IOException;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Test;

public class OpenCLDeviceTest {
    
    // <editor-fold defaultstate="collapsed" desc="toStringPretty">
    @Test
    @OpenCLTest
    public void toStringPretty_openCLDeviceExisting_stringCreated() throws IOException {
        // arrange
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        
        OpenCLBuilder openCLBuilder = new OpenCLBuilder();
        List<OpenCLPlatform> openCLPlatforms = openCLBuilder.build();
        final OpenCLDevice openCLDevice = openCLPlatforms.getFirst().getOpenCLDevices().getFirst();
        
        // act
        final String toStringPretty = openCLDevice.toStringPretty();
        
        // assert
        assertThat(toStringPretty, notNullValue());
        System.out.println("toStringPretty: " + toStringPretty);
    }
    // </editor-fold>
    
}