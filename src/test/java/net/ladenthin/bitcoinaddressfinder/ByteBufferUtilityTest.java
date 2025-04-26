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

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;
import jdk.internal.ref.Cleaner;
import sun.nio.ch.DirectBuffer;

@RunWith(DataProviderRunner.class)
public class ByteBufferUtilityTest {
    
    /**
     * It does not matter if the value is true or false.
     */
    private final static boolean ALLOCATE_DIRECT_DOES_NOT_MATTER = false;
    
    @Before
    public void init() throws IOException {
    }
    
    
    // <editor-fold defaultstate="collapsed" desc="freeByteBuffer">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT, location = CommonDataProvider.class)
    public void freeByteBuffer_nullGiven_noExceptionThrown(boolean allocateDirect) throws IOException {
        // arrange
        
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(allocateDirect);

        // act
        byteBufferUtility.freeByteBuffer(null);

        // assert
    }
    
    @Test
    public void freeByteBuffer_cleanerIsNull_noExceptionThrown() throws IOException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException {
        // arrange
        byte[] bytesGiven = createDummyByteArray(7);
        
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        
        ByteBuffer bytesAsByteBuffer = byteBufferUtility.byteArrayToByteBuffer(bytesGiven);
        DirectBuffer directBuffer = (DirectBuffer)bytesAsByteBuffer;
        
        ByteBuffer duplicate = bytesAsByteBuffer.duplicate();
        DirectBuffer directBufferDuplicate = (DirectBuffer)duplicate;

        // pre assert
        assertThat(directBuffer.cleaner(), is(not(nullValue())));
        assertThat(directBufferDuplicate.cleaner(), is(nullValue()));
        
        // act
        byteBufferUtility.freeByteBuffer(bytesAsByteBuffer);

        // assert
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT, location = CommonDataProvider.class)
    public void freeByteBuffer_freeAGivenByteBuffer_noExceptionThrown(boolean allocateDirect) throws IOException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException {
        // arrange
        byte[] bytesGiven = createDummyByteArray(7);
        
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(allocateDirect);
        ByteBuffer bytesAsByteBuffer = byteBufferUtility.byteArrayToByteBuffer(bytesGiven);

        // pre assert
        if (allocateDirect) {
            assertThat(isDirectBufferFreed((DirectBuffer)bytesAsByteBuffer), is(false));
        }
        
        // act
        byteBufferUtility.freeByteBuffer(bytesAsByteBuffer);

        // assert
        if (allocateDirect) {
            assertThat(isDirectBufferFreed((DirectBuffer)bytesAsByteBuffer), is(true));
        }
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT, location = CommonDataProvider.class)
    public void freeByteBuffer_freeAGivenByteBufferGivenTwice_noExceptionThrown(boolean allocateDirect) throws IOException {
        // arrange
        byte[] bytesGiven = createDummyByteArray(7);
        
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(allocateDirect);
        ByteBuffer bytesAsByteBuffer = byteBufferUtility.byteArrayToByteBuffer(bytesGiven);

        // act
        byteBufferUtility.freeByteBuffer(bytesAsByteBuffer);
        byteBufferUtility.freeByteBuffer(bytesAsByteBuffer);

        // assert
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
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT, location = CommonDataProvider.class)
    public void byteArrayToByteBuffer_wrapped_allBytesEquals(boolean allocateDirect) throws IOException {
        // arrange
        byte[] bytes = createDummyByteArray(7);

        // act
        ByteBuffer byteBuffer = new ByteBufferUtility(allocateDirect).byteArrayToByteBuffer(bytes);

        // assert
        assertThat(byteBuffer.isDirect(), is(equalTo(allocateDirect)));
        byte[] bytesFromByteBuffer = new ByteBufferUtility(ALLOCATE_DIRECT_DOES_NOT_MATTER).byteBufferToBytes(byteBuffer);
        assertThat(Arrays.toString(bytesFromByteBuffer), is(equalTo("[0, 1, 2, 3, 4, 5, 6]")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="ByteBuffer Hex conversion">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT, location = CommonDataProvider.class)
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

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ALLOCATE_DIRECT, location = CommonDataProvider.class)
    public void getByteBufferFromHex_HexProvided_returnByteBuffer(boolean allocateDirect) throws IOException {
        // arrange
        String hexGiven = "00010203040506";
        byte[] bytesExpected = createDummyByteArray(7);

        // act
        ByteBuffer byteBuffer = new ByteBufferUtility(allocateDirect).getByteBufferFromHex(hexGiven);

        // assert
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
        // does not work with newer JVMs (21) anymore
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
            long address = getAddressFromDirectBuffer((DirectBuffer)directBuffer);
            addressTest = address == 0L;
        }

        return next == prev && addressTest;
    }

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

    @Test(expected = IllegalArgumentException.class)
    public void ensureByteBufferCapacityFitsInt_valueTooLarge_throwsException() {
        // arrange
        long capacity = (long) Integer.MAX_VALUE + 1L;

        // act
        ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity);

        // assert is handled by exception rule
    }

    @Test(expected = IllegalArgumentException.class)
    public void ensureByteBufferCapacityFitsInt_negativeValueGiven_throwsException() {
        // arrange
        long capacity = -1L;

        // act
        ByteBufferUtility.ensureByteBufferCapacityFitsInt(capacity);

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
    @Test(expected = IllegalStateException.class)
    public void allocateByteBufferDirectStrict_directAllocationDisabled_throwsException() {
        // arrange
        final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(false);

        // act
        byteBufferUtility.allocateByteBufferDirectStrict(16);

        // assert handled by exception
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="bigIntegerToBytes">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BIG_INTEGER_VARIANTS, location = CommonDataProvider.class)
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

    // <editor-fold defaultstate="collapsed" desc="bigIntegerToBytes">
    @Test
    public void bigIntegerToBytes_maxPrivateKeyGiven_returnWithoutLeadingZeros() throws IOException {
        // arrange
        BigInteger key = PublicKeyBytes.MAX_TECHNICALLY_PRIVATE_KEY;
        byte[] maxPrivateKey = key.toByteArray();
        assertThat(maxPrivateKey.length, is(equalTo(33)));
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

        // act
        byte[] keyWithoutLeadingZeros = byteBufferUtility.bigIntegerToBytes(key);

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
    public void reverse_nullArray_doesNothing() {
        // act
        ByteBufferUtility.reverse(null);

        // assert
        // No exception expected, nothing to assert
    }

    @Test
    public void reverse_singleElement_noChange() {
        byte[] input = { 0x42 };
        byte[] expected = { 0x42 };

        ByteBufferUtility.reverse(input);

        assertThat(input, is(equalTo(expected)));
    }

    @Test
    public void reverse_evenLengthArray_correctlyReversed() {
        byte[] input = { 0x01, 0x02, 0x03, 0x04 };
        byte[] expected = { 0x04, 0x03, 0x02, 0x01 };

        ByteBufferUtility.reverse(input);

        assertThat(input, is(equalTo(expected)));
    }

    @Test
    public void reverse_oddLengthArray_correctlyReversed() {
        byte[] input = { 0x01, 0x02, 0x03 };
        byte[] expected = { 0x03, 0x02, 0x01 };

        ByteBufferUtility.reverse(input);

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

    @Test(expected = BufferOverflowException.class)
    public void putToByteBuffer_arrayLargerThanBuffer_throwsBufferOverflowException() {
       ByteBuffer buffer = ByteBuffer.allocate(1);
       byte[] input = { 0x11, 0x22 };

       ByteBufferUtility.putToByteBuffer(buffer, input);
    }
    // </editor-fold>
}
