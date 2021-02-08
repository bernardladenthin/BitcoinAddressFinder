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
package net.ladenthin.bitcoinaddressfinder.configuration;

import org.bitcoinj.core.Coin;

public class CLMDBConfigurationWrite extends CLMDBConfigurationReadOnly {

    /**
     * LMDB site in MiB (e.g. 1024).
     */
    public int initialMapSizeInMiB = 1;

    /**
     * Enable if empty hash160s should be deleted. Empty means zero satoshis. It
     * has a higher priority than {@link #useStaticAmount} which means empty
     * addresses will be delted instead written with {@link #staticAmount}.
     */
    public boolean deleteEmptyAddresses = false;

    /**
     * Only used if {@link #useStaticAmount} is {@code true}.
     * {@link Coin#ZERO} allows a smaller database.
     */
    public long staticAmount = Coin.ZERO.value;

    /**
     * Use the static amount {@link #staticAmount} instead the imported amount
     * to obscure amount and allow higher database compression.
     */
    public boolean useStaticAmount = true;
    
    public boolean increaseMapAutomatically = true;
    
    /**
     * LMDB site increase in MiB (e.g. 1024). Attention: If the value is too low, the increase for a full db was not enough and a {@link org.lmdbjava.Env.MapFullException} will be thrown.
     */
    public long increaseSizeInMiB = 8;
}
