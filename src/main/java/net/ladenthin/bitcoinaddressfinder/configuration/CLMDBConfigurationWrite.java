// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.bitcoinj.base.Coin;

/**
 * Configuration for opening the LMDB database in read-write mode.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CLMDBConfigurationWrite extends CLMDBConfigurationReadOnly {

    /** Creates a new {@link CLMDBConfigurationWrite}. */
    public CLMDBConfigurationWrite() {}

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

    /** Whether to grow the LMDB map size automatically when it becomes full. */
    public boolean increaseMapAutomatically = true;

    /**
     * LMDB site increase in MiB (e.g. 1024). Attention: If the value is too low, the increase for a full db was not enough and a {@link org.lmdbjava.Env.MapFullException} will be thrown.
     */
    public long increaseSizeInMiB = 8;
}
