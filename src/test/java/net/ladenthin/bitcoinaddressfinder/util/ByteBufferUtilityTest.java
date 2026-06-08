// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ByteBufferUtilityTest {

    /**
     * It does not matter if the value is true or false.
     */
    private static final boolean ALLOCATE_DIRECT_DOES_NOT_MATTER = false;

    @BeforeEach
    public void init() throws IOException {}

    // <editor-fold defaultstate="collapsed" desc="helper methods">
    private ByteBuffer createDummyByteBuffer(int size) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        for (int i = 0; i < size; i++) {
            byteBuffer.put((byte) i);
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    private byte[] createDummyByteArray(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="byteBufferToBytes">
    @Test
    public void byteBufferToBytes_allBytesEquals() throws IOException {
        // arrange
        ByteBuffer dummy = createDummyByteBuffer(7);

        // act
        byte[] bytes = new ByteBufferUtility(false).byteBufferToBytes(dummy);

        // assert
        assertThat(Arrays.toString(bytes), is(equalTo("[0, 1, 2, 3, 4, 5, 6]")));
    }

    @Test
    public void byteBufferToBytes_idempotence() throws IOException {
        // arrange
        ByteBuffer dummy = createDummyByteBuffer(7);

        // act
        byte[] bytes1 = new ByteBufferUtility(false).byteBufferToBytes(dummy);
        byte[] bytes2 = new ByteBufferUtility(false).byteBufferToBytes(dummy);

        // assert
        assertThat(Arrays.toString(bytes2), is(equalTo(Arrays.toString(bytes1))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="byteArrayToByteBuffer">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT)
    public void byteArrayToByteBuffer_wrapped_allBytesEquals(boolean allocateDirect) throws IOException {
        // arrange
        byte[] bytes = createDummyByteArray(7);

        // act
        ByteBuffer byteBuffer = new ByteBufferUtility(allocateDirect).byteArrayToByteBuffer(bytes);

        // assert
        assertThat(byteBuffer.isDirect(), is(equalTo(allocateDirect)));
        byte[] bytesFromByteBuffer =
                new ByteBufferUtility(ALLOCATE_DIRECT_DOES_NOT_MATTER).byteBufferToBytes(byteBuffer);
        assertThat(Arrays.toString(bytesFromByteBuffer), is(equalTo("[0, 1, 2, 3, 4, 5, 6]")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="ByteBuffer Hex conversion">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT)
    public void getHexFromByteBuffer_byteBufferProvided_returnHex(boolean allocateDirect) throws IOException {
        // arrange
        String hexExpected = "00010203040506";
        byte[] bytesGiven = createDummyByteArray(7);

        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(allocateDirect);
        ByteBuffer bytesAsByteBuffer = byteBufferUtility.byteArrayToByteBuffer(bytesGiven);

        // act
        String hex = byteBufferUtility.getHexFromByteBuffer(bytesAsByteBuffer);

        // assert
        assertThat(hex, is(equalTo(hexExpected)));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT)
    public void getByteBufferFromHex_HexProvided_returnByteBuffer(boolean allocateDirect) throws IOException {
        // arrange
        String hexGiven = "00010203040506";
        byte[] bytesExpected = createDummyByteArray(7);

        // act
        ByteBuffer byteBuffer = new ByteBufferUtility(allocateDirect).getByteBufferFromHex(hexGiven);

        // assert
        assertThat(byteBuffer.isDirect(), is(equalTo(allocateDirect)));
        byte[] bytesFromByteBuffer =
                new ByteBufferUtility(ALLOCATE_DIRECT_DOES_NOT_MATTER).byteBufferToBytes(byteBuffer);
        assertThat(bytesFromByteBuffer, is(equalTo(bytesExpected)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="ensureByteBufferCapacityFitsInt">
    @Test
    public void ensureByteBufferCapacityFitsInt_zeroGiven_returnZero() {
        // arrange
        long capacity = 0L;

        // act
        int result = ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity);

        // assert
        assertThat(result, is(equalTo(0)));
    }

    @Test
    public void ensureByteBufferCapacityFitsInt_smallValueGiven_returnAsInt() {
        // arrange
        long capacity = 1234L;

        // act
        int result = ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity);

        // assert
        assertThat(result, is(equalTo(1234)));
    }

    @Test
    public void ensureByteBufferCapacityFitsInt_maxIntGiven_returnAsInt() {
        // arrange
        long capacity = Integer.MAX_VALUE;

        // act
        int result = ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity);

        // assert
        assertThat(result, is(equalTo(Integer.MAX_VALUE)));
    }

    @Test
    public void ensureByteBufferCapacityFitsInt_valueTooLarge_throwsException() {
        // arrange
        long capacity = (long) Integer.MAX_VALUE + 1L;

        // act
        assertThrows(IllegalArgumentException.class, () -> ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity));

        // assert is handled by exception rule
    }

    @Test
    public void ensureByteBufferCapacityFitsInt_negativeValueGiven_throwsException() {
        // arrange
        long capacity = -1L;

        // act
        assertThrows(IllegalArgumentException.class, () -> ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity));

        // assert is handled by exception rule
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="allocateByteBufferDirectStrict">
    /**
     * Ensures that a direct ByteBuffer is successfully allocated when configured to allow direct allocation.
     */
    @Test
    public void allocateByteBufferDirectStrict_directAllocationEnabled_returnsDirectBuffer() {
        // arrange
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

        // act
        ByteBuffer directBuffer = byteBufferUtility.allocateByteBufferDirectStrict(16);

        // assert
        assertThat(directBuffer.isDirect(), is(true));
        assertThat(directBuffer.capacity(), is(equalTo(16)));
    }

    /**
     * Ensures that an exception is thrown when trying to allocate a direct ByteBuffer while direct allocation is disabled.
     */
    @Test
    public void allocateByteBufferDirectStrict_directAllocationDisabled_throwsException() {
        // arrange
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(false);

        // act
        assertThrows(IllegalStateException.class, () -> byteBufferUtility.allocateByteBufferDirectStrict(16));

        // assert handled by exception
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="bigIntegerToBytes">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BIG_INTEGER_VARIANTS)
    public void bigIntegerToBytes_leadingZeroStripped(BigInteger input, int expectedLength, byte expectedFirstByte) {
        // act
        byte[] actualBytes = ByteBufferUtility.bigIntegerToBytes(input);

        // assert
        assertThat(actualBytes.length, is(equalTo(expectedLength)));
        if (actualBytes.length > 0) {
            assertThat(actualBytes[0], is(equalTo(expectedFirstByte)));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="bigIntegerToFixedLengthBytes">
    @Test
    public void bigIntegerToFixedLengthBytes_leadingZeroKey_zeroPadsToFullWidthWithoutStaleBytes() {
        final int width = OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES;
        // 0xF0 << 240 (== 0x00F0..00) strips to only 31 bytes; written into the reused 32-byte
        // key buffer without zero-padding, the missing high byte stays stale from the previous
        // key and corrupts the scalar fed to the kernel (the OpenCL leading-zero-key regression).
        final BigInteger leadingZeroKey = new BigInteger("F0", 16).shiftLeft(240);

        final byte[] fixed = ByteBufferUtility.bigIntegerToFixedLengthBytes(leadingZeroKey, width);

        // full width, big-endian 0x00 0xF0 0x00 ... 0x00 (high byte explicitly 0, not stale)
        final byte[] expected = new byte[width];
        expected[1] = (byte) 0xF0;
        assertThat(fixed.length, is(equalTo(width)));
        assertThat(Arrays.equals(fixed, expected), is(true));
    }

    @Test
    public void bigIntegerToFixedLengthBytes_valueTooLargeForLength_throwsIllegalArgumentException() {
        final int width = OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES;
        final BigInteger tooLarge = BigInteger.ONE.shiftLeft(256); // needs 33 bytes
        assertThrows(
                IllegalArgumentException.class, () -> ByteBufferUtility.bigIntegerToFixedLengthBytes(tooLarge, width));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="bigIntegerToBytes">
    @Test
    public void bigIntegerToBytes_maxPrivateKeyGiven_returnWithoutLeadingZeros() throws IOException {
        // arrange
        BigInteger key = PublicKeyBytes.MAX_TECHNICALLY_PRIVATE_KEY;
        byte[] maxPrivateKey = key.toByteArray();
        assertThat(maxPrivateKey.length, is(equalTo(33)));
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

        // act
        byte[] keyWithoutLeadingZeros = ByteBufferUtility.bigIntegerToBytes(key);

        // assert
        assertThat(keyWithoutLeadingZeros.length, is(equalTo(32)));

        // copy back
        byte[] arrayWithLeadingZero = new byte[33];
        System.arraycopy(keyWithoutLeadingZeros, 0, arrayWithLeadingZero, 1, 32);

        // assert content equals
        assertThat(arrayWithLeadingZero, is(equalTo(maxPrivateKey)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="reverse">
    @Test
    public void reverse_singleElement_noChange() {
        // arrange
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

        byte[] input = {0x42};
        byte[] expected = {0x42};

        // act
        byteBufferUtility.reverse(input);

        // assert
        assertThat(input, is(equalTo(expected)));
    }

    @Test
    public void reverse_evenLengthArray_correctlyReversed() {
        // arrange
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        byte[] input = {0x01, 0x02, 0x03, 0x04};
        byte[] expected = {0x04, 0x03, 0x02, 0x01};

        // act
        byteBufferUtility.reverse(input);

        // assert
        assertThat(input, is(equalTo(expected)));
    }

    @Test
    public void reverse_oddLengthArray_correctlyReversed() {
        // arrange
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        byte[] input = {0x01, 0x02, 0x03};
        byte[] expected = {0x03, 0x02, 0x01};

        // act
        byteBufferUtility.reverse(input);

        // assert
        assertThat(input, is(equalTo(expected)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="putToByteBuffer">
    @Test
    public void putToByteBuffer_arraySmallerThanBuffer_writtenCorrectly() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        byte[] input = {0x11, 0x22};

        ByteBufferUtility.putToByteBuffer(buffer, input);

        buffer.rewind();
        assertThat(buffer.get(), is((byte) 0x11));
        assertThat(buffer.get(), is((byte) 0x22));
    }

    @Test
    public void putToByteBuffer_arraySameSizeAsBuffer_writtenCompletely() {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        byte[] input = {0x11, 0x22};

        ByteBufferUtility.putToByteBuffer(buffer, input);

        buffer.rewind();
        assertThat(buffer.get(), is((byte) 0x11));
        assertThat(buffer.get(), is((byte) 0x22));
    }

    @Test
    public void putToByteBuffer_arrayLargerThanBuffer_throwsBufferOverflowException() {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        byte[] input = {0x11, 0x22};

        assertThrows(BufferOverflowException.class, () -> ByteBufferUtility.putToByteBuffer(buffer, input));
    }
    // </editor-fold>
}
