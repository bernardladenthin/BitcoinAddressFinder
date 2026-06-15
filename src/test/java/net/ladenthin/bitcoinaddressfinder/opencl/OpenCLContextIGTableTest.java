// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Arrays;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import org.bitcoinj.crypto.ECKey;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pure-Java parity test for the host {@code i·G}-table generator
 * ({@link OpenCLContext#buildIGTable}) used by the single-anchor affine scalar walk (Stage 1).
 *
 * <p>No GPU is required &mdash; same philosophy as {@code Fuse8GpuHashParityTest}: pin the exact
 * byte layout the kernel will read. The kernel reads each entry as {@code [x(8 words)][y(8 words)]}
 * in device word order; this test decodes every entry back to a big-endian {@link BigInteger} and
 * asserts it equals the affine X / Y of {@code i·G} recomputed from the bitcoinj curve. This
 * catches any drift in coordinate packing, endianness, or entry offset without a device (the
 * end-to-end GPU correctness is gated separately by {@code ProbeAddressesOpenCLTest}).
 */
class OpenCLContextIGTableTest {

    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

    private static final int ONE_COORD = OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES; // 32
    private static final int TWO_COORD = OpenClKernelConstants.TWO_COORDINATES_NUM_BYTES; // 64

    /** {@code keysPerWorkItem == 1}: the walk never reads the table, so a one-byte placeholder is built. */
    @Test
    void buildIGTable_keysPerWorkItemOne_returnsPlaceholder() {
        byte[] table = OpenCLContext.buildIGTable(1, byteBufferUtility);
        assertThat(table.length, is(1));
    }

    /**
     * For {@code keysPerWorkItem > 1}, every entry {@code i-1} decodes back to {@code i·G}'s
     * affine X / Y exactly. Includes {@code i = 1}, which the {@code ECKey.fromPrivate} sentinel
     * guard would reject &mdash; confirming the generator derives points via the lower-level path.
     *
     * @param keysPerWorkItem the per-work-item key count under test
     */
    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8, 16, 64, 128})
    void buildIGTable_decodesBackToReferencePoints(int keysPerWorkItem) {
        byte[] table = OpenCLContext.buildIGTable(keysPerWorkItem, byteBufferUtility);
        assertThat(table.length, is((keysPerWorkItem - 1) * TWO_COORD));

        for (int i = 1; i < keysPerWorkItem; i++) {
            final int offset = (i - 1) * TWO_COORD;
            // Read the on-device coordinates and reverse them back to big-endian.
            byte[] xDevice = Arrays.copyOfRange(table, offset, offset + ONE_COORD);
            byte[] yDevice = Arrays.copyOfRange(table, offset + ONE_COORD, offset + TWO_COORD);
            byteBufferUtility.reverse(xDevice); // device word order -> big-endian
            byteBufferUtility.reverse(yDevice);
            BigInteger xActual = new BigInteger(1, xDevice);
            BigInteger yActual = new BigInteger(1, yDevice);

            // bitcoinj reference point i·G.
            ECPoint point = ECKey.publicPointFromPrivate(BigInteger.valueOf(i)).normalize();
            assertEquals(
                    point.getAffineXCoord().toBigInteger(),
                    xActual,
                    "X mismatch for i=" + i + " (keysPerWorkItem=" + keysPerWorkItem + ")");
            assertEquals(
                    point.getAffineYCoord().toBigInteger(),
                    yActual,
                    "Y mismatch for i=" + i + " (keysPerWorkItem=" + keysPerWorkItem + ")");
        }
    }
}
