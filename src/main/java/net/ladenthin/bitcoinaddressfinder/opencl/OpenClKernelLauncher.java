// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import java.util.OptionalLong;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClApi;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClCommandQueue;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClDeviceId;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClKernel;
import org.lwjgl.opencl.CL10;

/**
 * Launches the one-dimensional kernels {@link OpenCLContext} runs itself (the fixed-base precompute
 * kernels of {@code init()} and the benchmark hooks) with the same local-work-size choice as the key
 * kernel's launch in {@link OpenClTask}: on a pocl CPU device the size {@link OpenClLocalWorkSize}
 * chooses for the kernel's own {@code CL_KERNEL_WORK_GROUP_SIZE}, on every other device the driver's
 * choice, as before. Whether a pocl work-group of 4096 overflows the 8 MB stack of pocl's worker thread
 * depends on the kernel and the compiler, so no launch on that device is left to the default.
 */
@ToString
final class OpenClKernelLauncher {

    @ToString.Exclude
    private final ClApi clApi;

    private final ClDeviceId device;

    private final boolean poclCpuDevice;

    /**
     * Creates a launcher for one device.
     *
     * @param clApi the OpenCL API to launch through
     * @param device the device the kernels were built for
     * @param poclCpuDevice whether the device is a pocl CPU device, see {@link
     *     OpenClLocalWorkSize#isPoclCpuDevice}
     */
    OpenClKernelLauncher(ClApi clApi, ClDeviceId device, boolean poclCpuDevice) {
        this.clApi = clApi;
        this.device = device;
        this.poclCpuDevice = poclCpuDevice;
    }

    /**
     * Enqueues a one-dimensional launch of {@code kernel}; does not wait for it.
     *
     * @param commandQueue the queue to enqueue on
     * @param kernel the kernel, with its arguments bound
     * @param globalWorkSize the number of work items
     */
    void launch(ClCommandQueue commandQueue, ClKernel kernel, long globalWorkSize) {
        final long[] global = {globalWorkSize};
        if (!poclCpuDevice) {
            // every other device keeps exactly the launch it had before, without a driver query
            clApi.enqueueNDRangeKernel(commandQueue, kernel, global, false);
            return;
        }
        final long kernelWorkGroupSize =
                clApi.getKernelWorkGroupInfoSize(kernel, device, CL10.CL_KERNEL_WORK_GROUP_SIZE);
        final OptionalLong localWorkSize = OpenClLocalWorkSize.choose(globalWorkSize, kernelWorkGroupSize, true);
        if (localWorkSize.isPresent()) {
            clApi.enqueueNDRangeKernel(commandQueue, kernel, global, new long[] {localWorkSize.getAsLong()}, false);
        } else {
            clApi.enqueueNDRangeKernel(commandQueue, kernel, global, false);
        }
    }
}
