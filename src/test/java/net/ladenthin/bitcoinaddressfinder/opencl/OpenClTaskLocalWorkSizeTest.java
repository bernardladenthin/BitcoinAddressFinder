// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClApi;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClCommandQueue;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClContext;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClKernel;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClMem;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import org.junit.jupiter.api.Test;

/**
 * Pins which kernel launch {@link OpenClTask#executeKernel} issues: an explicit local work size on a
 * pocl CPU device (whose default group overflows its worker thread's stack), the driver's choice
 * everywhere else. The device is a mock, so no OpenCL runtime is needed.
 */
class OpenClTaskLocalWorkSizeTest {

    /** 2^12 keys with one key per work item: a global size of 4096. */
    private static final int BATCH_SIZE_IN_BITS = 12;

    private static final long GLOBAL_WORK_SIZE = 1L << BATCH_SIZE_IN_BITS;

    /** pocl's default work-group limit on its CPU device. */
    private static final long POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE = 4096L;

    private final ClKernel kernel = new ClKernel(11L);

    private final ClCommandQueue commandQueue = new ClCommandQueue(12L);

    private final ClMem anyMem = new ClMem(13L);

    @Test
    void executeKernel_poclCpuDevice_launchesWithTheCappedLocalWorkSize() {
        final ClApi clApi = mockClApi();
        final OpenClTask task = newTask(clApi, true);

        task.executeKernel(kernel, commandQueue, anyMem, anyMem, 1, 0, anyMem, anyMem);

        verify(clApi)
                .enqueueNDRangeKernel(
                        eq(commandQueue),
                        eq(kernel),
                        aryEq(new long[] {GLOBAL_WORK_SIZE}),
                        aryEq(new long[] {OpenClLocalWorkSize.POCL_CPU_MAX_LOCAL_WORK_SIZE}),
                        eq(false));
        verify(clApi, never()).enqueueNDRangeKernel(any(), any(), any(long[].class), anyBoolean());
    }

    @Test
    void executeKernel_otherDevice_leavesTheLocalWorkSizeToTheDriver() {
        final ClApi clApi = mockClApi();
        final OpenClTask task = newTask(clApi, false);

        task.executeKernel(kernel, commandQueue, anyMem, anyMem, 1, 0, anyMem, anyMem);

        verify(clApi)
                .enqueueNDRangeKernel(eq(commandQueue), eq(kernel), aryEq(new long[] {GLOBAL_WORK_SIZE}), eq(false));
        verify(clApi, never()).enqueueNDRangeKernel(any(), any(), any(long[].class), any(long[].class), anyBoolean());
    }

    private OpenClTask newTask(ClApi clApi, boolean poclCpuDevice) {
        final CProducerOpenCL cProducer = new CProducerOpenCL();
        cProducer.batchSizeInBits = BATCH_SIZE_IN_BITS;
        cProducer.keysPerWorkItem = 1;
        return new OpenClTask(
                clApi,
                new ClContext(10L),
                cProducer,
                new BitHelper(),
                new ByteBufferUtility(true),
                POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE,
                poclCpuDevice);
    }

    /** A device that accepts every call; reads leave the buffer zeroed (a compact count of 0). */
    private static ClApi mockClApi() {
        return mock(ClApi.class, invocation -> {
            final Class<?> returnType = invocation.getMethod().getReturnType();
            if (returnType == ClMem.class) {
                return new ClMem(20L);
            }
            return null;
        });
    }
}
