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
    private static final int COMB_DIGITS = OpenCLContext.COMB_DIGITS; // 16
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
     * {@code precompute_comb_table}: entry (pos, digit) must equal {@code (digit*2^(4*pos))*G}, and the
     * digit-0 slot must be zeroed.
     *
     * @param pos the window position under test
     */
    @ParameterizedTest
    @OpenCLTest
    @ValueSource(ints = {0, 1, 2, 15, 31, 62, 63})
    void precomputeCombTable_matchesBitcoinjReference(int pos) throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final int positions = OpenCLContext.COMB_POSITIONS; // 64
        try (OpenCLContext ctx = new OpenCLContext(minimalProducer(), bitHelper)) {
            ctx.init();
            final byte[] table = ctx.runPrecomputeKernelForTesting(
                    "precompute_comb_table", positions * COMB_DIGITS * TWO_COORD, null);

            // digit 0 = point at infinity -> all zero
            final int zeroOff = (pos * COMB_DIGITS) * TWO_COORD;
            for (int i = 0; i < TWO_COORD; i++) {
                assertEquals(0, table[zeroOff + i], "comb[" + pos + "][0] must be zero at byte " + i);
            }

            for (int digit = 1; digit < COMB_DIGITS; digit++) {
                final BigInteger scalar =
                        BigInteger.valueOf(digit).shiftLeft(4 * pos).mod(N);
                assertEntryEqualsScalarTimesG(
                        table, pos * COMB_DIGITS + digit, scalar, "comb[" + pos + "][" + digit + "]");
            }
        }
    }
}
