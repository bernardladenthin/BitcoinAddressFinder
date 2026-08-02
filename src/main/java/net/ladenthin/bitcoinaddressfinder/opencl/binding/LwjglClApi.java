// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL20;
import org.lwjgl.opencl.KHRICD;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * {@link ClApi} implementation backed by LWJGL.
 *
 * <p>The only class in the project that calls into the binding library directly. Everything it does
 * beyond forwarding falls into three categories: it checks every returned error code, it wraps raw
 * handles into their typed counterparts, and it manages the off-heap scratch memory that the
 * pointer-based queries need.
 *
 * <p>Scratch buffers come from {@link MemoryStack}, which allocates from a per-thread stack and
 * releases on scope exit. That is deliberate for the two-call query pattern OpenCL uses everywhere
 * (ask for the size, then ask for the value): it keeps those short-lived buffers off both the Java
 * heap and the allocator. The one exception is the chunked-write staging buffer, which is too large
 * for the stack and is therefore allocated and freed explicitly.
 */
@ToString
public class LwjglClApi implements ClApi {

    /** Number of bytes the OpenCL runtime appends to every string property as the C terminator. */
    private static final int NUL_TERMINATOR_BYTES = 1;

    /** Passed as the value pointer when a query should report the required size only. */
    private static final @Nullable ByteBuffer SIZE_QUERY_ONLY = null;

    /** Terminator of a {@code cl_context_properties} / {@code cl_queue_properties} list. */
    private static final long PROPERTY_LIST_TERMINATOR = 0L;

    /** Blocking flag for the enqueue calls; every transfer in this project is synchronous. */
    private static final boolean BLOCKING = true;

    /** Number of dimensions used for every kernel launch in this project. */
    private static final int WORK_DIMENSIONS = 1;

    /**
     * Size of the staging buffer used by the chunked host-to-device writes. Large enough that the
     * per-write overhead is negligible, small enough that it never competes with the device buffer
     * for host memory — the payloads it stages reach gigabytes.
     */
    private static final int STAGING_BUFFER_BYTES = 8 * 1024 * 1024;

    private final ClErrorChecker errorChecker;
    private final OpenClLibraryLoader libraryLoader;

    /**
     * Creates a new LWJGL-backed API.
     *
     * @param errorChecker  converts returned error codes into exceptions
     * @param libraryLoader ensures the native library is loaded before the first call
     */
    public LwjglClApi(ClErrorChecker errorChecker, OpenClLibraryLoader libraryLoader) {
        this.errorChecker = errorChecker;
        this.libraryLoader = libraryLoader;
    }

    // ---------------------------------------------------------------------------------------
    // Discovery
    // ---------------------------------------------------------------------------------------

    @Override
    public List<ClPlatformId> getPlatformIds() {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer platformCount = stack.mallocInt(1);
            final int countErrorCode = CL10.clGetPlatformIDs(null, platformCount);
            // An ICD loader can be installed while no implementation is: on a bare machine the
            // loader answers CL_PLATFORM_NOT_FOUND_KHR. That is "nothing to offer", not a fault,
            // so callers get an empty list and can skip the GPU path instead of failing.
            if (countErrorCode == KHRICD.CL_PLATFORM_NOT_FOUND_KHR) {
                return List.of();
            }
            errorChecker.checkSuccess(countErrorCode, "clGetPlatformIDs");

            final int count = platformCount.get(0);
            if (count == 0) {
                return List.of();
            }

            final PointerBuffer platforms = stack.mallocPointer(count);
            errorChecker.checkSuccess(CL10.clGetPlatformIDs(platforms, (IntBuffer) null), "clGetPlatformIDs");

            final List<ClPlatformId> platformIds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                platformIds.add(new ClPlatformId(platforms.get(i)));
            }
            return platformIds;
        }
    }

    @Override
    public String getPlatformInfoString(ClPlatformId platform, int paramName) {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer valueSize = stack.mallocPointer(1);
            errorChecker.checkSuccess(
                    CL10.clGetPlatformInfo(platform.handle(), paramName, SIZE_QUERY_ONLY, valueSize),
                    "clGetPlatformInfo");

            final int byteCount = (int) valueSize.get(0);
            final ByteBuffer value = stack.malloc(byteCount);
            errorChecker.checkSuccess(
                    CL10.clGetPlatformInfo(platform.handle(), paramName, value, null), "clGetPlatformInfo");

            return MemoryUtil.memUTF8(value, byteCount - NUL_TERMINATOR_BYTES);
        }
    }

    @Override
    public List<ClDeviceId> getDeviceIds(ClPlatformId platform, long deviceType) {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer deviceCount = stack.mallocInt(1);
            final int countErrorCode = CL10.clGetDeviceIDs(platform.handle(), deviceType, null, deviceCount);
            // A platform that exposes no device of the requested type answers CL_DEVICE_NOT_FOUND.
            // Same reasoning as for platforms above: an empty result, not an error.
            if (countErrorCode == CL10.CL_DEVICE_NOT_FOUND) {
                return List.of();
            }
            errorChecker.checkSuccess(countErrorCode, "clGetDeviceIDs");

            final int count = deviceCount.get(0);
            if (count == 0) {
                return List.of();
            }

            final PointerBuffer devices = stack.mallocPointer(count);
            errorChecker.checkSuccess(
                    CL10.clGetDeviceIDs(platform.handle(), deviceType, devices, (IntBuffer) null), "clGetDeviceIDs");

            final List<ClDeviceId> deviceIds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                deviceIds.add(new ClDeviceId(devices.get(i)));
            }
            return deviceIds;
        }
    }

    @Override
    public String getDeviceInfoString(ClDeviceId device, int paramName) {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer valueSize = stack.mallocPointer(1);
            errorChecker.checkSuccess(
                    CL10.clGetDeviceInfo(device.handle(), paramName, SIZE_QUERY_ONLY, valueSize), "clGetDeviceInfo");

            final int byteCount = (int) valueSize.get(0);
            final ByteBuffer value = stack.malloc(byteCount);
            errorChecker.checkSuccess(CL10.clGetDeviceInfo(device.handle(), paramName, value, null), "clGetDeviceInfo");

            return MemoryUtil.memUTF8(value, byteCount - NUL_TERMINATOR_BYTES);
        }
    }

    @Override
    public int getDeviceInfoInt(ClDeviceId device, int paramName) {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer value = stack.mallocInt(1);
            errorChecker.checkSuccess(CL10.clGetDeviceInfo(device.handle(), paramName, value, null), "clGetDeviceInfo");
            return value.get(0);
        }
    }

    @Override
    public long getDeviceInfoLong(ClDeviceId device, int paramName) {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final LongBuffer value = stack.mallocLong(1);
            errorChecker.checkSuccess(CL10.clGetDeviceInfo(device.handle(), paramName, value, null), "clGetDeviceInfo");
            return value.get(0);
        }
    }

    @Override
    public long[] getDeviceInfoSizes(ClDeviceId device, int paramName, int valueCount) {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // PointerBuffer already models size_t width per platform, which removes the manual
            // 4-versus-8-byte branch the previous binding required at every such call site.
            final PointerBuffer value = stack.mallocPointer(valueCount);
            errorChecker.checkSuccess(CL10.clGetDeviceInfo(device.handle(), paramName, value, null), "clGetDeviceInfo");

            final long[] sizes = new long[valueCount];
            for (int i = 0; i < valueCount; i++) {
                sizes[i] = value.get(i);
            }
            return sizes;
        }
    }

    @Override
    public byte[] getDeviceInfoBytesOrEmpty(ClDeviceId device, int paramName, int byteCount) {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final ByteBuffer value = stack.malloc(byteCount);
            final int errorCode = CL10.clGetDeviceInfo(device.handle(), paramName, value, null);
            if (errorCode != ClErrorCodeResolver.CL_SUCCESS) {
                return new byte[0];
            }
            final byte[] payload = new byte[byteCount];
            value.get(payload);
            return payload;
        } catch (RuntimeException | LinkageError e) {
            // Identity properties come from optional extensions; any refusal means "unknown", which
            // callers must handle anyway. Never let it fail the enumeration.
            return new byte[0];
        }
    }

    // ---------------------------------------------------------------------------------------
    // Context and command queue
    // ---------------------------------------------------------------------------------------

    @Override
    public ClContext createContext(ClPlatformId platform, ClDeviceId device) {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer properties = stack.mallocPointer(3);
            properties
                    .put(0, CL10.CL_CONTEXT_PLATFORM)
                    .put(1, platform.handle())
                    .put(2, PROPERTY_LIST_TERMINATOR);

            final IntBuffer errorCode = stack.mallocInt(1);
            final long handle = CL10.clCreateContext(properties, device.handle(), null, 0L, errorCode);
            errorChecker.checkSuccess(errorCode.get(0), "clCreateContext");
            return new ClContext(errorChecker.requireCreated(handle, "clCreateContext"));
        }
    }

    @Override
    public ClCommandQueue createCommandQueue(ClContext context, ClDeviceId device, boolean enableProfiling) {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // cl_queue_properties is a 64-bit cl_bitfield list, not a pointer list, so this one
            // takes a LongBuffer where the context properties above take a PointerBuffer.
            final LongBuffer properties;
            if (enableProfiling) {
                properties = stack.mallocLong(3);
                properties
                        .put(0, CL20.CL_QUEUE_PROPERTIES)
                        .put(1, CL10.CL_QUEUE_PROFILING_ENABLE)
                        .put(2, PROPERTY_LIST_TERMINATOR);
            } else {
                properties = stack.mallocLong(1);
                properties.put(0, PROPERTY_LIST_TERMINATOR);
            }

            final IntBuffer errorCode = stack.mallocInt(1);
            final long handle =
                    CL20.clCreateCommandQueueWithProperties(context.handle(), device.handle(), properties, errorCode);
            errorChecker.checkSuccess(errorCode.get(0), "clCreateCommandQueueWithProperties");
            return new ClCommandQueue(errorChecker.requireCreated(handle, "clCreateCommandQueueWithProperties"));
        }
    }

    @Override
    public void finish(ClCommandQueue commandQueue) {
        errorChecker.checkSuccess(CL10.clFinish(commandQueue.handle()), "clFinish");
    }

    // ---------------------------------------------------------------------------------------
    // Program and kernel
    // ---------------------------------------------------------------------------------------

    @Override
    public ClProgram createProgramWithSource(ClContext context, String[] sources) {
        libraryLoader.ensureLoaded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer errorCode = stack.mallocInt(1);
            final long handle = CL10.clCreateProgramWithSource(context.handle(), sources, errorCode);
            errorChecker.checkSuccess(errorCode.get(0), "clCreateProgramWithSource");
            return new ClProgram(errorChecker.requireCreated(handle, "clCreateProgramWithSource"));
        }
    }

    @Override
    public void buildProgram(ClProgram program, ClDeviceId device, String options) {
        errorChecker.checkSuccess(
                CL10.clBuildProgram(program.handle(), device.handle(), options, null, 0L), "clBuildProgram");
    }

    @Override
    public String getProgramBuildLog(ClProgram program, ClDeviceId device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer logSize = stack.mallocPointer(1);
            errorChecker.checkSuccess(
                    CL10.clGetProgramBuildInfo(
                            program.handle(), device.handle(), CL10.CL_PROGRAM_BUILD_LOG, SIZE_QUERY_ONLY, logSize),
                    "clGetProgramBuildInfo");

            final int byteCount = (int) logSize.get(0);
            if (byteCount <= NUL_TERMINATOR_BYTES) {
                return "";
            }
            final ByteBuffer log = MemoryUtil.memAlloc(byteCount);
            try {
                errorChecker.checkSuccess(
                        CL10.clGetProgramBuildInfo(
                                program.handle(), device.handle(), CL10.CL_PROGRAM_BUILD_LOG, log, null),
                        "clGetProgramBuildInfo");
                return MemoryUtil.memUTF8(log, byteCount - NUL_TERMINATOR_BYTES);
            } finally {
                // Off the stack on purpose: a verbose NVIDIA build log can be far larger than the
                // per-thread stack region MemoryStack carves out.
                MemoryUtil.memFree(log);
            }
        }
    }

    @Override
    public ClKernel createKernel(ClProgram program, String kernelName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer errorCode = stack.mallocInt(1);
            final long handle = CL10.clCreateKernel(program.handle(), kernelName, errorCode);
            errorChecker.checkSuccess(errorCode.get(0), "clCreateKernel");
            return new ClKernel(errorChecker.requireCreated(handle, "clCreateKernel"));
        }
    }

    @Override
    public long getKernelWorkGroupInfoSize(ClKernel kernel, ClDeviceId device, int paramName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer value = stack.mallocPointer(1);
            errorChecker.checkSuccess(
                    CL10.clGetKernelWorkGroupInfo(kernel.handle(), device.handle(), paramName, value, null),
                    "clGetKernelWorkGroupInfo");
            return value.get(0);
        }
    }

    @Override
    public long getKernelWorkGroupInfoUlong(ClKernel kernel, ClDeviceId device, int paramName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final LongBuffer value = stack.mallocLong(1);
            errorChecker.checkSuccess(
                    CL10.clGetKernelWorkGroupInfo(kernel.handle(), device.handle(), paramName, value, null),
                    "clGetKernelWorkGroupInfo");
            return value.get(0);
        }
    }

    @Override
    public void setKernelArg(ClKernel kernel, int argIndex, ClMem value) {
        errorChecker.checkSuccess(CL10.clSetKernelArg1p(kernel.handle(), argIndex, value.handle()), "clSetKernelArg");
    }

    @Override
    public void setKernelArgInt(ClKernel kernel, int argIndex, int value) {
        errorChecker.checkSuccess(CL10.clSetKernelArg1i(kernel.handle(), argIndex, value), "clSetKernelArg");
    }

    @Override
    public @Nullable ClEvent enqueueNDRangeKernel(
            ClCommandQueue commandQueue, ClKernel kernel, long[] globalWorkSize, boolean profilingEvent) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer global = stack.mallocPointer(globalWorkSize.length);
            for (int i = 0; i < globalWorkSize.length; i++) {
                global.put(i, globalWorkSize[i]);
            }
            // Null local work size: the runtime picks the work-group size, as before.
            final PointerBuffer event = profilingEvent ? stack.mallocPointer(1) : null;
            errorChecker.checkSuccess(
                    CL10.clEnqueueNDRangeKernel(
                            commandQueue.handle(), kernel.handle(), WORK_DIMENSIONS, null, global, null, null, event),
                    "clEnqueueNDRangeKernel");
            return event == null ? null : new ClEvent(event.get(0));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Buffers
    // ---------------------------------------------------------------------------------------

    @Override
    public ClMem createBuffer(ClContext context, long flags, long sizeBytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer errorCode = stack.mallocInt(1);
            final long handle = CL10.clCreateBuffer(context.handle(), flags, sizeBytes, errorCode);
            errorChecker.checkSuccess(errorCode.get(0), "clCreateBuffer");
            return new ClMem(errorChecker.requireCreated(handle, "clCreateBuffer"));
        }
    }

    @Override
    public ClMem createBufferFromHostMemory(ClContext context, long flags, ByteBuffer hostBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer errorCode = stack.mallocInt(1);
            final long handle = CL10.clCreateBuffer(context.handle(), flags, hostBuffer, errorCode);
            errorChecker.checkSuccess(errorCode.get(0), "clCreateBuffer");
            return new ClMem(errorChecker.requireCreated(handle, "clCreateBuffer"));
        }
    }

    @Override
    public void writeBufferChunked(ClCommandQueue commandQueue, ClMem mem, byte[] source) {
        writeChunked(commandQueue, mem, (long) source.length, Byte.BYTES, (staging, elementOffset, elementCount) -> {
            staging.put(source, (int) elementOffset, elementCount);
            return elementCount;
        });
    }

    @Override
    public void writeBufferChunked(ClCommandQueue commandQueue, ClMem mem, short[] source) {
        writeChunked(
                commandQueue,
                mem,
                (long) source.length * Short.BYTES,
                Short.BYTES,
                (staging, elementOffset, elementCount) -> {
                    staging.asShortBuffer().put(source, (int) elementOffset, elementCount);
                    return elementCount * Short.BYTES;
                });
    }

    @Override
    public void writeBufferChunked(ClCommandQueue commandQueue, ClMem mem, int[] source) {
        writeChunked(
                commandQueue,
                mem,
                (long) source.length * Integer.BYTES,
                Integer.BYTES,
                (staging, elementOffset, elementCount) -> {
                    staging.asIntBuffer().put(source, (int) elementOffset, elementCount);
                    return elementCount * Integer.BYTES;
                });
    }

    @Override
    public void writeBufferChunked(ClCommandQueue commandQueue, ClMem mem, long[] source) {
        writeChunked(
                commandQueue,
                mem,
                (long) source.length * Long.BYTES,
                Long.BYTES,
                (staging, elementOffset, elementCount) -> {
                    staging.asLongBuffer().put(source, (int) elementOffset, elementCount);
                    return elementCount * Long.BYTES;
                });
    }

    @Override
    public void enqueueWriteBuffer(ClCommandQueue commandQueue, ClMem mem, long deviceOffset, ByteBuffer source) {
        errorChecker.checkSuccess(
                CL10.clEnqueueWriteBuffer(
                        commandQueue.handle(), mem.handle(), BLOCKING, deviceOffset, source, null, null),
                "clEnqueueWriteBuffer");
    }

    @Override
    public @Nullable ClEvent enqueueReadBuffer(
            ClCommandQueue commandQueue, ClMem mem, long deviceOffset, ByteBuffer destination, boolean profilingEvent) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer event = profilingEvent ? stack.mallocPointer(1) : null;
            errorChecker.checkSuccess(
                    CL10.clEnqueueReadBuffer(
                            commandQueue.handle(), mem.handle(), BLOCKING, deviceOffset, destination, null, event),
                    "clEnqueueReadBuffer");
            return event == null ? null : new ClEvent(event.get(0));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------

    @Override
    public long getEventElapsedNanos(ClEvent event) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final LongBuffer start = stack.mallocLong(1);
            final LongBuffer end = stack.mallocLong(1);
            final long eventHandle = event.handle();
            errorChecker.checkSuccess(
                    CL10.clGetEventProfilingInfo(eventHandle, CL10.CL_PROFILING_COMMAND_START, start, null),
                    "clGetEventProfilingInfo");
            errorChecker.checkSuccess(
                    CL10.clGetEventProfilingInfo(eventHandle, CL10.CL_PROFILING_COMMAND_END, end, null),
                    "clGetEventProfilingInfo");
            return end.get(0) - start.get(0);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Release
    // ---------------------------------------------------------------------------------------

    @Override
    public void releaseMemObject(ClMem mem) {
        errorChecker.checkSuccess(CL10.clReleaseMemObject(mem.handle()), "clReleaseMemObject");
    }

    @Override
    public void releaseKernel(ClKernel kernel) {
        errorChecker.checkSuccess(CL10.clReleaseKernel(kernel.handle()), "clReleaseKernel");
    }

    @Override
    public void releaseProgram(ClProgram program) {
        errorChecker.checkSuccess(CL10.clReleaseProgram(program.handle()), "clReleaseProgram");
    }

    @Override
    public void releaseCommandQueue(ClCommandQueue commandQueue) {
        errorChecker.checkSuccess(CL10.clReleaseCommandQueue(commandQueue.handle()), "clReleaseCommandQueue");
    }

    @Override
    public void releaseContext(ClContext context) {
        errorChecker.checkSuccess(CL10.clReleaseContext(context.handle()), "clReleaseContext");
    }

    @Override
    public void releaseEvent(ClEvent event) {
        errorChecker.checkSuccess(CL10.clReleaseEvent(event.handle()), "clReleaseEvent");
    }

    /**
     * Copies a host payload into a device buffer through a bounded staging buffer.
     *
     * @param commandQueue the queue to submit the writes to
     * @param mem          the destination device buffer
     * @param totalBytes   the payload size in bytes
     * @param elementBytes the width of one payload element, so a chunk never splits an element
     * @param filler       copies one slice of the payload into the staging buffer
     */
    private void writeChunked(
            ClCommandQueue commandQueue, ClMem mem, long totalBytes, int elementBytes, StagingFiller filler) {
        if (totalBytes <= 0L) {
            return;
        }
        final int elementsPerChunk = STAGING_BUFFER_BYTES / elementBytes;
        final ByteBuffer staging = MemoryUtil.memAlloc(STAGING_BUFFER_BYTES).order(ByteOrder.nativeOrder());
        try {
            final long totalElements = totalBytes / elementBytes;
            long writtenElements = 0L;
            long deviceOffset = 0L;
            while (writtenElements < totalElements) {
                final int chunkElements = (int) Math.min(elementsPerChunk, totalElements - writtenElements);
                staging.clear();
                final int chunkBytes = filler.fill(staging, writtenElements, chunkElements);
                staging.position(0).limit(chunkBytes);
                enqueueWriteBuffer(commandQueue, mem, deviceOffset, staging);
                writtenElements += chunkElements;
                deviceOffset += chunkBytes;
            }
            finish(commandQueue);
        } finally {
            MemoryUtil.memFree(staging);
        }
    }

    /** Copies one slice of a primitive host payload into the staging buffer. */
    private interface StagingFiller {

        /**
         * Copies {@code elementCount} elements starting at {@code elementOffset} into the staging
         * buffer.
         *
         * @param staging       the staging buffer, already cleared
         * @param elementOffset index of the first element to copy
         * @param elementCount  number of elements to copy
         * @return the number of bytes written into the staging buffer
         */
        int fill(ByteBuffer staging, long elementOffset, int elementCount);
    }
}
