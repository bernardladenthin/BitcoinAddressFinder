// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import org.junit.jupiter.api.Test;

/**
 * Validates the reduced-radix 2^26 field module ({@code inc_ecc_secp256k1_fe10x26.cl}) against the
 * vendored radix-2^32 field ({@code copyfromhashcat/inc_ecc_secp256k1.cl}) by running the
 * {@code test_fe10x26} self-check kernel on the GPU.
 *
 * <p>The kernel takes deterministic pseudo-random operand pairs {@code (x, y)}, each fully reduced
 * into {@code [0, p)}, and for each cross-checks the 2^26 implementation against the radix-2^32
 * reference for: limb-representation roundtrip, modular multiply, square, add, and subtract. Every
 * output word must be {@code 0}; a non-zero word is a per-input failure bitmask (see the kernel
 * comment for the bit assignments). This is the proof that the 2^26 field is a byte-identical drop-in
 * before any production hot path relies on it.
 *
 * <p>{@code @OpenCLTest}: self-skips when no OpenCL 2.0+ device is present (runs under pocl in CI or a
 * real GPU).
 */
class OpenCLFe10x26ParityTest {

    private final BitHelper bitHelper = new BitHelper();

    private static CProducerOpenCL minimalProducer() {
        final CProducerOpenCL p = new CProducerOpenCL();
        p.batchSizeInBits = 8;
        p.keysPerWorkItem = 1; // irrelevant here: the test drives the self-check kernel directly
        return p;
    }

    @Test
    @OpenCLTest
    void fe10x26_matchesRadix32Field() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final int count = 8192;
        try (OpenCLContext ctx = new OpenCLContext(minimalProducer(), bitHelper)) {
            ctx.init();
            final byte[] status = ctx.runPrecomputeKernelForTesting("test_fe10x26", count * Integer.BYTES, count);
            for (int i = 0; i < status.length; i++) {
                assertEquals(0, status[i], "fe10x26 parity failure at status byte " + i);
            }
        }
    }
}
