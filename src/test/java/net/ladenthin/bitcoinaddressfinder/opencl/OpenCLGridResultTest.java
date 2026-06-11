// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OpenCLGridResult}.
 */
public class OpenCLGridResultTest {

    private static byte[] filled(int length, int value) {
        byte[] b = new byte[length];
        java.util.Arrays.fill(b, (byte) value);
        return b;
    }

    private static void putBytes(ByteBuffer buffer, int offset, byte[] src) {
        for (int i = 0; i < src.length; i++) {
            buffer.put(offset + i, src[i]);
        }
    }

    /** Builds a full-transfer unified output buffer (sentinel header + dense 108-byte entries). */
    private static ByteBuffer buildFullTransferBuffer(byte[][] xs, byte[][] ys, byte[][] hus, byte[][] hcs) {
        int workSize = xs.length;
        int size = OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES
                + OpenClKernelConstants.OUTPUT_ENTRY_SIZE_BYTES * workSize;
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0, OpenClKernelConstants.OUTPUT_COUNT_FULL_TRANSFER_SENTINEL);
        for (int i = 0; i < workSize; i++) {
            int base =
                    OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES + OpenClKernelConstants.OUTPUT_ENTRY_SIZE_BYTES * i;
            buffer.putInt(base + OpenClKernelConstants.OUTPUT_ENTRY_INDEX_BYTE_OFFSET, i);
            putBytes(buffer, base + OpenClKernelConstants.OUTPUT_ENTRY_X_BYTE_OFFSET, xs[i]);
            putBytes(buffer, base + OpenClKernelConstants.OUTPUT_ENTRY_Y_BYTE_OFFSET, ys[i]);
            putBytes(buffer, base + OpenClKernelConstants.OUTPUT_ENTRY_HASH160_UNCOMPRESSED_BYTE_OFFSET, hus[i]);
            putBytes(buffer, base + OpenClKernelConstants.OUTPUT_ENTRY_HASH160_COMPRESSED_BYTE_OFFSET, hcs[i]);
        }
        return buffer;
    }

    // <editor-fold defaultstate="collapsed" desc="getSecretKeyBase">
    @Test
    public void getSecretKeyBase_validInput_returnsExpectedValue() {
        // arrange
        BigInteger secretKeyBase = BigInteger.valueOf(12345678L);
        int workSize = 1;
        ByteBuffer result = ByteBuffer.allocate(OpenClKernelConstants.CHUNK_SIZE_NUM_BYTES * workSize);
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
        ByteBuffer result = ByteBuffer.allocate(OpenClKernelConstants.CHUNK_SIZE_NUM_BYTES * workSize);
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
        ByteBuffer result = ByteBuffer.allocate(OpenClKernelConstants.CHUNK_SIZE_NUM_BYTES * workSize);
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
        ByteBuffer result = ByteBuffer.allocate(OpenClKernelConstants.CHUNK_SIZE_NUM_BYTES * workSize);
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
        ByteBuffer expectedBuffer = ByteBuffer.allocate(OpenClKernelConstants.CHUNK_SIZE_NUM_BYTES * workSize);
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
        ByteBuffer buffer = ByteBuffer.allocate(OpenClKernelConstants.CHUNK_SIZE_NUM_BYTES * workSize);
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

    // <editor-fold defaultstate="collapsed" desc="getPublicKeyBytes — unified full-transfer layout (Step E)">
    @Test
    public void getPublicKeyBytes_sentinelCount_dispatchesToFullTransfer() {
        // arrange — two dense entries with distinct X/Y/hash160 fillers
        byte[] x0 = filled(32, 0x11);
        byte[] y0 = filled(32, 0x22);
        byte[] hu0 = filled(20, 0x33);
        byte[] hc0 = filled(20, 0x44);
        byte[] x1 = filled(32, 0x55);
        byte[] y1 = filled(32, 0x66);
        byte[] hu1 = filled(20, 0x77);
        byte[] hc1 = filled(20, (byte) 0x88);
        ByteBuffer buffer = buildFullTransferBuffer(
                new byte[][] {x0, x1}, new byte[][] {y0, y1}, new byte[][] {hu0, hu1}, new byte[][] {hc0, hc1});
        BigInteger secretKeyBase = BigInteger.valueOf(1000);
        OpenCLGridResult gridResult = new OpenCLGridResult(secretKeyBase, 2, buffer);

        // act
        PublicKeyBytes[] keys = gridResult.getPublicKeyBytes();

        // assert
        assertThat(keys.length, is(equalTo(2)));
        // secret = base + index (ADD combine mode used by OpenCLGridResult)
        assertThat(keys[0].getSecretKey(), is(equalTo(BigInteger.valueOf(1000))));
        assertThat(keys[1].getSecretKey(), is(equalTo(BigInteger.valueOf(1001))));
        // precomputed hashes surface verbatim
        assertThat(keys[0].getUncompressedKeyHash(), is(equalTo(hu0)));
        assertThat(keys[0].getCompressedKeyHash(), is(equalTo(hc0)));
        assertThat(keys[1].getUncompressedKeyHash(), is(equalTo(hu1)));
        assertThat(keys[1].getCompressedKeyHash(), is(equalTo(hc1)));
        // uncompressed key is 04 || X || Y
        assertThat(keys[0].getUncompressed(), is(equalTo(PublicKeyBytes.assembleUncompressedPublicKey(x0, y0))));
        assertThat(keys[1].getUncompressed(), is(equalTo(PublicKeyBytes.assembleUncompressedPublicKey(x1, y1))));
    }

    @Test
    public void getPublicKeyBytes_nonSentinelCount_throwsUntilCompactReaderLive() {
        // arrange — a buffer whose count word is a compact count (not the sentinel)
        ByteBuffer buffer = ByteBuffer.allocate(
                        OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES + OpenClKernelConstants.OUTPUT_ENTRY_SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0, 1); // compact count = 1
        OpenCLGridResult gridResult = new OpenCLGridResult(BigInteger.ONE, 1, buffer);

        // act + assert — Step E only emits the sentinel; compact reading arrives in Step G
        try {
            gridResult.getPublicKeyBytes();
            throw new AssertionError("expected IllegalStateException for non-sentinel count");
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage().contains("count word"), is(true));
        }
    }

    @Test
    public void getPublicKeyFromByteBufferXY_offsetShiftedByHeaderAndIndex() {
        // arrange — distinctive first X byte; the count word (bytes 0..3) and the work_item_index
        // (bytes 4..7) precede the X coordinate, so X[0] must be read from byte 8.
        byte[] x0 = new byte[32];
        x0[0] = (byte) 0xAB;
        byte[] y0 = filled(32, 0x01);
        byte[] hu0 = filled(20, 0x02);
        byte[] hc0 = filled(20, 0x03);
        ByteBuffer buffer =
                buildFullTransferBuffer(new byte[][] {x0}, new byte[][] {y0}, new byte[][] {hu0}, new byte[][] {hc0});
        OpenCLGridResult gridResult = new OpenCLGridResult(BigInteger.valueOf(5), 1, buffer);

        // assert the buffer really places X[0] at byte 8 (header 4 + index 4)
        assertThat(buffer.get(8), is(equalTo((byte) 0xAB)));

        // act
        PublicKeyBytes[] keys = gridResult.getPublicKeyBytes();

        // assert — uncompressed is 04 || X || Y, so uncompressed[1] is X[0] read from byte 8
        assertThat(keys[0].getUncompressed()[1], is(equalTo((byte) 0xAB)));
    }
    // </editor-fold>
}
