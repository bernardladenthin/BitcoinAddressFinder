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
import java.util.Arrays;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class Base36DecoderTest {

    private final Base36Decoder decoder = new Base36Decoder();

    // <editor-fold defaultstate="collapsed" desc="Valid decoding">
    @Test
    public void decodeBase36ToFixedLengthBytes_validInput_exact20Bytes() {
        // arrange
        String base36Encoded = new BigInteger(1, new byte[20]).toString(36);
        int expectedLength = 20;

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, expectedLength);

        // assert
        assertThat(result.length, is(expectedLength));
        assertThat(result, is(new byte[20]));
    }

    @Test
    public void decodeBase36ToFixedLengthBytes_validInput_shorterThan20Bytes() {
        // arrange
        byte[] original = {0x01, 0x02, 0x03}; // 3 bytes
        String base36Encoded = new BigInteger(1, original).toString(36);
        int expectedLength = 20;

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, expectedLength);

        // assert
        assertThat(result.length, is(expectedLength));
        assertThat(Arrays.copyOfRange(result, 17, 20), is(original));
    }

    @Test
    public void decodeBase36ToFixedLengthBytes_validInput_longerThan20Bytes() {
        // arrange
        byte[] longBytes = new byte[25];
        Arrays.fill(longBytes, (byte) 0x7F);
        String base36Encoded = new BigInteger(1, longBytes).toString(36);
        int expectedLength = 20;

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, expectedLength);

        // assert
        assertThat(result.length, is(expectedLength));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Invalid input handling">
    @Test(expected = NumberFormatException.class)
    public void decodeBase36ToFixedLengthBytes_invalidCharacters_throwsException() {
        // arrange
        String invalidBase36 = "O0I1L$%"; // invalid characters for BigInteger(36)

        // act
        decoder.decodeBase36ToFixedLengthBytes(invalidBase36, 20);
    }
    // </editor-fold>
}
