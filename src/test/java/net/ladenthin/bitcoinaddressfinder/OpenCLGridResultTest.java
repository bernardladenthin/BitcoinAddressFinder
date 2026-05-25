// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.Test;

public class OpenCLGridResultTest {

    @Test
    public void getSecretKeyBase_validInput_returnsExpectedValue() {
        BigInteger secretKeyBase = BigInteger.valueOf(12345678L);
        int workSize = 1;
        ByteBuffer result = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, result);
        assertThat(gridResult.getSecretKeyBase(), is(equalTo(secretKeyBase)));
    }

    @Test
    public void getSecretKeyBase_zeroBigInteger_returnsZero() {
        BigInteger secretKeyBase = BigInteger.ZERO;
        int workSize = 1;
        ByteBuffer result = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, result);
        assertThat(gridResult.getSecretKeyBase(), is(equalTo(BigInteger.ZERO)));
    }

    @Test
    public void getWorkSize_validInput_returnsExpectedValue() {
        BigInteger secretKeyBase = BigInteger.ONE;
        int workSize = 64;
        ByteBuffer result = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, result);
        assertThat(gridResult.getWorkSize(), is(equalTo(workSize)));
    }

    @Test
    public void getWorkSize_workSizeOne_returnsOne() {
        BigInteger secretKeyBase = BigInteger.ONE;
        int workSize = 1;
        ByteBuffer result = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, result);
        assertThat(gridResult.getWorkSize(), is(equalTo(1)));
    }

    @Test
    public void getResult_validInput_returnsExpectedBuffer() {
        BigInteger secretKeyBase = BigInteger.ONE;
        int workSize = 1;
        ByteBuffer expectedBuffer = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, expectedBuffer);
        ByteBuffer actual = gridResult.getResult();
        assertThat(actual, is(notNullValue()));
        assertThat(actual, is(equalTo(expectedBuffer)));
    }

    @Test
    public void getResult_validInput_returnsSameBufferReference() {
        BigInteger secretKeyBase = BigInteger.ONE;
        int workSize = 1;
        ByteBuffer buffer = ByteBuffer.allocate(PublicKeyBytes.CHUNK_SIZE_NUM_BYTES * workSize);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, workSize, buffer);
        ByteBuffer actual = gridResult.getResult();
        assertThat(actual == buffer, is(true));
    }

    @Test
    public void trimU32PrefixBytes_sevenByteArray_returnsFourBytes() {
        byte[] input = {0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04};
        byte[] result = OpenCLGridResult.trimU32PrefixBytes(input);
        assertThat(result.length, is(equalTo(4)));
    }

    @Test
    public void trimU32PrefixBytes_sevenByteArray_returnsLastFourBytes() {
        byte[] input = {0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04};
        byte[] result = OpenCLGridResult.trimU32PrefixBytes(input);
        assertThat(result[0], is(equalTo((byte) 0x01)));
        assertThat(result[1], is(equalTo((byte) 0x02)));
        assertThat(result[2], is(equalTo((byte) 0x03)));
        assertThat(result[3], is(equalTo((byte) 0x04)));
    }

    @Test
    public void trimU32PrefixBytes_exactlyThreeBytes_returnsEmptyArray() {
        byte[] input = {0x00, 0x00, 0x00};
        byte[] result = OpenCLGridResult.trimU32PrefixBytes(input);
        assertThat(result.length, is(equalTo(0)));
    }

    @Test
    public void trimU32PrefixBytes_fourByteArray_returnsOneByteArray() {
        byte[] input = {0x00, 0x00, 0x00, (byte) 0xFF};
        byte[] result = OpenCLGridResult.trimU32PrefixBytes(input);
        assertThat(result.length, is(equalTo(1)));
        assertThat(result[0], is(equalTo((byte) 0xFF)));
    }

    @Test
    public void trimU32PrefixBytes_largeArray_returnsCorrectLength() {
        byte[] input = new byte[35];
        input[3] = (byte) 0xAB;
        byte[] result = OpenCLGridResult.trimU32PrefixBytes(input);
        assertThat(result.length, is(equalTo(32)));
        assertThat(result[0], is(equalTo((byte) 0xAB)));
    }
}
