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
import org.jspecify.annotations.NonNull;

/**
 * Validates and manipulates private keys according to secp256k1 constraints.
 * <p>
 * This helper class encapsulates logic for checking if private keys fall within
 * valid ranges, and for correcting invalid keys to a known replacement value.
 * It is particularly useful for grid-based key generation where batch sizes must
 * be carefully bounded to avoid exceeding the secp256k1 private key limit.
 */
public class PrivateKeyValidator {

    /**
     * Calculates the maximum allowed private key value that can safely be used as a base
     * for grid-based key generation without exceeding the secp256k1 private key limit.
     * <p>
     * This is necessary for chunked or grid-based generation where the base key is incremented
     * by up to 2^batchSizeInBits - 1.
     *
     * @param batchSizeInBits The number of bits used for batch size (i.e., the number of keys generated in one grid chunk).
     *                        Must be in the range [0, {@link PublicKeyBytes#PRIVATE_KEY_MAX_NUM_BITS}].
     * @return The maximum base private key that will not overflow when incremented by the grid.
     * @throws IllegalArgumentException if batchSizeInBits is outside the valid range
     * @throws IllegalStateException if batchSizeInBits is too large and no valid keys remain
     */
    public BigInteger getMaxPrivateKeyForBatchSize(int batchSizeInBits) {
        if (batchSizeInBits < 0 || batchSizeInBits > PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS) {
            throw new IllegalArgumentException("batchSizeInBits must be between 0 and " + PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS);
        }

        // 2^batchSizeInBits represents the maximum offset (grid size)
        BigInteger maxOffset = BigInteger.ONE.shiftLeft(batchSizeInBits);

        // Subtract maxOffset - 1 to ensure that baseKey + (2^bits - 1) ≤ MAX_PRIVATE_KEY
        BigInteger maxSafeKey = PublicKeyBytes.MAX_PRIVATE_KEY.subtract(maxOffset).add(BigInteger.ONE);

        if (maxSafeKey.signum() < 0) {
            throw new IllegalStateException("batchSizeInBits too large; no valid private keys remain.");
        }

        return maxSafeKey;
    }

    /**
     * Checks whether a private key base exceeds the maximum allowed value for a given batch size.
     *
     * @param privateKeyBase the base private key to check
     * @param maxPrivateKeyForBatchSize the maximum allowed value for the batch size
     * @return true if the key exceeds the maximum; false otherwise
     */
    public boolean isInvalidWithBatchSize(@NonNull BigInteger privateKeyBase, @NonNull BigInteger maxPrivateKeyForBatchSize) {
        return privateKeyBase.compareTo(maxPrivateKeyForBatchSize) > 0;
    }

    /**
     * Checks whether a private key falls outside the valid range for secp256k1.
     * <p>
     * Valid private keys are in the range [{@link PublicKeyBytes#MIN_VALID_PRIVATE_KEY}, {@link PublicKeyBytes#MAX_PRIVATE_KEY}].
     *
     * @param secret the private key to check
     * @return true if the key is outside the valid range; false otherwise
     */
    public boolean isOutsidePrivateKeyRange(@NonNull BigInteger secret) {
        return secret.compareTo(PublicKeyBytes.MIN_VALID_PRIVATE_KEY) < 0
                || secret.compareTo(PublicKeyBytes.MAX_PRIVATE_KEY) > 0;
    }

    /**
     * Returns a valid private key, or a replacement value if the input is invalid.
     * <p>
     * If the input key is outside the valid range, this method returns
     * {@link PublicKeyBytes#INVALID_PRIVATE_KEY_REPLACEMENT}. Otherwise, it returns the input unchanged.
     *
     * @param secret the private key to validate
     * @return the input key if valid, or the replacement value if invalid
     */
    public @NonNull BigInteger returnValidPrivateKey(@NonNull BigInteger secret) {
        if (isOutsidePrivateKeyRange(secret)) {
            return PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT;
        }
        return secret;
    }

    /**
     * Replaces invalid private keys in an array with a known replacement value.
     * <p>
     * Each element in the array is checked and, if invalid, replaced with
     * {@link PublicKeyBytes#INVALID_PRIVATE_KEY_REPLACEMENT}. Valid keys are left unchanged.
     *
     * @param secrets the array of private keys to validate and correct (modified in-place)
     */
    public void replaceInvalidPrivateKeys(@NonNull BigInteger[] secrets) {
        for (int i = 0; i < secrets.length; i++) {
            secrets[i] = returnValidPrivateKey(secrets[i]);
        }
    }
}
