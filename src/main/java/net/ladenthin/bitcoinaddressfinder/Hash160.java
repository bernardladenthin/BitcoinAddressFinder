// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import com.google.common.hash.Hashing;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import org.bitcoinj.crypto.internal.CryptoUtils;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.jspecify.annotations.NonNull;

/**
 * Encapsulates the two SHA-256 + RIPEMD-160 implementations used to derive
 * Bitcoin hash160 values from public keys.
 *
 * <p>The implementation is selected at construction time:</p>
 * <ul>
 *   <li>{@code useFast = true} &#x2192; Guava SHA-256 + Bouncy Castle
 *       RIPEMD-160 (the fast path)</li>
 *   <li>{@code useFast = false} &#x2192; {@link CryptoUtils#sha256hash160}
 *       (the BitcoinJ built-in)</li>
 * </ul>
 *
 * <p>The default constructor uses {@link #DEFAULT_USE_FAST}.</p>
 */
@ToString
@EqualsAndHashCode
public class Hash160 {

    /**
     * Default algorithm selection: {@code true} &#x2192; Guava + Bouncy Castle
     * (fast path).
     */
    public static final boolean DEFAULT_USE_FAST = true;

    private final boolean useFast;

    /** Creates a {@link Hash160} using {@link #DEFAULT_USE_FAST}. */
    public Hash160() {
        this(DEFAULT_USE_FAST);
    }

    /**
     * Creates a {@link Hash160} with an explicit algorithm selection.
     *
     * @param useFast {@code true} for Guava + Bouncy Castle, {@code false}
     *                for {@link CryptoUtils#sha256hash160}
     */
    public Hash160(boolean useFast) {
        this.useFast = useFast;
    }

    /**
     * Computes RIPEMD-160(SHA-256(input)).
     *
     * @param input the raw public key bytes (compressed or uncompressed)
     * @return a 20-byte hash160 result
     */
    public byte @NonNull [] hash(byte @NonNull [] input) {
        if (useFast) {
            byte[] sha256 = Hashing.sha256().hashBytes(input).asBytes();
            RIPEMD160Digest digest = new RIPEMD160Digest();
            digest.update(sha256, 0, sha256.length);
            byte[] out = new byte[OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES];
            digest.doFinal(out, 0);
            return out;
        } else {
            return CryptoUtils.sha256hash160(input);
        }
    }
}
