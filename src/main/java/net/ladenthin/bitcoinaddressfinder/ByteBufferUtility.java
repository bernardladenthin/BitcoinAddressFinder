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

import java.nio.ByteBuffer;
import org.bouncycastle.util.encoders.Hex;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

public class ByteBufferUtility {
    
    /**
     * Decide between {@link java.nio.DirectByteBuffer} and {@link java.nio.HeapByteBuffer}.
     */
    private final boolean allocateDirect;

    public ByteBufferUtility(boolean allocateDirect) {
        this.allocateDirect = allocateDirect;
    }
    
    /**
     * https://stackoverflow.com/questions/8462200/examples-of-forcing-freeing-of-native-memory-direct-bytebuffer-has-allocated-us
     * @param byteBuffer the ByteBuffer to free 
     */
    public static void freeByteBuffer(ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return;
        }
        Cleaner cleaner = ((DirectBuffer) byteBuffer).cleaner();
        if (cleaner != null) {
            cleaner.clean();
        }
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
    
}
