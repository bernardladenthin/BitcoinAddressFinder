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
import static org.hamcrest.Matchers.is;
import org.jocl.CL;
import static org.jocl.CL.clGetPlatformIDs;
import org.junit.Assume;

public class OpenCLPlatform {
    public boolean isOpenCLAvailable() {
        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];
        return numPlatforms > 0;
    }
    
    public void assumeOpenCLAvailable() {
        Assume.assumeThat("OpenCL available", isOpenCLAvailable(), is(TRUE));
    }
}
