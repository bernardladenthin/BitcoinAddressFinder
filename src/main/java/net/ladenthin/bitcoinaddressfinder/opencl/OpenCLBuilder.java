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

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import org.apache.maven.artifact.versioning.ComparableVersion;
import static org.jocl.CL.CL_DEVICE_ADDRESS_BITS;
import static org.jocl.CL.CL_DEVICE_ERROR_CORRECTION_SUPPORT;
import static org.jocl.CL.CL_DEVICE_EXTENSIONS;
import static org.jocl.CL.CL_DEVICE_GLOBAL_MEM_SIZE;
import static org.jocl.CL.CL_DEVICE_IMAGE2D_MAX_HEIGHT;
import static org.jocl.CL.CL_DEVICE_IMAGE2D_MAX_WIDTH;
import static org.jocl.CL.CL_DEVICE_IMAGE3D_MAX_DEPTH;
import static org.jocl.CL.CL_DEVICE_IMAGE3D_MAX_HEIGHT;
import static org.jocl.CL.CL_DEVICE_IMAGE3D_MAX_WIDTH;
import static org.jocl.CL.CL_DEVICE_IMAGE_SUPPORT;
import static org.jocl.CL.CL_DEVICE_LOCAL_MEM_SIZE;
import static org.jocl.CL.CL_DEVICE_LOCAL_MEM_TYPE;
import static org.jocl.CL.CL_DEVICE_MAX_CLOCK_FREQUENCY;
import static org.jocl.CL.CL_DEVICE_MAX_COMPUTE_UNITS;
import static org.jocl.CL.CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE;
import static org.jocl.CL.CL_DEVICE_MAX_MEM_ALLOC_SIZE;
import static org.jocl.CL.CL_DEVICE_MAX_READ_IMAGE_ARGS;
import static org.jocl.CL.CL_DEVICE_MAX_WORK_GROUP_SIZE;
import static org.jocl.CL.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS;
import static org.jocl.CL.CL_DEVICE_MAX_WORK_ITEM_SIZES;
import static org.jocl.CL.CL_DEVICE_MAX_WRITE_IMAGE_ARGS;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR;
import static org.jocl.CL.CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE;
import static org.jocl.CL.CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT;
import static org.jocl.CL.CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT;
import static org.jocl.CL.CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG;
import static org.jocl.CL.CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT;
import static org.jocl.CL.CL_DEVICE_PROFILE;
import static org.jocl.CL.CL_DEVICE_QUEUE_PROPERTIES;
import static org.jocl.CL.CL_DEVICE_SINGLE_FP_CONFIG;
import static org.jocl.CL.CL_DEVICE_TYPE;
import static org.jocl.CL.CL_DEVICE_TYPE_ALL;
import static org.jocl.CL.CL_DEVICE_VENDOR;
import static org.jocl.CL.CL_DEVICE_VERSION;
import static org.jocl.CL.CL_DRIVER_VERSION;
import static org.jocl.CL.CL_PLATFORM_NAME;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clGetPlatformInfo;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_device_id;
import org.jocl.cl_platform_id;

public class OpenCLBuilder {
    
    public static boolean TRANSFORM_TO_PRINT = true;
    
    public List<OpenCLPlatform> build() {
        // Obtain the number of platforms
        int numPlatforms[] = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);

        // Obtain the platform IDs
        List<OpenCLPlatform> openCLPlatforms = new ArrayList<>();
        cl_platform_id platforms[] = new cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);

        for (int i=0; i<platforms.length; i++) {
            // Collect all devices of all platforms
            final cl_platform_id platformId = platforms[i];

            String platformName = getString(platformId, CL_PLATFORM_NAME);
            
            // Obtain the number of devices for the current platform
            int numDevices[] = new int[1];
            clGetDeviceIDs(platformId, CL_DEVICE_TYPE_ALL, 0, null, numDevices);
            
            cl_device_id devicesArray[] = new cl_device_id[numDevices[0]];
            clGetDeviceIDs(platformId, CL_DEVICE_TYPE_ALL, numDevices[0], devicesArray, null);

            List<OpenCLDevice> openCLDevices = new ArrayList<>();
            for (cl_device_id device : devicesArray) {
                OpenCLDevice openCLDevice = createOpenCLDevice(device);
                openCLDevices.add(openCLDevice);
            }
            
            OpenCLPlatform openCLPlatform = new OpenCLPlatform(platformName, openCLDevices);
            openCLPlatforms.add(openCLPlatform);
        }
        
        return openCLPlatforms;
    }

    private OpenCLDevice createOpenCLDevice(cl_device_id device) {
        String deviceName = getString(device, CL_DEVICE_NAME);
        String deviceVendor = getString(device, CL_DEVICE_VENDOR);
        String driverVersion = getString(device, CL_DRIVER_VERSION);
        String deviceProfile = getString(device, CL_DEVICE_PROFILE);
        String deviceVersion = getString(device, CL_DEVICE_VERSION);
        String deviceExtensions = getString(device, CL_DEVICE_EXTENSIONS);
        long deviceType = getLong(device, CL_DEVICE_TYPE);
        int maxComputeUnits = getInt(device, CL_DEVICE_MAX_COMPUTE_UNITS);
        long maxWorkItemDimensions = getLong(device, CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS);
        long maxWorkItemSizes[] = getSizes(device, CL_DEVICE_MAX_WORK_ITEM_SIZES, 3);
        long maxWorkGroupSize = getSize(device, CL_DEVICE_MAX_WORK_GROUP_SIZE);
        long maxClockFrequency = getLong(device, CL_DEVICE_MAX_CLOCK_FREQUENCY);
        int addressBits = getInt(device, CL_DEVICE_ADDRESS_BITS);
        long maxMemAllocSize = getLong(device, CL_DEVICE_MAX_MEM_ALLOC_SIZE);
        long globalMemSize = getLong(device, CL_DEVICE_GLOBAL_MEM_SIZE);
        int errorCorrectionSupport = getInt(device, CL_DEVICE_ERROR_CORRECTION_SUPPORT);
        int localMemType = getInt(device, CL_DEVICE_LOCAL_MEM_TYPE);
        long localMemSize = getLong(device, CL_DEVICE_LOCAL_MEM_SIZE);
        long maxConstantBufferSize = getLong(device, CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE);
        long queueProperties = getLong(device, CL_DEVICE_QUEUE_PROPERTIES);
        int imageSupport = getInt(device, CL_DEVICE_IMAGE_SUPPORT);
        int maxReadImageArgs = getInt(device, CL_DEVICE_MAX_READ_IMAGE_ARGS);
        int maxWriteImageArgs = getInt(device, CL_DEVICE_MAX_WRITE_IMAGE_ARGS);
        long singleFpConfig = getLong(device, CL_DEVICE_SINGLE_FP_CONFIG);
        long image2dMaxWidth = getSize(device, CL_DEVICE_IMAGE2D_MAX_WIDTH);
        long image2dMaxHeight = getSize(device, CL_DEVICE_IMAGE2D_MAX_HEIGHT);
        long image3dMaxWidth = getSize(device, CL_DEVICE_IMAGE3D_MAX_WIDTH);
        long image3dMaxHeight = getSize(device, CL_DEVICE_IMAGE3D_MAX_HEIGHT);
        long image3dMaxDepth = getSize(device, CL_DEVICE_IMAGE3D_MAX_DEPTH);
        int preferredVectorWidthChar = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR);
        int preferredVectorWidthShort = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT);
        int preferredVectorWidthInt = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT);
        int preferredVectorWidthLong = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG);
        int preferredVectorWidthFloat = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT);
        int preferredVectorWidthDouble = getInt(device, CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE);
        
        OpenCLDevice openCLDevice = new OpenCLDevice(
                deviceName,
                deviceVendor,
                driverVersion,
                deviceProfile,
                deviceVersion,
                deviceExtensions,
                deviceType,
                maxComputeUnits,
                maxWorkItemDimensions,
                maxWorkItemSizes,
                maxWorkGroupSize,
                maxClockFrequency,
                addressBits,
                maxMemAllocSize,
                globalMemSize,
                errorCorrectionSupport,
                localMemType,
                localMemSize,
                maxConstantBufferSize,
                queueProperties,
                imageSupport,
                maxReadImageArgs,
                maxWriteImageArgs,
                singleFpConfig,
                image2dMaxWidth,
                image2dMaxHeight,
                image3dMaxWidth,
                image3dMaxHeight,
                image3dMaxDepth,
                preferredVectorWidthChar,
                preferredVectorWidthShort,
                preferredVectorWidthInt,
                preferredVectorWidthLong,
                preferredVectorWidthFloat,
                preferredVectorWidthDouble
        );
        
        return openCLDevice;
    }
    
    
    public static boolean isOpenCLnativeLibraryLoadable() {
        try {
            Class.forName("org.jocl.CL");
            Field field = org.jocl.CL.class.getDeclaredField("nativeLibraryLoaded");
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch(java.lang.UnsatisfiedLinkError e) {
            return false;
        } catch (java.lang.NoClassDefFoundError e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean isOneOpenCL2_0OrGreaterDeviceAvailable(List<OpenCLPlatform> openCLPlatforms) {
        for (OpenCLPlatform openCLPlatform : openCLPlatforms) {
            List<OpenCLDevice> openCLDevices = openCLPlatform.openCLDevices();
            for (OpenCLDevice openCLDevice : openCLDevices) {
                if (isOpenCL2_0OrGreater(openCLDevice.getDeviceVersionAsComparableVersion())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isOpenCL2_0OrGreater(ComparableVersion openCLDeviceVersion) {
        final ComparableVersion v2_0 = new ComparableVersion("2.0");
        if (openCLDeviceVersion.compareTo(v2_0) >= 0) {
            return true;
        }
        return false;
    }
    
    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static int getInt(cl_device_id device, int paramName)
    {
        return getInts(device, paramName, 1)[0];
    }

    /**
     * Returns the values of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @param numValues The number of values
     * @return The value
     */
    private static int[] getInts(cl_device_id device, int paramName, int numValues)
    {
        int values[] = new int[numValues];
        clGetDeviceInfo(device, paramName, (long)Sizeof.cl_int * numValues, Pointer.to(values), null);
        return values;
    }

    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static long getLong(cl_device_id device, int paramName)
    {
        return getLongs(device, paramName, 1)[0];
    }

    /**
     * Returns the values of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @param numValues The number of values
     * @return The value
     */
    private static long[] getLongs(cl_device_id device, int paramName, int numValues)
    {
        long values[] = new long[numValues];
        clGetDeviceInfo(device, paramName, (long)Sizeof.cl_long * numValues, Pointer.to(values), null);
        return values;
    }

    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static String getString(cl_device_id device, int paramName)
    {
        // Obtain the length of the string that will be queried
        long size[] = new long[1];
        clGetDeviceInfo(device, paramName, 0, null, size);

        // Create a buffer of the appropriate size and fill it with the info
        byte buffer[] = new byte[(int)size[0]];
        clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);

        // Create a string from the buffer (excluding the trailing \0 byte)
        return new String(buffer, 0, buffer.length-1);
    }

    /**
     * Returns the value of the platform info parameter with the given name
     *
     * @param platform The platform
     * @param paramName The parameter name
     * @return The value
     */
    private static String getString(cl_platform_id platform, int paramName)
    {
        // Obtain the length of the string that will be queried
        long size[] = new long[1];
        clGetPlatformInfo(platform, paramName, 0, null, size);

        // Create a buffer of the appropriate size and fill it with the info
        byte buffer[] = new byte[(int)size[0]];
        clGetPlatformInfo(platform, paramName, buffer.length, Pointer.to(buffer), null);

        // Create a string from the buffer (excluding the trailing \0 byte)
        return new String(buffer, 0, buffer.length-1);
    }
    
    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static long getSize(cl_device_id device, int paramName)
    {
        return getSizes(device, paramName, 1)[0];
    }
    
    /**
     * Returns the values of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @param numValues The number of values
     * @return The value
     */
    static long[] getSizes(cl_device_id device, int paramName, int numValues)
    {
        // The size of the returned data has to depend on 
        // the size of a size_t, which is handled here
        long size = (long)numValues * Sizeof.size_t;
        ByteBuffer buffer = ByteBuffer.allocate(
            ByteBufferUtility.ensureByteBufferCapacityFitsInt(size)).order(ByteOrder.nativeOrder());
        clGetDeviceInfo(device, paramName, size, 
            Pointer.to(buffer), null);
        long values[] = new long[numValues];
        if (Sizeof.size_t == 4)
        {
            for (int i=0; i<numValues; i++)
            {
                values[i] = buffer.getInt(i * Sizeof.size_t);
            }
        }
        else
        {
            for (int i=0; i<numValues; i++)
            {
                values[i] = buffer.getLong(i * Sizeof.size_t);
            }
        }
        return values;
    }
}
