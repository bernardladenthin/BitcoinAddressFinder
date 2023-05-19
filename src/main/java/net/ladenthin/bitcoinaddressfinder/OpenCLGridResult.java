// @formatter:off
/*
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public class OpenCLGridResult {

    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

    private final BigInteger[] secretKeys;
    private final int workSize;
    private final boolean chunkMode;
    private ByteBuffer result;

    OpenCLGridResult(BigInteger[] secretKeys, int workSize, ByteBuffer result, boolean chunkMode)
            throws InvalidWorkSizeException {
        checkPrivateKeysAndWorkSize(secretKeys, workSize, chunkMode);
        this.secretKeys = secretKeys;
        this.workSize = workSize;
        this.result = result;
        this.chunkMode = chunkMode;
    }

    private void checkPrivateKeysAndWorkSize(BigInteger[] secretKeys, int workSize, boolean chunkMode)
            throws InvalidWorkSizeException {
        if (!chunkMode && (secretKeys.length != workSize)) {
            throw new InvalidWorkSizeException(
                    "When CHUNKMODE is DEACTIVATED, the number of the secretKeys (=" + secretKeys.length + ") must be EQUAL to the workSize (=" + workSize + ")!");
        } else if (chunkMode && secretKeys.length > 1) {
            // TODO use a logger
            System.out.println("Too many secret keys (=" + secretKeys.length + ") when CHUNKMODE is ACTIVATED! Will use first secret key ONLY!");
        }
    }

    public ByteBuffer getResult() {
        return result;
    }

    /**
     * Frees byte buffer and sets result to NULL
     */
    public void freeResult() {
        byteBufferUtility.freeByteBuffer(result);
        result = null;
    }

    /**
     * @return the calculated public keys
     */
    public PublicKeyBytes[] getPublicKeyBytes() {
        PublicKeyBytes[] publicKeys = new PublicKeyBytes[workSize];
        for (int i = 0; i < workSize; i++) {
            PublicKeyBytes publicKeyBytes;
            if (chunkMode) {
                publicKeyBytes = getPublicKeyFromByteBufferXY(result, i, secretKeys[0]);
                publicKeys[i] = publicKeyBytes;
            } else {
                publicKeyBytes = getPublicKeyFromByteBufferXY(result, i, secretKeys[workSize - 1 - i]);
                publicKeys[workSize - 1 - i] = publicKeyBytes;
            }

        }
        return publicKeys;
    }

    /**
     * Read the inner bytes in reverse order.
     */
    private static PublicKeyBytes getPublicKeyFromByteBufferXY(ByteBuffer b, int keyNumber,
                                                               BigInteger secretKeyBase) {

        BigInteger secret = AbstractProducer.calculateSecretKey(secretKeyBase, keyNumber);

        if (BigInteger.ZERO.equals(secret)) {
            return PublicKeyBytes.INVALID_KEY_ONE;
        }

        byte[] uncompressed = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES];
        uncompressed[0] = PublicKeyBytes.PARITY_UNCOMPRESSED;

        // Same way as in OpenCL kernel:
        // int r_offset = PUBLIC_KEY_LENGTH_X_Y_WITHOUT_PARITY * global_id;
        int keyOffsetInByteBuffer = PublicKeyBytes.TWO_COORDINATES_NUM_BYTES * keyNumber;

        // read ByteBuffer
        byte[] yx = new byte[PublicKeyBytes.TWO_COORDINATES_NUM_BYTES];
        for (int i = 0; i < PublicKeyBytes.TWO_COORDINATES_NUM_BYTES; i++) {
            yx[yx.length - 1 - i] = b.get(keyOffsetInByteBuffer + i);
        }

        // copy x
        System.arraycopy(yx, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES, uncompressed, PublicKeyBytes.PARITY_BYTES_LENGTH,
                PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        // copy y
        System.arraycopy(yx, 0, uncompressed,
                PublicKeyBytes.PARITY_BYTES_LENGTH + PublicKeyBytes.ONE_COORDINATE_NUM_BYTES,
                PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);

        return new PublicKeyBytes(secret, uncompressed);
    }
}