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

import java.nio.ByteOrder;

/**
 * Utility class for converting byte arrays between different endianness formats.
 * <p>
 * The conversion is based on a {@code sourceOrder} and a {@code targetOrder}.
 * If they differ, the array is reversed.
 * </p>
 */
public class EndiannessConverter {

    private final ByteOrder sourceOrder;
    private final ByteOrder targetOrder;
    private final ByteBufferUtility byteBufferUtility;

    /**
     * Creates a converter based on source and target byte orders.
     *
     * @param sourceOrder
     *     The byte order of the input data.
     * @param targetOrder
     *     The desired byte order after conversion.
     * @param byteBufferUtility
     *     The {@link ByteBufferUtility} instance used for reversing byte arrays.
     */
    EndiannessConverter(ByteOrder sourceOrder, ByteOrder targetOrder, ByteBufferUtility byteBufferUtility) {
        this.sourceOrder = sourceOrder;
        this.targetOrder = targetOrder;
        this.byteBufferUtility = byteBufferUtility;
    }

    /**
     * Converts the byte array if the source and target endianness differ.
     * The array may be modified in place.
     *
     * @param array The byte array to convert.
     */
    public void convertEndian(byte[] array) {
        if (mustConvert()) {
            byteBufferUtility.reverse(array);
        }
    }

    /**
     * Checks if a byte order conversion is required.
     *
     * @return true if source and target orders differ, false otherwise
     */
    public boolean mustConvert() {
        return sourceOrder != targetOrder;
    }

    public ByteOrder getSourceOrder() {
        return sourceOrder;
    }

    public ByteOrder getTargetOrder() {
        return targetOrder;
    }
}
