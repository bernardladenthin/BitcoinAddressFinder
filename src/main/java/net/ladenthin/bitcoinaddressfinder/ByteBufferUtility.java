// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.bouncycastle.util.encoders.Hex;
import org.jspecify.annotations.NonNull;

/**
 * Helper for {@link ByteBuffer} allocation, byte-array conversion and reversal.
 */
@ToString
@EqualsAndHashCode
public class ByteBufferUtility {

    /**
     * Default value for {@link #useXorSwap}: temporary-variable swap.
     * https://stackoverflow.com/questions/12893758/how-to-reverse-the-byte-array-in-java
     */
    private static final boolean DEFAULT_USE_XOR_SWAP = false;

    /**
     * Decide between {@link java.nio.DirectByteBuffer} and {@link java.nio.HeapByteBuffer}.
     */
    private final boolean allocateDirect;

    /**
     * Selects the byte-array reversal algorithm used by {@link #reverse(byte[])}.
     * {@code true} uses XOR swap (no temporary variable);
     * {@code false} uses a temporary-variable swap.
     */
    private final boolean useXorSwap;

    /**
     * Creates a new instance using the default swap algorithm.
     *
     * @param allocateDirect whether to allocate direct (off-heap) byte buffers
     */
    public ByteBufferUtility(boolean allocateDirect) {
        this(allocateDirect, DEFAULT_USE_XOR_SWAP);
    }

    /**
     * Creates a new instance with full control over the swap algorithm.
     *
     * @param allocateDirect whether to allocate direct (off-heap) byte buffers
     * @param useXorSwap     whether {@link #reverse(byte[])} uses the XOR swap algorithm
     */
    public ByteBufferUtility(boolean allocateDirect, boolean useXorSwap) {
        this.allocateDirect = allocateDirect;
        this.useXorSwap = useXorSwap;
    }

    // <editor-fold defaultstate="collapsed" desc="ByteBuffer byte array conversion">
    /**
     * Copies the remaining content of {@code byteBuffer} into a new byte array.
     *
     * @param byteBuffer the buffer to read; rewound on return
     * @return a new byte array with the buffer's content
     */
    public byte[] byteBufferToBytes(ByteBuffer byteBuffer) {
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        byteBuffer.rewind();
        return bytes;
    }

    /**
     * Wraps or copies {@code bytes} into a new {@link ByteBuffer} depending on the
     * {@code allocateDirect} flag configured for this instance.
     *
     * @param bytes the source byte array
     * @return a ready-to-read {@link ByteBuffer}
     */
    public ByteBuffer byteArrayToByteBuffer(byte[] bytes) {
        if (allocateDirect) {
            return byteArrayToByteBufferAllocatedDirect(bytes);
        } else {
            return byteArrayToByteBufferWrapped(bytes);
        }
    }

    private ByteBuffer byteArrayToByteBufferWrapped(byte[] bytes) {
        // wrap() delivers a buffer which is already flipped
        return ByteBuffer.wrap(bytes);
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
    /**
     * Encodes a {@link ByteBuffer} as a lower-case hex string.
     *
     * @param byteBuffer the buffer to encode
     * @return the lower-case hex representation of the buffer's content
     */
    public String getHexFromByteBuffer(ByteBuffer byteBuffer) {
        byte[] array = byteBufferToBytes(byteBuffer);
        return Hex.toHexString(array);
    }

    /**
     * Decodes a hex string into a {@link ByteBuffer}.
     *
     * @param hex the hex-encoded input
     * @return a buffer containing the decoded bytes
     */
    public ByteBuffer getByteBufferFromHex(String hex) {
        byte[] decoded = Hex.decode(hex);
        // wrap() delivers a buffer which is already flipped
        return byteArrayToByteBuffer(decoded);
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
            throw new IllegalStateException(
                    "Direct ByteBuffer allocation requested (capacity=" + capacity
                            + " bytes) but this ByteBufferUtility was constructed with allocateDirect=false");
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

    /**
     * Reverses the given byte array in place.
     * <p>See https://stackoverflow.com/questions/12893758/how-to-reverse-the-byte-array-in-java.
     *
     * @param array the array to reverse in place
     */
    public void reverse(byte @NonNull [] array) {
        if (useXorSwap) {
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
