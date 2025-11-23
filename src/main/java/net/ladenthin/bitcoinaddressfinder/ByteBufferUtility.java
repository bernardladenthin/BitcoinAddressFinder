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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import jdk.internal.misc.Unsafe;
import org.bouncycastle.util.encoders.Hex;
import org.jspecify.annotations.Nullable;

public class ByteBufferUtility {
    
    /**
     * Decide between {@link java.nio.DirectByteBuffer} and {@link java.nio.HeapByteBuffer}.
     */
    private final boolean allocateDirect;

    public ByteBufferUtility(boolean allocateDirect) {
        this.allocateDirect = allocateDirect;
    }
    
    /**
     * ATTENTION: The {@link Unsafe#getUnsafe} can throw an {@link java.lang.IllegalAccessError}.
     * https://stackoverflow.com/questions/8462200/examples-of-forcing-freeing-of-native-memory-direct-bytebuffer-has-allocated-us
     * https://stackoverflow.com/questions/13003871/how-do-i-get-the-instance-of-sun-misc-unsafe
     * https://stackoverflow.com/questions/29301755/got-securityexception-in-java
     * https://bugs.openjdk.org/browse/JDK-8171377
     * @param byteBuffer the ByteBuffer to free
     */
    public void freeByteBuffer(@Nullable ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return;
        }

        if (!byteBuffer.isDirect()) {
            return;
        }

        Unsafe u = Unsafe.getUnsafe();
        // https://bugs.openjdk.org/browse/JDK-8171377
        // https://openjdk.org/jeps/8323072
        // https://stackoverflow.com/questions/3496508/deallocating-direct-buffer-native-memory-in-java-for-jogl/26777380
        u.invokeCleaner(byteBuffer);
    }
    
    // <editor-fold defaultstate="collapsed" desc="ByteBuffer byte array conversion">
    public byte[] byteBufferToBytes(ByteBuffer byteBuffer) {
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        byteBuffer.rewind();
        return bytes;
    }
    
    public ByteBuffer byteArrayToByteBuffer(byte[] bytes) {
        if (allocateDirect) { 
            return byteArrayToByteBufferAllocatedDirect(bytes);
        } else {
            return byteArrayToByteBufferWrapped(bytes);
        }
    }

    private ByteBuffer byteArrayToByteBufferWrapped(byte[] bytes) {
        // wrap() delivers a buffer which is already flipped
        ByteBuffer wrap = ByteBuffer.wrap(bytes);
        return wrap;
    }

    private ByteBuffer byteArrayToByteBufferAllocatedDirect(byte[] bytes) {
        ByteBuffer key = ByteBuffer.allocateDirect(bytes.length);
        key.put(bytes).flip();
        return key;
    }
    
    /**
    * Writes a BigInteger into a ByteBuffer.
    *
    * @param buffer The ByteBuffer to write to.
    * @param byteArray The byte array to write.
    */
   public static void putToByteBuffer(ByteBuffer buffer, byte[] byteArray) {
       buffer.clear();
       buffer.put(byteArray, 0, byteArray.length);
       buffer.rewind();
   }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="ByteBuffer Hex conversion">
    public String getHexFromByteBuffer(ByteBuffer byteBuffer) {
        byte[] array = byteBufferToBytes(byteBuffer);
        String hexString = Hex.toHexString(array);
        return hexString;
    }

    public ByteBuffer getByteBufferFromHex(String hex) {
        byte[] decoded = Hex.decode(hex);
        // wrap() delivers a buffer which is already flipped
        final ByteBuffer byteBuffer = byteArrayToByteBuffer(decoded);
        return byteBuffer;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="ensureByteBufferCapacityFitsInt">
    /**
    * Validates that the given capacity fits within Java's ByteBuffer limit.
    * 
    * @param capacity the desired buffer capacity in bytes
    * @return the same value as an int, if within bounds
    * @throws IllegalArgumentException if capacity exceeds Integer.MAX_VALUE or is negative
    */
   public static int ensureByteBufferCapacityFitsInt(long capacity) {
       if (capacity < 0) {
           throw new IllegalArgumentException("Capacity must not be negative: " + capacity);
       }
       if (capacity > Integer.MAX_VALUE) {
           throw new IllegalArgumentException("Capacity exceeds maximum ByteBuffer limit: " + capacity);
       }
       return (int) capacity;
   }
    // </editor-fold>
   
    // <editor-fold defaultstate="collapsed" desc="allocateByteBufferDirectStrict (enforce direct allocation)">
    /**
     * Allocates a {@link ByteBuffer} strictly using {@link ByteBuffer#allocateDirect(int)}.
     * <p>
     * This method enforces that {@code allocateDirect == true}.
     * It throws an {@link IllegalStateException} if heap allocation is enabled.
     * Useful when native memory access is required, e.g. for OpenCL interop.
     *
     * @param capacity the number of bytes to allocate
     * @return a direct {@link ByteBuffer}
     * @throws IllegalStateException if not configured for direct allocation
     */
    public ByteBuffer allocateByteBufferDirectStrict(int capacity) {
        if (!allocateDirect) {
            throw new IllegalStateException("Direct allocation requested, but allocateDirect is false.");
        }
        return ByteBuffer.allocateDirect(capacity);
    }
    // </editor-fold>
    
    /**
     * https://bitbucket.org/connect2id/nimbus-srp/pull-requests/6/remove-leading-zero-byte-when-converting/diff
     * Converts a BigInteger into a byte array ignoring the sign of the
     * BigInteger, according to SRP specification
     *
     * @param bigInteger BigInteger, must not be null
     *
     * @return byte array (leading byte is always != 0), empty array if
     * BigInteger is zero.
     */
    public static byte[] bigIntegerToBytes(final BigInteger bigInteger) {
        byte[] bytes = bigInteger.toByteArray();
        if (bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
    
    private final static boolean USE_XOR_SWAP = false;
    
    /**
     * https://stackoverflow.com/questions/12893758/how-to-reverse-the-byte-array-in-java
     */
    public void reverse(byte[] array) {
        if (array == null) {
            return;
        }
        if (USE_XOR_SWAP) {
            int len = array.length;
            for (int i = 0; i < len / 2; i++) {
                array[i] ^= array[len - i - 1];
                array[len - i - 1] ^= array[i];
                array[i] ^= array[len - i - 1];
            }
        } else {
            int i = 0;
            int j = array.length - 1;
            byte tmp;
            while (j > i) {
                tmp = array[j];
                array[j] = array[i];
                array[i] = tmp;
                j--;
                i++;
            }
        }
    }

}
