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
import org.apache.maven.artifact.versioning.ComparableVersion;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import org.junit.Test;
import org.jocl.CL;

public class OpenCLDeviceTest {
    
    // <editor-fold defaultstate="collapsed" desc="toStringPretty">
    @Test
    @OpenCLTest
    @ToStringTest
    public void toStringPretty_openCLDeviceExisting_stringCreated() throws IOException {
        // arrange
        new OpenCLPlatformAssume().assumeOpenCLLibraryLoadableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        
        OpenCLBuilder openCLBuilder = new OpenCLBuilder();
        List<OpenCLPlatform> openCLPlatforms = openCLBuilder.build();
        final OpenCLDevice openCLDevice = openCLPlatforms.getFirst().openCLDevices().getFirst();
        
        // act
        final String toStringPretty = openCLDevice.toStringPretty();
        
        // assert
        assertThat(toStringPretty, not(emptyOrNullString()));
        System.out.println("toStringPretty: " + toStringPretty);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="toStringPretty (static sample)">
    @Test
    @OpenCLTest
    @ToStringTest
    public void toStringPretty_staticDeviceData_stringCreated() {
        // arrange
        OpenCLDevice device = new OpenCLDevice(
            "NVIDIA GeForce RTX 3070 Laptop GPU",
            "NVIDIA Corporation",
            "561.19",
            "FULL_PROFILE",
            "OpenCL 3.0 CUDA",
            "cl_khr_global_int32_base_atomics cl_khr_global_int32_extended_atomics cl_khr_local_int32_base_atomics cl_khr_local_int32_extended_atomics cl_khr_fp64 cl_khr_3d_image_writes cl_khr_byte_addressable_store cl_khr_icd cl_khr_gl_sharing cl_nv_compiler_options cl_nv_device_attribute_query cl_nv_pragma_unroll cl_nv_d3d10_sharing cl_khr_d3d10_sharing cl_nv_d3d11_sharing cl_nv_copy_opts cl_nv_create_buffer cl_khr_int64_base_atomics cl_khr_int64_extended_atomics cl_khr_device_uuid cl_khr_pci_bus_info cl_khr_external_semaphore cl_khr_external_memory cl_khr_external_semaphore_win32 cl_khr_external_memory_win32",
            CL.CL_DEVICE_TYPE_GPU,
            40,
            3,
            new long[]{1024, 1024, 64},
            1024,
            1290,
            64,
            2047L * 1024 * 1024,
            8191L * 1024 * 1024,
            0,
            CL.CL_LOCAL,
            48L * 1024,
            64L * 1024,
            CL.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE | CL.CL_QUEUE_PROFILING_ENABLE,
            1,
            256,
            32,
            CL.CL_FP_DENORM | CL.CL_FP_INF_NAN | CL.CL_FP_ROUND_TO_NEAREST | CL.CL_FP_ROUND_TO_ZERO | CL.CL_FP_ROUND_TO_INF | CL.CL_FP_FMA | CL.CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT,
            32768,
            32768,
            16384,
            16384,
            16384,
            1,
            1,
            1,
            1,
            1,
            1
        );

        // act
        String output = device.toStringPretty();

        // assert
        assertThat(output, not(emptyOrNullString()));
        System.out.println("OpenCLDevice Pretty String:\n" + output);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="formatWorkItemSizes">
    @Test
    public void formatWorkItemSizes_nullInput_returnsNone() {
        // arrange
        long[] input = null;

        // act
        String result = OpenCLDevice.formatWorkItemSizes(input);

        // assert
        assertThat(result, is("(none)"));
    }

    @Test
    public void formatWorkItemSizes_emptyInput_returnsNone() {
        // arrange
        long[] input = new long[0];

        // act
        String result = OpenCLDevice.formatWorkItemSizes(input);

        // assert
        assertThat(result, is("(none)"));
    }

    @Test
    public void formatWorkItemSizes_singleElement_returnsElement() {
        // arrange
        long[] input = {42};

        // act
        String result = OpenCLDevice.formatWorkItemSizes(input);

        // assert
        assertThat(result, is("42"));
    }

    @Test
    public void formatWorkItemSizes_threeElements_returnsFormattedString() {
        // arrange
        long[] input = {64, 128, 256};

        // act
        String result = OpenCLDevice.formatWorkItemSizes(input);

        // assert
        assertThat(result, is("64 / 128 / 256"));
    }

    @Test
    public void formatWorkItemSizes_fiveElements_returnsFormattedString() {
        // arrange
        long[] input = {1, 2, 3, 4, 5};

        // act
        String result = OpenCLDevice.formatWorkItemSizes(input);

        // assert
        assertThat(result, is("1 / 2 / 3 / 4 / 5"));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getComparableVersionFromDeviceVersion">

    @Test
    public void getComparableVersionFromDeviceVersion_openCLPrefix_removedCorrectly() {
        // arrange
        String input = "OpenCL 3.0 CUDA";
        
        // act
        ComparableVersion version = OpenCLDevice.getComparableVersionFromDeviceVersion(input);
        
        // assert
        assertThat(version.toString(), equalTo("3.0"));
    }

    @Test
    public void getComparableVersionFromDeviceVersion_cudaSuffix_removedCorrectly() {
        // arrange
        String input = "3.0 CUDA";
        
        // act
        ComparableVersion version = OpenCLDevice.getComparableVersionFromDeviceVersion(input);
        
        // assert
        assertThat(version.toString(), equalTo("3.0"));
    }

    @Test
    public void getComparableVersionFromDeviceVersion_noPrefixOrSuffix_versionUnchanged() {
        // arrange
        String input = "2.1";
        
        // act
        ComparableVersion version = OpenCLDevice.getComparableVersionFromDeviceVersion(input);
        
        // assert
        assertThat(version.toString(), equalTo("2.1"));
    }

    @Test
    public void getComparableVersionFromDeviceVersion_emptyString_returnsEmptyVersion() {
        // arrange
        String input = "";
        
        // act
        ComparableVersion version = OpenCLDevice.getComparableVersionFromDeviceVersion(input);
        
        // assert
        assertThat(version.toString(), equalTo(""));
    }
    
    @Test
    public void getComparableVersionFromDeviceVersion_trailingWhitespace_trimmedCorrectly() {
        // arrange
        String input = "3.0 ";

        // act
        ComparableVersion version = OpenCLDevice.getComparableVersionFromDeviceVersion(input);

        // assert
        assertThat(version.toString(), equalTo("3.0"));
    }

    // </editor-fold>
}