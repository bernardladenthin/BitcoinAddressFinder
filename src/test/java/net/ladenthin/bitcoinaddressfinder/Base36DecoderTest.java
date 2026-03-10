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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class Base36DecoderTest {

    private final Base36Decoder decoder = new Base36Decoder();

    // <editor-fold defaultstate="collapsed" desc="decodeBase36ToFixedLengthBytes - exact length">
    @Test
    public void decodeBase36ToFixedLengthBytes_validInput_exact20Bytes() {
        // arrange
        String base36Encoded = new BigInteger(1, new byte[20]).toString(36);
        int expectedLength = 20;

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, expectedLength);

        // assert
        assertThat(result.length, is(equalTo(expectedLength)));
        assertThat(result, is(new byte[20]));
    }

    @Test
    public void decodeBase36ToFixedLengthBytes_validInput_exact1Byte() {
        // arrange
        byte[] original = {0x7F};
        String base36Encoded = new BigInteger(1, original).toString(36);

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, 1);

        // assert
        assertThat(result.length, is(equalTo(1)));
        assertThat(result[0], is(equalTo((byte) 0x7F)));
    }

    @Test
    public void decodeBase36ToFixedLengthBytes_zeroBytes_producesAllZeros() {
        // arrange
        String base36Encoded = "0";
        int expectedLength = 20;

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, expectedLength);

        // assert
        assertThat(result.length, is(equalTo(expectedLength)));
        for (byte b : result) {
            assertThat(b, is(equalTo((byte) 0)));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="decodeBase36ToFixedLengthBytes - shorter input">
    @Test
    public void decodeBase36ToFixedLengthBytes_validInput_shorterThan20Bytes() {
        // arrange
        byte[] original = {0x01, 0x02, 0x03}; // 3 bytes
        String base36Encoded = new BigInteger(1, original).toString(36);
        int expectedLength = 20;

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, expectedLength);

        // assert
        assertThat(result.length, is(equalTo(expectedLength)));
        // The original bytes should be right-aligned (at the end)
        assertThat(Arrays.copyOfRange(result, 17, 20), is(original));
        // Leading bytes should be padded with zeros
        assertThat(Arrays.copyOfRange(result, 0, 17), is(new byte[17]));
    }

    @Test
    public void decodeBase36ToFixedLengthBytes_singleByteInput_leftPaddedWithZeros() {
        // arrange
        byte[] original = {(byte) 0xAB};
        String base36Encoded = new BigInteger(1, original).toString(36);

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, 10);

        // assert
        assertThat(result.length, is(equalTo(10)));
        assertThat(result[9], is(equalTo((byte) 0xAB)));
        for (int i = 0; i < 9; i++) {
            assertThat(result[i], is(equalTo((byte) 0)));
        }
    }

    @Test
    public void decodeBase36ToFixedLengthBytes_twoBytesInput_leftPaddedWithZeros() {
        // arrange
        byte[] original = {0x12, 0x34};
        String base36Encoded = new BigInteger(1, original).toString(36);
        int targetLength = 5;

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, targetLength);

        // assert
        assertThat(result.length, is(equalTo(targetLength)));
        assertThat(Arrays.copyOfRange(result, 3, 5), is(original));
        assertThat(Arrays.copyOfRange(result, 0, 3), is(new byte[3]));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="decodeBase36ToFixedLengthBytes - longer input">
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
        assertThat(result.length, is(equalTo(expectedLength)));
        // When input is longer, the least-significant bytes are kept
        // The result should be 20 bytes from the 25-byte input
    }

    @Test
    public void decodeBase36ToFixedLengthBytes_muchlongerInput_trimsToTargetLength() {
        // arrange
        byte[] longBytes = new byte[50];
        Arrays.fill(longBytes, (byte) 0xFF);
        String base36Encoded = new BigInteger(1, longBytes).toString(36);

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, 10);

        // assert
        assertThat(result.length, is(equalTo(10)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="decodeBase36ToFixedLengthBytes - boundary values">
    @Test
    public void decodeBase36ToFixedLengthBytes_maxByteValue_decodesCorrectly() {
        // arrange
        byte[] original = {(byte) 0xFF};
        String base36Encoded = new BigInteger(1, original).toString(36);

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, 1);

        // assert
        assertThat(result.length, is(equalTo(1)));
        assertThat(result[0], is(equalTo((byte) 0xFF)));
    }

    @Test
    public void decodeBase36ToFixedLengthBytes_allMaxBytes_decodesCorrectly() {
        // arrange
        byte[] original = new byte[5];
        Arrays.fill(original, (byte) 0xFF);
        String base36Encoded = new BigInteger(1, original).toString(36);

        // act
        byte[] result = decoder.decodeBase36ToFixedLengthBytes(base36Encoded, 5);

        // assert
        assertThat(result.length, is(equalTo(5)));
        for (int i = 0; i < 5; i++) {
            assertThat(result[i], is(equalTo((byte) 0xFF)));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="decodeBase36ToFixedLengthBytes - invalid input">
    @Test(expected = NumberFormatException.class)
    public void decodeBase36ToFixedLengthBytes_invalidCharacters_throwsException() {
        // arrange
        String invalidBase36 = "O0I1L$%"; // invalid characters for BigInteger(36)

        // act
        decoder.decodeBase36ToFixedLengthBytes(invalidBase36, 20);
    }

    @Test(expected = NumberFormatException.class)
    public void decodeBase36ToFixedLengthBytes_emptyString_throwsException() {
        // arrange
        String emptyString = "";

        // act
        decoder.decodeBase36ToFixedLengthBytes(emptyString, 20);
    }

    @Test(expected = NumberFormatException.class)
    public void decodeBase36ToFixedLengthBytes_invalidCharacterG_throwsException() {
        // arrange
        String invalidBase36 = "123g456"; // 'g' is invalid in base36

        // act
        decoder.decodeBase36ToFixedLengthBytes(invalidBase36, 20);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="decodeBase36ToFixedLengthBytes - round-trip verification">
    @Test
    public void decodeBase36ToFixedLengthBytes_encodeDecodeRoundTrip_producesOriginalData() {
        // arrange
        byte[] original = {0x01, 0x02, 0x03, 0x04, 0x05};
        String encoded = new BigInteger(1, original).toString(36);

        // act
        byte[] decoded = decoder.decodeBase36ToFixedLengthBytes(encoded, 5);

        // pre-assert
        assertThat(decoded, is(original));

        // Re-encode should produce the same base36 string
        String reencoded = new BigInteger(1, decoded).toString(36);

        // assert
        assertThat(reencoded, is(equalTo(encoded)));
    }
    // </editor-fold>
}
