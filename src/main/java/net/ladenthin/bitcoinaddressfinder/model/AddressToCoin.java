// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.model;

import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import org.bitcoinj.base.Coin;
import org.jspecify.annotations.NonNull;

/**
 * Represents an immutable mapping from a hash160 to a Coin amount.
 *
 * <p>Note: hash160 is expected to be exactly
 * {@link OpenClKernelConstants#RIPEMD160_HASH_NUM_BYTES} bytes long.</p>
 *
 * @param hash160 the 20-byte RIPEMD-160 hash of the public key
 * @param coin    the amount associated with the address
 * @param type    the address type
 */
public record AddressToCoin(
        @NonNull ByteBuffer hash160,
        @NonNull Coin coin,
        @NonNull AddressType type) {

    /**
     * Compact constructor validating the {@code hash160} byte length.
     */
    public AddressToCoin {
        if (hash160.limit() != OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES) {
            throw new IllegalArgumentException("Given hash160 has not the correct size: " + hash160.limit());
        }
    }

    /**
     * Custom {@code toString} override.
     *
     * <p>Java's default record {@code toString} renders {@code hash160} as
     * {@code java.nio.HeapByteBuffer[pos=0 lim=20 cap=20]} (uninformative). This override
     * formats the 20-byte hash as a lower-case hex string for log readability, and matches
     * the standard record-style {@code Class[field=value, ...]} format so the output sits
     * naturally alongside the other BAF records (KeyUtility, OpenCLDevice, etc.).
     *
     * <p>This is the only record in BAF that needs a custom toString; the rest can rely on
     * the auto-generated record form because their components have informative defaults.
     * The {@code coin} and {@code type} components delegate to the bitcoinj {@code Coin}
     * {@code AddressType} {@code toString} implementations respectively.
     */
    @Override
    public @NonNull String toString() {
        return "AddressToCoin[hash160="
                + new ByteBufferUtility(false).getHexFromByteBuffer(hash160) + ", coin="
                + coin + ", type="
                + type + ']';
    }
}
