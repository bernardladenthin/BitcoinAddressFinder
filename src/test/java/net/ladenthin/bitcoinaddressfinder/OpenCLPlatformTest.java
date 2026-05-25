// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import com.google.common.collect.ImmutableList;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
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
