// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_ADDRESS_BITS;
import static org.jocl.CL.CL_DEVICE_ENDIAN_LITTLE;
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

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_platform_id;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers available OpenCL platforms and devices via JOCL and wraps them in
 * {@link OpenCLPlatform} / {@link OpenCLDevice} objects.
 */
// JOCL upstream API is not annotated for nullness; every clGet*(...) call here
// passes the null values that the OpenCL C ABI accepts (size-only queries, etc.).
// Field.getBoolean(null) is the documented JDK contract for static fields.
@SuppressWarnings({"nullness:argument", "nullness:dereference.of.nullable"})
public class OpenCLBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenCLBuilder.class);

    /** Creates a new {@link OpenCLBuilder}. */
    public OpenCLBuilder() {}

    /** Whether device-info conversions should be formatted for human consumption when querying device info. */
    public static final boolean TRANSFORM_TO_PRINT = true;

    /**
     * Discovers and returns every available OpenCL platform with its devices.
     *
     * @return the list of detected OpenCL platforms
     */
    public List<OpenCLPlatform> build() {
        // Obtain the number of platforms
        int[] numPlatforms = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);
        int numPlatformCount = numPlatforms[0];

        // Obtain the platform IDs
        List<OpenCLPlatform> openCLPlatforms = new ArrayList<>(numPlatformCount);
        cl_platform_id[] platforms = new cl_platform_id[numPlatformCount];
        clGetPlatformIDs(platforms.length, platforms, null);

        for (int i = 0; i < platforms.length; i++) {
            // Collect all devices of all platforms
            final cl_platform_id platformId = platforms[i];

            // Initialize the context properties
            cl_context_properties contextProperties = new cl_context_properties();
            contextProperties.addProperty(CL_CONTEXT_PLATFORM, platformId);

            String platformName = getString(platformId, CL_PLATFORM_NAME);

            // Obtain the number of devices for the current platform
            int[] numDevicesArray = new int[1];
            clGetDeviceIDs(platformId, CL_DEVICE_TYPE_ALL, 0, null, numDevicesArray);
            int numDevices = numDevicesArray[0];

            cl_device_id[] devicesArray = new cl_device_id[numDevices];
            clGetDeviceIDs(platformId, CL_DEVICE_TYPE_ALL, numDevices, devicesArray, null);

            List<OpenCLDevice> openCLDevices = new ArrayList<>(numDevices);
            for (cl_device_id device : devicesArray) {
                OpenCLDevice openCLDevice = createOpenCLDevice(device);
                openCLDevices.add(openCLDevice);
            }

            OpenCLPlatform openCLPlatform =
                    new OpenCLPlatform(platformName, contextProperties, ImmutableList.copyOf(openCLDevices));
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
        boolean endianLittle = getInt(device, CL_DEVICE_ENDIAN_LITTLE) == 1;
        long deviceType = getLong(device, CL_DEVICE_TYPE);
        int maxComputeUnits = getInt(device, CL_DEVICE_MAX_COMPUTE_UNITS);
        long maxWorkItemDimensions = getLong(device, CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS);
        long[] maxWorkItemSizes = getSizes(device, CL_DEVICE_MAX_WORK_ITEM_SIZES, 3);
        long maxWorkGroupSize = getSize(device, CL_DEVICE_MAX_WORK_GROUP_SIZE);
        long maxClockFrequency = getLong(device, CL_DEVICE_MAX_CLOCK_FREQUENCY);
        int addressBits = getInt(device, CL_DEVICE_ADDRESS_BITS);
        long maxMemAllocSize = getLong(device, CL_DEVICE_MAX_MEM_ALLOC_SIZE);
        long globalMemSize = getLong(device, CL_DEVICE_GLOBAL_MEM_SIZE);
        int errorCorrectionSupport = getInt(device, CL_DEVICE_ERROR_CORRECTION_SUPPORT);
        int localMemType = getInt(device, CL_DEVICE_LOCAL_MEM_TYPE);
        long localMemSize = getLong(device, CL_DEVICE_LOCAL_MEM_SIZE);
        long maxConstantBufferSize = getLong(device, CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE);
        // JOCL upstream deprecated CL_DEVICE_QUEUE_PROPERTIES (OpenCL 2.0+ replaced it with
        // CL_DEVICE_QUEUE_ON_HOST_PROPERTIES); JOCL has not exposed the replacement constant
        // yet. Suppress narrowly so any other deprecation in this method still surfaces.
        @SuppressWarnings("deprecation")
        final int queuePropertiesConstant = CL_DEVICE_QUEUE_PROPERTIES;
        long queueProperties = getLong(device, queuePropertiesConstant);
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

        return new OpenCLDevice(
                device,
                deviceName,
                deviceVendor,
                driverVersion,
                deviceProfile,
                deviceVersion,
                deviceExtensions,
                deviceType,
                endianLittle,
                maxComputeUnits,
                maxWorkItemDimensions,
                longsToImmutableList(maxWorkItemSizes),
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
                preferredVectorWidthDouble);
    }

    private static ImmutableList<@NonNull Long> longsToImmutableList(long... array) {
        ImmutableList.Builder<@NonNull Long> b = ImmutableList.builderWithExpectedSize(array.length);
        for (long l : array) {
            b.add(l);
        }
        return b.build();
    }

    /**
     * Reports whether the JOCL native OpenCL library has been loaded.
     *
     * <p>Reflects the internal {@code org.jocl.CL.nativeLibraryLoaded} flag,
     * which JOCL sets to {@code true} after a successful first call into the
     * native library and never resets. Treats any
     * {@link UnsatisfiedLinkError} / {@link NoClassDefFoundError} during
     * the reflective lookup as "not loaded".
     *
     * @return {@code true} if the native library was loaded successfully and is usable
     */
    public static boolean isOpenClNativeLibraryLoaded() {
        try {
            Class.forName("org.jocl.CL");
            Field field = org.jocl.CL.class.getDeclaredField("nativeLibraryLoaded");
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (java.lang.UnsatisfiedLinkError e) {
            return false;
        } catch (java.lang.NoClassDefFoundError e) {
            return false;
        } catch (Exception e) {
            LOGGER.error("OpenCL native library probe failed", e);
            return false;
        }
    }

    /**
     * Checks whether at least one device with OpenCL 2.0 or newer is available.
     *
     * @param openCLPlatforms the platforms to inspect
     * @return {@code true} if at least one OpenCL 2.0+ device exists
     */
    public static boolean isOneOpenCL2_0OrGreaterDeviceAvailable(Iterable<OpenCLPlatform> openCLPlatforms) {
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

    /**
     * Checks whether the supplied OpenCL device version is at least 2.0.
     *
     * @param openCLDeviceVersion the parsed device version
     * @return {@code true} if {@code openCLDeviceVersion >= 2.0}
     */
    public static boolean isOpenCL2_0OrGreater(ComparableVersion openCLDeviceVersion) {
        final ComparableVersion v2_0 = new ComparableVersion("2.0");
        return openCLDeviceVersion.compareTo(v2_0) >= 0;
    }

    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static int getInt(cl_device_id device, int paramName) {
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
    private static int[] getInts(cl_device_id device, int paramName, int numValues) {
        int[] values = new int[numValues];
        clGetDeviceInfo(device, paramName, (long) Sizeof.cl_int * numValues, Pointer.to(values), null);
        return values;
    }

    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static long getLong(cl_device_id device, int paramName) {
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
    private static long[] getLongs(cl_device_id device, int paramName, int numValues) {
        long[] values = new long[numValues];
        clGetDeviceInfo(device, paramName, (long) Sizeof.cl_long * numValues, Pointer.to(values), null);
        return values;
    }

    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static String getString(cl_device_id device, int paramName) {
        // Obtain the length of the string that will be queried
        long[] size = new long[1];
        clGetDeviceInfo(device, paramName, 0, null, size);

        // Create a buffer of the appropriate size and fill it with the info
        byte[] buffer = new byte[(int) size[0]];
        clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);

        // Create a string from the buffer (excluding the trailing \0 byte)
        return new String(buffer, 0, buffer.length - 1, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Returns the value of the platform info parameter with the given name
     *
     * @param platform The platform
     * @param paramName The parameter name
     * @return The value
     */
    private static String getString(cl_platform_id platform, int paramName) {
        // Obtain the length of the string that will be queried
        long[] size = new long[1];
        clGetPlatformInfo(platform, paramName, 0, null, size);

        // Create a buffer of the appropriate size and fill it with the info
        byte[] buffer = new byte[(int) size[0]];
        clGetPlatformInfo(platform, paramName, buffer.length, Pointer.to(buffer), null);

        // Create a string from the buffer (excluding the trailing \0 byte)
        return new String(buffer, 0, buffer.length - 1, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Returns the value of the device info parameter with the given name
     *
     * @param device The device
     * @param paramName The parameter name
     * @return The value
     */
    private static long getSize(cl_device_id device, int paramName) {
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
    static long[] getSizes(cl_device_id device, int paramName, int numValues) {
        // The size of the returned data has to depend on
        // the size of a size_t, which is handled here
        long size = (long) numValues * Sizeof.size_t;
        ByteBuffer buffer = ByteBuffer.allocate(ByteBufferUtility.ensureByteBufferCapacityFitsInt(size))
                .order(ByteOrder.nativeOrder());
        clGetDeviceInfo(device, paramName, size, Pointer.to(buffer), null);
        long[] values = new long[numValues];
        if (Sizeof.size_t == 4) {
            for (int i = 0; i < numValues; i++) {
                values[i] = buffer.getInt(i * Sizeof.size_t);
            }
        } else {
            for (int i = 0; i < numValues; i++) {
                values[i] = buffer.getLong(i * Sizeof.size_t);
            }
        }
        return values;
    }
}
