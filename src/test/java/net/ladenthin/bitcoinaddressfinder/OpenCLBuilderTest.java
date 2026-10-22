// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClApi;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClDeviceId;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClPlatformId;
import org.junit.jupiter.api.Test;
import org.lwjgl.opencl.CL10;

public class OpenCLBuilderTest {

    // <editor-fold defaultstate="collapsed" desc="build">
    @Test
    @OpenCLTest
    public void build_openCLDeviceExisting_platformsAndDevicesReturned() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailable();
        // arrange
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        OpenCLBuilder openCLBuilder = new OpenCLBuilder();

        // act
        List<OpenCLPlatform> openCLPlatforms = openCLBuilder.build();

        // assert
        assertThat(openCLPlatforms.size(), is(greaterThan(Integer.valueOf(0))));
        assertThat(openCLPlatforms.getFirst().openCLDevices().size(), is(greaterThan(Integer.valueOf(0))));
        System.out.println(openCLPlatforms);
        System.out.println("isOpenClLibraryAvailable: " + new OpenCLBuilder().isOpenClLibraryAvailable());
        System.out.println("isOneOpenCL2DeviceAvailable: "
                + new OpenCLBuilder().isOneOpenCL2_0OrGreaterDeviceAvailable(openCLPlatforms));
    }
    // </editor-fold>

    @Test
    public void isOpenCL2_0OrGreater_OpenCLVersion1_2Given_ReturnFalse() throws IOException {
        assertThat(
                new OpenCLBuilder()
                        .isOpenCL2_0OrGreater(OpenCLDevice.getComparableVersionFromDeviceVersion("OpenCL 1.2")),
                is(equalTo(Boolean.FALSE)));
    }

    @Test
    public void isOpenCL2_0OrGreater_OpenCLVersion3_0_CUDA_Given_ReturnFalse() throws IOException {
        assertThat(
                new OpenCLBuilder()
                        .isOpenCL2_0OrGreater(OpenCLDevice.getComparableVersionFromDeviceVersion("OpenCL 3.0 CUDA")),
                is(equalTo(Boolean.TRUE)));
    }

    @Test
    public void build_deviceWithMoreThanThreeWorkItemDimensions_readsEverySize() {
        // arrange: a device reporting 4 dimensions; like clGetDeviceInfo, the fake refuses a
        // CL_DEVICE_MAX_WORK_ITEM_SIZES buffer smaller than the reported dimension count
        final int dimensions = 4;
        final ClApi clApi = mock(ClApi.class, invocation -> {
            final Class<?> returnType = invocation.getMethod().getReturnType();
            if (returnType == String.class) {
                return "OpenCL 3.0 test";
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            return null;
        });
        when(clApi.getPlatformIds()).thenReturn(List.of(new ClPlatformId(1L)));
        when(clApi.getDeviceIds(any(), anyLong())).thenReturn(List.of(new ClDeviceId(2L)));
        when(clApi.getDeviceInfoInt(any(), eq(CL10.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS)))
                .thenReturn(dimensions);
        when(clApi.getDeviceInfoSizes(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            final int paramName = invocation.getArgument(1);
            final int valueCount = invocation.getArgument(2);
            if (paramName == CL10.CL_DEVICE_MAX_WORK_ITEM_SIZES && valueCount < dimensions) {
                throw new IllegalStateException("clGetDeviceInfo: CL_INVALID_VALUE");
            }
            return new long[valueCount];
        });

        // act
        final List<OpenCLPlatform> platforms = assertDoesNotThrow(() -> new OpenCLBuilder(clApi).build());

        // assert
        assertThat(platforms.get(0).openCLDevices().get(0).maxWorkItemSizes().size(), is(equalTo(dimensions)));
    }
}
