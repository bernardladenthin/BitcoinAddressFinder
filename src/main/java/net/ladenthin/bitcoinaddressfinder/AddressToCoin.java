// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.nio.ByteBuffer;
import org.bitcoinj.base.Coin;
import org.jspecify.annotations.NonNull;

/**
 * Represents an immutable mapping from a hash160 to a Coin amount.
 *
 * <p>Note: hash160 is expected to be exactly {@code PublicKeyBytes.HASH160_SIZE} bytes long.</p>
 */
public record AddressToCoin(@NonNull ByteBuffer hash160, @NonNull Coin coin, @NonNull AddressType type) {

    public AddressToCoin {
        if (hash160.limit() != PublicKeyBytes.RIPEMD160_HASH_NUM_BYTES) {
            throw new IllegalArgumentException("Given hash160 has not the correct size: " + hash160.limit());
        }
    }

    @Override
    public @NonNull String toString() {
        return "AddressToCoin{" +
                "hash160=" + new ByteBufferUtility(false).getHexFromByteBuffer(hash160) +
                ", coin=" + coin +
                ", type=" + type +
                '}';
    }
}
