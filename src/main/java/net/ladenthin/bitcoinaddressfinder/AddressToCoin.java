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
import javax.annotation.Nonnull;
import org.bitcoinj.core.Coin;

public class AddressToCoin {

    @Nonnull
    private final ByteBuffer hash160;
    @Nonnull
    private final Coin coin;

    public AddressToCoin(@Nonnull ByteBuffer hash160, @Nonnull Coin coin) {
        this.hash160 = hash160;
        this.coin = coin;
    }

    @Nonnull
    public Coin getCoin() {
        return coin;
    }

    @Nonnull
    public ByteBuffer getHash160() {
        return hash160;
    }

    @Override
    public String toString() {
        return new ByteBufferUtility(false).getHexFromByteBuffer(hash160);
    }

}
