// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.nio.ByteBuffer;
import org.bitcoinj.base.Network;

/**
 * Helper for converting between {@code long}/byte representations used in the LMDB layer.
 */
public class PersistenceUtils {

    private final ByteBuffer zeroByteBuffer = longValueToByteBufferDirectAsReadOnlyBuffer(0L);

    /** The {@link Network} associated with the persistence layer. */
    public final Network network;

    /**
     * Creates a new helper for the given network.
     *
     * @param network the network used to interpret addresses
     */
    public PersistenceUtils(Network network) {
        this.network = network;
    }

    /**
     * Returns a direct {@link ByteBuffer} containing the big-endian representation of {@code longValue}.
     *
     * @param longValue the value to encode
     * @return a direct buffer holding the encoded value
     */
    public ByteBuffer longToByteBufferDirect(long longValue) {
        if (longValue == 0) {
            // use the cached zero value to reduce allocations
            return zeroByteBuffer;
        }
        ByteBuffer newValue = ByteBuffer.allocateDirect(Long.BYTES);
        newValue.putLong(longValue);
        newValue.flip();
        return newValue;
    }

    private static ByteBuffer longValueToByteBufferDirectAsReadOnlyBuffer(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
        byteBuffer.putLong(value);
        return byteBuffer.asReadOnlyBuffer();
    }
}
