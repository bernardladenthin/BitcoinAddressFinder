// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import java.nio.ByteBuffer;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Instance-based view of the OpenCL API.
 *
 * <p>The binding library exposes OpenCL exclusively through static methods, which cannot be
 * substituted in a test: any class calling them directly can only be exercised on a machine with a
 * working driver. Routing those calls through this interface is what allows the layers above to be
 * tested with mocks and spies, and it keeps the choice of binding library confined to a single
 * implementation class.
 *
 * <p>Methods here report failures as {@link OpenClCallFailedException} rather than returning error
 * codes; the raw {@code cl_int} contract does not leak past this seam.
 */
public interface ClApi {

    // ---------------------------------------------------------------------------------------
    // Discovery
    // ---------------------------------------------------------------------------------------

    /**
     * Returns every OpenCL platform installed on the machine.
     *
     * @return the platform handles, empty if the ICD loader reports no installed platform
     */
    List<ClPlatformId> getPlatformIds();

    /**
     * Reads a string-valued platform property.
     *
     * @param platform  the platform to query
     * @param paramName the {@code cl_platform_info} parameter, e.g. {@code CL_PLATFORM_NAME}
     * @return the property value without its C string terminator
     */
    String getPlatformInfoString(ClPlatformId platform, int paramName);

    /**
     * Returns the devices of a platform that match the given device type.
     *
     * @param platform   the platform to enumerate
     * @param deviceType the {@code cl_device_type} bit mask, e.g. {@code CL_DEVICE_TYPE_ALL}
     * @return the matching device handles, empty if the platform exposes none
     */
    List<ClDeviceId> getDeviceIds(ClPlatformId platform, long deviceType);

    /**
     * Reads a string-valued device property.
     *
     * @param device    the device to query
     * @param paramName the {@code cl_device_info} parameter, e.g. {@code CL_DEVICE_NAME}
     * @return the property value without its C string terminator
     */
    String getDeviceInfoString(ClDeviceId device, int paramName);

    /**
     * Reads a 32-bit integer device property.
     *
     * @param device    the device to query
     * @param paramName the {@code cl_device_info} parameter
     * @return the property value
     */
    int getDeviceInfoInt(ClDeviceId device, int paramName);

    /**
     * Reads a 64-bit integer device property.
     *
     * @param device    the device to query
     * @param paramName the {@code cl_device_info} parameter
     * @return the property value
     */
    long getDeviceInfoLong(ClDeviceId device, int paramName);

    /**
     * Reads a device property consisting of one or more {@code size_t} values.
     *
     * @param device     the device to query
     * @param paramName  the {@code cl_device_info} parameter
     * @param valueCount the number of {@code size_t} values the property carries
     * @return the property values, widened to {@code long}
     */
    long[] getDeviceInfoSizes(ClDeviceId device, int paramName, int valueCount);

    /**
     * Reads a device property as raw bytes, for properties whose layout is a vendor- or
     * extension-defined struct.
     *
     * <p>Returns an empty array when the driver rejects the query, so an unsupported extension and a
     * driver error are the same outcome for the caller. Extension-defined identity properties are
     * best-effort by nature: callers treat "no answer" as "unknown", never as a failure.
     *
     * @param device    the device to query
     * @param paramName the {@code cl_device_info} parameter
     * @param byteCount the expected payload size in bytes
     * @return the payload, or an empty array when the query was rejected
     */
    byte[] getDeviceInfoBytesOrEmpty(ClDeviceId device, int paramName, int byteCount);

    // ---------------------------------------------------------------------------------------
    // Context and command queue
    // ---------------------------------------------------------------------------------------

    /**
     * Creates a context bound to one device of one platform.
     *
     * @param platform the platform the device belongs to
     * @param device   the device the context is created for
     * @return the created context
     */
    ClContext createContext(ClPlatformId platform, ClDeviceId device);

    /**
     * Creates a command queue for a device.
     *
     * @param context          the owning context
     * @param device           the target device
     * @param enableProfiling  whether the queue timestamps its commands, which callers need to read
     *                         device-side durations but which costs throughput
     * @return the created command queue
     */
    ClCommandQueue createCommandQueue(ClContext context, ClDeviceId device, boolean enableProfiling);

    /**
     * Blocks until every command previously enqueued on the queue has completed.
     *
     * @param commandQueue the queue to drain
     */
    void finish(ClCommandQueue commandQueue);

    // ---------------------------------------------------------------------------------------
    // Program and kernel
    // ---------------------------------------------------------------------------------------

    /**
     * Creates a program from OpenCL C sources.
     *
     * @param context the owning context
     * @param sources the source strings, concatenated by the runtime in the given order
     * @return the created program
     */
    ClProgram createProgramWithSource(ClContext context, String[] sources);

    /**
     * Compiles and links a program for one device.
     *
     * @param program the program to build
     * @param device  the target device
     * @param options the {@code clBuildProgram} option string
     */
    void buildProgram(ClProgram program, ClDeviceId device, String options);

    /**
     * Returns the build log a device produced for a program.
     *
     * @param program the built program
     * @param device  the device it was built for
     * @return the build log, possibly empty
     */
    String getProgramBuildLog(ClProgram program, ClDeviceId device);

    /**
     * Creates a kernel from a built program.
     *
     * @param program    the built program
     * @param kernelName the entry-point name
     * @return the created kernel
     */
    ClKernel createKernel(ClProgram program, String kernelName);

    /**
     * Reads a {@code size_t}-typed kernel work-group property.
     *
     * <p>Separate from {@link #getKernelWorkGroupInfoUlong} because the two differ in width on a
     * 32-bit host, where querying a {@code size_t} property into a 64-bit slot is rejected outright.
     *
     * @param kernel    the kernel to query
     * @param device    the device the kernel was built for
     * @param paramName the {@code cl_kernel_work_group_info} parameter, e.g. {@code CL_KERNEL_WORK_GROUP_SIZE}
     * @return the property value
     */
    long getKernelWorkGroupInfoSize(ClKernel kernel, ClDeviceId device, int paramName);

    /**
     * Reads a {@code cl_ulong}-typed kernel work-group property.
     *
     * @param kernel    the kernel to query
     * @param device    the device the kernel was built for
     * @param paramName the {@code cl_kernel_work_group_info} parameter, e.g. {@code CL_KERNEL_PRIVATE_MEM_SIZE}
     * @return the property value
     */
    long getKernelWorkGroupInfoUlong(ClKernel kernel, ClDeviceId device, int paramName);

    /**
     * Binds a memory object as a kernel argument.
     *
     * @param kernel   the kernel
     * @param argIndex the zero-based argument position
     * @param value    the memory object to bind
     */
    void setKernelArg(ClKernel kernel, int argIndex, ClMem value);

    /**
     * Binds a 32-bit integer as a kernel argument.
     *
     * @param kernel   the kernel
     * @param argIndex the zero-based argument position
     * @param value    the value to bind
     */
    void setKernelArgInt(ClKernel kernel, int argIndex, int value);

    /**
     * Enqueues a kernel launch.
     *
     * @param commandQueue   the queue to submit to
     * @param kernel         the kernel to launch
     * @param globalWorkSize the number of work items per dimension
     * @param profilingEvent whether to capture a profiling event for the launch
     * @return the profiling event when requested, otherwise {@code null}
     */
    @Nullable
    ClEvent enqueueNDRangeKernel(
            ClCommandQueue commandQueue, ClKernel kernel, long[] globalWorkSize, boolean profilingEvent);

    // ---------------------------------------------------------------------------------------
    // Buffers
    // ---------------------------------------------------------------------------------------

    /**
     * Allocates an uninitialised device buffer.
     *
     * @param context   the owning context
     * @param flags     the {@code cl_mem_flags}, see {@link ClBufferFlags}
     * @param sizeBytes the buffer size in bytes
     * @return the created memory object
     */
    ClMem createBuffer(ClContext context, long flags, long sizeBytes);

    /**
     * Allocates a device buffer backed by caller-owned host memory.
     *
     * @param context    the owning context
     * @param flags      the {@code cl_mem_flags}, which must include {@link ClBufferFlags#USE_HOST_PTR}
     * @param hostBuffer a direct buffer that must outlive the memory object
     * @return the created memory object
     */
    ClMem createBufferFromHostMemory(ClContext context, long flags, ByteBuffer hostBuffer);

    /**
     * Copies host data into a device buffer in bounded slices.
     *
     * <p>This exists instead of a {@code CL_MEM_COPY_HOST_PTR} allocation because the binding cannot
     * hand a Java array to the driver: every transfer must originate from a direct buffer. Staging a
     * whole payload would therefore need an off-heap copy as large as the payload itself, and the
     * fingerprint filters this project uploads reach multiple gigabytes — the Full-DB 16-bit filter
     * exceeds what a Java {@code byte[]} can even represent. Writing in slices through a small,
     * reusable staging buffer keeps the extra footprint constant regardless of payload size.
     *
     * @param commandQueue the queue to submit the writes to
     * @param mem          the destination device buffer
     * @param source       the host payload
     */
    void writeBufferChunked(ClCommandQueue commandQueue, ClMem mem, byte[] source);

    /**
     * Copies host data into a device buffer in bounded slices.
     *
     * @param commandQueue the queue to submit the writes to
     * @param mem          the destination device buffer
     * @param source       the host payload, written in the host's native byte order
     * @see #writeBufferChunked(ClCommandQueue, ClMem, byte[])
     */
    void writeBufferChunked(ClCommandQueue commandQueue, ClMem mem, short[] source);

    /**
     * Copies host data into a device buffer in bounded slices.
     *
     * @param commandQueue the queue to submit the writes to
     * @param mem          the destination device buffer
     * @param source       the host payload, written in the host's native byte order
     * @see #writeBufferChunked(ClCommandQueue, ClMem, byte[])
     */
    void writeBufferChunked(ClCommandQueue commandQueue, ClMem mem, int[] source);

    /**
     * Copies host data into a device buffer in bounded slices.
     *
     * @param commandQueue the queue to submit the writes to
     * @param mem          the destination device buffer
     * @param source       the host payload, written in the host's native byte order
     * @see #writeBufferChunked(ClCommandQueue, ClMem, byte[])
     */
    void writeBufferChunked(ClCommandQueue commandQueue, ClMem mem, long[] source);

    /**
     * Enqueues a blocking write from a direct host buffer into a device buffer.
     *
     * @param commandQueue the queue to submit to
     * @param mem          the destination device buffer
     * @param deviceOffset the byte offset into the device buffer
     * @param source       the direct host buffer; its remaining bytes are written
     */
    void enqueueWriteBuffer(ClCommandQueue commandQueue, ClMem mem, long deviceOffset, ByteBuffer source);

    /**
     * Enqueues a blocking read from a device buffer into a direct host buffer.
     *
     * @param commandQueue   the queue to submit to
     * @param mem            the source device buffer
     * @param deviceOffset   the byte offset into the device buffer
     * @param destination    the direct host buffer; its remaining bytes are filled
     * @param profilingEvent whether to capture a profiling event for the transfer
     * @return the profiling event when requested, otherwise {@code null}
     */
    @Nullable
    ClEvent enqueueReadBuffer(
            ClCommandQueue commandQueue, ClMem mem, long deviceOffset, ByteBuffer destination, boolean profilingEvent);

    // ---------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------

    /**
     * Returns the device-side duration of a completed command.
     *
     * @param event a completed event from a profiling-enabled queue
     * @return the on-device execution time in nanoseconds
     */
    long getEventElapsedNanos(ClEvent event);

    // ---------------------------------------------------------------------------------------
    // Release
    // ---------------------------------------------------------------------------------------

    /**
     * Releases a memory object.
     *
     * @param mem the memory object to release
     */
    void releaseMemObject(ClMem mem);

    /**
     * Releases a kernel.
     *
     * @param kernel the kernel to release
     */
    void releaseKernel(ClKernel kernel);

    /**
     * Releases a program.
     *
     * @param program the program to release
     */
    void releaseProgram(ClProgram program);

    /**
     * Releases a command queue.
     *
     * @param commandQueue the queue to release
     */
    void releaseCommandQueue(ClCommandQueue commandQueue);

    /**
     * Releases a context.
     *
     * @param context the context to release
     */
    void releaseContext(ClContext context);

    /**
     * Releases an event.
     *
     * @param event the event to release
     */
    void releaseEvent(ClEvent event);
}
