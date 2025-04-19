// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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

/**
 * Exception thrown when a given private key exceeds the safe upper bound
 * for use in grid-based key chunking (incrementing the base key).
 * <p>
 * The maximum allowed private key is {@link PublicKeyBytes#MAX_PRIVATE_KEY}.
 * This exception is typically thrown if the base key plus the chunk range (2^bits) exceeds this bound.
 */
public class PrivateKeyTooLargeException extends IllegalArgumentException {

    private final BigInteger providedKey;
    private final BigInteger maxAllowedKey;
    private final int batchSizeInBits;

    public PrivateKeyTooLargeException(BigInteger providedKey, BigInteger maxAllowedKey, int batchSizeInBits) {
        super(buildMessage(providedKey, maxAllowedKey, batchSizeInBits));
        this.providedKey = providedKey;
        this.maxAllowedKey = maxAllowedKey;
        this.batchSizeInBits = batchSizeInBits;
    }

    private static String buildMessage(BigInteger providedKey, BigInteger maxAllowedKey, int batchSizeInBits) {
        return "Private key exceeds maximum allowed range for chunked grid mode: " +
               "\nProvided key:        0x" + providedKey.toString(16) +
               "\nMaximum allowed key: 0x" + maxAllowedKey.toString(16) +
               "\n(batchSizeInBits = " + batchSizeInBits + ")" +
               "\nThe maximum private key is defined in: PublicKeyBytes.MAX_PRIVATE_KEY";
    }

    public BigInteger getProvidedKey() {
        return providedKey;
    }

    public BigInteger getMaxAllowedKey() {
        return maxAllowedKey;
    }

    public int getBatchSizeInBits() {
        return batchSizeInBits;
    }
}
