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

import com.google.common.collect.ImmutableList;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.jspecify.annotations.NonNull;
import org.junit.Test;
import java.util.Collections;
import java.util.List;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.not;
import org.jocl.cl_context_properties;

public class OpenCLPlatformTest {

    // <editor-fold defaultstate="collapsed" desc="constructor and getters">
    @Test
    public void constructor_validArguments_returnsPlatform() {
        // arrange
        String platformName = "Test Platform";
        ImmutableList<@NonNull OpenCLDevice> devices = ImmutableList.<OpenCLDevice>builder().build();

        // act
        OpenCLPlatform platform = new OpenCLPlatform(platformName, new cl_context_properties(), devices);

        // assert
        assertThat(platform, is(notNullValue()));
        assertThat(platform.platformName(), is(equalTo(platformName)));
        assertThat(platform.openCLDevices(), is(devices));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="toString">
    @Test
    @ToStringTest
    public void toString_containsPlatformName() {
        // arrange
        String platformName = "Platform A";
        OpenCLPlatform platform = new OpenCLPlatform(platformName, new cl_context_properties(), ImmutableList.<OpenCLDevice>builder().build());

        // act
        String result = platform.toString();

        // assert
        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString(platformName));
    }
    // </editor-fold>
}
