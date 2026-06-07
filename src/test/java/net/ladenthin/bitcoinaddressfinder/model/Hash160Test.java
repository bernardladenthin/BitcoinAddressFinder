// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import org.junit.jupiter.api.Test;

/**
 * Mutation-oriented tests for {@link Hash160}.
 *
 * <p>The two implementations compute the identical standard
 * RIPEMD-160(SHA-256(input)); each is exercised directly against shared known
 * vectors and asserted equal to the other. Because {@link Hash160} no longer
 * branches on a runtime flag, every mutation in the class is killable (no
 * equivalent selector mutant).</p>
 */
public class Hash160Test {

    /** RIPEMD-160(SHA-256("")) — empty input. */
    private static final String HASH160_EMPTY = "b472a266d0bd89c13706a4132ccfb16f7c3b9fcb";

    /** RIPEMD-160(SHA-256("hello")). */
    private static final String HASH160_HELLO = "b6a9c8c230722b7c748331a8b450f05566dc7d0f";

    /** RIPEMD-160(SHA-256({0..9})). */
    private static final String HASH160_0_TO_9 = "329d6233d3245ac3618bd637bb8698030172e11c";

    private static byte[] hex(final String s) {
        final byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static byte[] bytes(final int... values) {
        final byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // hashFast — Guava SHA-256 + Bouncy Castle RIPEMD-160
    // -------------------------------------------------------------------------

    @Test
    public void hashFastMatchesKnownVectorEmpty() {
        assertArrayEquals(hex(HASH160_EMPTY), new Hash160().hashFast(new byte[0]));
    }

    @Test
    public void hashFastMatchesKnownVectorHello() {
        assertArrayEquals(hex(HASH160_HELLO), new Hash160().hashFast("hello".getBytes()));
    }

    @Test
    public void hashFastMatchesKnownVectorTenBytes() {
        assertArrayEquals(hex(HASH160_0_TO_9), new Hash160().hashFast(bytes(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
    }

    @Test
    public void hashFastProducesTwentyBytes() {
        assertThat(
                new Hash160().hashFast("hello".getBytes()).length, is(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES));
    }

    // -------------------------------------------------------------------------
    // hashSlow — bitcoinj CryptoUtils.sha256hash160
    // -------------------------------------------------------------------------

    @Test
    public void hashSlowMatchesKnownVectorEmpty() {
        assertArrayEquals(hex(HASH160_EMPTY), new Hash160().hashSlow(new byte[0]));
    }

    @Test
    public void hashSlowMatchesKnownVectorHello() {
        assertArrayEquals(hex(HASH160_HELLO), new Hash160().hashSlow("hello".getBytes()));
    }

    @Test
    public void hashSlowMatchesKnownVectorTenBytes() {
        assertArrayEquals(hex(HASH160_0_TO_9), new Hash160().hashSlow(bytes(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
    }

    // -------------------------------------------------------------------------
    // hash — default entry point (delegates to the fast path)
    // -------------------------------------------------------------------------

    @Test
    public void hashDelegatesToFastPath() {
        final byte[] input = "hello".getBytes();
        assertArrayEquals(hex(HASH160_HELLO), new Hash160().hash(input));
        assertArrayEquals(new Hash160().hashFast(input), new Hash160().hash(input));
    }

    @Test
    public void bothImplementationsProduceIdenticalOutput() {
        final byte[] input = bytes(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertArrayEquals(new Hash160().hashFast(input), new Hash160().hashSlow(input));
    }

    // -------------------------------------------------------------------------
    // Lombok value semantics (stateless)
    // -------------------------------------------------------------------------

    @Test
    public void equalsAndHashCodeAreValueBased() {
        assertThat(new Hash160(), is(new Hash160()));
        assertThat(new Hash160().hashCode(), is(new Hash160().hashCode()));
    }
}
