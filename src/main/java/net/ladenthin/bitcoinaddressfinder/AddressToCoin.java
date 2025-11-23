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
