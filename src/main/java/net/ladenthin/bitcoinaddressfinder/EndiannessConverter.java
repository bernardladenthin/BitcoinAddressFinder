// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
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
