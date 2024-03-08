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

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.io.IOException;
import java.lang.reflect.Field;
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

}
