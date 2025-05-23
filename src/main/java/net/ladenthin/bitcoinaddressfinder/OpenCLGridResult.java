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

import java.util.Arrays;
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
        ByteBuffer readOnlyResult = result.asReadOnlyBuffer();
        PublicKeyBytes[] publicKeys = new PublicKeyBytes[workSize];
        
        for (int i = 0; i < workSize; i++) {
            PublicKeyBytes publicKeyBytes = getPublicKeyFromByteBufferXY(readOnlyResult, i, secretKeyBase);
            publicKeys[i] = publicKeyBytes;
        }
        return publicKeys;
    }
    
    public static byte[] trimU32PrefixBytes(byte[] fullArray) {
        final int PREFIX_BYTES_TO_SKIP = 3;
        return Arrays.copyOfRange(fullArray, PREFIX_BYTES_TO_SKIP, fullArray.length);
    }

    /**
     * Reconstructs a {@link PublicKeyBytes} object from the OpenCL kernel output.
     * <p>
     * This method extracts a block of bytes for one key from the given {@link ByteBuffer},
     * based on the work-item index ({@code keyNumber}). The layout of each chunk is defined
     * by constants in {@link PublicKeyBytes}:
     * <ul>
     *   <li>{@code CHUNK_SIZE_00_NUM_BYTES_BIG_ENDIAN_X}: X coordinate (Big-Endian)</li>
     *   <li>{@code CHUNK_SIZE_01_NUM_BYTES_BIG_ENDIAN_Y}: Y coordinate (Big-Endian)</li>
     *   <li>{@code CHUNK_SIZE_10_NUM_BYTES_RIPEMD160_UNCOMPRESSED}: RIPEMD-160 hash of the uncompressed key</li>
     *   <li>{@code CHUNK_SIZE_11_NUM_BYTES_RIPEMD160_COMPRESSED}: RIPEMD-160 hash of the compressed key</li>
     * </ul>
     * <p>
     * The method reads and assembles the uncompressed public key in SEC format
     * ({@code 04 || X || Y}) using the provided X and Y coordinates. It also
     * extracts the precomputed RIPEMD-160 hashes for both uncompressed and
     * compressed formats from the buffer.
     * <p>
     * If the reconstructed secret key is zero, a predefined fallback key is returned.
     *
     * @param resultBuffer the buffer containing OpenCL results for all keys
     * @param keyNumber the zero-based index of the key to extract
     * @param secretKeyBase the base secret key used to derive the current key
     * @return the reconstructed {@link PublicKeyBytes} object
     * @throws RuntimeException if the key bytes are invalid (e.g. all coordinate bytes are zero)
     */
    private static final PublicKeyBytes getPublicKeyFromByteBufferXY(ByteBuffer resultBuffer, int keyNumber, BigInteger secretKeyBase) {
        BigInteger secret = AbstractProducer.calculateSecretKey(secretKeyBase, keyNumber);
        if(BigInteger.ZERO.equals(secret)) {
            // the calculated key is invalid, return a fallback
            return PublicKeyBytes.INVALID_KEY_ONE;
        }
        
        final int keyOffsetInByteBuffer = PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * keyNumber;
        
        // Get X
        byte[] xFromBigEndian = new byte[PublicKeyBytes.CHUNK_SIZE_00_NUM_BYTES_BIG_ENDIAN_X];
        resultBuffer.get(keyOffsetInByteBuffer + PublicKeyBytes.CHUNK_OFFSET_00_NUM_BYTES_BIG_ENDIAN_X, xFromBigEndian, 0, xFromBigEndian.length);
        
        // Get Y
        byte[] yFromBigEndian = new byte[PublicKeyBytes.CHUNK_SIZE_01_NUM_BYTES_BIG_ENDIAN_Y];
        resultBuffer.get(keyOffsetInByteBuffer + PublicKeyBytes.CHUNK_OFFSET_01_NUM_BYTES_BIG_ENDIAN_Y, yFromBigEndian, 0, yFromBigEndian.length);
        
        // Assemble uncompressed key
        byte[] uncompressedFromBigEndian = PublicKeyBytes.assembleUncompressedPublicKey(xFromBigEndian, yFromBigEndian);
        
        // Get RIPEMD160 for uncompressed key
        byte[] ripemd160Uncompressed = new byte[PublicKeyBytes.CHUNK_SIZE_10_NUM_BYTES_RIPEMD160_UNCOMPRESSED];
        resultBuffer.get(keyOffsetInByteBuffer + PublicKeyBytes.CHUNK_OFFSET_10_NUM_BYTES_RIPEMD160_UNCOMPRESSED, ripemd160Uncompressed, 0, ripemd160Uncompressed.length);
        
        // Get RIPEMD160 for uncompressed key
        byte[] ripemd160Compressed = new byte[PublicKeyBytes.CHUNK_SIZE_11_NUM_BYTES_RIPEMD160_COMPRESSED];
        resultBuffer.get(keyOffsetInByteBuffer + PublicKeyBytes.CHUNK_OFFSET_11_NUM_BYTES_RIPEMD160_COMPRESSED, ripemd160Compressed, 0, ripemd160Compressed.length);

        if (ENABLE_UNCOMPRESSED_KEY_VALIDATION) {
            boolean allZero = PublicKeyBytes.isAllCoordinateBytesZero(uncompressedFromBigEndian);
            if (allZero) {
                throw new RuntimeException("Invalid GPU result: all coordinate bytes are zero in uncompressed public key.");
            }
        }
        
        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secret, uncompressedFromBigEndian,  ripemd160Uncompressed, ripemd160Compressed);
        
        return publicKeyBytes;
    }
}
