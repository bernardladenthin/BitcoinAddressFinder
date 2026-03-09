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
import java.util.Collections;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDeviceSelection;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatformSelector;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.Test;

public class OpenCLPlatformSelectorTest {

    // CL_DEVICE_TYPE_CPU = (1 << 1) = 2, CL_DEVICE_TYPE_GPU = (1 << 2) = 4
    private static final long DEVICE_TYPE_CPU = 2L;
    private static final long DEVICE_TYPE_GPU = 4L;

    // <editor-fold defaultstate="collapsed" desc="select">
    @Test
    public void select_validPlatformAndDeviceIndex_returnsExpectedSelection() {
        // arrange
        OpenCLDevice gpuDevice = createTestDevice(DEVICE_TYPE_GPU);
        OpenCLPlatform platform = createTestPlatform("Platform0", gpuDevice);
        List<OpenCLPlatform> platforms = List.of(platform);

        // act
        OpenCLDeviceSelection result = OpenCLPlatformSelector.select(platforms, 0, DEVICE_TYPE_GPU, 0);

        // assert
        assertThat(result, is(notNullValue()));
        assertThat(result.platform(), is(equalTo(platform)));
        assertThat(result.device(), is(equalTo(gpuDevice)));
    }

    @Test
    public void select_deviceTypeFilter_onlyMatchingDeviceSelected() {
        // arrange
        OpenCLDevice cpuDevice = createTestDevice(DEVICE_TYPE_CPU);
        OpenCLDevice gpuDevice = createTestDevice(DEVICE_TYPE_GPU);
        OpenCLPlatform platform = createTestPlatform("Platform0", cpuDevice, gpuDevice);
        List<OpenCLPlatform> platforms = List.of(platform);

        // act
        OpenCLDeviceSelection result = OpenCLPlatformSelector.select(platforms, 0, DEVICE_TYPE_GPU, 0);

        // assert
        assertThat(result.device(), is(equalTo(gpuDevice)));
    }

    @Test
    public void select_secondPlatformSelected_returnsDeviceFromSecondPlatform() {
        // arrange
        OpenCLDevice firstDevice = createTestDevice(DEVICE_TYPE_GPU);
        OpenCLDevice secondDevice = createTestDevice(DEVICE_TYPE_GPU);
        OpenCLPlatform firstPlatform = createTestPlatform("Platform0", firstDevice);
        OpenCLPlatform secondPlatform = createTestPlatform("Platform1", secondDevice);
        List<OpenCLPlatform> platforms = List.of(firstPlatform, secondPlatform);

        // act
        OpenCLDeviceSelection result = OpenCLPlatformSelector.select(platforms, 1, DEVICE_TYPE_GPU, 0);

        // assert
        assertThat(result.platform(), is(equalTo(secondPlatform)));
        assertThat(result.device(), is(equalTo(secondDevice)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void select_negativePlatformIndex_throwsIllegalArgumentException() {
        // arrange
        OpenCLPlatform platform = createTestPlatform("Platform0", createTestDevice(DEVICE_TYPE_GPU));
        List<OpenCLPlatform> platforms = List.of(platform);

        // act
        OpenCLPlatformSelector.select(platforms, -1, DEVICE_TYPE_GPU, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void select_platformIndexTooLarge_throwsIllegalArgumentException() {
        // arrange
        OpenCLPlatform platform = createTestPlatform("Platform0", createTestDevice(DEVICE_TYPE_GPU));
        List<OpenCLPlatform> platforms = List.of(platform);

        // act
        OpenCLPlatformSelector.select(platforms, 1, DEVICE_TYPE_GPU, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void select_emptyPlatformList_throwsIllegalArgumentException() {
        // arrange
        List<OpenCLPlatform> platforms = Collections.emptyList();

        // act
        OpenCLPlatformSelector.select(platforms, 0, DEVICE_TYPE_GPU, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void select_negativeDeviceIndex_throwsIllegalArgumentException() {
        // arrange
        OpenCLDevice gpuDevice = createTestDevice(DEVICE_TYPE_GPU);
        OpenCLPlatform platform = createTestPlatform("Platform0", gpuDevice);
        List<OpenCLPlatform> platforms = List.of(platform);

        // act
        OpenCLPlatformSelector.select(platforms, 0, DEVICE_TYPE_GPU, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void select_deviceIndexTooLarge_throwsIllegalArgumentException() {
        // arrange
        OpenCLDevice gpuDevice = createTestDevice(DEVICE_TYPE_GPU);
        OpenCLPlatform platform = createTestPlatform("Platform0", gpuDevice);
        List<OpenCLPlatform> platforms = List.of(platform);

        // act
        OpenCLPlatformSelector.select(platforms, 0, DEVICE_TYPE_GPU, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void select_noDeviceMatchingType_throwsIllegalArgumentException() {
        // arrange
        OpenCLDevice cpuDevice = createTestDevice(DEVICE_TYPE_CPU);
        OpenCLPlatform platform = createTestPlatform("Platform0", cpuDevice);
        List<OpenCLPlatform> platforms = List.of(platform);

        // act
        OpenCLPlatformSelector.select(platforms, 0, DEVICE_TYPE_GPU, 0);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="helper">
    private static OpenCLPlatform createTestPlatform(String name, OpenCLDevice... devices) {
        return new OpenCLPlatform(name, new cl_context_properties(), ImmutableList.copyOf(devices));
    }

    private static OpenCLDevice createTestDevice(long deviceType) {
        return new OpenCLDevice(
            new cl_device_id(), "TestDevice", "TestVendor", "1.0",
            "FULL_PROFILE", "OpenCL 2.0", "", deviceType,
            true, 1, 1L, ImmutableList.of(64L), 64L, 1000L, 32,
            1024L * 1024L, 1024L * 1024L * 1024L, 0L, 1,
            32L * 1024L, 64L * 1024L, 0L,
            1, 128, 8, 0L,
            4096L, 4096L, 2048L, 2048L, 2048L,
            1, 1, 1, 1, 1, 1
        );
    }
    // </editor-fold>
}
