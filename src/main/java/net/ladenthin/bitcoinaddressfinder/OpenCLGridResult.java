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
import java.nio.ByteOrder;
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
    private final ByteOrder clByteOrder;
    private final int workSize;
    private ByteBuffer result;
    
    OpenCLGridResult(BigInteger secretKeyBase, ByteOrder clByteOrder, int workSize, ByteBuffer result) {
        this.secretKeyBase = secretKeyBase;
        this.clByteOrder = clByteOrder;
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
     * Reads the computed public keys from the OpenCL result buffer and converts them into the correct format.
     * <p>
     * OpenCL writes 32-bit integers (u32) into memory using the device's native endianness (typically Little-Endian).
     * However, Bitcoin/ECC standards expect public keys in Big-Endian (MSB-first) byte order.
     * <p>
     * Therefore, after reading X and Y coordinates for each public key, the bytes of each coordinate
     * must be reversed if the device endianness differs from the target Big-Endian format.
     * <p>
     * The resulting format matches the uncompressed SEC (Standards for Efficient Cryptography) format:
     * <ul>
     *   <li>Prefix byte 0x04</li>
     *   <li>Followed by 32 bytes for X coordinate (Big-Endian)</li>
     *   <li>Followed by 32 bytes for Y coordinate (Big-Endian)</li>
     * </ul>
     * <p>
     * <b>Note:</b> This operation is relatively time-consuming because it involves memory copying and per-key byte order correction.
     *
     * @return an array of {@link PublicKeyBytes} containing the reconstructed public keys.
     */
    public PublicKeyBytes[] getPublicKeyBytes() {
        PublicKeyBytes[] publicKeys = new PublicKeyBytes[workSize];
        for (int i = 0; i < workSize; i++) {
            PublicKeyBytes publicKeyBytes = getPublicKeyFromByteBufferXY(clByteOrder, byteBufferUtility, result, i, secretKeyBase);
            publicKeys[i] = publicKeyBytes;
        }
        return publicKeys;
    }

    /**
     * Reconstructs a {@link PublicKeyBytes} object from OpenCL kernel result bytes.
     * <p>
     * This method reads a block of 64 bytes (X and Y coordinates) from a given {@link ByteBuffer},
     * corrects for endianness if necessary, and assembles an uncompressed public key
     * in SEC (Standards for Efficient Cryptography) format ({@code 04 || X || Y}).
     * <p>
     * OpenCL devices typically write data in device-native byte order (often Little-Endian).
     * Bitcoin and ECC protocols expect Big-Endian (MSB-first) ordering for public key coordinates.
     * Therefore, each coordinate is converted to Big-Endian before assembling the public key.
     * <p>
     * If the reconstructed secret key is zero, a predefined invalid public key constant is returned.
     * <p>
     * Internal structure:
     * <ul>
     *   <li>32 bytes: X coordinate</li>
     *   <li>32 bytes: Y coordinate</li>
     * </ul>
     *
     * @param clByteOrder the {@link ByteOrder} used by the OpenCL device (e.g., Little-Endian)
     * @param byteBufferUtility the {@link ByteBufferUtility} instance for array operations
     * @param resultBuffer the {@link ByteBuffer} containing the OpenCL kernel output
     * @param keyNumber the index of the key to extract (based on work-item ID)
     * @param secretKeyBase the base private key corresponding to the first work item
     * @return a {@link PublicKeyBytes} object representing the reconstructed public key
     * @throws RuntimeException if all coordinate bytes are zero (invalid GPU output)
     */
    private static final PublicKeyBytes getPublicKeyFromByteBufferXY(ByteOrder clByteOrder, ByteBufferUtility byteBufferUtility, ByteBuffer resultBuffer, int keyNumber, BigInteger secretKeyBase) {
        BigInteger secret = AbstractProducer.calculateSecretKey(secretKeyBase, keyNumber);
        if(BigInteger.ZERO.equals(secret)) {
            // the calculated key is invalid, return a fallback
            return PublicKeyBytes.INVALID_KEY_ONE;
        }
        final int resultBlockSize = PublicKeyBytes.TWO_COORDINATES_NUM_BYTES;
        final int keyOffsetInByteBuffer = resultBlockSize*keyNumber;
        
        // Read XY block
        byte[] kernelResultBlock = new byte[resultBlockSize];
        resultBuffer.get(keyOffsetInByteBuffer, kernelResultBlock, 0, resultBlockSize);
        
        EndiannessConverter endiannessConverter = new EndiannessConverter(clByteOrder, ByteOrder.BIG_ENDIAN, byteBufferUtility);
        
        // Extract x
        byte[] x = new byte[PublicKeyBytes.ONE_COORDINATE_NUM_BYTES];
        System.arraycopy(kernelResultBlock, 0, x, 0, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        // To SEC format (Big-Endian) (MSB-first)
        endiannessConverter.convertEndian(x);
        
        // Extract Y
        byte[] y = new byte[PublicKeyBytes.ONE_COORDINATE_NUM_BYTES];
        System.arraycopy(kernelResultBlock, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES, y, 0, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        // To SEC format (Big-Endian) (MSB-first)
        endiannessConverter.convertEndian(y);
        
        // Assemble uncompressed key
        byte[] uncompressed = PublicKeyBytes.assembleUncompressedPublicKey(x, y);
        
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
