// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import org.junit.jupiter.api.Test;
import org.lwjgl.opencl.CL10;

/**
 * Launches a trivial kernel through {@link LwjglClApi#enqueueNDRangeKernel(ClCommandQueue, ClKernel,
 * long[], long[], boolean)}, the overload with an explicit local work size. The device tests show that
 * the local size really reaches the driver: one that does not divide the global size is refused (the
 * kernel is built as OpenCL C 1.2, where work-groups must be uniform), one that does runs correctly.
 */
class LwjglClApiKernelLaunchTest {

    private static final String INDEX_KERNEL_SOURCE =
            "__kernel void index(__global int* out) { out[get_global_id(0)] = (int) get_global_id(0); }\n";

    private static final String BUILD_OPTIONS = "-cl-std=CL1.2";

    private static final int GLOBAL_WORK_SIZE = 1024;

    private final ClApi clApi = new LwjglClApi(
            new ClErrorChecker(new ClErrorCodeResolver()), new OpenClLibraryLoader(new LwjglClLibraryInitializer()));

    @Test
    void enqueueNDRangeKernel_localSizeWithOtherDimensionCount_isRefusedBeforeTheDriverIsCalled() {
        assertThrows(
                IllegalArgumentException.class,
                () -> clApi.enqueueNDRangeKernel(
                        new ClCommandQueue(1L), new ClKernel(2L), new long[] {1024L}, new long[] {8L, 8L}, false));
    }

    @Test
    @OpenCLTest
    void enqueueNDRangeKernel_localSizeNotDividingTheGlobalSize_isRefusedByTheDriver() {
        new OpenCLPlatformAssume().assumeOneOpenClDeviceAvailable();

        final OpenClCallFailedException failure =
                assertThrows(OpenClCallFailedException.class, () -> runIndexKernel(new long[] {GLOBAL_WORK_SIZE - 1}));

        assertThat(failure.getErrorCode(), is(equalTo(CL10.CL_INVALID_WORK_GROUP_SIZE)));
    }

    @Test
    @OpenCLTest
    void enqueueNDRangeKernel_localSizeDividingTheGlobalSize_runsEveryWorkItem() {
        new OpenCLPlatformAssume().assumeOneOpenClDeviceAvailable();

        final int[] out = runIndexKernel(new long[] {8L});

        for (int i = 0; i < GLOBAL_WORK_SIZE; i++) {
            assertThat("work item " + i, out[i], is(equalTo(i)));
        }
    }

    private int[] runIndexKernel(long[] localWorkSize) {
        final ClPlatformId platform = clApi.getPlatformIds().get(0);
        final List<ClDeviceId> devices = clApi.getDeviceIds(platform, CL10.CL_DEVICE_TYPE_ALL);
        final ClDeviceId device = devices.get(0);
        final ClContext context = clApi.createContext(platform, device);
        try {
            final ClCommandQueue queue = clApi.createCommandQueue(context, device, false);
            final ClProgram program = clApi.createProgramWithSource(context, new String[] {INDEX_KERNEL_SOURCE});
            try {
                clApi.buildProgram(program, device, BUILD_OPTIONS);
                final ClKernel kernel = clApi.createKernel(program, "index");
                final long bytes = (long) Integer.BYTES * GLOBAL_WORK_SIZE;
                final ClMem out = clApi.createBuffer(context, ClBufferFlags.WRITE_ONLY, bytes);
                try {
                    clApi.setKernelArg(kernel, 0, out);
                    clApi.enqueueNDRangeKernel(queue, kernel, new long[] {GLOBAL_WORK_SIZE}, localWorkSize, false);
                    final ByteBuffer readBack =
                            ByteBuffer.allocateDirect((int) bytes).order(ByteOrder.nativeOrder());
                    clApi.enqueueReadBuffer(queue, out, 0L, readBack, false);
                    clApi.finish(queue);
                    final int[] values = new int[GLOBAL_WORK_SIZE];
                    readBack.position(0);
                    readBack.asIntBuffer().get(values);
                    return values;
                } finally {
                    clApi.releaseMemObject(out);
                    clApi.releaseKernel(kernel);
                }
            } finally {
                clApi.releaseProgram(program);
                clApi.releaseCommandQueue(queue);
            }
        } finally {
            clApi.releaseContext(context);
        }
    }
}
