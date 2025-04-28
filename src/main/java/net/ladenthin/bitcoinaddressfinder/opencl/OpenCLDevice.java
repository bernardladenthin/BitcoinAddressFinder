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
package net.ladenthin.bitcoinaddressfinder.opencl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jocl.CL;
import org.jocl.cl_device_id;

/**
 * Represents an OpenCL device and its properties.
 *
 * @param device                     See {@link org.jocl.cl_device_id}
 * @param deviceName                 See {@link org.jocl.CL#CL_DEVICE_NAME}
 * @param deviceVendor               See {@link org.jocl.CL#CL_DEVICE_VENDOR}
 * @param driverVersion              See {@link org.jocl.CL#CL_DRIVER_VERSION}
 * @param deviceProfile              See {@link org.jocl.CL#CL_DEVICE_PROFILE}
 * @param deviceVersion              See {@link org.jocl.CL#CL_DEVICE_VERSION}
 * @param deviceExtensions           See {@link org.jocl.CL#CL_DEVICE_EXTENSIONS}
 * @param deviceType                 See {@link org.jocl.CL#CL_DEVICE_TYPE}
 * @param endianLittle               See {@link org.jocl.CL#CL_DEVICE_ENDIAN_LITTLE}
 * @param maxComputeUnits            See {@link org.jocl.CL#CL_DEVICE_MAX_COMPUTE_UNITS}
 * @param maxWorkItemDimensions      See {@link org.jocl.CL#CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS}
 * @param maxWorkItemSizes           See {@link org.jocl.CL#CL_DEVICE_MAX_WORK_ITEM_SIZES}
 * @param maxWorkGroupSize           See {@link org.jocl.CL#CL_DEVICE_MAX_WORK_GROUP_SIZE}
 * @param maxClockFrequency          See {@link org.jocl.CL#CL_DEVICE_MAX_CLOCK_FREQUENCY}
 * @param addressBits                See {@link org.jocl.CL#CL_DEVICE_ADDRESS_BITS}
 * @param maxMemAllocSize            See {@link org.jocl.CL#CL_DEVICE_MAX_MEM_ALLOC_SIZE}
 * @param globalMemSize              See {@link org.jocl.CL#CL_DEVICE_GLOBAL_MEM_SIZE}
 * @param errorCorrectionSupport     See {@link org.jocl.CL#CL_DEVICE_ERROR_CORRECTION_SUPPORT}
 * @param localMemType               See {@link org.jocl.CL#CL_DEVICE_LOCAL_MEM_TYPE}
 * @param localMemSize               See {@link org.jocl.CL#CL_DEVICE_LOCAL_MEM_SIZE}
 * @param maxConstantBufferSize      See {@link org.jocl.CL#CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE}
 * @param queueProperties            See {@link org.jocl.CL#CL_DEVICE_QUEUE_PROPERTIES}
 * @param imageSupport               See {@link org.jocl.CL#CL_DEVICE_IMAGE_SUPPORT}
 * @param maxReadImageArgs           See {@link org.jocl.CL#CL_DEVICE_MAX_READ_IMAGE_ARGS}
 * @param maxWriteImageArgs          See {@link org.jocl.CL#CL_DEVICE_MAX_WRITE_IMAGE_ARGS}
 * @param singleFpConfig             See {@link org.jocl.CL#CL_DEVICE_SINGLE_FP_CONFIG}
 * @param image2dMaxWidth            See {@link org.jocl.CL#CL_DEVICE_IMAGE2D_MAX_WIDTH}
 * @param image2dMaxHeight           See {@link org.jocl.CL#CL_DEVICE_IMAGE2D_MAX_HEIGHT}
 * @param image3dMaxWidth            See {@link org.jocl.CL#CL_DEVICE_IMAGE3D_MAX_WIDTH}
 * @param image3dMaxHeight           See {@link org.jocl.CL#CL_DEVICE_IMAGE3D_MAX_HEIGHT}
 * @param image3dMaxDepth            See {@link org.jocl.CL#CL_DEVICE_IMAGE3D_MAX_DEPTH}
 * @param preferredVectorWidthChar   See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR}
 * @param preferredVectorWidthShort  See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT}
 * @param preferredVectorWidthInt    See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT}
 * @param preferredVectorWidthLong   See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG}
 * @param preferredVectorWidthFloat  See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT}
 * @param preferredVectorWidthDouble See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE}
 */
public record OpenCLDevice(
    cl_device_id device,
    String deviceName,
    String deviceVendor,
    String driverVersion,
    String deviceProfile,
    String deviceVersion,
    String deviceExtensions,
    long deviceType,
    boolean endianLittle,
    int maxComputeUnits,
    long maxWorkItemDimensions,
    long[] maxWorkItemSizes,
    long maxWorkGroupSize,
    long maxClockFrequency,
    int addressBits,
    long maxMemAllocSize,
    long globalMemSize,
    long errorCorrectionSupport,
    int localMemType,
    long localMemSize,
    long maxConstantBufferSize,
    long queueProperties,
    int imageSupport,
    int maxReadImageArgs,
    int maxWriteImageArgs,
    long singleFpConfig,
    long image2dMaxWidth,
    long image2dMaxHeight,
    long image3dMaxWidth,
    long image3dMaxHeight,
    long image3dMaxDepth,
    int preferredVectorWidthChar,
    int preferredVectorWidthShort,
    int preferredVectorWidthInt,
    int preferredVectorWidthLong,
    int preferredVectorWidthFloat,
    int preferredVectorWidthDouble
) implements Serializable {
    
    public ByteOrder getByteOrder() {
        if(endianLittle) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        return ByteOrder.BIG_ENDIAN;
    }
    
    public String toStringPretty() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Charset charset = java.nio.charset.StandardCharsets.UTF_8;

        try (PrintStream ps = new PrintStream(baos, true, charset)) {
            ps.println("--- Info for OpenCL device: " + deviceName + " ---");
            ps.printf("cl_device_id:                          %s%n", device);
            ps.printf("CL_DEVICE_NAME:                        %s%n", deviceName);
            ps.printf("CL_DEVICE_VENDOR:                      %s%n", deviceVendor);
            ps.printf("CL_DRIVER_VERSION:                     %s%n", driverVersion);
            ps.printf("CL_DEVICE_PROFILE:                     %s%n", deviceProfile);
            ps.printf("CL_DEVICE_VERSION:                     %s%n", deviceVersion);
            ps.printf("CL_DEVICE_EXTENSIONS:                  %s%n", deviceExtensions);
            ps.printf("CL_DEVICE_TYPE:                        %s%n", CL.stringFor_cl_device_type(deviceType));
            ps.printf("CL_DEVICE_ENDIAN_LITTLE:               %b%n", endianLittle);
            ps.printf("CL_DEVICE_MAX_COMPUTE_UNITS:           %d%n", maxComputeUnits);
            ps.printf("CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS:    %d%n", maxWorkItemDimensions);
            ps.printf("CL_DEVICE_MAX_WORK_ITEM_SIZES:         %s%n", formatWorkItemSizes(maxWorkItemSizes));
            ps.printf("CL_DEVICE_MAX_WORK_GROUP_SIZE:         %d%n", maxWorkGroupSize);
            ps.printf("CL_DEVICE_MAX_CLOCK_FREQUENCY:         %d MHz%n", maxClockFrequency);
            ps.printf("CL_DEVICE_ADDRESS_BITS:                %d%n", addressBits);
            ps.printf("CL_DEVICE_MAX_MEM_ALLOC_SIZE:          %d MByte%n", maxMemAllocSize / (1024 * 1024));
            ps.printf("CL_DEVICE_GLOBAL_MEM_SIZE:             %d MByte%n", globalMemSize / (1024 * 1024));
            ps.printf("CL_DEVICE_ERROR_CORRECTION_SUPPORT:    %s%n", errorCorrectionSupport != 0 ? "yes" : "no");
            ps.printf("CL_DEVICE_LOCAL_MEM_TYPE:              %s%n", CL.stringFor_cl_device_local_mem_type(localMemType));
            ps.printf("CL_DEVICE_LOCAL_MEM_SIZE:              %d KByte%n", localMemSize / 1024);
            ps.printf("CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE:    %d KByte%n", maxConstantBufferSize / 1024);
            ps.printf("CL_DEVICE_QUEUE_PROPERTIES:            %s%n", CL.stringFor_cl_command_queue_properties(queueProperties));
            ps.printf("CL_DEVICE_IMAGE_SUPPORT:               %d%n", imageSupport);
            ps.printf("CL_DEVICE_MAX_READ_IMAGE_ARGS:         %d%n", maxReadImageArgs);
            ps.printf("CL_DEVICE_MAX_WRITE_IMAGE_ARGS:        %d%n", maxWriteImageArgs);
            ps.printf("CL_DEVICE_SINGLE_FP_CONFIG:            %s%n", CL.stringFor_cl_device_fp_config(singleFpConfig));
            ps.printf("CL_DEVICE_IMAGE2D_MAX_WIDTH:           %d%n", image2dMaxWidth);
            ps.printf("CL_DEVICE_IMAGE2D_MAX_HEIGHT:          %d%n", image2dMaxHeight);
            ps.printf("CL_DEVICE_IMAGE3D_MAX_WIDTH:           %d%n", image3dMaxWidth);
            ps.printf("CL_DEVICE_IMAGE3D_MAX_HEIGHT:          %d%n", image3dMaxHeight);
            ps.printf("CL_DEVICE_IMAGE3D_MAX_DEPTH:           %d%n", image3dMaxDepth);
            ps.printf("CL_DEVICE_PREFERRED_VECTOR_WIDTHS:     CHAR %d, SHORT %d, INT %d, LONG %d, FLOAT %d, DOUBLE %d%n",
                preferredVectorWidthChar, preferredVectorWidthShort,
                preferredVectorWidthInt, preferredVectorWidthLong,
                preferredVectorWidthFloat, preferredVectorWidthDouble);
        }

        return baos.toString(charset);
    }
    
    public static String formatWorkItemSizes(long[] sizes) {
        if (sizes == null || sizes.length == 0) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sizes.length; i++) {
            sb.append(sizes[i]);
            if (i < sizes.length - 1) sb.append(" / ");
        }
        return sb.toString();
    }
    
    public ComparableVersion getDeviceVersionAsComparableVersion() {
        return getComparableVersionFromDeviceVersion(deviceVersion());
    }
    
    public static ComparableVersion getComparableVersionFromDeviceVersion(String deviceVersion) {
        String s = deviceVersion;
        s = s.replace("OpenCL ", "");
        s = s.replace("CUDA", "");
        return new ComparableVersion(s.trim());
    }
}
