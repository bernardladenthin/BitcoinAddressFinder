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
package net.ladenthin.bitcoinaddressfinder;

import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.junit.Test;
import java.util.Collections;
import java.util.List;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.not;

public class OpenCLPlatformTest {

    // <editor-fold defaultstate="collapsed" desc="constructor and getters">
    @Test
    public void constructor_validArguments_returnsPlatform() {
        // arrange
        String platformName = "Test Platform";
        List<OpenCLDevice> devices = Collections.emptyList();

        // act
        OpenCLPlatform platform = new OpenCLPlatform(platformName, devices);

        // assert
        assertThat(platform, is(notNullValue()));
        assertThat(platform.platformName(), is(equalTo(platformName)));
        assertThat(platform.openCLDevices(), is(devices));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void constructor_givenModifiableList_resultingListIsUnmodifiable() {
        // arrange
        List<OpenCLDevice> mutableList = new java.util.ArrayList<>();

        // act
        OpenCLPlatform platform = new OpenCLPlatform("Immutable Test", mutableList);

        // try to modify list (should throw)
        platform.openCLDevices().add(null);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="toString">
    @Test
    @ToStringTest
    public void toString_containsPlatformName() {
        // arrange
        String platformName = "Platform A";
        OpenCLPlatform platform = new OpenCLPlatform(platformName, Collections.emptyList());

        // act
        String result = platform.toString();

        // assert
        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString(platformName));
    }
    // </editor-fold>
}
