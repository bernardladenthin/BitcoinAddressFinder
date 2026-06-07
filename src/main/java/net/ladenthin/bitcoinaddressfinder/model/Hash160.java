// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.model;

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
 * <p>Both implementations compute the identical standard
 * RIPEMD-160(SHA-256(input)); they are exposed as two distinct, branch-free
 * methods so each can be selected explicitly and unit/mutation-tested on its
 * own:</p>
 * <ul>
 *   <li>{@link #hashFast(byte[])} &#x2192; Guava SHA-256 + Bouncy Castle
 *       RIPEMD-160 (the fast path)</li>
 *   <li>{@link #hashSlow(byte[])} &#x2192; {@link CryptoUtils#sha256hash160(byte[])}
 *       (the BitcoinJ built-in)</li>
 * </ul>
 *
 * <p>{@link #hash(byte[])} is the default entry point and delegates to the fast
 * path. There is intentionally no {@code boolean} selector field: a runtime flag
 * choosing between two functions that return identical bytes is an equivalent
 * mutation (untestable), so the choice is made by calling the method you want.
 * Performance comparison between the two is done by the JMH benchmark calling
 * {@code hashFast} / {@code hashSlow} directly.</p>
 */
@ToString
@EqualsAndHashCode
public class Hash160 {

    /** Creates a {@link Hash160}. */
    public Hash160() {
        // stateless
    }

    /**
     * Computes RIPEMD-160(SHA-256(input)) using the default (fast) implementation.
     *
     * @param input the raw public key bytes (compressed or uncompressed)
     * @return a 20-byte hash160 result
     */
    public byte @NonNull [] hash(byte @NonNull [] input) {
        return hashFast(input);
    }

    /**
     * Computes RIPEMD-160(SHA-256(input)) via Guava SHA-256 + Bouncy Castle RIPEMD-160.
     *
     * @param input the raw public key bytes (compressed or uncompressed)
     * @return a 20-byte hash160 result
     */
    public byte @NonNull [] hashFast(byte @NonNull [] input) {
        byte[] sha256 = Hashing.sha256().hashBytes(input).asBytes();
        RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(sha256, 0, sha256.length);
        byte[] out = new byte[OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES];
        digest.doFinal(out, 0);
        return out;
    }

    /**
     * Computes RIPEMD-160(SHA-256(input)) via {@link CryptoUtils#sha256hash160(byte[])}
     * (the BitcoinJ built-in).
     *
     * @param input the raw public key bytes (compressed or uncompressed)
     * @return a 20-byte hash160 result
     */
    public byte @NonNull [] hashSlow(byte @NonNull [] input) {
        return CryptoUtils.sha256hash160(input);
    }
}
