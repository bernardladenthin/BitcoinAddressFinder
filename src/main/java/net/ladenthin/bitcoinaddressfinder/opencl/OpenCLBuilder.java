// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClApi;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClDeviceId;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClPlatformId;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.OpenClBinding;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jspecify.annotations.NonNull;
import org.lwjgl.opencl.CL10;

/**
 * Discovers the available OpenCL platforms and devices and wraps them in {@link OpenCLPlatform} /
 * {@link OpenCLDevice} value objects.
 *
 * <p>Every property is read at its exact declared width. That is not pedantry: the OpenCL
 * specification permits a query buffer larger than the property, so reading a 32-bit
 * {@code cl_uint} into a 64-bit slot is legal and used to work only because the previous binding
 * happened to hand the driver a zero-initialised array. Scratch memory here is not zeroed, so an
 * over-wide read would return whatever the upper half of that slot last held.
 */
@ToString
public class OpenCLBuilder {

    /** Number of dimensions reported by {@code CL_DEVICE_MAX_WORK_ITEM_SIZES}. */
    private static final int MAX_WORK_ITEM_SIZE_DIMENSIONS = 3;

    /** The OpenCL version from which this project's compact filter mode is usable. */
    private static final ComparableVersion MINIMUM_COMPACT_MODE_VERSION = new ComparableVersion("2.0");

    /** Value {@code CL_DEVICE_ENDIAN_LITTLE} reports for a little-endian device. */
    private static final int CL_TRUE_AS_INT = 1;

    private final ClApi clApi;

    /**
     * Creates a builder using the project's default OpenCL binding.
     *
     * @see OpenClBinding
     */
    public OpenCLBuilder() {
        this(OpenClBinding.defaultClApi());
    }

    /**
     * Creates a builder on an explicit OpenCL API, so tests can substitute it.
     *
     * @param clApi the OpenCL API to enumerate through
     */
    public OpenCLBuilder(ClApi clApi) {
        this.clApi = clApi;
    }

    /**
     * Discovers and returns every available OpenCL platform with its devices.
     *
     * @return the list of detected OpenCL platforms, empty when none is installed
     */
    public List<OpenCLPlatform> build() {
        final List<ClPlatformId> platformIds = clApi.getPlatformIds();
        final List<OpenCLPlatform> openCLPlatforms = new ArrayList<>(platformIds.size());
        for (ClPlatformId platformId : platformIds) {
            final String platformName = clApi.getPlatformInfoString(platformId, CL10.CL_PLATFORM_NAME);
            final List<ClDeviceId> deviceIds = clApi.getDeviceIds(platformId, CL10.CL_DEVICE_TYPE_ALL);

            final List<OpenCLDevice> openCLDevices = new ArrayList<>(deviceIds.size());
            for (ClDeviceId deviceId : deviceIds) {
                openCLDevices.add(createOpenCLDevice(deviceId));
            }
            openCLPlatforms.add(new OpenCLPlatform(platformId, platformName, ImmutableList.copyOf(openCLDevices)));
        }
        return openCLPlatforms;
    }

    /**
     * Reads every property of one device into an {@link OpenCLDevice}.
     *
     * @param device the device to describe
     * @return the populated value object
     */
    private OpenCLDevice createOpenCLDevice(ClDeviceId device) {
        return new OpenCLDevice(
                device,
                clApi.getDeviceInfoString(device, CL10.CL_DEVICE_NAME),
                clApi.getDeviceInfoString(device, CL10.CL_DEVICE_VENDOR),
                clApi.getDeviceInfoString(device, CL10.CL_DRIVER_VERSION),
                clApi.getDeviceInfoString(device, CL10.CL_DEVICE_PROFILE),
                clApi.getDeviceInfoString(device, CL10.CL_DEVICE_VERSION),
                clApi.getDeviceInfoString(device, CL10.CL_DEVICE_EXTENSIONS),
                clApi.getDeviceInfoLong(device, CL10.CL_DEVICE_TYPE),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_ENDIAN_LITTLE) == CL_TRUE_AS_INT,
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_MAX_COMPUTE_UNITS),
                // cl_uint, widened for the value object; never queried as 64 bits (see class comment)
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS),
                longsToImmutableList(clApi.getDeviceInfoSizes(
                        device, CL10.CL_DEVICE_MAX_WORK_ITEM_SIZES, MAX_WORK_ITEM_SIZE_DIMENSIONS)),
                clApi.getDeviceInfoSizes(device, CL10.CL_DEVICE_MAX_WORK_GROUP_SIZE, 1)[0],
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_MAX_CLOCK_FREQUENCY),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_ADDRESS_BITS),
                clApi.getDeviceInfoLong(device, CL10.CL_DEVICE_MAX_MEM_ALLOC_SIZE),
                clApi.getDeviceInfoLong(device, CL10.CL_DEVICE_GLOBAL_MEM_SIZE),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_ERROR_CORRECTION_SUPPORT),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_LOCAL_MEM_TYPE),
                clApi.getDeviceInfoLong(device, CL10.CL_DEVICE_LOCAL_MEM_SIZE),
                clApi.getDeviceInfoLong(device, CL10.CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE),
                clApi.getDeviceInfoLong(device, CL10.CL_DEVICE_QUEUE_PROPERTIES),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_IMAGE_SUPPORT),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_MAX_READ_IMAGE_ARGS),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_MAX_WRITE_IMAGE_ARGS),
                clApi.getDeviceInfoLong(device, CL10.CL_DEVICE_SINGLE_FP_CONFIG),
                clApi.getDeviceInfoSizes(device, CL10.CL_DEVICE_IMAGE2D_MAX_WIDTH, 1)[0],
                clApi.getDeviceInfoSizes(device, CL10.CL_DEVICE_IMAGE2D_MAX_HEIGHT, 1)[0],
                clApi.getDeviceInfoSizes(device, CL10.CL_DEVICE_IMAGE3D_MAX_WIDTH, 1)[0],
                clApi.getDeviceInfoSizes(device, CL10.CL_DEVICE_IMAGE3D_MAX_HEIGHT, 1)[0],
                clApi.getDeviceInfoSizes(device, CL10.CL_DEVICE_IMAGE3D_MAX_DEPTH, 1)[0],
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT),
                clApi.getDeviceInfoInt(device, CL10.CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE));
    }

    /**
     * Wraps a primitive array into an immutable list.
     *
     * @param array the values to wrap
     * @return an immutable list of the same values
     */
    private static ImmutableList<@NonNull Long> longsToImmutableList(long... array) {
        final ImmutableList.Builder<@NonNull Long> builder = ImmutableList.builderWithExpectedSize(array.length);
        for (long value : array) {
            builder.add(value);
        }
        return builder.build();
    }

    /**
     * Reports whether the native OpenCL library could be loaded.
     *
     * @return {@code true} if the OpenCL runtime is usable on this machine
     */
    public boolean isOpenClLibraryAvailable() {
        try {
            clApi.getPlatformIds();
            return true;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * Checks whether at least one device with OpenCL 2.0 or newer is available.
     *
     * @param openCLPlatforms the platforms to inspect
     * @return {@code true} if at least one OpenCL 2.0+ device exists
     */
    public boolean isOneOpenCL2_0OrGreaterDeviceAvailable(Iterable<OpenCLPlatform> openCLPlatforms) {
        for (OpenCLPlatform openCLPlatform : openCLPlatforms) {
            for (OpenCLDevice openCLDevice : openCLPlatform.openCLDevices()) {
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
    public boolean isOpenCL2_0OrGreater(ComparableVersion openCLDeviceVersion) {
        return openCLDeviceVersion.compareTo(MINIMUM_COMPACT_MODE_VERSION) >= 0;
    }
}
