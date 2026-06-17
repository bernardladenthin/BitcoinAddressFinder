// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.KernelProfileStage;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Regression guard that every compile-time kernel mode <b>and the meaningful combinations of them</b>
 * actually build and run. The production kernel has three independent build-time switches, each driven
 * by a {@link CProducerOpenCL} field and toggled with a {@code -D} define in {@link OpenCLContext#buildOptions()}:
 *
 * <ul>
 *   <li>{@code useSafeGcdInverse} — safegcd (default) vs {@code -D USE_LEGACY_BINARY_GCD_INV_MOD};</li>
 *   <li>{@code useReducedRadixField} — radix-2³² (default) vs {@code -D USE_REDUCED_RADIX_FIELD} (2²⁶ walk);</li>
 *   <li>{@code kernelProfileStage} — {@code FULL} vs {@code -D PROFILE_SKIP_SECOND_HASH160} / {@code -D PROFILE_SKIP_HASH160}.</li>
 * </ul>
 *
 * <p><b>Each individual switch is already exercised elsewhere</b> — {@code kernelProfileStage_buildsAndRuns}
 * (the three stages, radix-2³²), {@code OpenCLPrecomputeKernelTest#invModSafegcd_…} (legacy inverse,
 * radix-2³²), and {@code ProbeAddressesManySeedsOpenCLTest} (reduced-radix and radix-2³², FULL, vs
 * bitcoinj). What none of them covers is the <b>interaction</b> of {@code useReducedRadixField=true}
 * with the other two switches: the 2²⁶ walk feeding the legacy binary-GCD inverse, and the 2²⁶ walk
 * under the profiling stages. Those are the combinations selected here (kept small on purpose: every
 * row is a distinct {@code -D} set, so each is a fresh kernel build, and the whole class must finish
 * within the Surefire per-fork timeout). For each it builds the full program
 * ({@link OpenCLContext#init()}), launches it ({@link OpenCLContext#createKeys(BigInteger)}), and:
 *
 * <ul>
 *   <li>for {@code FULL} stages (correct hashing) verifies every derived key against bitcoinj via
 *       {@link PublicKeyBytes#runtimePublicKeyCalculationCheck()};</li>
 *   <li>for the {@code PROFILE_SKIP_*} stages (timing-only — hashes are intentionally wrong) only
 *       asserts the launch produced a non-empty result buffer, i.e. the branch compiled and ran.</li>
 * </ul>
 *
 * <p>{@code @OpenCLTest}: self-skips when no OpenCL 2.0+ device is present (runs under pocl in CI or a
 * real GPU). All combinations build in one forked JVM; the first {@code init()} pays the cold
 * OpenCL-compiler cost, the rest reuse the warm driver, keeping the class within the Surefire fork
 * timeout. The grid is intentionally tiny ({@code batchSizeInBits=8}) — this guards compilation and
 * basic execution of each branch, not throughput.
 */
public class OpenCLKernelModeMatrixTest {

    private final BitHelper bitHelper = new BitHelper();

    @ParameterizedTest(name = "[{index}] safegcd={0} reducedRadix={1} stage={2}")
    @OpenCLTest
    @CsvSource({
        // useSafeGcdInverse, useReducedRadixField, kernelProfileStage
        // Only the reduced-radix interactions not covered elsewhere (each row = one fresh kernel build;
        // kept to 3 to stay within the Surefire fork timeout).
        "false, true,  FULL", // reduced-radix 2^26 walk + legacy binary-GCD inverse (asserts vs bitcoinj)
        "true,  true,  ONE_HASH160", // reduced-radix 2^26 walk + skip-second-hash160 profiling
        "true,  true,  NO_HASH160", // reduced-radix 2^26 walk + EC-only profiling
    })
    public void kernelMode_buildsRunsAndIsCorrectWhenFull(
            boolean useSafeGcdInverse, boolean useReducedRadixField, KernelProfileStage kernelProfileStage)
            throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        final CProducerOpenCL producerOpenCL = new CProducerOpenCL();
        producerOpenCL.batchSizeInBits = 8;
        producerOpenCL.keysPerWorkItem = 8; // > 1 so the scalar-walker path (and its #ifdef) is exercised
        producerOpenCL.useSafeGcdInverse = useSafeGcdInverse;
        producerOpenCL.useReducedRadixField = useReducedRadixField;
        producerOpenCL.kernelProfileStage = kernelProfileStage;

        try (OpenCLContext openCLContext = new OpenCLContext(producerOpenCL, bitHelper)) {
            openCLContext.init(); // builds the full program with this mode's -D defines

            // The base must be aligned to batchSizeInBits (low bits zero): the producer labels each
            // result secret as base | index, and the runtime check compares the kernel's pubkey to
            // bitcoinj for that secret. With low bits zero, base | index == base + index, so the grid
            // covers a clean contiguous range. (0x...00 has its low 8 bits zero for batchSizeInBits=8.)
            final BigInteger secretBase = BigInteger.valueOf(0x1234_5600L);
            try (OpenCLGridResult result = openCLContext.createKeys(secretBase)) {
                // The launch completed and produced a readable result buffer (the branch compiled + ran).
                assertThat(result.getResult().capacity(), is(greaterThan(0)));

                if (kernelProfileStage == KernelProfileStage.FULL) {
                    // FULL hashing is correct -> every key must match bitcoinj for both representations
                    // and both inverse implementations.
                    final PublicKeyBytes[] publicKeys = result.getPublicKeyBytes();
                    for (int i = 0; i < publicKeys.length; i++) {
                        assertTrue(
                                publicKeys[i].runtimePublicKeyCalculationCheck(),
                                "safegcd=" + useSafeGcdInverse
                                        + " reducedRadix=" + useReducedRadixField
                                        + " index=" + i
                                        + " secretKey=" + publicKeys[i].getSecretKey());
                    }
                }
            }
        }
    }
}
