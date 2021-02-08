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
package net.ladenthin.bitcoinaddressfinder.persistence;

import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;

public class PersistenceUtils {

    private final ByteBuffer emptyByteBuffer = ByteBuffer.allocateDirect(0).asReadOnlyBuffer();
    private final ByteBuffer zeroByteBuffer = longValueToByteBufferDirectAsReadOnlyBuffer(0L);

    public final NetworkParameters networkParameters;

    public PersistenceUtils(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
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

    public ByteBuffer addressListToByteBufferDirect(List<LegacyAddress> addresses) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(LegacyAddress.LENGTH * addresses.size());
        for (LegacyAddress address : addresses) {
            byteBuffer.put(address.getHash160());
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    public List<LegacyAddress> byteBufferToAddressList(ByteBuffer byteBuffer) {
        List<LegacyAddress> addresses = new ArrayList<>();
        int count = byteBuffer.remaining() / LegacyAddress.LENGTH;
        for (int i = 0; i < count; i++) {
            byte[] hash160 = new byte[LegacyAddress.LENGTH];
            byteBuffer.get(hash160);
            addresses.add(new LegacyAddress(networkParameters, hash160));
        }
        return addresses;
    }

    public ByteBuffer hashToByteBufferDirect(Sha256Hash hash) {
        return new ByteBufferUtility(true).byteArrayToByteBuffer(hash.getBytes());
    }

    public ByteBuffer longValueToByteBufferDirectAsReadOnlyBuffer(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
        byteBuffer.putLong(value);
        return byteBuffer.asReadOnlyBuffer();
    }
}
