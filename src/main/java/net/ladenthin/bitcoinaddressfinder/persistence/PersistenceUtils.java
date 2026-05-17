// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import org.bitcoinj.base.LegacyAddress;
import org.bitcoinj.base.Network;
import org.bitcoinj.base.Sha256Hash;

public class PersistenceUtils {

    @Deprecated
    private final ByteBuffer emptyByteBuffer = ByteBuffer.allocateDirect(0).asReadOnlyBuffer();
    private final ByteBuffer zeroByteBuffer = longValueToByteBufferDirectAsReadOnlyBuffer(0L);

    public final Network network;

    public PersistenceUtils(Network network) {
        this.network = network;
    }

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

    @Deprecated
    private ByteBuffer addressListToByteBufferDirect(List<LegacyAddress> addresses) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(LegacyAddress.LENGTH * addresses.size());
        for (LegacyAddress address : addresses) {
            byteBuffer.put(address.getHash());
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    @Deprecated
    private List<LegacyAddress> byteBufferToAddressList(ByteBuffer byteBuffer) {
        List<LegacyAddress> addresses = new ArrayList<>();
        int count = byteBuffer.remaining() / LegacyAddress.LENGTH;
        for (int i = 0; i < count; i++) {
            byte[] hash160 = new byte[LegacyAddress.LENGTH];
            byteBuffer.get(hash160);
            addresses.add(LegacyAddress.fromPubKeyHash(network, hash160));
        }
        return addresses;
    }

    @Deprecated
    private ByteBuffer hashToByteBufferDirect(Sha256Hash hash) {
        return new ByteBufferUtility(true).byteArrayToByteBuffer(hash.getBytes());
    }

    private ByteBuffer longValueToByteBufferDirectAsReadOnlyBuffer(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
        byteBuffer.putLong(value);
        return byteBuffer.asReadOnlyBuffer();
    }
}
