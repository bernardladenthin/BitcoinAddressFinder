// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import jdk.internal.ref.Cleaner;
import sun.nio.ch.DirectBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ByteBufferUtilityTest {
    
    private final static boolean ALLOCATE_DIRECT_DOES_NOT_MATTER = false;
    
    @BeforeEach
    public void init() throws IOException {
    }
    
    
    // <editor-fold defaultstate="collapsed" desc="freeByteBuffer">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT)
    public void freeByteBuffer_nullGiven_noExceptionThrown(boolean allocateDirect) throws IOException {
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(allocateDirect);
        byteBufferUtility.freeByteBuffer(null);
    }
    
    @Test
    public void freeByteBuffer_cleanerIsNull_noExceptionThrown() throws IOException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException {
        byte[] bytesGiven = createDummyByteArray(7);
        
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        
        ByteBuffer bytesAsByteBuffer = byteBufferUtility.byteArrayToByteBuffer(bytesGiven);
        DirectBuffer directBuffer = (DirectBuffer)bytesAsByteBuffer;
        
        ByteBuffer duplicate = bytesAsByteBuffer.duplicate();
        DirectBuffer directBufferDuplicate = (DirectBuffer)duplicate;

        assertThat(directBuffer.cleaner(), is(not(nullValue())));
        assertThat(directBufferDuplicate.cleaner(), is(nullValue()));
        
        byteBufferUtility.freeByteBuffer(bytesAsByteBuffer);
    }
    
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT)
    public void freeByteBuffer_freeAGivenByteBuffer_noExceptionThrown(boolean allocateDirect) throws IOException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException {
        byte[] bytesGiven = createDummyByteArray(7);
        
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(allocateDirect);
        ByteBuffer bytesAsByteBuffer = byteBufferUtility.byteArrayToByteBuffer(bytesGiven);

        if (allocateDirect) {
            assertThat(isDirectBufferFreed((DirectBuffer)bytesAsByteBuffer), is(false));
        }
        
        byteBufferUtility.freeByteBuffer(bytesAsByteBuffer);

        if (allocateDirect) {
            assertThat(isDirectBufferFreed((DirectBuffer)bytesAsByteBuffer), is(true));
        }
    }
    
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT)
    public void freeByteBuffer_freeAGivenByteBufferGivenTwice_noExceptionThrown(boolean allocateDirect) throws IOException {
        byte[] bytesGiven = createDummyByteArray(7);
        
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(allocateDirect);
        ByteBuffer bytesAsByteBuffer = byteBufferUtility.byteArrayToByteBuffer(bytesGiven);

        byteBufferUtility.freeByteBuffer(bytesAsByteBuffer);
        byteBufferUtility.freeByteBuffer(bytesAsByteBuffer);
    }
    
    // </editor-fold>

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
        ByteBuffer dummy = createDummyByteBuffer(7);
        byte[] bytes = new ByteBufferUtility(false).byteBufferToBytes(dummy);
        assertThat(Arrays.toString(bytes), is(equalTo("[0, 1, 2, 3, 4, 5, 6]")));
    }

    @Test
    public void byteBufferToBytes_idempotence() throws IOException {
        ByteBuffer dummy = createDummyByteBuffer(7);
        byte[] bytes1 = new ByteBufferUtility(false).byteBufferToBytes(dummy);
        byte[] bytes2 = new ByteBufferUtility(false).byteBufferToBytes(dummy);
        assertThat(Arrays.toString(bytes2), is(equalTo(Arrays.toString(bytes1))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="byteArrayToByteBuffer">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT)
    public void byteArrayToByteBuffer_wrapped_allBytesEquals(boolean allocateDirect) throws IOException {
        byte[] bytes = createDummyByteArray(7);
        ByteBuffer byteBuffer = new ByteBufferUtility(allocateDirect).byteArrayToByteBuffer(bytes);
        assertThat(byteBuffer.isDirect(), is(equalTo(allocateDirect)));
        byte[] bytesFromByteBuffer = new ByteBufferUtility(ALLOCATE_DIRECT_DOES_NOT_MATTER).byteBufferToBytes(byteBuffer);
        assertThat(Arrays.toString(bytesFromByteBuffer), is(equalTo("[0, 1, 2, 3, 4, 5, 6]")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="ByteBuffer Hex conversion">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT)
    public void getHexFromByteBuffer_byteBufferProvided_returnHex(boolean allocateDirect) throws IOException {
        String hexExpected = "00010203040506";
        byte[] bytesGiven = createDummyByteArray(7);
        
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(allocateDirect);
        ByteBuffer bytesAsByteBuffer = byteBufferUtility.byteArrayToByteBuffer(bytesGiven);

        String hex = byteBufferUtility.getHexFromByteBuffer(bytesAsByteBuffer);

        assertThat(hex, is(equalTo(hexExpected)));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT)
    public void getByteBufferFromHex_HexProvided_returnByteBuffer(boolean allocateDirect) throws IOException {
        String hexGiven = "00010203040506";
        byte[] bytesExpected = createDummyByteArray(7);

        ByteBuffer byteBuffer = new ByteBufferUtility(allocateDirect).getByteBufferFromHex(hexGiven);

        assertThat(byteBuffer.isDirect(), is(equalTo(allocateDirect)));
        byte[] bytesFromByteBuffer = new ByteBufferUtility(ALLOCATE_DIRECT_DOES_NOT_MATTER).byteBufferToBytes(byteBuffer);
        assertThat(bytesFromByteBuffer, is(equalTo(bytesExpected)));
    }
    // </editor-fold>
    
    private long getAddressFromDirectBuffer(DirectBuffer directBuffer) throws IllegalArgumentException, NoSuchFieldException, SecurityException, IllegalAccessException {
        Cleaner cleaner = directBuffer.cleaner();
        Field thunkField = cleaner.getClass().getDeclaredField("thunk");
        thunkField.setAccessible(true);
        Object deallocator = thunkField.get(cleaner);
        
        Field addressField = deallocator.getClass().getDeclaredField("address");
        addressField.setAccessible(true);
        return addressField.getLong(deallocator);
    }

    private boolean isDirectBufferFreed(DirectBuffer directBuffer) throws IllegalArgumentException, NoSuchFieldException, SecurityException, IllegalAccessException {
        boolean testWithAddress = false;
        boolean addressTest = true;
        
        Cleaner cleaner = directBuffer.cleaner();

        Field nextField = cleaner.getClass().getDeclaredField("next");
        nextField.setAccessible(true);
        Object next = nextField.get(cleaner);

        Field prevField = cleaner.getClass().getDeclaredField("prev");
        prevField.setAccessible(true);
        Object prev = prevField.get(cleaner);

        if (testWithAddress) {
            long address = getAddressFromDirectBuffer(directBuffer);
            addressTest = address == 0L;
        }

        return next == prev && addressTest;
    }

    // <editor-fold defaultstate="collapsed" desc="ensureByteBufferCapacityFitsInt">
    @Test
    public void ensureByteBufferCapacityFitsInt_zeroGiven_returnZero() {
        long capacity = 0L;
        int result = ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity);
        assertThat(result, is(equalTo(0)));
    }

    @Test
    public void ensureByteBufferCapacityFitsInt_smallValueGiven_returnAsInt() {
        long capacity = 1234L;
        int result = ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity);
        assertThat(result, is(equalTo(1234)));
    }

    @Test
    public void ensureByteBufferCapacityFitsInt_maxIntGiven_returnAsInt() {
        long capacity = Integer.MAX_VALUE;
        int result = ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity);
        assertThat(result, is(equalTo(Integer.MAX_VALUE)));
    }

    @Test
    public void ensureByteBufferCapacityFitsInt_valueTooLarge_throwsException() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            long capacity = (long) Integer.MAX_VALUE + 1L;
            ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity);
        });
    }

    @Test
    public void ensureByteBufferCapacityFitsInt_negativeValueGiven_throwsException() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            long capacity = -1L;
            ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity);
        });
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="allocateByteBufferDirectStrict">
    @Test
    public void allocateByteBufferDirectStrict_directAllocationEnabled_returnsDirectBuffer() {
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        ByteBuffer directBuffer = byteBufferUtility.allocateByteBufferDirectStrict(16);
        assertThat(directBuffer.isDirect(), is(true));
        assertThat(directBuffer.capacity(), is(equalTo(16)));
    }

    @Test
    public void allocateByteBufferDirectStrict_directAllocationDisabled_throwsException() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(false);
            byteBufferUtility.allocateByteBufferDirectStrict(16);
        });
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="bigIntegerToBytes">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BIG_INTEGER_VARIANTS)
    public void bigIntegerToBytes_leadingZeroStripped(BigInteger input, int expectedLength, byte expectedFirstByte) {
        byte[] actualBytes = ByteBufferUtility.bigIntegerToBytes(input);
        assertThat(actualBytes.length, is(equalTo(expectedLength)));
        if (actualBytes.length > 0) {
            assertThat(actualBytes[0], is(equalTo(expectedFirstByte)));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="bigIntegerToBytes">
    @Test
    public void bigIntegerToBytes_maxPrivateKeyGiven_returnWithoutLeadingZeros() throws IOException {
        BigInteger key = PublicKeyBytes.MAX_TECHNICALLY_PRIVATE_KEY;
        byte[] maxPrivateKey = key.toByteArray();
        assertThat(maxPrivateKey.length, is(equalTo(33)));
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

        byte[] keyWithoutLeadingZeros = ByteBufferUtility.bigIntegerToBytes(key);

        assertThat(keyWithoutLeadingZeros.length, is(equalTo(32)));
        
        byte[] arrayWithLeadingZero = new byte[33];
        System.arraycopy(keyWithoutLeadingZeros, 0, arrayWithLeadingZero, 1, 32);
        
        assertThat(arrayWithLeadingZero, is(equalTo(maxPrivateKey)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="reverse">
    @Test
    public void reverse_singleElement_noChange() {
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        byte[] input = { 0x42 };
        byte[] expected = { 0x42 };
        byteBufferUtility.reverse(input);
        assertThat(input, is(equalTo(expected)));
    }

    @Test
    public void reverse_evenLengthArray_correctlyReversed() {
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        byte[] input = { 0x01, 0x02, 0x03, 0x04 };
        byte[] expected = { 0x04, 0x03, 0x02, 0x01 };
        byteBufferUtility.reverse(input);
        assertThat(input, is(equalTo(expected)));
    }

    @Test
    public void reverse_oddLengthArray_correctlyReversed() {
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        byte[] input = { 0x01, 0x02, 0x03 };
        byte[] expected = { 0x03, 0x02, 0x01 };
        byteBufferUtility.reverse(input);
        assertThat(input, is(equalTo(expected)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="putToByteBuffer">
    @Test
    public void putToByteBuffer_arraySmallerThanBuffer_writtenCorrectly() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        byte[] input = { 0x11, 0x22 };
        ByteBufferUtility.putToByteBuffer(buffer, input);
        buffer.rewind();
        assertThat(buffer.get(), is((byte) 0x11));
        assertThat(buffer.get(), is((byte) 0x22));
    }

    @Test
    public void putToByteBuffer_arraySameSizeAsBuffer_writtenCompletely() {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        byte[] input = { 0x11, 0x22 };
        ByteBufferUtility.putToByteBuffer(buffer, input);
        buffer.rewind();
        assertThat(buffer.get(), is((byte) 0x11));
        assertThat(buffer.get(), is((byte) 0x22));
    }

    @Test
    public void putToByteBuffer_arrayLargerThanBuffer_throwsBufferOverflowException() {
        org.junit.jupiter.api.Assertions.assertThrows(BufferOverflowException.class, () -> {
           ByteBuffer buffer = ByteBuffer.allocate(1);
           byte[] input = { 0x11, 0x22 };
           ByteBufferUtility.putToByteBuffer(buffer, input);
        });
    }
    // </editor-fold>
}
