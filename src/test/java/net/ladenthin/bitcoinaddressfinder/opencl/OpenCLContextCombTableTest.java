// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import org.bitcoinj.crypto.ECKey;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pure-Java parity test for the fixed-base comb table generator
 * ({@link OpenCLContext#buildCombTable}) used to compute the {@code P0 = k0·G} anchor (Stage 2).
 *
 * <p>No GPU required. Two independent checks:
 * <ol>
 *   <li><b>Per-entry</b>: each {@code (pos, digit)} slot decodes back to {@code (digit·2^(4·pos))·G}.
 *   <li><b>Whole algorithm</b>: for random scalars {@code k}, summing the table points selected by
 *       {@code k}'s 4-bit windows (the exact decomposition the kernel walks) reconstructs {@code k·G}.
 *       This validates the comb math itself; the kernel's {@code point_add} usage is gated end-to-end
 *       by {@code ProbeAddressesOpenCLTest}.
 * </ol>
 *
 * <p>Points are compared by affine BigInteger coordinates (not {@code ECPoint.equals}) so the
 * independent Bouncy Castle curve instance used here and the one inside bitcoinj never trip a
 * curve-identity mismatch.
 */
class OpenCLContextCombTableTest {

    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

    private static final int ONE_COORD = OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES; // 32
    private static final int TWO_COORD = OpenClKernelConstants.TWO_COORDINATES_NUM_BYTES; // 64
    private static final int DIGITS = OpenCLContext.COMB_DIGITS; // 16
    private static final int POSITIONS = OpenCLContext.COMB_POSITIONS; // 64
    private static final BigInteger N = Secp256k1Constants.MAX_PRIVATE_KEY;

    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECCurve CURVE = CURVE_PARAMS.getCurve();

    /** Decodes the affine point stored at comb entry {@code (pos, digit)}. */
    private ECPoint decode(byte[] table, int pos, int digit) {
        final int off = (pos * DIGITS + digit) * TWO_COORD;
        byte[] x = Arrays.copyOfRange(table, off, off + ONE_COORD);
        byte[] y = Arrays.copyOfRange(table, off + ONE_COORD, off + TWO_COORD);
        byteBufferUtility.reverse(x); // device word order -> big-endian
        byteBufferUtility.reverse(y);
        return CURVE.createPoint(new BigInteger(1, x), new BigInteger(1, y));
    }

    private static void assertSameAffinePoint(String message, ECPoint expected, ECPoint actual) {
        ECPoint e = expected.normalize();
        ECPoint a = actual.normalize();
        assertEquals(e.getAffineXCoord().toBigInteger(), a.getAffineXCoord().toBigInteger(), message + " (x)");
        assertEquals(e.getAffineYCoord().toBigInteger(), a.getAffineYCoord().toBigInteger(), message + " (y)");
    }

    @Test
    void buildCombTable_hasExpectedSize() {
        byte[] table = OpenCLContext.buildCombTable(byteBufferUtility);
        assertThat(table.length, is(POSITIONS * DIGITS * TWO_COORD)); // 64 * 16 * 64 = 65536
    }

    /**
     * Each non-zero-digit entry decodes to {@code (digit·2^(4·pos))·G}.
     *
     * @param pos the window position under test
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 15, 31, 62, 63})
    void buildCombTable_eachEntryIsCorrectMultipleOfG(int pos) {
        byte[] table = OpenCLContext.buildCombTable(byteBufferUtility);
        for (int digit = 1; digit < DIGITS; digit++) {
            BigInteger scalar = BigInteger.valueOf(digit).shiftLeft(4 * pos).mod(N);
            ECPoint expected = ECKey.publicPointFromPrivate(scalar);
            assertSameAffinePoint("comb[" + pos + "][" + digit + "]", expected, decode(table, pos, digit));
        }
    }

    /**
     * Summing the table points selected by {@code k}'s 4-bit windows reconstructs {@code k·G} —
     * the exact decomposition {@code point_mul_xy_comb} computes on the GPU.
     */
    @Test
    void combDecomposition_reconstructsKtimesG() {
        byte[] table = OpenCLContext.buildCombTable(byteBufferUtility);
        Random random = new Random(0xC0FFEEL);
        for (int trial = 0; trial < 32; trial++) {
            BigInteger k = new BigInteger(256, random);
            ECPoint acc = CURVE.getInfinity();
            for (int pos = 0; pos < POSITIONS; pos++) {
                int digit = k.shiftRight(4 * pos).and(BigInteger.valueOf(0x0f)).intValue();
                if (digit == 0) {
                    continue;
                }
                acc = acc.add(decode(table, pos, digit));
            }
            ECPoint expected = ECKey.publicPointFromPrivate(k.mod(N));
            assertSameAffinePoint("comb sum for k=" + k.toString(16), expected, acc);
        }
    }
}
