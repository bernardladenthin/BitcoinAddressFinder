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
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;

import java.math.BigInteger;

/**
 * Utility class providing convenience methods for creating and initializing
 * byte arrays that represent Bitcoin private key secrets.
 * <p>
 * These helpers are mainly used for testing and mock implementations of key producers
 * (e.g. {@code KeyProducerJavaZmqTest}) where deterministic or filled byte arrays
 * are required to simulate secret key transmission.
 */
public class KeyProducerTestUtility {
    
    /**
     * Creates a new secret byte array of the standard private key length
     * defined by {@link PublicKeyBytes#PRIVATE_KEY_MAX_NUM_BYTES}, filled entirely with zeroes.
     * <p>
     * This method is a shorthand for {@link #createFilledSecret(byte)} with a fill byte of {@code 0x00}.
     *
     * @return a new byte array filled with {@code 0x00}, having a length equal to
     *         {@link PublicKeyBytes#PRIVATE_KEY_MAX_NUM_BYTES}.
     */
    public byte[] createZeroedSecret() {
        return createFilledSecret((byte)0x0);
    }
    
    /**
     * Creates a new secret byte array filled with the specified byte value.
     * <p>
     * Each position in the resulting array will contain the provided {@code fillByte}.
     *
     * @param fillByte the byte value used to fill every position in the secret array.
     * @return a new byte array of size {@link PublicKeyBytes#PRIVATE_KEY_MAX_NUM_BYTES}
     *         where each element is set to {@code fillByte}.
     */
    public byte[] createFilledSecret(byte fillByte) {
        byte[] secretBytes = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
        for (int i = 0; i < secretBytes.length; i++) {
            secretBytes[i] = fillByte;
        }
        return secretBytes;
    }
    
    /**
     * Creates a new secret byte array where each byte is derived by incrementing the given {@code fillByte}.
     * <p>
     * This variant is useful for generating non-uniform test data, as each byte will contain
     * {@code (fillByte + 1)}.
     * <p>
     * Note that this implementation currently applies the same incremented value to all bytes
     * in the array — it does not perform cumulative iteration.
     *
     * @param startByte the base byte value used as the starting point for incrementing.
     * @return a new byte array of size {@link PublicKeyBytes#PRIVATE_KEY_MAX_NUM_BYTES},
     *         filled with the value {@code (fillByte + 1)}.
     */
    public byte[] createIncrementedSecret(byte startByte) {
        byte[] secretBytes = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
        for (int i = 0; i < secretBytes.length; i++) {
            secretBytes[i] = (byte) (startByte + 1);
        }
        return secretBytes;               
    }

    /**
     * Asserts that an array of secrets increases by one per index.
     * <p>
     * Each secret is expected to represent a BigInteger whose least-significant byte
     * equals {@code (index + 1)}.
     *
     * @param secrets the array of BigInteger secrets to verify
     */
    public void assertIncrementedSecrets(BigInteger[] secrets) {
        for (int i = 0; i < secrets.length; i++) {
            byte[] bytes = secrets[i].toByteArray();

            // Handle BigInteger sign and potential leading zero
            byte lastByte = bytes[bytes.length - 1];
            byte expected = (byte) (i + 1);

            assertThat(
                "Secret at index " + i + " should end with byte value " + expected,
                lastByte,
                is(expected)
            );
        }
    }
    
    /**
    * Asserts that the given secret consists entirely of the specified fill byte.
    * <p>
    * This checks the least significant byte of the {@link BigInteger}'s backing array,
    * which is sufficient when verifying uniformly filled secrets produced by
    * {@code generateFilledSecret(byte)} or similar utilities.
    *
    * @param secret   the BigInteger secret to verify
    * @param fillByte the byte value expected to fill the secret
    */
   public void assertFilledSecret(BigInteger secret, byte fillByte) {
       byte[] bytes = secret.toByteArray();

       // Defensive: skip any sign byte (0x00) that BigInteger may prepend
       int lastIndex = bytes.length - 1;
       byte lastByte = bytes[lastIndex];

       assertThat(
           "Secret should end with byte value " + fillByte + " but was " + lastByte,
           lastByte,
           is(equalTo(fillByte))
       );
   }
}
