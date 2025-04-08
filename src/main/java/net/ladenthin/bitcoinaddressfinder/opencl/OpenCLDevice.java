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

import com.google.errorprone.annotations.Immutable;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jocl.CL;
import org.jocl.cl_device_id;

@Immutable
@ToString
@EqualsAndHashCode
public class OpenCLDevice implements Serializable {
    
    @NonNull
    private final transient cl_device_id id;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_NAME}.
     */
    @NonNull
    private final String deviceName;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_VENDOR}.
     */
    @NonNull
    private final String deviceVendor;
    
    /**
     * See {@link org.jocl.CL#CL_DRIVER_VERSION}.
     */
    @NonNull
    private final String driverVersion;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_PROFILE}.
     */
    @NonNull
    private final String deviceProfile;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_VERSION}.
     */
    @NonNull
    private final String deviceVersion;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_EXTENSIONS}.
     */
    @NonNull
    private final String deviceExtensions;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_TYPE}.
     */
    @NonNull
    private final long deviceType;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_MAX_COMPUTE_UNITS}.
     */
    @NonNull
    private final int maxComputeUnits;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS}.
     */
    @NonNull
    private final long maxWorkItemDimensions;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_MAX_WORK_ITEM_SIZES}.
     */
    @NonNull
    private final long maxWorkItemSizes[];
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_MAX_WORK_GROUP_SIZE}.
     */
    @NonNull
    private final long maxWorkGroupSize;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_MAX_CLOCK_FREQUENCY}.
     */
    @NonNull
    private final long maxClockFrequency;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_ADDRESS_BITS}.
     */
    @NonNull
    private final int addressBits;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_MAX_MEM_ALLOC_SIZE}.
     */
    @NonNull
    private final long maxMemAllocSize;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_GLOBAL_MEM_SIZE}.
     */
    @NonNull
    private final long globalMemSize;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_ERROR_CORRECTION_SUPPORT}.
     */
    @NonNull
    private final long errorCorrectionSupport;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_LOCAL_MEM_TYPE}.
     */
    @NonNull
    private final int localMemType;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_LOCAL_MEM_SIZE}.
     */
    @NonNull
    private final long localMemSize;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE}.
     */
    @NonNull
    private final long maxConstantBufferSize;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_QUEUE_PROPERTIES}.
     */
    @NonNull
    private final long queueProperties;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_IMAGE_SUPPORT}.
     */
    @NonNull
    private final int imageSupport;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_MAX_READ_IMAGE_ARGS}.
     */
    @NonNull
    private final int maxReadImageArgs;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_MAX_WRITE_IMAGE_ARGS}.
     */
    @NonNull
    private final int maxWriteImageArgs;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_SINGLE_FP_CONFIG}.
     */
    @NonNull
    private final long singleFpConfig;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_IMAGE2D_MAX_WIDTH}.
     */
    @NonNull
    private final long image2dMaxWidth;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_IMAGE2D_MAX_HEIGHT}.
     */
    @NonNull
    private final long image2dMaxHeight;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_IMAGE3D_MAX_WIDTH}.
     */
    @NonNull
    private final long image3dMaxWidth;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_IMAGE3D_MAX_HEIGHT}.
     */
    @NonNull
    private final long image3dMaxHeight;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_IMAGE3D_MAX_DEPTH}.
     */
    @NonNull
    private final long image3dMaxDepth;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR}.
     */
    @NonNull
    private final int preferredVectorWidthChar;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT}.
     */
    @NonNull
    private final int preferredVectorWidthShort;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT}.
     */
    @NonNull
    private final int preferredVectorWidthInt;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG}.
     */
    @NonNull
    private final int preferredVectorWidthLong;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT}.
     */
    @NonNull
    private final int preferredVectorWidthFloat;
    
    /**
     * See {@link org.jocl.CL#CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE}.
     */
    @NonNull
    private final int preferredVectorWidthDouble;
    
    public OpenCLDevice(
            cl_device_id id,
            String deviceName,
            String deviceVendor,
            String driverVersion,
            String deviceProfile,
            String deviceVersion,
            String deviceExtensions,
            long deviceType,
            int maxComputeUnits,
            long maxWorkItemDimensions,
            long maxWorkItemSizes[],
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
    ) {
        this.id = id;
        this.deviceName = deviceName;
        this.deviceVendor = deviceVendor;
        this.driverVersion = driverVersion;
        this.deviceProfile = deviceProfile;
        this.deviceVersion = deviceVersion;
        this.deviceExtensions = deviceExtensions;
        this.deviceType = deviceType;
        this.maxComputeUnits = maxComputeUnits;
        this.maxWorkItemDimensions = maxWorkItemDimensions;
        this.maxWorkItemSizes = maxWorkItemSizes;
        this.maxWorkGroupSize = maxWorkGroupSize;
        this.maxClockFrequency = maxClockFrequency;
        this.addressBits = addressBits;
        this.maxMemAllocSize = maxMemAllocSize;
        this.globalMemSize = globalMemSize;
        this.errorCorrectionSupport = errorCorrectionSupport;
        this.localMemType = localMemType;
        this.localMemSize = localMemSize;
        this.maxConstantBufferSize = maxConstantBufferSize;
        this.queueProperties = queueProperties;
        this.imageSupport = imageSupport;
        this.maxReadImageArgs = maxReadImageArgs;
        this.maxWriteImageArgs = maxWriteImageArgs;
        this.singleFpConfig = singleFpConfig;
        this.image2dMaxWidth = image2dMaxWidth;
        this.image2dMaxHeight = image2dMaxHeight;
        this.image3dMaxWidth = image3dMaxWidth;
        this.image3dMaxHeight = image3dMaxHeight;
        this.image3dMaxDepth = image3dMaxDepth;
        this.preferredVectorWidthChar = preferredVectorWidthChar;
        this.preferredVectorWidthShort = preferredVectorWidthShort;
        this.preferredVectorWidthInt = preferredVectorWidthInt;
        this.preferredVectorWidthLong = preferredVectorWidthLong;
        this.preferredVectorWidthFloat = preferredVectorWidthFloat;
        this.preferredVectorWidthDouble = preferredVectorWidthDouble;
    }

    public cl_device_id getId() {
        return id;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceVendor() {
        return deviceVendor;
    }

    public String getDriverVersion() {
        return driverVersion;
    }

    public String getDeviceProfile() {
        return deviceProfile;
    }

    public String getDeviceVersion() {
        return deviceVersion;
    }

    public String getDeviceExtensions() {
        return deviceExtensions;
    }

    public long getDeviceType() {
        return deviceType;
    }

    public int getMaxComputeUnits() {
        return maxComputeUnits;
    }

    public long getMaxWorkItemDimensions() {
        return maxWorkItemDimensions;
    }

    public long[] getMaxWorkItemSizes() {
        return maxWorkItemSizes;
    }

    public long getMaxWorkGroupSize() {
        return maxWorkGroupSize;
    }

    public long getMaxClockFrequency() {
        return maxClockFrequency;
    }

    public int getAddressBits() {
        return addressBits;
    }

    public long getMaxMemAllocSize() {
        return maxMemAllocSize;
    }

    public long getGlobalMemSize() {
        return globalMemSize;
    }

    public long getErrorCorrectionSupport() {
        return errorCorrectionSupport;
    }

    public int getLocalMemType() {
        return localMemType;
    }

    public long getLocalMemSize() {
        return localMemSize;
    }

    public long getMaxConstantBufferSize() {
        return maxConstantBufferSize;
    }

    public long getQueueProperties() {
        return queueProperties;
    }

    public int getImageSupport() {
        return imageSupport;
    }

    public int getMaxReadImageArgs() {
        return maxReadImageArgs;
    }

    public int getMaxWriteImageArgs() {
        return maxWriteImageArgs;
    }

    public long getSingleFpConfig() {
        return singleFpConfig;
    }

    public long getImage2dMaxWidth() {
        return image2dMaxWidth;
    }

    public long getImage2dMaxHeight() {
        return image2dMaxHeight;
    }

    public long getImage3dMaxWidth() {
        return image3dMaxWidth;
    }

    public long getImage3dMaxHeight() {
        return image3dMaxHeight;
    }

    public long getImage3dMaxDepth() {
        return image3dMaxDepth;
    }

    public int getPreferredVectorWidthChar() {
        return preferredVectorWidthChar;
    }

    public int getPreferredVectorWidthShort() {
        return preferredVectorWidthShort;
    }

    public int getPreferredVectorWidthInt() {
        return preferredVectorWidthInt;
    }

    public int getPreferredVectorWidthLong() {
        return preferredVectorWidthLong;
    }

    public int getPreferredVectorWidthFloat() {
        return preferredVectorWidthFloat;
    }

    public int getPreferredVectorWidthDouble() {
        return preferredVectorWidthDouble;
    }
    
    public String toStringPretty() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Charset charset = java.nio.charset.StandardCharsets.UTF_8;
        
        try (PrintStream ps = new PrintStream(baos, true, charset)) {
            ps.println("--- Info for device "+deviceName+": ---");
            ps.printf("CL_DEVICE_NAME: \t\t\t%s\n", deviceName);
            ps.printf("CL_DEVICE_VENDOR: \t\t\t%s\n", deviceVendor);
            ps.printf("CL_DRIVER_VERSION: \t\t\t%s\n", driverVersion);
            ps.printf("CL_DEVICE_PROFILE: \t\t\t%s\n", deviceProfile);
            ps.printf("CL_DEVICE_VERSION: \t\t\t%s\n", deviceVersion);
            ps.printf("CL_DEVICE_EXTENSIONS: \t\t\t%s\n", deviceExtensions);
            ps.printf("CL_DEVICE_TYPE:\t\t\t\t%s\n", CL.stringFor_cl_device_type(deviceType));
            ps.printf("CL_DEVICE_MAX_COMPUTE_UNITS:\t\t%d\n", maxComputeUnits);
            ps.printf("CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS:\t%d\n", maxWorkItemDimensions);
            ps.printf("CL_DEVICE_MAX_WORK_ITEM_SIZES:\t\t%d / %d / %d \n",
                maxWorkItemSizes[0], maxWorkItemSizes[1], maxWorkItemSizes[2]);
            ps.printf("CL_DEVICE_MAX_WORK_GROUP_SIZE:\t\t%d\n", maxWorkGroupSize);
            ps.printf("CL_DEVICE_MAX_CLOCK_FREQUENCY:\t\t%d MHz\n", maxClockFrequency);
            ps.printf("CL_DEVICE_ADDRESS_BITS:\t\t\t%d\n", addressBits);
            ps.printf("CL_DEVICE_MAX_MEM_ALLOC_SIZE:\t\t%d MByte\n", (int)(maxMemAllocSize / (1024 * 1024)));
            ps.printf("CL_DEVICE_GLOBAL_MEM_SIZE:\t\t%d MByte\n", (int)(globalMemSize / (1024 * 1024)));
            ps.printf("CL_DEVICE_ERROR_CORRECTION_SUPPORT:\t%s\n", errorCorrectionSupport != 0 ? "yes" : "no");
            ps.printf("CL_DEVICE_LOCAL_MEM_TYPE:\t\t%s\n", CL.stringFor_cl_device_local_mem_type(localMemType));
            ps.printf("CL_DEVICE_LOCAL_MEM_SIZE:\t\t%d KByte\n", (int)(localMemSize / 1024));
            ps.printf("CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE:\t%d KByte\n", (int)(maxConstantBufferSize / 1024));
            ps.printf("CL_DEVICE_QUEUE_PROPERTIES:\t\t%s\n", CL.stringFor_cl_command_queue_properties(queueProperties));
            ps.printf("CL_DEVICE_IMAGE_SUPPORT:\t\t%d\n", imageSupport);
            ps.printf("CL_DEVICE_MAX_READ_IMAGE_ARGS:\t\t%d\n", maxReadImageArgs);
            ps.printf("CL_DEVICE_MAX_WRITE_IMAGE_ARGS:\t\t%d\n", maxWriteImageArgs);
            ps.printf("CL_DEVICE_SINGLE_FP_CONFIG:\t\t%s\n", CL.stringFor_cl_device_fp_config(singleFpConfig));
            ps.printf("CL_DEVICE_2D_MAX_WIDTH\t\t\t%d\n", image2dMaxWidth);
            ps.printf("CL_DEVICE_2D_MAX_HEIGHT\t\t\t%d\n", image2dMaxHeight);
            ps.printf("CL_DEVICE_3D_MAX_WIDTH\t\t\t%d\n", image3dMaxWidth);
            ps.printf("CL_DEVICE_3D_MAX_HEIGHT\t\t\t%d\n", image3dMaxHeight);
            ps.printf("CL_DEVICE_3D_MAX_DEPTH\t\t\t%d\n", image3dMaxDepth);
            ps.printf("CL_DEVICE_PREFERRED_VECTOR_WIDTH_<t>\t");
            ps.printf("CHAR %d, SHORT %d, INT %d, LONG %d, FLOAT %d, DOUBLE %d\n\n\n",
               preferredVectorWidthChar, preferredVectorWidthShort,
               preferredVectorWidthInt, preferredVectorWidthLong,
               preferredVectorWidthFloat, preferredVectorWidthDouble);
        }
        
        String string = new String(baos.toByteArray(), charset);
        return string;
    }
    
    public ComparableVersion getDeviceVersionAsComparableVersion() {
        return getComparableVersionFromDeviceVersion(getDeviceVersion());
    }
    
    public static ComparableVersion getComparableVersionFromDeviceVersion(String deviceVersion) {
        String s = deviceVersion;
        s = s.replace("OpenCL ", "");
        s = s.replace("CUDA", "");
        return new ComparableVersion(s);
    }
}
