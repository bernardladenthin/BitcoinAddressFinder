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

public class OpenCLGridResult {

    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
    
    private final BigInteger secretKeyBase;
    private final int workSize;
    private ByteBuffer result;
    
    OpenCLGridResult(BigInteger secretKeyBase, int workSize, ByteBuffer result) {
        this.secretKeyBase = secretKeyBase;
        this.workSize = workSize;
        this.result = result;
    }

    public BigInteger getSecretKeyBase() {
        return secretKeyBase;
    }

    public int getWorkSize() {
        return workSize;
    }

    public ByteBuffer getResult() {
        return result;
    }
    
    public void freeResult() {
        // free and do not use anymore
        byteBufferUtility.freeByteBuffer(result);
        result = null;
    }
    
    /**
     * Time consuming.
     */
    public PublicKeyBytes[] getPublicKeyBytes() {
        PublicKeyBytes[] publicKeys = new PublicKeyBytes[workSize];
        for (int i = 0; i < workSize; i++) {
            PublicKeyBytes publicKeyBytes = getPublicKeyFromByteBufferXY(result, i, secretKeyBase);
            publicKeys[i] = publicKeyBytes;
        }
        return publicKeys;
    }
    
    /**
     * Read the inner bytes in reverse order.
     */
    private static final PublicKeyBytes getPublicKeyFromByteBufferXY(ByteBuffer b, int keyNumber, BigInteger secretKeyBase) {
        BigInteger secret = AbstractProducer.calculateSecretKey(secretKeyBase, keyNumber);
        if(BigInteger.ZERO.equals(secret)) {
            // the calculated key is invalid, return a fallback
            return PublicKeyBytes.INVALID_KEY_ONE;
        }
        byte[] uncompressed = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES];
        uncompressed[0] = PublicKeyBytes.PARITY_UNCOMPRESSED;
        
        int keyOffsetInByteBuffer = PublicKeyBytes.TWO_COORDINATES_NUM_BYTES*keyNumber;
        
        // read ByteBuffer
        byte[] yx = new byte[PublicKeyBytes.TWO_COORDINATES_NUM_BYTES];
        for (int i = 0; i < PublicKeyBytes.TWO_COORDINATES_NUM_BYTES; i++) {
            yx[yx.length-1-i] = b.get(keyOffsetInByteBuffer+i);
        }
        
        // copy x
        System.arraycopy(yx, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES, uncompressed, PublicKeyBytes.PARITY_BYTES_LENGTH, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        // copy y
        System.arraycopy(yx, 0, uncompressed, PublicKeyBytes.PARITY_BYTES_LENGTH+PublicKeyBytes.ONE_COORDINATE_NUM_BYTES, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        
        if (false) {
            assertValidResult(uncompressed);
        }
        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secret, uncompressed);
        return publicKeyBytes;
    }
    
    private static void assertValidResult(byte[] uncompressed) {
        boolean invalid = true;
        for (int i = 1; i < uncompressed.length; i++) {
            if (uncompressed[i] != 0) {
                invalid = false;
                break;
            }
        }
        if (invalid) {
            throw new RuntimeException("Invalid result from GPU, all uncompressed key bytes are 0.");
        }
    }
    
}
