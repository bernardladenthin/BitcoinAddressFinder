// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import java.util.OptionalLong;
import org.lwjgl.opencl.CL10;

/**
 * Chooses the local work size (work-group size) of a kernel launch: of the key-generation kernel in
 * {@link OpenClTask}, and of every other kernel {@link OpenCLContext} launches (precompute, benchmark
 * hooks) through {@link OpenClKernelLauncher}.
 *
 * <h2>Why</h2>
 * Without a local size the runtime picks the work-group size. pocl's CPU device then picks its
 * maximum ({@code CL_KERNEL_WORK_GROUP_SIZE} 4096, measured on 4- and 16-core hosts), and its
 * work-group function for {@code generateKeysKernel_grid} overflows the 8 MB stack of the pocl
 * worker thread: the JVM dies with {@code SIGSEGV} in
 * {@code _pocl_kernel_generateKeysKernel_grid_workgroup} (exit code 139 or 134), at default settings,
 * on a platform the README lists as supported. A Java process cannot set
 * {@code POCL_MAX_WORK_GROUP_SIZE} for itself before pocl initialises, so the launch passes an
 * explicit local size there instead: at most {@value #POCL_CPU_MAX_LOCAL_WORK_SIZE}, the value under
 * which the crash was never observed.
 *
 * <p>Every other device keeps the driver's choice (no local size), as before: GPU drivers do not
 * show the problem, and an explicit size there would change a behaviour that is measured to work.
 *
 * <h2>Detection</h2>
 * By the platform name {@value #POCL_PLATFORM_NAME} or {@value #POCL_DEVICE_VERSION_MARKER} in
 * {@code CL_DEVICE_VERSION}, and the device type CPU. Not by {@code CL_DEVICE_VENDOR}: pocl's CPU
 * device reports the CPU's {@code vendor_id} there ({@code GenuineIntel}, {@code AuthenticAMD}). pocl's
 * other backends (CUDA and others) do not run the CPU work-group function and are left alone.
 */
public final class OpenClLocalWorkSize {

    /** {@code CL_PLATFORM_NAME} of pocl. */
    public static final String POCL_PLATFORM_NAME = "Portable Computing Language";

    /** Marker pocl writes into {@code CL_DEVICE_VERSION} (e.g. {@code "OpenCL 3.0 PoCL HSTR: cpu-x86_64-..."}). */
    public static final String POCL_DEVICE_VERSION_MARKER = "PoCL";

    /** The largest local work size used on a pocl CPU device. */
    public static final long POCL_CPU_MAX_LOCAL_WORK_SIZE = 256L;

    private OpenClLocalWorkSize() {}

    /**
     * Tells whether a device is a pocl CPU device.
     *
     * @param platformName the device's {@code CL_PLATFORM_NAME}
     * @param deviceVersion the device's {@code CL_DEVICE_VERSION}
     * @param deviceType the device's {@code CL_DEVICE_TYPE} bit field
     * @return {@code true} for a CPU device of pocl
     */
    public static boolean isPoclCpuDevice(String platformName, String deviceVersion, long deviceType) {
        final boolean cpu = (deviceType & CL10.CL_DEVICE_TYPE_CPU) != 0;
        final boolean pocl =
                POCL_PLATFORM_NAME.equals(platformName) || deviceVersion.contains(POCL_DEVICE_VERSION_MARKER);
        return cpu && pocl;
    }

    /**
     * Chooses the local work size of a one-dimensional kernel launch.
     *
     * @param globalWorkSize the number of work items of the launch
     * @param kernelWorkGroupSize the kernel's {@code CL_KERNEL_WORK_GROUP_SIZE} on the device (this
     *     already reflects a {@code POCL_MAX_WORK_GROUP_SIZE} set by the user)
     * @param poclCpuDevice whether the device is a pocl CPU device, see {@link #isPoclCpuDevice}
     * @return empty to leave the choice to the driver (every device but a pocl CPU device); otherwise
     *     the largest power of two that is at most {@value #POCL_CPU_MAX_LOCAL_WORK_SIZE}, at most
     *     {@code kernelWorkGroupSize} and divides {@code globalWorkSize} (OpenCL 1.x requires it)
     * @throws IllegalArgumentException if a size is not positive on a pocl CPU device
     */
    public static OptionalLong choose(long globalWorkSize, long kernelWorkGroupSize, boolean poclCpuDevice) {
        if (!poclCpuDevice) {
            // checked first: every other device keeps exactly the launch it had before
            return OptionalLong.empty();
        }
        if (globalWorkSize < 1) {
            throw new IllegalArgumentException("globalWorkSize must be positive but was " + globalWorkSize);
        }
        if (kernelWorkGroupSize < 1) {
            throw new IllegalArgumentException("kernelWorkGroupSize must be positive but was " + kernelWorkGroupSize);
        }
        // The largest power of two dividing the global size is its lowest set bit.
        final long limit = Math.min(
                POCL_CPU_MAX_LOCAL_WORK_SIZE, Math.min(kernelWorkGroupSize, Long.lowestOneBit(globalWorkSize)));
        return OptionalLong.of(Long.highestOneBit(limit));
    }
}
