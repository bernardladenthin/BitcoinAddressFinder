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

import org.bitcoinj.base.Bech32;
import org.bitcoinj.base.exceptions.AddressFormatException;

import java.lang.reflect.Method;
import java.util.Arrays;

import static net.ladenthin.bitcoinaddressfinder.AddressTxtLine.BITCOIN_CASH_PREFIX;

public class Bech32Helper {

    /**
     * Bech32 character set as defined in BIP-0173.
     */
    final static String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";

    /**
     * Lookup table for fast Bech32 character-to-value resolution.
     */
    private static final int[] LOOKUP = new int[128];

    static {
        Arrays.fill(LOOKUP, -1);
        for (int i = 0; i < CHARSET.length(); i++) {
            LOOKUP[CHARSET.charAt(i)] = i;
        }
    }

    public byte[] decodeBech32CharsetToValues(String base32String) {
        // Decode characters to 5-bit values
        int len = base32String.length();
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            char c = base32String.charAt(i);
            if (c >= LOOKUP.length || LOOKUP[c] == -1) {
                throw new IllegalArgumentException("Invalid character in Bech32 string: " + c);
            }
            result[i] = (byte) LOOKUP[c];
        }

        return result;
    }

    /**
     * Return the data, fully-decoded with 8-bits per byte.
     * @return The data, fully-decoded as a byte array.
     */
    private byte[] decode5to8(byte[] bytes) throws ReflectiveOperationException {
        return invokeConvertBitsStatic(bytes, 0, bytes.length, 5, 8, false);
    }

    private byte[] decode5to8WithPadding(byte[] bytes) throws ReflectiveOperationException {
        return invokeConvertBitsStatic(bytes, 0, bytes.length, 5, 8, true);
    }

    private byte[] encode8to5(byte[] data) throws ReflectiveOperationException {
        return invokeConvertBitsStatic(data, 0, data.length, 8, 5, true);
    }

    @SuppressWarnings("unchecked")
    private byte[] invokeConvertBitsStatic(byte[] in, int inStart, int inLen, int fromBits, int toBits, boolean pad) throws ReflectiveOperationException {
        Method method = Bech32.class.getDeclaredMethod("convertBits", byte[].class, int.class, int.class, int.class, int.class, boolean.class);
        method.setAccessible(true);
        try {
            return (byte[]) method.invoke(null, in, inStart, inLen, fromBits, toBits, pad);
        } catch (ReflectiveOperationException e) {
            // rethrow AddressFormatException if it's the underlying cause
            Throwable cause = e.getCause();
            if (cause instanceof AddressFormatException) {
                throw (AddressFormatException) cause;
            }
            throw e;
        }
    }

    public byte[] extractPKHFromBitcoinCashAddress(String address) throws ReflectiveOperationException {
        if (address.startsWith(BITCOIN_CASH_PREFIX)) {
            address = address.substring(BITCOIN_CASH_PREFIX.length());
        }
        byte[] decoded5 = decodeBech32CharsetToValues(address);
        byte[] decoded8 = decode5to8WithPadding(decoded5);
        // Extracts the payload portion from the decoded Bech32 data.
        // Skips the first byte (address type/version) and removes the last 6 bytes (checksum).
        // The result is the raw payload encoded in 5-bit format.
        return Arrays.copyOfRange(decoded8, 1, decoded8.length - 6);
    }

    public byte[] getWitnessPrograms(Bech32.Bech32Data bechData) throws ReflectiveOperationException {
        return invokeProtectedMethod(bechData, "witnessProgram", byte[].class);
    }

    public Short getWitnessVersion(Bech32.Bech32Data bechData) throws ReflectiveOperationException {
        return invokeProtectedMethod(bechData, "witnessVersion", Short.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeProtectedMethod(Bech32.Bech32Bytes bech32Bytes, String methodName, Class<T> returnType) throws ReflectiveOperationException  {
        Class<?> clazz = Bech32.Bech32Bytes.class;
        Method method = clazz.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (T) method.invoke(bech32Bytes);
    }
}
