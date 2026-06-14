// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_USE_HOST_PTR;
import static org.jocl.CL.CL_MEM_WRITE_ONLY;
import static org.jocl.CL.CL_PROFILING_COMMAND_END;
import static org.jocl.CL.CL_PROFILING_COMMAND_START;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clFinish;
import static org.jocl.CL.clGetEventProfilingInfo;
import static org.jocl.CL.clReleaseEvent;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clSetKernelArg;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.EndiannessConverter;
import net.ladenthin.bitcoinaddressfinder.util.PrivateKeyTooLargeException;
import net.ladenthin.bitcoinaddressfinder.util.PrivateKeyValidator;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates one OpenCL kernel invocation: manages source/destination buffers and runs the kernel.
 */
// JOCL upstream API is not annotated for nullness; every clXxx(...) call here passes
// the null values that the OpenCL C ABI accepts (e.g. errcode_ret, event_wait_list,
// event, global_work_offset). Suppress at class scope to avoid per-call noise.
@SuppressWarnings({"nullness:argument", "nullness:dereference.of.nullable"})
@ToString
public class OpenClTask implements ReleaseCLObject {

    /** SLF4J logger for this task. */
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenClTask.class);

    private static final int PRIVATE_KEY_SOURCE_SIZE_IN_BYTES = OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES;

    private final CProducerOpenCL cProducer;

    // JOCL cl_context is a native-pointer wrapper — toString is uninformative.
    @ToString.Exclude
    private final cl_context context;

    // SourceArgument carries its own ByteBuffer payload — heavy and not useful in logs.
    @ToString.Exclude
    private final SourceArgument privateKeySourceArgument;

    private final BitHelper bitHelper;
    private final ByteBufferUtility byteBufferUtility;
    private final BigInteger maxPrivateKeyForBatchSize;
    private final PrivateKeyValidator privateKeyValidator;

    private boolean closed = false;

    /** Sentinel meaning "no profiling timestamp captured for the most recent launch". */
    public static final long PROFILING_NOT_AVAILABLE = -1L;

    // Device-side nanosecond timings of the most recent executeKernel() call. Only populated when
    // CProducerOpenCL.enableProfiling is true (a diagnostic/benchmark switch); otherwise they stay
    // at PROFILING_NOT_AVAILABLE. Mutated from executeKernel and read by the benchmark; volatile so
    // a reader on another thread sees a consistent value. Excluded from toString (mutable state).
    @ToString.Exclude
    private volatile long lastKernelExecutionNanos = PROFILING_NOT_AVAILABLE;

    @ToString.Exclude
    private volatile long lastResultReadbackNanos = PROFILING_NOT_AVAILABLE;

    /**
     * Common base for source and destination OpenCL buffer arguments backed by a {@link ByteBuffer}.
     */
    public abstract static class CLByteBufferPointerArgument implements ReleaseCLObject {
        /**
         * Controls how memory is allocated for the OpenCL output buffer.
         *
         * If set to {@link org.jocl.CL#CL_MEM_USE_HOST_PTR}, the OpenCL buffer is created using a host pointer,
         * meaning the host's {@link ByteBuffer} is directly used by the device (zero-copy if supported).
         * This may reduce memory copy overhead on some platforms, but:
         * <ul>
         *     <li>It requires the buffer to remain valid and pinned in memory.</li>
         *     <li>On some OpenCL implementations or devices (e.g. discrete GPUs), it may cause slower access due to lack of true zero-copy support.</li>
         *     <li>Debugging and compatibility issues can arise if host memory alignment or page-locking requirements aren't met.</li>
         * </ul>
         *
         * If set to {@link org.jocl.CL#CL_MEM_WRITE_ONLY}, the buffer is created with no reference to host memory,
         * and OpenCL manages the memory internally. This is typically safer and potentially faster on discrete GPUs,
         * although it requires an explicit copy back to the host after kernel execution.
         *
         * In most cases, {@link org.jocl.CL#CL_MEM_WRITE_ONLY} (i.e. setting this flag to {@code false}) is more robust and portable.
         */
        protected static final boolean USE_HOST_PTR = false;

        /** Host-side direct byte buffer holding the data. */
        protected final ByteBuffer byteBuffer;
        /** {@link Pointer} that references {@link #byteBuffer} for host memory transfers. */
        protected final Pointer hostMemoryPointer;
        /** Underlying OpenCL memory object. */
        protected final cl_mem mem;
        /** {@link Pointer} that references {@link #mem} for {@code clSetKernelArg}. */
        protected final Pointer clMemPointer;

        private boolean closed = false;

        /**
         * Creates a new wrapper around an allocated OpenCL buffer.
         *
         * @param byteBuffer        the backing direct byte buffer
         * @param hostMemoryPointer pointer to {@code byteBuffer}
         * @param mem               the OpenCL memory object
         * @param clMemPointer      pointer to {@code mem}
         */
        public CLByteBufferPointerArgument(
                ByteBuffer byteBuffer, Pointer hostMemoryPointer, cl_mem mem, Pointer clMemPointer) {
            this.byteBuffer = byteBuffer;
            this.hostMemoryPointer = hostMemoryPointer;
            this.mem = mem;
            this.clMemPointer = clMemPointer;
        }

        /**
         * Returns the backing host-side byte buffer.
         *
         * @return the backing host-side byte buffer
         */
        public ByteBuffer getByteBuffer() {
            return byteBuffer;
        }

        /**
         * Returns the host-memory pointer used for {@code clEnqueueRead/WriteBuffer}.
         *
         * @return the host-memory pointer used for {@code clEnqueueRead/WriteBuffer}
         */
        public Pointer getHostMemoryPointer() {
            return hostMemoryPointer;
        }

        /**
         * Returns the {@code cl_mem} pointer used for {@code clSetKernelArg}.
         *
         * @return the {@code cl_mem} pointer used for {@code clSetKernelArg}
         */
        public Pointer getClMemPointer() {
            return clMemPointer;
        }

        /**
         * Returns the wrapped {@link cl_mem} object.
         *
         * @return the wrapped {@link cl_mem} object
         */
        public cl_mem getMem() {
            return mem;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (!closed) {
                clReleaseMemObject(mem);
                closed = true;
            }
        }
    }

    /**
     * Destination (kernel output) OpenCL buffer argument.
     */
    public static class DestinationArgument extends CLByteBufferPointerArgument {

        private DestinationArgument(
                ByteBuffer byteBuffer, Pointer hostMemoryPointer, cl_mem mem, Pointer clMemPointer) {
            super(byteBuffer, hostMemoryPointer, mem, clMemPointer);
        }

        /**
         * Allocates a new destination buffer of {@code sizeInBytes} bytes.
         *
         * @param context     the OpenCL context
         * @param sizeInBytes the buffer size in bytes
         * @return the created {@link DestinationArgument}
         */
        public static DestinationArgument create(cl_context context, long sizeInBytes) {
            final ByteBuffer byteBuffer =
                    ByteBuffer.allocateDirect(ByteBufferUtility.ensureByteBufferCapacityFitsInt(sizeInBytes));
            final Pointer hostMemoryPointer = Pointer.to(byteBuffer);
            final cl_mem mem;

            if (USE_HOST_PTR) {
                mem = clCreateBuffer(context, CL_MEM_USE_HOST_PTR, sizeInBytes, hostMemoryPointer, null);
            } else {
                mem = clCreateBuffer(context, CL_MEM_WRITE_ONLY, sizeInBytes, null, null);
            }
            final Pointer clMemPointer = Pointer.to(mem);

            return new DestinationArgument(byteBuffer, hostMemoryPointer, mem, clMemPointer);
        }
    }

    /**
     * Source (kernel input) OpenCL buffer argument.
     */
    public static class SourceArgument extends CLByteBufferPointerArgument {

        private SourceArgument(ByteBuffer byteBuffer, Pointer hostMemoryPointer, cl_mem mem, Pointer clMemPointer) {
            super(byteBuffer, hostMemoryPointer, mem, clMemPointer);
        }

        /**
         * Allocates a new source buffer of {@code sizeInBytes} bytes.
         *
         * @param context     the OpenCL context
         * @param sizeInBytes the buffer size in bytes
         * @return the created {@link SourceArgument}
         */
        public static SourceArgument create(cl_context context, long sizeInBytes) {
            final ByteBuffer byteBuffer =
                    ByteBuffer.allocateDirect(ByteBufferUtility.ensureByteBufferCapacityFitsInt(sizeInBytes));
            final Pointer hostMemoryPointer = Pointer.to(byteBuffer);
            final cl_mem mem = clCreateBuffer(
                    context, CL_MEM_READ_ONLY | CL_MEM_USE_HOST_PTR, sizeInBytes, hostMemoryPointer, null);
            final Pointer clMemPointer = Pointer.to(mem);
            return new SourceArgument(byteBuffer, hostMemoryPointer, mem, clMemPointer);
        }
    }

    /**
     * Creates a new OpenCL task. Only usable after the surrounding {@link OpenCLContext} has been initialised.
     *
     * @param context           the OpenCL context
     * @param cProducer         the OpenCL producer configuration
     * @param bitHelper         bit/batch-size helper
     * @param byteBufferUtility byte-buffer helper used for endian conversion
     */
    public OpenClTask(
            cl_context context, CProducerOpenCL cProducer, BitHelper bitHelper, ByteBufferUtility byteBufferUtility) {
        this.context = context;
        this.cProducer = cProducer;
        this.bitHelper = bitHelper;
        this.byteBufferUtility = byteBufferUtility;
        this.privateKeyValidator = new PrivateKeyValidator();
        this.maxPrivateKeyForBatchSize = privateKeyValidator.getMaxPrivateKeyForBatchSize(cProducer.batchSizeInBits);
        this.privateKeySourceArgument = SourceArgument.create(context, PRIVATE_KEY_SOURCE_SIZE_IN_BYTES);
    }

    /**
     * Returns the size of the destination buffer for the current batch.
     *
     * @return the size of the destination buffer in bytes for the current batch
     */
    public long getDstSizeInBytes() {
        // Unified output buffer: a 4-byte count header followed by overallWorkSize entries of
        // OUTPUT_ENTRY_SIZE_BYTES (108) each. This is also exactly compact mode's worst case
        // (every candidate is a filter hit), so the single allocation covers both write modes.
        // The (long) cast forces 64-bit multiplication before the addition, preventing int
        // overflow for large work sizes (108 × MAXIMUM_CHUNK_ELEMENTS ≈ 2.1 GB).
        return OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES
                + (long) OpenClKernelConstants.OUTPUT_ENTRY_SIZE_BYTES * cProducer.getOverallWorkSize();
    }

    /**
     * Writes the base private key to the source buffer in the format expected by the OpenCL kernel.
     * <p>
     * The method ensures that the provided private key is valid for the current batch size. If it exceeds
     * the allowed range, a {@link PrivateKeyTooLargeException} is thrown.
     * <p>
     * Internally, the private key is first converted to a byte array in Big-Endian format (as returned
     * by {@link BigInteger#toByteArray()}). Because the OpenCL kernel expects the private key as a
     * {@code __global const u32 *k} array in <strong>Little-Endian</strong> word order, the byte array
     * is then converted from Big-Endian to Little-Endian before being written to the OpenCL input buffer.
     * <p>
     * This matches the behavior of the OpenCL kernel {@code generateKeysKernel_grid}, which reads the key
     * using {@code copy_u32_array(k_littleEndian_local, k, ...)} assuming Little-Endian input and applies
     * the work-item ID to the least-significant word.
     *
     * @param privateKeyBase the base private key used as input to the OpenCL kernel
     * @throws PrivateKeyTooLargeException if the key is too large for the current batch size
     */
    public void setSrcPrivateKeyChunk(BigInteger privateKeyBase) {
        if (privateKeyValidator.isInvalidWithBatchSize(privateKeyBase, maxPrivateKeyForBatchSize)) {
            throw new PrivateKeyTooLargeException(privateKeyBase, maxPrivateKeyForBatchSize, cProducer.batchSizeInBits);
        }

        // BigInteger.toByteArray() always returns a big-endian (MSB-first) representation,
        // meaning the most significant byte (MSB) comes first.
        // Therefore, the source format is always Big Endian.
        //
        // Use a fixed-width (left zero-padded) array so the full key buffer is overwritten. The
        // source buffer is reused across createKeys() calls (one OpenCLContext per producer), so
        // a shorter (leading-zero) key must not leave stale high-order bytes from a previous,
        // longer key — that would corrupt the scalar fed to the kernel.
        final byte[] byteArray =
                ByteBufferUtility.bigIntegerToFixedLengthBytes(privateKeyBase, PRIVATE_KEY_SOURCE_SIZE_IN_BYTES);
        EndiannessConverter endiannessConverter = new EndiannessConverter(
                ByteOrder.BIG_ENDIAN, OpenClKernelConstants.GPU_NATIVE_WORD_ORDER, byteBufferUtility);
        endiannessConverter.convertEndian(byteArray);
        ByteBufferUtility.putToByteBuffer(privateKeySourceArgument.getByteBuffer(), byteArray);
    }

    /**
     * Returns the private-key source argument backing this task (visible for testing).
     *
     * @return the private-key source argument backing this task
     */
    public SourceArgument getPrivateKeySourceArgument() {
        return privateKeySourceArgument;
    }

    /**
     * Runs the OpenCL kernel for the configured batch and returns the result buffer.
     *
     * <p>Binds the two GPU Binary Fuse 8 filter buffers and the {@code transfer_all} mode flag
     * as kernel arguments, zero-initialises the output buffer's leading count word (so compact
     * mode's {@code atomic_add} starts from zero), runs the kernel, then reads back only the
     * bytes the kernel actually produced: in full-transfer mode the whole grid
     * ({@code overallWorkSize} entries), in compact mode just the {@code K} hit entries the
     * count word reports &mdash; realising the PCIe bandwidth saving.
     *
     * @param kernel        the compiled OpenCL kernel
     * @param commandQueue  the OpenCL command queue
     * @param fuse8FpMem    the GPU fingerprint slot buffer (a dummy empty filter when none is uploaded)
     * @param fuse8MetaMem  the GPU 5-int metadata buffer {@code [seedLo, seedHi, segLen, segLenMask, segCountLen]}
     * @param transferAll   {@code 0} for compact (filter) mode, non-zero to force full transfer
     * @return the destination buffer containing kernel results
     */
    public ByteBuffer executeKernel(
            cl_kernel kernel, cl_command_queue commandQueue, cl_mem fuse8FpMem, cl_mem fuse8MetaMem, int transferAll) {
        final long dstSizeInBytes = getDstSizeInBytes();
        // Allocate a new destination buffer so that cloning after kernel execution is unnecessary
        try (final DestinationArgument destinationArgument = DestinationArgument.create(context, dstSizeInBytes)) {
            // Set the work-item dimensions
            final long totalResultCount = bitHelper.convertBitsToSize(cProducer.batchSizeInBits);
            final int keysPerWorkItem = cProducer.keysPerWorkItem;
            final long adjustedWorkSize = totalResultCount / keysPerWorkItem;

            // Validate keysPerWorkItem constraints
            if (keysPerWorkItem < 1) {
                throw new IllegalArgumentException("keysPerWorkItem must be >= 1 but was " + keysPerWorkItem
                        + " (totalResultCount=" + totalResultCount + ")");
            }
            if (keysPerWorkItem > totalResultCount) {
                throw new IllegalArgumentException("keysPerWorkItem must not exceed total result count. Given: "
                        + keysPerWorkItem + ", max: " + totalResultCount);
            }
            if (totalResultCount % keysPerWorkItem != 0) {
                throw new IllegalArgumentException("totalResultCount=" + totalResultCount
                        + " is not divisible by keysPerWorkItem=" + keysPerWorkItem
                        + "; result count would be invalid"
                        + " (cProducer.batchSizeInBits=" + cProducer.batchSizeInBits + ")");
            }

            final long[] global_work_size = new long[] {adjustedWorkSize};
            final long[] localWorkSize = null; // new long[]{1}; // enabling the system to choose the work-group size.
            final int workDim = 1;

            // Set the arguments for the kernel
            clSetKernelArg(kernel, 0, Sizeof.cl_mem, destinationArgument.getClMemPointer());
            clSetKernelArg(kernel, 1, Sizeof.cl_mem, privateKeySourceArgument.getClMemPointer());
            clSetKernelArg(kernel, 2, Sizeof.cl_uint, Pointer.to(new int[] {keysPerWorkItem}));
            clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(fuse8FpMem));
            clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(fuse8MetaMem));
            clSetKernelArg(kernel, 5, Sizeof.cl_uint, Pointer.to(new int[] {transferAll}));

            {
                // write src buffer
                clEnqueueWriteBuffer(
                        commandQueue,
                        privateKeySourceArgument.getMem(),
                        CL_TRUE,
                        0,
                        PRIVATE_KEY_SOURCE_SIZE_IN_BYTES,
                        privateKeySourceArgument.getHostMemoryPointer(),
                        0,
                        null,
                        null);
                clFinish(commandQueue);
            }
            {
                // zero-initialise the leading count word so compact mode's atomic_add starts at
                // 0 (full mode's work-item 0 overwrites it with the sentinel).
                final ByteBuffer zeroHeader = ByteBuffer.allocateDirect(OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES);
                clEnqueueWriteBuffer(
                        commandQueue,
                        destinationArgument.getMem(),
                        CL_TRUE,
                        0,
                        OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES,
                        Pointer.to(zeroHeader),
                        0,
                        null,
                        null);
                clFinish(commandQueue);
            }
            final boolean profiling = cProducer.enableProfiling;
            {
                // execute the kernel
                final long beforeExecute = System.currentTimeMillis();
                if (profiling) {
                    // Profiling path: timestamp the kernel on the device. The event is a non-null
                    // local kept within this branch so NullAway sees no nullable JOCL argument.
                    final cl_event kernelEvent = new cl_event();
                    clEnqueueNDRangeKernel(
                            commandQueue, kernel, workDim, null, global_work_size, localWorkSize, 0, null, kernelEvent);
                    clFinish(commandQueue);
                    lastKernelExecutionNanos = deviceElapsedNanos(kernelEvent);
                    clReleaseEvent(kernelEvent);
                } else {
                    clEnqueueNDRangeKernel(
                            commandQueue, kernel, workDim, null, global_work_size, localWorkSize, 0, null, null);
                    clFinish(commandQueue);
                    lastKernelExecutionNanos = PROFILING_NOT_AVAILABLE;
                }

                final long afterExecute = System.currentTimeMillis();

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Executed OpenCL kernel in " + (afterExecute - beforeExecute) + "ms");
                }
            }
            {
                // Read back the count word first, then only the bytes the kernel produced:
                // full mode -> overallWorkSize entries; compact mode -> K hit entries.
                final ByteBuffer countHeader = ByteBuffer.allocateDirect(OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES)
                        .order(OpenClKernelConstants.GPU_NATIVE_WORD_ORDER);
                clEnqueueReadBuffer(
                        commandQueue,
                        destinationArgument.getMem(),
                        CL_TRUE,
                        0,
                        OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES,
                        Pointer.to(countHeader),
                        0,
                        null,
                        null);
                clFinish(commandQueue);

                final int count = countHeader.getInt(0);
                final long entriesToRead;
                if (count == OpenClKernelConstants.OUTPUT_COUNT_FULL_TRANSFER_SENTINEL) {
                    entriesToRead = cProducer.getOverallWorkSize();
                } else {
                    // The sentinel (0xFFFF_FFFF = 4_294_967_295 unsigned) can never alias a
                    // real compact count: MAXIMUM_CHUNK_ELEMENTS (~19.9 M) is far below that
                    // value, so the bounds check below always catches it before it would
                    // silently look like a normal count.
                    final long compactCount = Integer.toUnsignedLong(count);
                    if (compactCount > cProducer.getOverallWorkSize()) {
                        throw new IllegalStateException("GPU compact-mode count " + compactCount
                                + " exceeds overallWorkSize " + cProducer.getOverallWorkSize()
                                + "; kernel output is corrupt");
                    }
                    entriesToRead = compactCount;
                }
                final long bytesToRead = OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES
                        + entriesToRead * OpenClKernelConstants.OUTPUT_ENTRY_SIZE_BYTES;

                final long beforeRead = System.currentTimeMillis();
                if (profiling) {
                    final cl_event readEvent = new cl_event();
                    clEnqueueReadBuffer(
                            commandQueue,
                            destinationArgument.getMem(),
                            CL_TRUE,
                            0,
                            bytesToRead,
                            destinationArgument.getHostMemoryPointer(),
                            0,
                            null,
                            readEvent);
                    clFinish(commandQueue);
                    lastResultReadbackNanos = deviceElapsedNanos(readEvent);
                    clReleaseEvent(readEvent);
                } else {
                    clEnqueueReadBuffer(
                            commandQueue,
                            destinationArgument.getMem(),
                            CL_TRUE,
                            0,
                            bytesToRead,
                            destinationArgument.getHostMemoryPointer(),
                            0,
                            null,
                            null);
                    clFinish(commandQueue);
                    lastResultReadbackNanos = PROFILING_NOT_AVAILABLE;
                }

                final long afterRead = System.currentTimeMillis();

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Read OpenCL data " + ((bytesToRead / 1024) / 1024) + "Mb in "
                            + (afterRead - beforeRead) + "ms");
                }
            }
            return destinationArgument.getByteBuffer();
        }
    }

    /**
     * Reads the device-side {@code END - START} duration of a profiled command.
     *
     * @param event a completed event from a profiling-enabled queue
     * @return the on-device execution time of the command, in nanoseconds
     */
    private static long deviceElapsedNanos(cl_event event) {
        final long[] start = new long[1];
        final long[] end = new long[1];
        clGetEventProfilingInfo(event, CL_PROFILING_COMMAND_START, Sizeof.cl_ulong, Pointer.to(start), null);
        clGetEventProfilingInfo(event, CL_PROFILING_COMMAND_END, Sizeof.cl_ulong, Pointer.to(end), null);
        return end[0] - start[0];
    }

    /**
     * Returns the on-device kernel execution time of the most recent {@link #executeKernel} call.
     *
     * <p>Populated only when {@link CProducerOpenCL#enableProfiling} is {@code true}; otherwise
     * returns {@link #PROFILING_NOT_AVAILABLE}. This is GPU compute time only &mdash; it excludes
     * PCIe transfer and host-side parsing.
     *
     * @return the last kernel execution time in nanoseconds, or {@link #PROFILING_NOT_AVAILABLE}
     */
    public long getLastKernelExecutionNanos() {
        return lastKernelExecutionNanos;
    }

    /**
     * Returns the on-device result read-back (PCIe transfer) time of the most recent
     * {@link #executeKernel} call.
     *
     * <p>Populated only when {@link CProducerOpenCL#enableProfiling} is {@code true}; otherwise
     * returns {@link #PROFILING_NOT_AVAILABLE}. In compact mode this covers only the {@code K} hit
     * entries; in full-transfer mode the whole grid.
     *
     * @return the last read-back time in nanoseconds, or {@link #PROFILING_NOT_AVAILABLE}
     */
    public long getLastResultReadbackNanos() {
        return lastResultReadbackNanos;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            privateKeySourceArgument.close();
            closed = true;
            // hint: destinationArgument will be released immediately
        }
    }

    /**
     * https://stackoverflow.com/questions/3366925/deep-copy-duplicate-of-javas-bytebuffer/4074089
     */
    // Preserved as a reusable helper for potential future safe native↔JVM-heap handoff
    // in the OpenCL pipeline. No current production or test caller; UnusedMethod
    // suppressed to keep -Werror clean.
    @SuppressWarnings("UnusedMethod")
    private static ByteBuffer cloneByteBuffer(final ByteBuffer original) {
        // Create clone with same capacity as original.
        final ByteBuffer clone = original.isDirect()
                ? ByteBuffer.allocateDirect(original.capacity())
                : ByteBuffer.allocate(original.capacity());

        // Create a read-only copy of the original.
        // This allows reading from the original without modifying it.
        final ByteBuffer readOnlyCopy = original.asReadOnlyBuffer();

        // Flip and read from the original.
        readOnlyCopy.flip();
        clone.put(readOnlyCopy);

        return clone;
    }
}
