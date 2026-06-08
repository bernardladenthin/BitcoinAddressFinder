// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.util.List;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.jocl.CL;
import org.junit.jupiter.api.Assumptions;

public class OpenCLPlatformAssume implements PlatformAssume {

    /**
     * Default upper bound (in bits) for the grid size when only a CPU OpenCL device is available
     * (e.g. pocl in CI). A CPU device evaluates the secp256k1 grid kernel orders of magnitude
     * slower than a GPU, so large grids ({@code 2^bitSize}) exceed the per-fork test timeout.
     * {@code 2^4 = 16} work-items keeps the CPU run short. A GPU runs the full
     * {@link OpenClKernelConstants#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY} sweep unchanged.
     */
    public static final int CPU_DEVICE_MAX_GRID_BITS_DEFAULT = 4;

    /**
     * System property to override {@link #CPU_DEVICE_MAX_GRID_BITS_DEFAULT}, e.g.
     * {@code -Dnet.ladenthin.bitcoinaddressfinder.test.opencl.cpu.maxGridBits=12}.
     */
    public static final String PROPERTY_CPU_MAX_GRID_BITS =
            "net.ladenthin.bitcoinaddressfinder.test.opencl.cpu.maxGridBits";

    public void assumeOpenClLibraryAvailable() {
        Assumptions.assumeTrue(OpenCLBuilder.isOpenClNativeLibraryLoaded(), "OpenCL library not available");
    }

    public void assumeOneOpenCL2_0OrGreaterDeviceAvailable(List<OpenCLPlatform> openCLPlatforms) {
        Assumptions.assumeTrue(
                OpenCLBuilder.isOneOpenCL2_0OrGreaterDeviceAvailable(openCLPlatforms),
                "One OpenCL 2.0 or greater device available");
    }

    public void assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable() {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailable();
        OpenCLBuilder openCLBuilder = new OpenCLBuilder();
        List<OpenCLPlatform> openCLPlatforms = openCLBuilder.build();
        new OpenCLPlatformAssume().assumeOneOpenCL2_0OrGreaterDeviceAvailable(openCLPlatforms);
    }

    /**
     * Returns whether at least one available OpenCL device is a GPU.
     *
     * @return {@code true} if any discovered device reports {@link CL#CL_DEVICE_TYPE_GPU}
     */
    public boolean hasGpuOpenCLDevice() {
        List<OpenCLPlatform> openCLPlatforms = new OpenCLBuilder().build();
        for (OpenCLPlatform openCLPlatform : openCLPlatforms) {
            for (OpenCLDevice openCLDevice : openCLPlatform.openCLDevices()) {
                if ((openCLDevice.deviceType() & CL.CL_DEVICE_TYPE_GPU) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the maximum grid bit-size that should be exercised on the available device.
     *
     * <p>A GPU runs the full {@link OpenClKernelConstants#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY} range;
     * a CPU-only setup is capped to {@link #CPU_DEVICE_MAX_GRID_BITS_DEFAULT} (overridable via
     * {@link #PROPERTY_CPU_MAX_GRID_BITS}) so the CPU run stays within the test timeout.
     *
     * @return the maximum grid bit-size to run on the available device
     */
    public int maxGridBitsForAvailableDevice() {
        if (hasGpuOpenCLDevice()) {
            return OpenClKernelConstants.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY;
        }
        final Integer override = Integer.getInteger(PROPERTY_CPU_MAX_GRID_BITS);
        return override != null ? override : CPU_DEVICE_MAX_GRID_BITS_DEFAULT;
    }

    /**
     * Skips (does not fail) a parameterized grid-size case that is too large for the available
     * device. GPUs run every {@code bitSize}; CPU-only setups skip anything above the cap.
     *
     * @param bitSize the grid bit-size of the current case ({@code 2^bitSize} work-items)
     */
    public void assumeGridBitsRunnableOnAvailableDevice(int bitSize) {
        final int max = maxGridBitsForAvailableDevice();
        final String reason = "Skipping grid 2^" + bitSize + " (cap " + max + " bits) on this OpenCL device";
        Assumptions.assumeTrue(bitSize <= max, reason);
    }
}
