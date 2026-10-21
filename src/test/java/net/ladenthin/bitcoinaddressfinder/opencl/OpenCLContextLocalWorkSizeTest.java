// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClApi;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.OpenClBinding;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import org.junit.jupiter.api.Test;

/**
 * Pins, on a real pocl CPU device, that every kernel {@link OpenCLContext} launches itself (the
 * fixed-base precompute kernels of {@code init()} and the three benchmark hooks) passes the local work
 * size {@link OpenClLocalWorkSize} chooses, as the key kernel does (F211). Without one, pocl runs a
 * launch in work-groups of its maximum (4096) and whether the work-group function fits the 8 MB stack
 * of pocl's worker thread then depends on the kernel and the compiler. The OpenCL API is a Mockito spy
 * over the real binding, so the kernels really run; the test self-skips without a pocl CPU device
 * (CI job {@code test-opencl} has one).
 */
class OpenCLContextLocalWorkSizeTest {

    /** A global size pocl would otherwise run as one work-group of 4096. */
    private static final int BENCH_GLOBAL_WORK_SIZE = 4096;

    private static final int BENCH_ITERATIONS = 2;

    @OpenCLTest
    @Test
    void benchmarkHooks_poclCpuDevice_launchWithTheCappedLocalWorkSize() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final ClApi clApi = spy(OpenClBinding.defaultClApi());
        final CProducerOpenCL producer = new CProducerOpenCL();
        producer.batchSizeInBits = 8;
        assumeTrue(selectsPoclCpuDevice(clApi, producer), "the selected device is not pocl's CPU device");

        final OpenCLContext context = new OpenCLContext(producer, new BitHelper(), clApi);
        try {
            context.init();
            context.runBenchInvMod(BENCH_GLOBAL_WORK_SIZE, BENCH_ITERATIONS, false);
            context.runBenchFeMul(BENCH_GLOBAL_WORK_SIZE, BENCH_ITERATIONS, false);
            context.prepareBenchFilterProbeBlockedBloom(
                    new long[8], new int[] {1, 1}, new long[BENCH_GLOBAL_WORK_SIZE]);
            context.runBenchFilterProbe();
            context.releaseBenchFilterProbe();
        } finally {
            context.close();
        }

        // init()'s precompute kernels are single work items: their choice is a local size of 1
        verify(clApi, atLeastOnce())
                .enqueueNDRangeKernel(any(), any(), aryEq(new long[] {1L}), aryEq(new long[] {1L}), eq(false));
        verify(clApi, atLeastOnce())
                .enqueueNDRangeKernel(
                        any(),
                        any(),
                        aryEq(new long[] {BENCH_GLOBAL_WORK_SIZE}),
                        aryEq(new long[] {OpenClLocalWorkSize.POCL_CPU_MAX_LOCAL_WORK_SIZE}),
                        eq(false));
        verify(clApi, never()).enqueueNDRangeKernel(any(), any(), any(long[].class), anyBoolean());
    }

    private static boolean selectsPoclCpuDevice(ClApi clApi, CProducerOpenCL producer) {
        final List<OpenCLPlatform> platforms = new OpenCLBuilder(clApi).build();
        final OpenCLDeviceSelection selection = new OpenCLPlatformSelector()
                .select(platforms, producer.platformIndex, producer.deviceType, producer.deviceIndex);
        return OpenClLocalWorkSize.isPoclCpuDevice(
                selection.platform().platformName(),
                selection.device().deviceVersion(),
                selection.device().deviceType());
    }
}
