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
 * <p>Both algorithm selections compute the identical standard
 * RIPEMD-160(SHA-256(input)), so they are pinned against the same known vectors
 * and asserted equal to each other. The known-vector bytes were produced by the
 * implementation itself and cross-checked between the two paths.
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
    // Fast path (Guava SHA-256 + Bouncy Castle RIPEMD-160) — useFast = true
    // -------------------------------------------------------------------------

    @Test
    public void fastPathMatchesKnownVectorEmpty() {
        assertArrayEquals(hex(HASH160_EMPTY), new Hash160(true).hash(new byte[0]));
    }

    @Test
    public void fastPathMatchesKnownVectorHello() {
        assertArrayEquals(hex(HASH160_HELLO), new Hash160(true).hash("hello".getBytes()));
    }

    @Test
    public void fastPathProducesTwentyBytes() {
        assertThat(new Hash160(true).hash("hello".getBytes()).length, is(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES));
    }

    // -------------------------------------------------------------------------
    // Slow path (bitcoinj CryptoUtils.sha256hash160) — useFast = false
    // This is the branch that was previously NO_COVERAGE (kills the
    // NullReturnVals mutant on `return CryptoUtils.sha256hash160(input)`).
    // -------------------------------------------------------------------------

    @Test
    public void slowPathMatchesKnownVectorEmpty() {
        assertArrayEquals(hex(HASH160_EMPTY), new Hash160(false).hash(new byte[0]));
    }

    @Test
    public void slowPathMatchesKnownVectorHello() {
        assertArrayEquals(hex(HASH160_HELLO), new Hash160(false).hash("hello".getBytes()));
    }

    @Test
    public void slowPathMatchesKnownVectorTenBytes() {
        assertArrayEquals(hex(HASH160_0_TO_9), new Hash160(false).hash(bytes(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
    }

    // -------------------------------------------------------------------------
    // Cross-implementation equivalence + default selection
    // -------------------------------------------------------------------------

    @Test
    public void bothImplementationsProduceIdenticalOutput() {
        final byte[] input = bytes(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertArrayEquals(new Hash160(true).hash(input), new Hash160(false).hash(input));
    }

    @Test
    public void defaultConstructorUsesFastPath() {
        // DEFAULT_USE_FAST is true; the no-arg ctor must match the explicit fast path.
        assertArrayEquals(new Hash160(Hash160.DEFAULT_USE_FAST).hash("hello".getBytes()), new Hash160().hash("hello".getBytes()));
        assertThat(Hash160.DEFAULT_USE_FAST, is(true));
    }

    // -------------------------------------------------------------------------
    // Lombok value semantics
    // -------------------------------------------------------------------------

    @Test
    public void equalsAndHashCodeAreValueBased() {
        assertThat(new Hash160(true), is(new Hash160(true)));
        assertThat(new Hash160(true).hashCode(), is(new Hash160(true).hashCode()));
    }
}
