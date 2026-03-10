// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.Test;

/**
 * Unit tests for {@link OpenCLGridResult}.
 */
public class OpenCLGridResultTest {

    // <editor-fold defaultstate="collapsed" desc="getSecretKeyBase">
    @Test
    public void getSecretKeyBase_validInput_returnsExpectedValue() {
        // arrange
        BigInteger secretKeyBase = BigInteger.valueOf(12345678L);
        int workSize = 1;
        ByteBuffer result = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, result);

        // act
        BigInteger actual = gridResult.getSecretKeyBase();

        // assert
        assertThat(actual, is(equalTo(secretKeyBase)));
    }

    @Test
    public void getSecretKeyBase_zeroBigInteger_returnsZero() {
        // arrange
        BigInteger secretKeyBase = BigInteger.ZERO;
        int workSize = 1;
        ByteBuffer result = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, result);

        // act
        BigInteger actual = gridResult.getSecretKeyBase();

        // assert
        assertThat(actual, is(equalTo(BigInteger.ZERO)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getWorkSize">
    @Test
    public void getWorkSize_validInput_returnsExpectedValue() {
        // arrange
        BigInteger secretKeyBase = BigInteger.ONE;
        int workSize = 64;
        ByteBuffer result = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, result);

        // act
        int actual = gridResult.getWorkSize();

        // assert
        assertThat(actual, is(equalTo(workSize)));
    }

    @Test
    public void getWorkSize_workSizeOne_returnsOne() {
        // arrange
        BigInteger secretKeyBase = BigInteger.ONE;
        int workSize = 1;
        ByteBuffer result = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, result);

        // act
        int actual = gridResult.getWorkSize();

        // assert
        assertThat(actual, is(equalTo(1)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getResult">
    @Test
    public void getResult_validInput_returnsExpectedBuffer() {
        // arrange
        BigInteger secretKeyBase = BigInteger.ONE;
        int workSize = 1;
        ByteBuffer expectedBuffer = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, expectedBuffer);

        // act
        ByteBuffer actual = gridResult.getResult();

        // assert
        assertThat(actual, is(notNullValue()));
        assertThat(actual, is(equalTo(expectedBuffer)));
    }

    @Test
    public void getResult_validInput_returnsSameBufferReference() {
        // arrange
        BigInteger secretKeyBase = BigInteger.ONE;
        int workSize = 1;
        ByteBuffer buffer = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, buffer);

        // act
        ByteBuffer actual = gridResult.getResult();

        // assert
        assertThat(actual == buffer, is(true));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="trimU32PrefixBytes">
    @Test
    public void trimU32PrefixBytes_sevenByteArray_returnsFourBytes() {
        // arrange
        byte[] input = {0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04};

        // act
        byte[] result = OpenCLGridResult.trimU32PrefixBytes(input);

        // assert
        assertThat(result.length, is(equalTo(4)));
    }

    @Test
    public void trimU32PrefixBytes_sevenByteArray_returnsLastFourBytes() {
        // arrange
        byte[] input = {0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04};

        // act
        byte[] result = OpenCLGridResult.trimU32PrefixBytes(input);

        // assert
        assertThat(result[0], is(equalTo((byte) 0x01)));
        assertThat(result[1], is(equalTo((byte) 0x02)));
        assertThat(result[2], is(equalTo((byte) 0x03)));
        assertThat(result[3], is(equalTo((byte) 0x04)));
    }

    @Test
    public void trimU32PrefixBytes_exactlyThreeBytes_returnsEmptyArray() {
        // arrange
        byte[] input = {0x00, 0x00, 0x00};

        // act
        byte[] result = OpenCLGridResult.trimU32PrefixBytes(input);

        // assert
        assertThat(result.length, is(equalTo(0)));
    }

    @Test
    public void trimU32PrefixBytes_fourByteArray_returnsOneByteArray() {
        // arrange
        byte[] input = {0x00, 0x00, 0x00, (byte) 0xFF};

        // act
        byte[] result = OpenCLGridResult.trimU32PrefixBytes(input);

        // assert
        assertThat(result.length, is(equalTo(1)));
        assertThat(result[0], is(equalTo((byte) 0xFF)));
    }

    @Test
    public void trimU32PrefixBytes_largeArray_returnsCorrectLength() {
        // arrange
        byte[] input = new byte[35]; // 35 - 3 = 32 expected bytes
        input[3] = (byte) 0xAB;

        // act
        byte[] result = OpenCLGridResult.trimU32PrefixBytes(input);

        // assert
        assertThat(result.length, is(equalTo(32)));
        assertThat(result[0], is(equalTo((byte) 0xAB)));
    }
    // </editor-fold>
}
