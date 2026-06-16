// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import org.bitcoinj.crypto.ECKey;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Validates the on-device fixed-base table precompute kernels
 * ({@code precompute_ig_table}, {@code precompute_comb_table} in
 * {@code copyfromhashcat/inc_ecc_secp256k1.cl}) by running them on the GPU, reading the result
 * back, and comparing every entry against the bitcoinj reference point.
 *
 * <p>These kernels build the tables from the base point {@code G} using only generic EC primitives
 * (no host upload, no wNAF precomputed table), so this is the proof they produce exactly what the
 * host-built tables ({@link OpenCLContext#buildIGTable}/{@link OpenCLContext#buildCombTable}) do —
 * and therefore that an external library could use them as a drop-in faster path.
 *
 * <p>{@code @OpenCLTest}: self-skips when no OpenCL 2.0+ device is present (runs under pocl in CI or
 * a real GPU). Points are compared by affine BigInteger coordinates.
 */
class OpenCLPrecomputeKernelTest {

    private final BitHelper bitHelper = new BitHelper();
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

    private static final int ONE_COORD = OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES; // 32
    private static final int TWO_COORD = OpenClKernelConstants.TWO_COORDINATES_NUM_BYTES; // 64
    private static final int COMB_MAGNITUDES = OpenCLContext.COMB_MAGNITUDES; // 8
    private static final BigInteger N = Secp256k1Constants.MAX_PRIVATE_KEY;

    private static CProducerOpenCL minimalProducer() {
        final CProducerOpenCL p = new CProducerOpenCL();
        p.batchSizeInBits = 8;
        p.keysPerWorkItem = 1; // irrelevant here: the test drives the precompute kernels directly
        return p;
    }

    /** Asserts table entry {@code entryIndex} (16 words = [x(8)][y(8)], device order) equals {@code scalar*G}. */
    private void assertEntryEqualsScalarTimesG(byte[] table, int entryIndex, BigInteger scalar, String where) {
        final int off = entryIndex * TWO_COORD;
        byte[] x = Arrays.copyOfRange(table, off, off + ONE_COORD);
        byte[] y = Arrays.copyOfRange(table, off + ONE_COORD, off + TWO_COORD);
        byteBufferUtility.reverse(x); // device word order -> big-endian
        byteBufferUtility.reverse(y);
        final ECPoint expected = ECKey.publicPointFromPrivate(scalar).normalize();
        assertEquals(expected.getAffineXCoord().toBigInteger(), new BigInteger(1, x), where + " (x)");
        assertEquals(expected.getAffineYCoord().toBigInteger(), new BigInteger(1, y), where + " (y)");
    }

    /** {@code precompute_ig_table}: entry m-1 must equal m*G for m = 1..count. */
    @Test
    @OpenCLTest
    void precomputeIgTable_matchesBitcoinjReference() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final int count = 20;
        try (OpenCLContext ctx = new OpenCLContext(minimalProducer(), bitHelper)) {
            ctx.init();
            final byte[] table = ctx.runPrecomputeKernelForTesting("precompute_ig_table", count * TWO_COORD, count);
            for (int m = 1; m <= count; m++) {
                assertEntryEqualsScalarTimesG(table, m - 1, BigInteger.valueOf(m), "iG m=" + m);
            }
        }
    }

    /**
     * {@code precompute_comb_table} (signed-digit layout): entry (pos, mag) at slot {@code mag-1}
     * must equal {@code (mag*2^(4*pos))*G} for magnitudes {@code 1..8}. There is no zero slot.
     * Position 64 is the carry-out window; its entries are {@code (mag*2^256)*G}.
     *
     * @param pos the window position under test
     */
    @ParameterizedTest
    @OpenCLTest
    @ValueSource(ints = {0, 1, 2, 15, 31, 62, 63, 64})
    void precomputeCombTable_matchesBitcoinjReference(int pos) throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final int positions = OpenCLContext.COMB_POSITIONS; // 65
        try (OpenCLContext ctx = new OpenCLContext(minimalProducer(), bitHelper)) {
            ctx.init();
            final byte[] table = ctx.runPrecomputeKernelForTesting(
                    "precompute_comb_table", positions * COMB_MAGNITUDES * TWO_COORD, null);

            for (int mag = 1; mag <= COMB_MAGNITUDES; mag++) {
                final BigInteger scalar =
                        BigInteger.valueOf(mag).shiftLeft(4 * pos).mod(N);
                assertEntryEqualsScalarTimesG(
                        table, pos * COMB_MAGNITUDES + (mag - 1), scalar, "comb[" + pos + "][mag=" + mag + "]");
            }
        }
    }

    /**
     * {@code test_inv_mod_safegcd}: the on-device self-check kernel runs the safegcd modular inverse
     * over {@code count} deterministic pseudo-random field elements and cross-checks each against the
     * {@code inv_mod} the kernel was built with and the identity {@code x * x^-1 == 1 (mod p)}. Every
     * output word must be {@code 0} (a non-zero word is a failure bitmask for that input).
     *
     * <p>Built with {@code useSafeGcdInverse = false} on purpose: that compiles the legacy binary-GCD
     * {@code inv_mod} (the {@code -D USE_LEGACY_BINARY_GCD_INV_MOD} branch), so the kernel's
     * {@code inv_mod} is the binary GCD and the agreement check is a genuine safegcd-vs-binary-GCD
     * cross-comparison (with safegcd as the default, both would be safegcd and the check would be a
     * no-op).
     */
    @Test
    @OpenCLTest
    void invModSafegcd_matchesLegacyInverseAndIdentity() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final int count = 4096;
        final CProducerOpenCL producer = minimalProducer();
        producer.useSafeGcdInverse = false; // build the legacy inv_mod so the cross-check is meaningful
        try (OpenCLContext ctx = new OpenCLContext(producer, bitHelper)) {
            ctx.init();
            final byte[] status =
                    ctx.runPrecomputeKernelForTesting("test_inv_mod_safegcd", count * Integer.BYTES, count);
            for (int i = 0; i < status.length; i++) {
                assertEquals(0, status[i], "safegcd self-check failure at status byte " + i);
            }
        }
    }
}
