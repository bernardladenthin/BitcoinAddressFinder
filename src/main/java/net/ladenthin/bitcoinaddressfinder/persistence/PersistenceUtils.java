// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import org.bitcoinj.base.LegacyAddress;
import org.bitcoinj.base.Network;
import org.bitcoinj.base.Sha256Hash;

/**
 * Helper for converting between {@code long}/byte representations used in the LMDB layer.
 */
public class PersistenceUtils {

    // Preserved as a reusable helper for potential future use (see git history). No
    // current production or test caller; UnusedVariable suppressed to keep -Werror clean
    // while leaving the implementation available for revival.
    @Deprecated
    @SuppressWarnings("UnusedVariable")
    private final ByteBuffer emptyByteBuffer = ByteBuffer.allocateDirect(0).asReadOnlyBuffer();

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

    // Preserved as a reusable helper for potential future LegacyAddress-batch serialization.
    // No current production or test caller; UnusedMethod suppressed to keep -Werror clean.
    @Deprecated
    @SuppressWarnings("UnusedMethod")
    private ByteBuffer addressListToByteBufferDirect(Collection<LegacyAddress> addresses) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(LegacyAddress.LENGTH * addresses.size());
        for (LegacyAddress address : addresses) {
            byteBuffer.put(address.getHash());
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    // Inverse of addressListToByteBufferDirect; preserved as a pair for future revival.
    @Deprecated
    @SuppressWarnings("UnusedMethod")
    private List<LegacyAddress> byteBufferToAddressList(ByteBuffer byteBuffer) {
        int count = byteBuffer.remaining() / LegacyAddress.LENGTH;
        List<LegacyAddress> addresses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] hash160 = new byte[LegacyAddress.LENGTH];
            byteBuffer.get(hash160);
            addresses.add(LegacyAddress.fromPubKeyHash(network, hash160));
        }
        return addresses;
    }

    // Preserved as a reusable helper for potential future Sha256Hash-direct-buffer wiring.
    @Deprecated
    @SuppressWarnings("UnusedMethod")
    private ByteBuffer hashToByteBufferDirect(Sha256Hash hash) {
        return new ByteBufferUtility(true).byteArrayToByteBuffer(hash.getBytes());
    }

    private static ByteBuffer longValueToByteBufferDirectAsReadOnlyBuffer(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
        byteBuffer.putLong(value);
        return byteBuffer.asReadOnlyBuffer();
    }
}
