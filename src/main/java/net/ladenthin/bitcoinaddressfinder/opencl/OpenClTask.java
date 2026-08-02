// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClApi;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClBufferFlags;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClCommandQueue;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClContext;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClEvent;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClKernel;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClMem;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.EndiannessConverter;
import net.ladenthin.bitcoinaddressfinder.util.PrivateKeyTooLargeException;
import net.ladenthin.bitcoinaddressfinder.util.PrivateKeyValidator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates one OpenCL kernel invocation: manages source/destination buffers and runs the kernel.
 */
@ToString
public class OpenClTask implements ReleaseCLObject {

    /** SLF4J logger for this task. */
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenClTask.class);

    private static final int PRIVATE_KEY_SOURCE_SIZE_IN_BYTES = OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES;

    /** Sentinel meaning "no profiling timestamp captured for the most recent launch". */
    public static final long PROFILING_NOT_AVAILABLE = -1L;

    /** Kernel argument positions, in the order {@code generateKeysKernel_grid} declares them. */
    private static final int ARG_DESTINATION = 0;

    private static final int ARG_PRIVATE_KEY_SOURCE = 1;
    private static final int ARG_KEYS_PER_WORK_ITEM = 2;
    private static final int ARG_FILTER_FINGERPRINTS = 3;
    private static final int ARG_FILTER_METADATA = 4;
    private static final int ARG_TRANSFER_ALL = 5;

    /**
     * {@code filter_type} sits directly behind {@code transfer_all} in the kernel signature, so the
     * table buffers that follow it occupy 7 and 8 rather than 6 and 7.
     */
    private static final int ARG_FILTER_TYPE = 6;

    private static final int ARG_IG_TABLE = 7;
    private static final int ARG_COMB_TABLE = 8;

    @ToString.Exclude
    private final ClApi clApi;

    private final CProducerOpenCL cProducer;

    // SourceArgument carries its own ByteBuffer payload — heavy and not useful in logs.
    @ToString.Exclude
    private final SourceArgument privateKeySourceArgument;

    // Reusable GPU output buffer (write-only). Allocated once at the fixed per-batch result size and
    // reused for every executeKernel() launch, instead of creating and releasing it per launch (the
    // ~100+ MB device buffer alloc/free dominated per-launch host overhead). It is safe to reuse a
    // single device buffer because executeKernel() uses it strictly synchronously (kernel write +
    // readback, each followed by a finish, on the single producer thread) before returning; the
    // asynchronous result readers only ever touch the host-side ByteBuffer copy, never this buffer.
    // Released in close().
    @ToString.Exclude
    private final ClMem dstMem;

    // Pool of reusable host-side readback buffers (full per-batch result size). Unlike dstMem, each
    // launch's host buffer is handed to an OpenCLGridResult and read ASYNCHRONOUSLY by the
    // result-reader pool, so it must not be shared with the next launch until that reader is done.
    // The pool isolates in-flight buffers (up to maxResultReaderThreads, the same peak as before)
    // while avoiding a fresh 100+ MB allocateDirect (and its zeroing) on every launch: executeKernel
    // checks one out; OpenCLGridResult.close() (called by the reader when finished) returns it.
    // A caller that never closes its result simply lets the buffer be GC'd (no reuse, no leak), so
    // reuse is an optimisation, not a correctness requirement. Thread-safe: checkout on the single
    // producer thread, return on reader threads.
    @ToString.Exclude
    private final Queue<ByteBuffer> hostBufferPool = new ConcurrentLinkedQueue<>();

    private final BitHelper bitHelper;
    private final ByteBufferUtility byteBufferUtility;
    private final BigInteger maxPrivateKeyForBatchSize;
    private final PrivateKeyValidator privateKeyValidator;

    private volatile boolean closed = false;

    // Device-side nanosecond timings of the most recent executeKernel() call. Only populated when
    // CProducerOpenCL.enableProfiling is true (a diagnostic/benchmark switch); otherwise they stay
    // at PROFILING_NOT_AVAILABLE. Mutated from executeKernel and read by the benchmark; volatile so
    // a reader on another thread sees a consistent value. Excluded from toString (mutable state).
    @ToString.Exclude
    private volatile long lastKernelExecutionNanos = PROFILING_NOT_AVAILABLE;

    @ToString.Exclude
    private volatile long lastResultReadbackNanos = PROFILING_NOT_AVAILABLE;

    /**
     * Kernel input buffer backed by host memory the caller keeps alive.
     *
     * <p>The device buffer is created over the host allocation rather than as a copy, which is what
     * lets the private key be rewritten between launches without reallocating anything.
     */
    public static class SourceArgument implements ReleaseCLObject {

        private final ClApi clApi;
        private final ByteBuffer byteBuffer;
        private final ClMem mem;
        private boolean closed = false;

        private SourceArgument(ClApi clApi, ByteBuffer byteBuffer, ClMem mem) {
            this.clApi = clApi;
            this.byteBuffer = byteBuffer;
            this.mem = mem;
        }

        /**
         * Allocates a new source buffer.
         *
         * @param clApi       the OpenCL API to allocate through
         * @param context     the OpenCL context
         * @param sizeInBytes the buffer size in bytes
         * @return the created {@link SourceArgument}
         */
        public static SourceArgument create(ClApi clApi, ClContext context, long sizeInBytes) {
            final ByteBuffer byteBuffer =
                    ByteBuffer.allocateDirect(ByteBufferUtility.ensureByteBufferCapacityFitsInt(sizeInBytes));
            final ClMem mem = clApi.createBufferFromHostMemory(
                    context, ClBufferFlags.READ_ONLY | ClBufferFlags.USE_HOST_PTR, byteBuffer);
            return new SourceArgument(clApi, byteBuffer, mem);
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
         * Returns the wrapped device memory object.
         *
         * @return the wrapped device memory object
         */
        public ClMem getMem() {
            return mem;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (!closed) {
                clApi.releaseMemObject(mem);
                closed = true;
            }
        }
    }

    /**
     * Creates a new OpenCL task. Only usable after the surrounding {@link OpenCLContext} has been initialised.
     *
     * @param clApi             the OpenCL API to work through
     * @param context           the OpenCL context
     * @param cProducer         the OpenCL producer configuration
     * @param bitHelper         bit/batch-size helper
     * @param byteBufferUtility byte-buffer helper used for endian conversion
     */
    public OpenClTask(
            ClApi clApi,
            ClContext context,
            CProducerOpenCL cProducer,
            BitHelper bitHelper,
            ByteBufferUtility byteBufferUtility) {
        this.clApi = clApi;
        this.cProducer = cProducer;
        this.bitHelper = bitHelper;
        this.byteBufferUtility = byteBufferUtility;
        this.privateKeyValidator = new PrivateKeyValidator();
        this.maxPrivateKeyForBatchSize = privateKeyValidator.getMaxPrivateKeyForBatchSize(cProducer.batchSizeInBits);
        this.privateKeySourceArgument = SourceArgument.create(clApi, context, PRIVATE_KEY_SOURCE_SIZE_IN_BYTES);
        // Allocate the reusable GPU output buffer once at the fixed per-batch size (constant for this
        // task's lifetime). Reused by every executeKernel() launch; released in close(). Uses the
        // static size helper to avoid a this-escape (no overridable instance call in the constructor).
        this.dstMem = clApi.createBuffer(context, ClBufferFlags.WRITE_ONLY, dstSizeInBytes(cProducer));
    }

    /**
     * Returns the size of the destination buffer for the current batch.
     *
     * @return the size of the destination buffer in bytes for the current batch
     */
    public long getDstSizeInBytes() {
        return dstSizeInBytes(cProducer);
    }

    /**
     * Computes the destination buffer size for the given producer config. {@code static} so the
     * constructor can size the reusable {@code dstMem} without calling an overridable instance
     * method ({@code this}-escape during construction).
     *
     * @param cProducer the OpenCL producer configuration
     * @return the destination buffer size in bytes
     */
    private static long dstSizeInBytes(CProducerOpenCL cProducer) {
        // Unified output buffer: a 4-byte count header followed by overallWorkSize entries of
        // OUTPUT_ENTRY_SIZE_BYTES (108) each. This is also exactly compact mode's worst case
        // (every candidate is a filter hit), so the single allocation covers both write modes.
        // The (long) cast forces 64-bit multiplication before the addition, preventing int
        // overflow for large work sizes (108 × MAXIMUM_CHUNK_ELEMENTS ≈ 2.1 GB).
        return OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES
                + (long) OpenClKernelConstants.OUTPUT_ENTRY_SIZE_BYTES * cProducer.getOverallWorkSize();
    }

    /**
     * Checks out a host-side readback buffer: reuses one from the pool, or allocates a fresh
     * full-size direct buffer if the pool is empty. The returned buffer has position 0 so the
     * transfer reads from the start.
     *
     * @return a host readback buffer of {@link #getDstSizeInBytes()} bytes
     */
    private ByteBuffer acquireHostBuffer() {
        final ByteBuffer pooled = hostBufferPool.poll();
        if (pooled != null) {
            pooled.clear();
            return pooled;
        }
        return ByteBuffer.allocateDirect(ByteBufferUtility.ensureByteBufferCapacityFitsInt(getDstSizeInBytes()));
    }

    /**
     * Returns a host-side readback buffer to the pool for reuse by a later launch. Called by
     * {@link OpenCLGridResult#close()} once the asynchronous reader has finished with it.
     *
     * @param buffer the host readback buffer to return to the pool
     */
    void releaseHostBuffer(ByteBuffer buffer) {
        // Unbounded ConcurrentLinkedQueue: offer() never fails, but its boolean result must not be
        // silently dropped (SpotBugs RV_RETURN_VALUE_IGNORED). Should a future bounded pool ever
        // reject the buffer, leave it for the JVM Cleaner to reclaim rather than retrying.
        if (!hostBufferPool.offer(buffer)) {
            LOGGER.trace("Host buffer pool rejected a returned buffer; leaving it for GC.");
        }
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
     * <p>Binds the two GPU Binary Fuse filter buffers and the {@code transfer_all} mode flag
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
     * @param filterType    the {@code GpuFilterType} wire value telling the kernel how wide the
     *     fingerprint slots in {@code fuse8FpMem} are ({@code 0} = Binary Fuse 8, {@code 1} =
     *     Binary Fuse 16). The buffer is uploaded as raw bytes either way and reinterpreted on the
     *     device, so this value must match the width that was actually uploaded — a mismatch does
     *     not fail, it silently produces false negatives.
     * @param iGTableMem    the GPU {@code i·G} table buffer for the affine scalar walk (a one-byte
     *     placeholder when {@code keysPerWorkItem == 1})
     * @param combTableMem  the GPU fixed-base comb table buffer used to compute the {@code P0} anchor
     * @return the destination buffer containing kernel results
     */
    public ByteBuffer executeKernel(
            ClKernel kernel,
            ClCommandQueue commandQueue,
            ClMem fuse8FpMem,
            ClMem fuse8MetaMem,
            int transferAll,
            int filterType,
            ClMem iGTableMem,
            ClMem combTableMem) {
        // Host-side readback buffer for this launch. Checked out from the pool (reused, see field
        // doc) or freshly allocated if the pool is empty; returned by OpenCLGridResult.close().
        final ByteBuffer dstByteBuffer = acquireHostBuffer();

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

        final long[] globalWorkSize = new long[] {adjustedWorkSize};

        // Set the arguments for the kernel
        clApi.setKernelArg(kernel, ARG_DESTINATION, dstMem);
        clApi.setKernelArg(kernel, ARG_PRIVATE_KEY_SOURCE, privateKeySourceArgument.getMem());
        clApi.setKernelArgInt(kernel, ARG_KEYS_PER_WORK_ITEM, keysPerWorkItem);
        clApi.setKernelArg(kernel, ARG_FILTER_FINGERPRINTS, fuse8FpMem);
        clApi.setKernelArg(kernel, ARG_FILTER_METADATA, fuse8MetaMem);
        clApi.setKernelArgInt(kernel, ARG_TRANSFER_ALL, transferAll);
        clApi.setKernelArgInt(kernel, ARG_FILTER_TYPE, filterType);
        clApi.setKernelArg(kernel, ARG_IG_TABLE, iGTableMem);
        clApi.setKernelArg(kernel, ARG_COMB_TABLE, combTableMem);

        {
            // write src buffer. The transfer length is the buffer's remaining bytes, so the window
            // is set explicitly rather than relying on whatever position the last writer left.
            final ByteBuffer source = privateKeySourceArgument.getByteBuffer();
            source.position(0).limit(PRIVATE_KEY_SOURCE_SIZE_IN_BYTES);
            clApi.enqueueWriteBuffer(commandQueue, privateKeySourceArgument.getMem(), 0L, source);
            clApi.finish(commandQueue);
        }
        {
            // zero-initialise the leading count word so compact mode's atomic_add starts at
            // 0 (full mode's work-item 0 overwrites it with the sentinel). Also resets any stale
            // count left in the reused dstMem from the previous launch.
            final ByteBuffer zeroHeader = ByteBuffer.allocateDirect(OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES);
            clApi.enqueueWriteBuffer(commandQueue, dstMem, 0L, zeroHeader);
            clApi.finish(commandQueue);
        }
        final boolean profiling = cProducer.enableProfiling;
        {
            // execute the kernel
            final long beforeExecute = System.currentTimeMillis();
            final @Nullable ClEvent kernelEvent =
                    clApi.enqueueNDRangeKernel(commandQueue, kernel, globalWorkSize, profiling);
            clApi.finish(commandQueue);
            lastKernelExecutionNanos = consumeElapsedNanos(kernelEvent);

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
            clApi.enqueueReadBuffer(commandQueue, dstMem, 0L, countHeader, false);
            clApi.finish(commandQueue);

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
            // The read length is the destination's remaining bytes, so the window is narrowed to
            // exactly the produced range — this is where compact mode's bandwidth saving happens.
            dstByteBuffer.position(0).limit(ByteBufferUtility.ensureByteBufferCapacityFitsInt(bytesToRead));
            final @Nullable ClEvent readEvent =
                    clApi.enqueueReadBuffer(commandQueue, dstMem, 0L, dstByteBuffer, profiling);
            clApi.finish(commandQueue);
            lastResultReadbackNanos = consumeElapsedNanos(readEvent);
            dstByteBuffer.position(0);

            final long afterRead = System.currentTimeMillis();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Read OpenCL data " + ((bytesToRead / 1024) / 1024) + "Mb in " + (afterRead - beforeRead)
                        + "ms");
            }
        }
        return dstByteBuffer;
    }

    /**
     * Reads and releases a profiling event, if one was captured.
     *
     * @param event the event captured for the command, or {@code null} when profiling is off
     * @return the on-device duration in nanoseconds, or {@link #PROFILING_NOT_AVAILABLE}
     */
    private long consumeElapsedNanos(@Nullable ClEvent event) {
        if (event == null) {
            return PROFILING_NOT_AVAILABLE;
        }
        try {
            return clApi.getEventElapsedNanos(event);
        } finally {
            clApi.releaseEvent(event);
        }
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
            // Release the reusable GPU output buffer. Safe here: close() runs only after the
            // producer has stopped issuing launches; the asynchronous result readers never touch
            // dstMem (they read the host-side ByteBuffer copies), so there is no use-after-free.
            clApi.releaseMemObject(dstMem);
            closed = true;
        }
    }
}
