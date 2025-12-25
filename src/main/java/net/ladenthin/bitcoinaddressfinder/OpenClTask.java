// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_USE_HOST_PTR;
import static org.jocl.CL.CL_MEM_WRITE_ONLY;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clFinish;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clSetKernelArg;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenClTask implements ReleaseCLObject {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private final static int PRIVATE_KEY_SOURCE_SIZE_IN_BYTES = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES;
    
    private final CProducerOpenCL cProducer;

    private final cl_context context;
    
    private final SourceArgument privateKeySourceArgument;
    
    private final BitHelper bitHelper;
    private final ByteBufferUtility byteBufferUtility;
    private final BigInteger maxPrivateKeyForBatchSize;
    
    private boolean closed = false;

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

        protected final ByteBuffer byteBuffer;
        protected final Pointer hostMemoryPointer;
        protected final cl_mem mem;
        protected final Pointer clMemPointer;
        private boolean closed = false;

        public CLByteBufferPointerArgument(ByteBuffer byteBuffer, Pointer hostMemoryPointer, cl_mem mem, Pointer clMemPointer) {
            this.byteBuffer = byteBuffer;
            this.hostMemoryPointer = hostMemoryPointer;
            this.mem = mem;
            this.clMemPointer = clMemPointer;
        }

        public ByteBuffer getByteBuffer() {
            return byteBuffer;
        }

        /** Used for reading/writing data to the host via clEnqueueRead/WriteBuffer. */
        public Pointer getHostMemoryPointer() {
            return hostMemoryPointer;
        }

        /** Used to pass the buffer to the kernel via clSetKernelArg. */
        public Pointer getClMemPointer() {
            return clMemPointer;
        }

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

    public static class DestinationArgument extends CLByteBufferPointerArgument {
        
        private DestinationArgument(ByteBuffer byteBuffer, Pointer hostMemoryPointer, cl_mem mem, Pointer clMemPointer) {
            super(byteBuffer, hostMemoryPointer, mem, clMemPointer);
        }

        public static DestinationArgument create(cl_context context, long sizeInBytes) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(ByteBufferUtility.ensureByteBufferCapacityFitsInt(sizeInBytes));
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
    
    public static class SourceArgument extends CLByteBufferPointerArgument {

        private SourceArgument(ByteBuffer byteBuffer, Pointer hostMemoryPointer, cl_mem mem, Pointer clMemPointer) {
            super(byteBuffer, hostMemoryPointer, mem, clMemPointer);
        }

        public static SourceArgument create(cl_context context, long sizeInBytes) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(ByteBufferUtility.ensureByteBufferCapacityFitsInt(sizeInBytes));
            final Pointer hostMemoryPointer = Pointer.to(byteBuffer);
            final cl_mem mem = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_USE_HOST_PTR, sizeInBytes, hostMemoryPointer, null);
            final Pointer clMemPointer = Pointer.to(mem);
            return new SourceArgument(byteBuffer, hostMemoryPointer, mem, clMemPointer);
        }
    }

    // Only available after init
    public OpenClTask(cl_context context, CProducerOpenCL cProducer, BitHelper bitHelper, ByteBufferUtility byteBufferUtility) {
        this.context = context;
        this.cProducer = cProducer;
        this.bitHelper = bitHelper;
        this.byteBufferUtility = byteBufferUtility;
        this.maxPrivateKeyForBatchSize = KeyUtility.getMaxPrivateKeyForBatchSize(cProducer.batchSizeInBits);
        this.privateKeySourceArgument = SourceArgument.create(context, PRIVATE_KEY_SOURCE_SIZE_IN_BYTES);
    }

    public long getDstSizeInBytes() {
        return (long) PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * cProducer.getOverallWorkSize(bitHelper);
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
        if (KeyUtility.isInvalidWithBatchSize(privateKeyBase, maxPrivateKeyForBatchSize)) {
            throw new PrivateKeyTooLargeException(privateKeyBase, maxPrivateKeyForBatchSize, cProducer.batchSizeInBits);
        }

        // BigInteger.toByteArray() always returns a big-endian (MSB-first) representation, 
        // meaning the most significant byte (MSB) comes first.
        // Therefore, the source format is always Big Endian.
        final byte[] byteArray = ByteBufferUtility.bigIntegerToBytes(privateKeyBase);
        EndiannessConverter endiannessConverter = new EndiannessConverter(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN, byteBufferUtility);
        endiannessConverter.convertEndian(byteArray);
        ByteBufferUtility.putToByteBuffer(privateKeySourceArgument.getByteBuffer(), byteArray);
    }
    
    @VisibleForTesting
    public SourceArgument getPrivateKeySourceArgument() {
        return privateKeySourceArgument;
    }

    public ByteBuffer executeKernel(cl_kernel kernel, cl_command_queue commandQueue) {
        final long dstSizeInBytes = getDstSizeInBytes();
        // Allocate a new destination buffer so that cloning after kernel execution is unnecessary
        try (final DestinationArgument destinationArgument = DestinationArgument.create(context, dstSizeInBytes) ) {
            // Set the work-item dimensions
            final long totalResultCount = bitHelper.convertBitsToSize(cProducer.batchSizeInBits);
            final int loopCount = cProducer.loopCount;
            final long adjustedWorkSize = totalResultCount / loopCount;
            
            // Validate loopCount constraints
            if (loopCount < 1) {
                throw new IllegalArgumentException("loopCount must be >= 1.");
            }
            if (loopCount > totalResultCount) {
                throw new IllegalArgumentException("loopCount must not exceed total result count. Given: " + loopCount + ", max: " + totalResultCount);
            }
            if (totalResultCount % loopCount != 0) {
                throw new IllegalArgumentException("batchSizeInBits is not divisible by loopCount; result count would be invalid.");
            }
            
            final long[] global_work_size = new long[]{adjustedWorkSize};
            final long[] localWorkSize = null; // new long[]{1}; // enabling the system to choose the work-group size.
            final int workDim = 1;
            
            // Set the arguments for the kernel
            clSetKernelArg(kernel, 0, Sizeof.cl_mem, destinationArgument.getClMemPointer());
            clSetKernelArg(kernel, 1, Sizeof.cl_mem, privateKeySourceArgument.getClMemPointer());
            clSetKernelArg(kernel, 2, Sizeof.cl_uint, Pointer.to(new int[] { loopCount }));

            {
                // write src buffer
                clEnqueueWriteBuffer(commandQueue,
                        privateKeySourceArgument.getMem(),
                        CL_TRUE,
                        0,
                        PRIVATE_KEY_SOURCE_SIZE_IN_BYTES,
                        privateKeySourceArgument.getHostMemoryPointer(),
                        0,
                        null,
                        null
                );
                clFinish(commandQueue);
            }
            {
                // execute the kernel
                final long beforeExecute = System.currentTimeMillis();
                clEnqueueNDRangeKernel(
                        commandQueue,
                        kernel,
                        workDim,
                        null,
                        global_work_size,
                        localWorkSize,
                        0,
                        null,
                        null
                );
                clFinish(commandQueue);

                final long afterExecute = System.currentTimeMillis();

                if (logger.isTraceEnabled()) {
                    logger.trace("Executed OpenCL kernel in " + (afterExecute - beforeExecute) + "ms");
                }
            }
            {
                // read the dst buffer
                final long beforeRead = System.currentTimeMillis();

                clEnqueueReadBuffer(commandQueue,
                        destinationArgument.getMem(),
                        CL_TRUE,
                        0,
                        dstSizeInBytes,
                        destinationArgument.getHostMemoryPointer(),
                        0,
                        null,
                        null
                );
                clFinish(commandQueue);
                destinationArgument.close();

                final long afterRead = System.currentTimeMillis();
                if (logger.isTraceEnabled()) {
                    logger.trace("Read OpenCL data "+((dstSizeInBytes / 1024) / 1024) + "Mb in " + (afterRead - beforeRead) + "ms");
                }
            }
            return destinationArgument.getByteBuffer();
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
    
    @Override
    public void close() {
        if(!closed) {
            privateKeySourceArgument.close();
            closed = true;
            // hint: destinationArgument will be released immediately
        }
    }

    /**
     * https://stackoverflow.com/questions/3366925/deep-copy-duplicate-of-javas-bytebuffer/4074089
     */
    private static ByteBuffer cloneByteBuffer(final ByteBuffer original) {
        // Create clone with same capacity as original.
        final ByteBuffer clone = (original.isDirect())
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
