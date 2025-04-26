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
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;

public class OpenCLGridResult {
    
    /**
     * Enable additional validation to check if generated uncompressed keys are not all zero.
     * <p>
     * This should remain <b>disabled in production</b> to avoid unnecessary performance overhead.
     * Useful only during debugging or when validating OpenCL/GPU kernel correctness.
     * </p>
     * <p>
     * Superseded by {@link PublicKeyBytes#runtimePublicKeyCalculationCheck(org.slf4j.Logger)}
     * and its activation via {@link CConsumerJava#runtimePublicKeyCalculationCheck}.
     * </p>
     */
    private static final boolean ENABLE_UNCOMPRESSED_KEY_VALIDATION = false;

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
        final int resultBlockSize = PublicKeyBytes.TWO_COORDINATES_NUM_BYTES;
        final int keyOffsetInByteBuffer = resultBlockSize*keyNumber;
        
        // Read XY block
        byte[] kernelResultBlock = new byte[resultBlockSize];
        b.get(keyOffsetInByteBuffer, kernelResultBlock, 0, resultBlockSize);
        
        // Extract and reverse X
        byte[] x = new byte[PublicKeyBytes.ONE_COORDINATE_NUM_BYTES];
        System.arraycopy(kernelResultBlock, 0, x, 0, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        // LSB to MSB: From Java to OpenCL
        ByteBufferUtility.reverse(x);
        
        // Extract and reverse Y
        byte[] y = new byte[PublicKeyBytes.ONE_COORDINATE_NUM_BYTES];
        System.arraycopy(kernelResultBlock, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES, y, 0, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        // LSB to MSB: From Java to OpenCL
        ByteBufferUtility.reverse(y);
        
        // Assemble uncompressed key
        byte[] uncompressed = PublicKeyBytes.assembleUncompressedPublicKey(x,y);
        
        if (ENABLE_UNCOMPRESSED_KEY_VALIDATION) {
            boolean allZero = PublicKeyBytes.isAllCoordinateBytesZero(uncompressed);
            if (allZero) {
                throw new RuntimeException("Invalid GPU result: all coordinate bytes are zero in uncompressed public key.");
            }
        }
        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secret, uncompressed);
        return publicKeyBytes;
    }
}
