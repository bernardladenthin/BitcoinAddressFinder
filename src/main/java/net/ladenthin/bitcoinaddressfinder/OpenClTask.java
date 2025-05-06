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

public class OpenClTask {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    
    /**
     * I din't know which is better.
     */
    private static final boolean USE_HOST_PTR = false;
    
    private final CProducer cProducer;

    private final cl_context context;
    private final ByteBuffer srcByteBuffer;
    private final Pointer srcPointer;

    private final cl_mem srcMem;
    private final BitHelper bitHelper;
    private final ByteBufferUtility byteBufferUtility;
    private final BigInteger maxPrivateKeyForBatchSize;

    // Only available after init
    public OpenClTask(cl_context context, CProducer cProducer, BitHelper bitHelper, ByteBufferUtility byteBufferUtility) {
        this.context = context;
        this.cProducer = cProducer;
        this.bitHelper = bitHelper;
        this.byteBufferUtility = byteBufferUtility;

        int srcSizeInBytes = getSrcSizeInBytes();
        maxPrivateKeyForBatchSize = KeyUtility.getMaxPrivateKeyForBatchSize(cProducer.batchSizeInBits);
        srcByteBuffer = ByteBuffer.allocateDirect(srcSizeInBytes);
        srcPointer = Pointer.to(srcByteBuffer);
        srcMem = clCreateBuffer(
                context,
                CL_MEM_READ_ONLY | CL_MEM_USE_HOST_PTR,
                srcSizeInBytes,
                srcPointer,
                null
        );
    }

    public int getSrcSizeInBytes() {
        return PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES;
    }

    public long getDstSizeInBytes() {
        return (long) PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * bitHelper.convertBitsToSize(cProducer.batchSizeInBits);
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
        byte[] byteArray = byteBufferUtility.bigIntegerToBytes(privateKeyBase);
        EndiannessConverter endiannessConverter = new EndiannessConverter(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN, byteBufferUtility);
        endiannessConverter.convertEndian(byteArray);
        byteBufferUtility.putToByteBuffer(srcByteBuffer, byteArray);
    }
    
    @VisibleForTesting
    public ByteBuffer getSrcByteBuffer() {
        return srcByteBuffer;
    }

    public ByteBuffer executeKernel(cl_kernel kernel, cl_command_queue commandQueue) {
        final long dstSizeInBytes = getDstSizeInBytes();
        // allocate a new dst buffer that a clone afterwards is not necessary
        final ByteBuffer dstByteBuffer = ByteBuffer.allocateDirect(ByteBufferUtility.ensureByteBufferCapacityFitsInt(dstSizeInBytes));
        final Pointer dstPointer = Pointer.to(dstByteBuffer);
        final cl_mem dstMem;
        if (USE_HOST_PTR) {
            dstMem = clCreateBuffer(context,
                    CL_MEM_USE_HOST_PTR, dstSizeInBytes,
                    dstPointer,
                    null
            );
        } else {
            dstMem = clCreateBuffer(context,
                    CL_MEM_WRITE_ONLY, dstSizeInBytes,
                    null,
                    null
            );
        }

        // Set the arguments for the kernel
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(dstMem));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(srcMem));

        // Set the work-item dimensions
        long global_work_size[] = new long[]{bitHelper.convertBitsToSize(cProducer.batchSizeInBits)};
        long localWorkSize[] = null; // new long[]{1}; // enabling the system to choose the work-group size.
        int workDim = 1;

        {
            // write src buffer
            clEnqueueWriteBuffer(
                    commandQueue,
                    srcMem,
                    CL_TRUE,
                    0,
                    getSrcSizeInBytes(),
                    srcPointer,
                    0,
                    null,
                    null
            );
            clFinish(commandQueue);
        }
        {
            // execute the kernel
            long beforeExecute = System.currentTimeMillis();
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

            long afterExecute = System.currentTimeMillis();
            
            if (logger.isTraceEnabled()) {
                logger.trace("Executed OpenCL kernel in " + (afterExecute - beforeExecute) + "ms");
            }
        }
        {
            // read the dst buffer
            long beforeRead = System.currentTimeMillis();

            clEnqueueReadBuffer(commandQueue,
                    dstMem,
                    CL_TRUE,
                    0,
                    dstSizeInBytes,
                    dstPointer,
                    0,
                    null,
                    null
            );
            clFinish(commandQueue);
            clReleaseMemObject(dstMem);

            long afterRead = System.currentTimeMillis();
            if (logger.isTraceEnabled()) {
                logger.trace("Read OpenCL data "+((dstSizeInBytes / 1024) / 1024) + "Mb in " + (afterRead - beforeRead) + "ms");
            }
        }
        return dstByteBuffer;
    }

    public void releaseCl() {
        clReleaseMemObject(srcMem);
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
