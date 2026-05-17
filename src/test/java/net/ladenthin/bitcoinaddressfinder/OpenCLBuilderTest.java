// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.IOException;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import org.junit.Test;

public class OpenCLBuilderTest {
    
    // <editor-fold defaultstate="collapsed" desc="build">
    @Test
    @OpenCLTest
    public void build_openCLDeviceExisting_platformsAndDevicesReturned() throws IOException {
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadable();
        // arrange
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        OpenCLBuilder openCLBuilder = new OpenCLBuilder();
        
        // act
        List<OpenCLPlatform> openCLPlatforms = openCLBuilder.build();
        
        // assert
        assertThat(openCLPlatforms.size(),is(greaterThan(Integer.valueOf(0))));
        assertThat(openCLPlatforms.getFirst().openCLDevices().size(),is(greaterThan(Integer.valueOf(0))));
        System.out.println(openCLPlatforms);
        System.out.println("isOpenCLnativeLibraryLoadable: " + OpenCLBuilder.isOpenCLnativeLibraryLoadable());
        System.out.println("isOneOpenCL2DeviceAvailable: " + OpenCLBuilder.isOneOpenCL2_0OrGreaterDeviceAvailable(openCLPlatforms));
    }
    // </editor-fold>
    
    @Test
    public void isOpenCL2_0OrGreater_OpenCLVersion1_2Given_ReturnFalse() throws IOException {
        OpenCLBuilder openCLBuilder = new OpenCLBuilder();
        assertThat(OpenCLBuilder.isOpenCL2_0OrGreater(OpenCLDevice.getComparableVersionFromDeviceVersion("OpenCL 1.2")), is(equalTo(Boolean.FALSE)));
    }
    
    @Test
    public void isOpenCL2_0OrGreater_OpenCLVersion3_0_CUDA_Given_ReturnFalse() throws IOException {
        OpenCLBuilder openCLBuilder = new OpenCLBuilder();
        assertThat(OpenCLBuilder.isOpenCL2_0OrGreater(OpenCLDevice.getComparableVersionFromDeviceVersion("OpenCL 3.0 CUDA")), is(equalTo(Boolean.TRUE)));
    }
}