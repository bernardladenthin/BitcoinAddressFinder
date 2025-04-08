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

import com.google.errorprone.annotations.Immutable;
import java.nio.ByteBuffer;
import lombok.EqualsAndHashCode;
import org.bitcoinj.core.Coin;
import lombok.NonNull;

@Immutable
@EqualsAndHashCode
public class AddressToCoin {

    @NonNull
    private final ByteBuffer hash160;
    @NonNull
    private final Coin coin;

    public AddressToCoin(@NonNull ByteBuffer hash160, @NonNull Coin coin) {
        if (hash160.limit() != PublicKeyBytes.HASH160_SIZE) {
            throw new IllegalArgumentException("Given hash160 has not the correct size: " + hash160.limit());
        }
        this.hash160 = hash160;
        this.coin = coin;
    }

    @NonNull
    public Coin getCoin() {
        return coin;
    }

    @NonNull
    public ByteBuffer getHash160() {
        return hash160;
    }

    // handcraftet to print the ByteBuffer pretty
    @Override
    public String toString() {
        return "AddressToCoin{" + "hash160=" + new ByteBufferUtility(false).getHexFromByteBuffer(hash160) + ", coin=" + coin + '}';
    }
    

}
