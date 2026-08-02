// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClDeviceId;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClDeviceInfoFormatter;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jspecify.annotations.NonNull;

/**
 * Represents an OpenCL device and its properties.
 *
 * @param device                     the device handle
 * @param deviceName                 See {@code CL_DEVICE_NAME}
 * @param deviceVendor               See {@code CL_DEVICE_VENDOR}
 * @param driverVersion              See {@code CL_DRIVER_VERSION}
 * @param deviceProfile              See {@code CL_DEVICE_PROFILE}
 * @param deviceVersion              See {@code CL_DEVICE_VERSION}
 * @param deviceExtensions           See {@code CL_DEVICE_EXTENSIONS}
 * @param deviceType                 See {@code CL_DEVICE_TYPE}
 * @param endianLittle               See {@code CL_DEVICE_ENDIAN_LITTLE}
 * @param maxComputeUnits            See {@code CL_DEVICE_MAX_COMPUTE_UNITS}
 * @param maxWorkItemDimensions      See {@code CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS}
 * @param maxWorkItemSizes           See {@code CL_DEVICE_MAX_WORK_ITEM_SIZES}
 * @param maxWorkGroupSize           See {@code CL_DEVICE_MAX_WORK_GROUP_SIZE}
 * @param maxClockFrequency          See {@code CL_DEVICE_MAX_CLOCK_FREQUENCY}
 * @param addressBits                See {@code CL_DEVICE_ADDRESS_BITS}
 * @param maxMemAllocSize            See {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE}
 * @param globalMemSize              See {@code CL_DEVICE_GLOBAL_MEM_SIZE}
 * @param errorCorrectionSupport     See {@code CL_DEVICE_ERROR_CORRECTION_SUPPORT}
 * @param localMemType               See {@code CL_DEVICE_LOCAL_MEM_TYPE}
 * @param localMemSize               See {@code CL_DEVICE_LOCAL_MEM_SIZE}
 * @param maxConstantBufferSize      See {@code CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE}
 * @param queueProperties            See {@code CL_DEVICE_QUEUE_PROPERTIES}
 * @param imageSupport               See {@code CL_DEVICE_IMAGE_SUPPORT}
 * @param maxReadImageArgs           See {@code CL_DEVICE_MAX_READ_IMAGE_ARGS}
 * @param maxWriteImageArgs          See {@code CL_DEVICE_MAX_WRITE_IMAGE_ARGS}
 * @param singleFpConfig             See {@code CL_DEVICE_SINGLE_FP_CONFIG}
 * @param image2dMaxWidth            See {@code CL_DEVICE_IMAGE2D_MAX_WIDTH}
 * @param image2dMaxHeight           See {@code CL_DEVICE_IMAGE2D_MAX_HEIGHT}
 * @param image3dMaxWidth            See {@code CL_DEVICE_IMAGE3D_MAX_WIDTH}
 * @param image3dMaxHeight           See {@code CL_DEVICE_IMAGE3D_MAX_HEIGHT}
 * @param image3dMaxDepth            See {@code CL_DEVICE_IMAGE3D_MAX_DEPTH}
 * @param preferredVectorWidthChar   See {@code CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR}
 * @param preferredVectorWidthShort  See {@code CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT}
 * @param preferredVectorWidthInt    See {@code CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT}
 * @param preferredVectorWidthLong   See {@code CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG}
 * @param preferredVectorWidthFloat  See {@code CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT}
 * @param preferredVectorWidthDouble See {@code CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE}
 */
@Immutable
public record OpenCLDevice(
        ClDeviceId device,
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
        ImmutableList<@NonNull Long> maxWorkItemSizes,
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
        int preferredVectorWidthDouble)
        implements Serializable {

    /**
     * Renders the OpenCL bit fields in {@link #toStringPretty()}. A shared instance: the formatter is
     * stateless, and a record component would make it part of this device's identity.
     */
    @SuppressWarnings("Immutable")
    private static final ClDeviceInfoFormatter FORMATTER = new ClDeviceInfoFormatter();

    /**
     * Renders the device handle for the human-readable dump.
     *
     * <p>Formatted as hexadecimal rather than as the record's own {@code toString()}: the value is a
     * native pointer, which is how every driver tool and log prints it.
     *
     * @return the handle as {@code cl_device_id[0x...]}
     */
    private String formatHandle() {
        return String.format("cl_device_id[0x%x]", device.handle());
    }

    /**
     * Returns the byte order this device uses.
     *
     * @return {@link ByteOrder#LITTLE_ENDIAN} or {@link ByteOrder#BIG_ENDIAN}
     */
    public ByteOrder getByteOrder() {
        if (endianLittle) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        return ByteOrder.BIG_ENDIAN;
    }

    /**
     * Returns a human-readable, multi-line description of this device.
     *
     * @return a multi-line string describing this OpenCL device
     */
    public String toStringPretty() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Charset charset = java.nio.charset.StandardCharsets.UTF_8;

        try (PrintStream ps = new PrintStream(baos, true, charset)) {
            ps.println("--- Info for OpenCL device: " + deviceName + " ---");
            ps.printf("cl_device_id:                          %s%n", formatHandle());
            ps.printf("CL_DEVICE_NAME:                        %s%n", deviceName);
            ps.printf("CL_DEVICE_VENDOR:                      %s%n", deviceVendor);
            ps.printf("CL_DRIVER_VERSION:                     %s%n", driverVersion);
            ps.printf("CL_DEVICE_PROFILE:                     %s%n", deviceProfile);
            ps.printf("CL_DEVICE_VERSION:                     %s%n", deviceVersion);
            ps.printf("CL_DEVICE_EXTENSIONS:                  %s%n", deviceExtensions);
            ps.printf("CL_DEVICE_TYPE:                        %s%n", FORMATTER.formatDeviceType(deviceType));
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
            ps.printf("CL_DEVICE_LOCAL_MEM_TYPE:              %s%n", FORMATTER.formatLocalMemType(localMemType));
            ps.printf("CL_DEVICE_LOCAL_MEM_SIZE:              %d KByte%n", localMemSize / 1024);
            ps.printf("CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE:    %d KByte%n", maxConstantBufferSize / 1024);
            ps.printf(
                    "CL_DEVICE_QUEUE_PROPERTIES:            %s%n",
                    FORMATTER.formatCommandQueueProperties(queueProperties));
            ps.printf("CL_DEVICE_IMAGE_SUPPORT:               %d%n", imageSupport);
            ps.printf("CL_DEVICE_MAX_READ_IMAGE_ARGS:         %d%n", maxReadImageArgs);
            ps.printf("CL_DEVICE_MAX_WRITE_IMAGE_ARGS:        %d%n", maxWriteImageArgs);
            ps.printf("CL_DEVICE_SINGLE_FP_CONFIG:            %s%n", FORMATTER.formatDeviceFpConfig(singleFpConfig));
            ps.printf("CL_DEVICE_IMAGE2D_MAX_WIDTH:           %d%n", image2dMaxWidth);
            ps.printf("CL_DEVICE_IMAGE2D_MAX_HEIGHT:          %d%n", image2dMaxHeight);
            ps.printf("CL_DEVICE_IMAGE3D_MAX_WIDTH:           %d%n", image3dMaxWidth);
            ps.printf("CL_DEVICE_IMAGE3D_MAX_HEIGHT:          %d%n", image3dMaxHeight);
            ps.printf("CL_DEVICE_IMAGE3D_MAX_DEPTH:           %d%n", image3dMaxDepth);
            ps.printf(
                    "CL_DEVICE_PREFERRED_VECTOR_WIDTHS:     CHAR %d, SHORT %d, INT %d, LONG %d, FLOAT %d, DOUBLE %d%n",
                    preferredVectorWidthChar,
                    preferredVectorWidthShort,
                    preferredVectorWidthInt,
                    preferredVectorWidthLong,
                    preferredVectorWidthFloat,
                    preferredVectorWidthDouble);

            // A quick starting-point config derived purely from the device info above (no benchmark
            // run) — a rough assumption, "better than the keysPerWorkItem=1 default". Sweep
            // keysPerWorkItem on the real hardware to find the actual optimum (see docs/performance.md).
            final OpenClConfigSuggestion suggestion = OpenClConfigSuggestion.suggest(maxComputeUnits, maxMemAllocSize);
            ps.printf("SUGGESTED START CONFIG (heuristic from the info above; sweep keysPerWorkItem to confirm):%n");
            ps.printf("    producerOpenCL.batchSizeInBits = %d%n", suggestion.batchSizeInBits());
            ps.printf("    producerOpenCL.keysPerWorkItem = %d%n", suggestion.keysPerWorkItem());
        }

        return baos.toString(charset);
    }

    /**
     * Formats a list of maximum work-item sizes as a slash-separated string.
     *
     * @param sizes the work-item sizes
     * @return the formatted representation
     */
    public static String formatWorkItemSizes(List<Long> sizes) {
        if (sizes.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sizes.size(); i++) {
            sb.append(sizes.get(i));
            if (i < sizes.size() - 1) sb.append(" / ");
        }
        return sb.toString();
    }

    /**
     * Returns the device's OpenCL version as a {@link ComparableVersion}.
     *
     * @return the parsed device version
     */
    public ComparableVersion getDeviceVersionAsComparableVersion() {
        return getComparableVersionFromDeviceVersion(deviceVersion());
    }

    /**
     * Parses a {@code CL_DEVICE_VERSION} string into a {@link ComparableVersion}, stripping the {@code OpenCL} and {@code CUDA} prefixes.
     *
     * @param deviceVersion the raw device version string
     * @return the parsed comparable version
     */
    public static ComparableVersion getComparableVersionFromDeviceVersion(String deviceVersion) {
        String s = deviceVersion;
        s = s.replace("OpenCL ", "");
        s = s.replace("CUDA", "");
        return new ComparableVersion(s.trim());
    }
}
