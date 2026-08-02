// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.ToString;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL11;
import org.lwjgl.opencl.CL12;

/**
 * Translates {@code cl_int} error codes into their symbolic OpenCL names.
 *
 * <p>The LWJGL binding exposes the error constants but offers no reverse lookup, so a failed call
 * would otherwise be reported as a bare negative number. Resolving {@code -30} to
 * {@code CL_INVALID_VALUE} is the difference between a log line that can be acted upon and one that
 * has to be looked up in the specification first.
 *
 * <p>The numeric values are never spelled out here — every entry references the corresponding LWJGL
 * constant, so the mapping cannot drift away from the binding it describes.
 *
 * <p>An instance class rather than a static utility, per the project convention on helper classes:
 * collaborators receive it through the constructor and can substitute it in tests.
 */
@ToString
public class ClErrorCodeResolver {

    /** The OpenCL success code. Every entry point returns this when the call succeeded. */
    public static final int CL_SUCCESS = CL10.CL_SUCCESS;

    /** Number of entries in {@link #ERROR_NAMES_BY_CODE}; used only to presize the map. */
    private static final int ERROR_NAME_COUNT = 54;

    /**
     * Symbolic names of every error code defined by OpenCL 1.0 through 1.2, which is the range the
     * project's kernels and host code are built against ({@code -cl-std=CL1.2}). Codes outside this
     * range fall back to a numeric-only description.
     *
     * <p>Deliberately populated through a static block with explicit {@code put} calls rather than
     * {@code Map.ofEntries(...)}: the latter forced the Checker Framework into roughly a minute of
     * generic type inference for this many entries, which it reported as {@code [slow.typechecking]}
     * and {@code -Werror} then turned into a build failure.
     */
    private static final Map<Integer, String> ERROR_NAMES_BY_CODE;

    static {
        // Presized: the table below is a fixed set, so let it start at its final capacity.
        final Map<Integer, String> names = new HashMap<>(ERROR_NAME_COUNT * 2);
        names.put(CL10.CL_SUCCESS, "CL_SUCCESS");
        names.put(CL10.CL_DEVICE_NOT_FOUND, "CL_DEVICE_NOT_FOUND");
        names.put(CL10.CL_DEVICE_NOT_AVAILABLE, "CL_DEVICE_NOT_AVAILABLE");
        names.put(CL10.CL_COMPILER_NOT_AVAILABLE, "CL_COMPILER_NOT_AVAILABLE");
        names.put(CL10.CL_MEM_OBJECT_ALLOCATION_FAILURE, "CL_MEM_OBJECT_ALLOCATION_FAILURE");
        names.put(CL10.CL_OUT_OF_RESOURCES, "CL_OUT_OF_RESOURCES");
        names.put(CL10.CL_OUT_OF_HOST_MEMORY, "CL_OUT_OF_HOST_MEMORY");
        names.put(CL10.CL_PROFILING_INFO_NOT_AVAILABLE, "CL_PROFILING_INFO_NOT_AVAILABLE");
        names.put(CL10.CL_MEM_COPY_OVERLAP, "CL_MEM_COPY_OVERLAP");
        names.put(CL10.CL_IMAGE_FORMAT_MISMATCH, "CL_IMAGE_FORMAT_MISMATCH");
        names.put(CL10.CL_IMAGE_FORMAT_NOT_SUPPORTED, "CL_IMAGE_FORMAT_NOT_SUPPORTED");
        names.put(CL10.CL_BUILD_PROGRAM_FAILURE, "CL_BUILD_PROGRAM_FAILURE");
        names.put(CL10.CL_MAP_FAILURE, "CL_MAP_FAILURE");
        names.put(CL11.CL_MISALIGNED_SUB_BUFFER_OFFSET, "CL_MISALIGNED_SUB_BUFFER_OFFSET");
        names.put(CL11.CL_EXEC_STATUS_ERROR_FOR_EVENTS_IN_WAIT_LIST, "CL_EXEC_STATUS_ERROR_FOR_EVENTS_IN_WAIT_LIST");
        names.put(CL12.CL_COMPILE_PROGRAM_FAILURE, "CL_COMPILE_PROGRAM_FAILURE");
        names.put(CL12.CL_LINKER_NOT_AVAILABLE, "CL_LINKER_NOT_AVAILABLE");
        names.put(CL12.CL_LINK_PROGRAM_FAILURE, "CL_LINK_PROGRAM_FAILURE");
        names.put(CL12.CL_DEVICE_PARTITION_FAILED, "CL_DEVICE_PARTITION_FAILED");
        names.put(CL12.CL_KERNEL_ARG_INFO_NOT_AVAILABLE, "CL_KERNEL_ARG_INFO_NOT_AVAILABLE");
        names.put(CL10.CL_INVALID_VALUE, "CL_INVALID_VALUE");
        names.put(CL10.CL_INVALID_DEVICE_TYPE, "CL_INVALID_DEVICE_TYPE");
        names.put(CL10.CL_INVALID_PLATFORM, "CL_INVALID_PLATFORM");
        names.put(CL10.CL_INVALID_DEVICE, "CL_INVALID_DEVICE");
        names.put(CL10.CL_INVALID_CONTEXT, "CL_INVALID_CONTEXT");
        names.put(CL10.CL_INVALID_QUEUE_PROPERTIES, "CL_INVALID_QUEUE_PROPERTIES");
        names.put(CL10.CL_INVALID_COMMAND_QUEUE, "CL_INVALID_COMMAND_QUEUE");
        names.put(CL10.CL_INVALID_HOST_PTR, "CL_INVALID_HOST_PTR");
        names.put(CL10.CL_INVALID_MEM_OBJECT, "CL_INVALID_MEM_OBJECT");
        names.put(CL10.CL_INVALID_BINARY, "CL_INVALID_BINARY");
        names.put(CL10.CL_INVALID_BUILD_OPTIONS, "CL_INVALID_BUILD_OPTIONS");
        names.put(CL10.CL_INVALID_PROGRAM, "CL_INVALID_PROGRAM");
        names.put(CL10.CL_INVALID_PROGRAM_EXECUTABLE, "CL_INVALID_PROGRAM_EXECUTABLE");
        names.put(CL10.CL_INVALID_KERNEL_NAME, "CL_INVALID_KERNEL_NAME");
        names.put(CL10.CL_INVALID_KERNEL_DEFINITION, "CL_INVALID_KERNEL_DEFINITION");
        names.put(CL10.CL_INVALID_KERNEL, "CL_INVALID_KERNEL");
        names.put(CL10.CL_INVALID_ARG_INDEX, "CL_INVALID_ARG_INDEX");
        names.put(CL10.CL_INVALID_ARG_VALUE, "CL_INVALID_ARG_VALUE");
        names.put(CL10.CL_INVALID_ARG_SIZE, "CL_INVALID_ARG_SIZE");
        names.put(CL10.CL_INVALID_KERNEL_ARGS, "CL_INVALID_KERNEL_ARGS");
        names.put(CL10.CL_INVALID_WORK_DIMENSION, "CL_INVALID_WORK_DIMENSION");
        names.put(CL10.CL_INVALID_WORK_GROUP_SIZE, "CL_INVALID_WORK_GROUP_SIZE");
        names.put(CL10.CL_INVALID_WORK_ITEM_SIZE, "CL_INVALID_WORK_ITEM_SIZE");
        names.put(CL10.CL_INVALID_GLOBAL_OFFSET, "CL_INVALID_GLOBAL_OFFSET");
        names.put(CL10.CL_INVALID_EVENT_WAIT_LIST, "CL_INVALID_EVENT_WAIT_LIST");
        names.put(CL10.CL_INVALID_EVENT, "CL_INVALID_EVENT");
        names.put(CL10.CL_INVALID_OPERATION, "CL_INVALID_OPERATION");
        names.put(CL10.CL_INVALID_BUFFER_SIZE, "CL_INVALID_BUFFER_SIZE");
        names.put(CL10.CL_INVALID_GLOBAL_WORK_SIZE, "CL_INVALID_GLOBAL_WORK_SIZE");
        names.put(CL11.CL_INVALID_PROPERTY, "CL_INVALID_PROPERTY");
        names.put(CL12.CL_INVALID_IMAGE_DESCRIPTOR, "CL_INVALID_IMAGE_DESCRIPTOR");
        names.put(CL12.CL_INVALID_COMPILER_OPTIONS, "CL_INVALID_COMPILER_OPTIONS");
        names.put(CL12.CL_INVALID_LINKER_OPTIONS, "CL_INVALID_LINKER_OPTIONS");
        names.put(CL12.CL_INVALID_DEVICE_PARTITION_COUNT, "CL_INVALID_DEVICE_PARTITION_COUNT");
        ERROR_NAMES_BY_CODE = Collections.unmodifiableMap(names);
    }

    /** Creates a new {@link ClErrorCodeResolver}. */
    public ClErrorCodeResolver() {}

    /**
     * Reports whether the given error code denotes success.
     *
     * @param errorCode the {@code cl_int} value returned by an OpenCL entry point
     * @return {@code true} if the call succeeded
     */
    public boolean isSuccess(int errorCode) {
        return errorCode == CL_SUCCESS;
    }

    /**
     * Describes the given error code as its symbolic name plus the numeric value.
     *
     * @param errorCode the {@code cl_int} value returned by an OpenCL entry point
     * @return a description such as {@code "CL_INVALID_VALUE (-30)"}, or a numeric fallback if the
     *         code is not part of the OpenCL 1.0-1.2 error set
     */
    public String describe(int errorCode) {
        final String name = ERROR_NAMES_BY_CODE.get(errorCode);
        if (name == null) {
            return String.format("unknown OpenCL error code (%d)", errorCode);
        }
        return String.format("%s (%d)", name, errorCode);
    }
}
