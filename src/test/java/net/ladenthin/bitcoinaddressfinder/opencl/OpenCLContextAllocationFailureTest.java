// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import org.jocl.CLException;
import org.junit.jupiter.api.Test;

/**
 * Pins down, empirically on a real device, <b>how an over-large OpenCL allocation fails</b> — the
 * question behind {@code TuneConfiguration.runArm}'s per-arm error handling.
 *
 * <p>The tuner sweeps {@code batchSizeInBits} up to the hard framework cap {@code
 * BIT_COUNT_FOR_MAX_CHUNKS_ARRAY} (24), whose per-launch output buffer scales as {@code 2^bits}
 * (~1.8&nbsp;GB at 24). A card too small to serve that buffer must skip the arm, not abort the run.
 * Whether {@code catch (Exception)} is enough hinges on <em>what type</em> the driver throws. This
 * test answers it by measurement rather than assumption: with JOCL exceptions enabled it is a JOCL
 * {@link CLException} (a {@link RuntimeException}), <b>not</b> an {@link OutOfMemoryError}. So the
 * pre-existing {@code catch (Exception)} already covered it; the failure is an ordinary skipped arm.
 *
 * <p>The buffer size requested here is deliberately larger than any device's global memory rather
 * than merely above {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE}: that reported maximum is only a spec
 * <em>floor</em>, and some drivers (NVIDIA, measured this cycle) happily allocate a single buffer
 * several times larger than it. Only a request above total global memory is a portable, guaranteed
 * rejection.
 */
public class OpenCLContextAllocationFailureTest {

    private final BitHelper bitHelper = new BitHelper();

    /**
     * A single buffer of 2<sup>60</sup> bytes (~1.15&nbsp;EB) — far beyond the global memory of any
     * real OpenCL device, and beyond the practically addressable range — so every conforming driver
     * rejects it with {@code CL_INVALID_BUFFER_SIZE} (or, if it clamps to available memory first,
     * {@code CL_MEM_OBJECT_ALLOCATION_FAILURE}). Both are {@code CLException} statuses; neither is an
     * {@code OutOfMemoryError}.
     */
    private static final long IMPOSSIBLE_BUFFER_BYTES = 1L << 60;

    /**
     * The core assertion: an impossible device allocation throws a JOCL {@link CLException} that is a
     * {@link RuntimeException}. This is precisely why {@code TuneConfiguration.runArm} can rely on
     * {@code catch (Exception)} to demote an over-large {@code batchSizeInBits} arm to "unusable" and
     * carry on to the next candidate — no {@code OutOfMemoryError} (an {@link Error}) is involved.
     */
    @OpenCLTest
    @Test
    public void oversizedDeviceAllocation_throwsClExceptionNotOutOfMemoryError() throws IOException {
        OpenCLPlatformAssume assume = new OpenCLPlatformAssume();
        assume.assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        CProducerOpenCL cfg = new CProducerOpenCL();
        // A tiny grid: we only need an initialised context, not a real key launch.
        cfg.batchSizeInBits = 1;
        OpenCLContext context = new OpenCLContext(cfg, bitHelper);
        try {
            context.init();

            CLException ex = assertThrows(
                    CLException.class, () -> context.allocateDeviceReadWriteBufferForTesting(IMPOSSIBLE_BUFFER_BYTES));

            // A CLException is a RuntimeException, so catch (Exception) in the tuner covers it.
            assertThat(ex, is(instanceOf(RuntimeException.class)));
            // Drivers reject an impossible buffer with one of these two OpenCL status codes.
            assertThat(
                    ex.getMessage(),
                    anyOf(
                            containsString("CL_INVALID_BUFFER_SIZE"),
                            containsString("CL_MEM_OBJECT_ALLOCATION_FAILURE"),
                            containsString("CL_OUT_OF_RESOURCES")));
        } finally {
            context.close();
        }
    }

    /**
     * The success side of the same hook: a modest buffer allocates and releases without throwing.
     * Without this, an implementation whose probe always threw would make the failure test pass for
     * the wrong reason.
     */
    @OpenCLTest
    @Test
    public void modestDeviceAllocation_succeeds() throws IOException {
        OpenCLPlatformAssume assume = new OpenCLPlatformAssume();
        assume.assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();

        CProducerOpenCL cfg = new CProducerOpenCL();
        cfg.batchSizeInBits = 1;
        OpenCLContext context = new OpenCLContext(cfg, bitHelper);
        try {
            context.init();
            // 1 MiB fits on any device that passed the assume above.
            context.allocateDeviceReadWriteBufferForTesting(1L << 20);
        } finally {
            context.close();
        }
    }
}
