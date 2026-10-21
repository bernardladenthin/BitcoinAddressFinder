// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClApi;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClCommandQueue;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClDeviceId;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClKernel;
import org.junit.jupiter.api.Test;
import org.lwjgl.opencl.CL10;

/**
 * Pins which launch {@link OpenClKernelLauncher#launch} issues for the kernels {@link OpenCLContext}
 * launches itself: on a pocl CPU device the local work size {@link OpenClLocalWorkSize} chooses for the
 * kernel's own {@code CL_KERNEL_WORK_GROUP_SIZE}, everywhere else the driver's choice, without asking the
 * driver for the kernel's limit. The OpenCL API is a mock, so no runtime is needed.
 */
class OpenClKernelLauncherTest {

    private static final long GLOBAL_WORK_SIZE = 4096L;

    /** pocl's default work-group limit on its CPU device. */
    private static final long POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE = 4096L;

    private final ClDeviceId device = new ClDeviceId(30L);

    private final ClKernel kernel = new ClKernel(31L);

    private final ClCommandQueue commandQueue = new ClCommandQueue(32L);

    @Test
    void launch_poclCpuDevice_passesTheCappedLocalWorkSize() {
        final ClApi clApi = clApiWithKernelWorkGroupSize(POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE);

        new OpenClKernelLauncher(clApi, device, true).launch(commandQueue, kernel, GLOBAL_WORK_SIZE);

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
    void launch_poclCpuDeviceWithASmallerKernelLimit_passesTheKernelLimit() {
        final ClApi clApi = clApiWithKernelWorkGroupSize(128L);

        new OpenClKernelLauncher(clApi, device, true).launch(commandQueue, kernel, GLOBAL_WORK_SIZE);

        verify(clApi)
                .enqueueNDRangeKernel(
                        eq(commandQueue),
                        eq(kernel),
                        aryEq(new long[] {GLOBAL_WORK_SIZE}),
                        aryEq(new long[] {128L}),
                        eq(false));
    }

    @Test
    void launch_poclCpuDeviceSingleWorkItem_passesALocalWorkSizeOfOne() {
        final ClApi clApi = clApiWithKernelWorkGroupSize(POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE);

        new OpenClKernelLauncher(clApi, device, true).launch(commandQueue, kernel, 1L);

        verify(clApi)
                .enqueueNDRangeKernel(
                        eq(commandQueue), eq(kernel), aryEq(new long[] {1L}), aryEq(new long[] {1L}), eq(false));
    }

    @Test
    void launch_otherDevice_leavesTheLocalWorkSizeToTheDriver() {
        final ClApi clApi = clApiWithKernelWorkGroupSize(POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE);

        new OpenClKernelLauncher(clApi, device, false).launch(commandQueue, kernel, GLOBAL_WORK_SIZE);

        verify(clApi)
                .enqueueNDRangeKernel(eq(commandQueue), eq(kernel), aryEq(new long[] {GLOBAL_WORK_SIZE}), eq(false));
        verify(clApi, never()).enqueueNDRangeKernel(any(), any(), any(long[].class), any(long[].class), anyBoolean());
        verify(clApi, never()).getKernelWorkGroupInfoSize(any(), any(), anyInt());
    }

    private ClApi clApiWithKernelWorkGroupSize(long kernelWorkGroupSize) {
        final ClApi clApi = mock(ClApi.class);
        when(clApi.getKernelWorkGroupInfoSize(kernel, device, CL10.CL_KERNEL_WORK_GROUP_SIZE))
                .thenReturn(kernelWorkGroupSize);
        return clApi;
    }
}
