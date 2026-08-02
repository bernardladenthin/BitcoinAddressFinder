// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClApi;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClBufferFlags;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClCommandQueue;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClContext;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClDeviceId;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClKernel;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClMem;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.ClProgram;
import net.ladenthin.bitcoinaddressfinder.opencl.binding.OpenClBinding;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Minimal OpenCL smoke test: compiles and runs a trivial element-wise {@code add} kernel on every
 * discovered device.
 *
 * <p>Its purpose is to verify the bare OpenCL pipeline — build program, set args, enqueue, read back
 * — works on any vendor (NVIDIA, AMD, or a CPU runtime such as pocl), <em>independently</em> of the
 * large {@code generateKeysKernel_grid} secp256k1 kernel. The production kernel is a single huge
 * fully-inlined function whose build time is vendor-sensitive (seconds on NVIDIA, minutes on some
 * AMD LLVM toolchains); this test deliberately uses a tiny kernel that compiles in milliseconds
 * everywhere, so "does OpenCL work on this box at all?" can be answered in well under a second.
 *
 * <p>Requires only that the OpenCL native library and at least one device are present; it does
 * <em>not</em> require OpenCL 2.0 (a plain {@code add} needs nothing newer), so it runs on the
 * widest possible device set. It self-skips (does not fail) when no OpenCL library is available, and
 * also when the library/loader is present but reports no platform or device (e.g. a bare CI runner
 * without a GPU driver — the loader returns {@code CL_PLATFORM_NOT_FOUND_KHR}).
 */
public class OpenCLAddKernelSmokeTest {

    /** Trivial, vendor-neutral element-wise integer add kernel. Compiles in milliseconds anywhere. */
    private static final String ADD_KERNEL_SOURCE =
            "__kernel void add(__global const int* a, __global const int* b, __global int* out) {\n"
                    + "    int i = get_global_id(0);\n"
                    + "    out[i] = a[i] + b[i];\n"
                    + "}\n";

    /** Build options pinned to OpenCL C 1.2, matching the production kernel build (a plain add needs nothing newer). */
    private static final String BUILD_OPTIONS = "-cl-std=CL1.2";

    /** Kernel entry-point name. */
    private static final String KERNEL_NAME = "add";

    /** Number of work-items / array elements exercised. */
    private static final int ELEMENT_COUNT = 1024;

    // <editor-fold defaultstate="collapsed" desc="addKernel_allDevices_computeElementwiseSum">
    @Test
    @OpenCLTest
    public void addKernel_allDevices_computeElementwiseSum() {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailable();
        // arrange
        // No global error-reporting switch to flip: every call is checked at the binding seam.
        List<OpenCLPlatform> openCLPlatforms = new OpenCLBuilder().build();

        // act + assert: run the add kernel on every discovered device and verify each result.
        int devicesExercised = 0;
        for (OpenCLPlatform openCLPlatform : openCLPlatforms) {
            for (OpenCLDevice openCLDevice : openCLPlatform.openCLDevices()) {
                runAddKernelOnDevice(openCLPlatform, openCLDevice);
                devicesExercised++;
            }
        }

        // An OpenCL loader can be present with zero platforms/devices (a bare CI runner without a GPU
        // driver — the loader returns CL_PLATFORM_NOT_FOUND_KHR, which OpenCLBuilder.build() maps to an
        // empty list). There is nothing to smoke-test then, so skip cleanly rather than fail. When a
        // device IS present, runAddKernelOnDevice above has already asserted the elementwise sum on it.
        Assumptions.assumeTrue(devicesExercised > 0, "No OpenCL platform/device present — skipping smoke test");
    }
    // </editor-fold>

    /**
     * Compiles and runs the {@link #ADD_KERNEL_SOURCE} kernel on a single device and asserts that
     * every output element equals the element-wise sum of its inputs. All OpenCL resources created
     * here are released before returning so the test never leaks across devices.
     *
     * @param openCLPlatform the platform owning the device
     * @param openCLDevice   the device to build and run the kernel on
     */
    private void runAddKernelOnDevice(OpenCLPlatform openCLPlatform, OpenCLDevice openCLDevice) {
        final int[] a = new int[ELEMENT_COUNT];
        final int[] b = new int[ELEMENT_COUNT];
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            a[i] = i;
            b[i] = 2 * i + 1;
        }

        final ClApi clApi = OpenClBinding.defaultClApi();
        final ClDeviceId deviceId = openCLDevice.device();
        final ClContext context = clApi.createContext(openCLPlatform.platformId(), deviceId);
        final ClCommandQueue commandQueue = clApi.createCommandQueue(context, deviceId, false);
        final ClProgram program = clApi.createProgramWithSource(context, new String[] {ADD_KERNEL_SOURCE});
        clApi.buildProgram(program, deviceId, BUILD_OPTIONS);
        final ClKernel kernel = clApi.createKernel(program, KERNEL_NAME);

        final long intBufferBytes = (long) Integer.BYTES * ELEMENT_COUNT;
        final ClMem aMem = clApi.createBuffer(context, ClBufferFlags.READ_ONLY, intBufferBytes);
        final ClMem bMem = clApi.createBuffer(context, ClBufferFlags.READ_ONLY, intBufferBytes);
        final ClMem outMem = clApi.createBuffer(context, ClBufferFlags.WRITE_ONLY, intBufferBytes);
        try {
            clApi.writeBufferChunked(commandQueue, aMem, a);
            clApi.writeBufferChunked(commandQueue, bMem, b);

            clApi.setKernelArg(kernel, 0, aMem);
            clApi.setKernelArg(kernel, 1, bMem);
            clApi.setKernelArg(kernel, 2, outMem);

            clApi.enqueueNDRangeKernel(commandQueue, kernel, new long[] {ELEMENT_COUNT}, false);

            final ByteBuffer readBack =
                    ByteBuffer.allocateDirect((int) intBufferBytes).order(ByteOrder.nativeOrder());
            clApi.enqueueReadBuffer(commandQueue, outMem, 0L, readBack, false);
            clApi.finish(commandQueue);

            final int[] out = new int[ELEMENT_COUNT];
            readBack.position(0);
            readBack.asIntBuffer().get(out);

            for (int i = 0; i < ELEMENT_COUNT; i++) {
                assertThat(
                        "add kernel result on device " + openCLDevice.deviceName() + " at index " + i,
                        out[i],
                        is(a[i] + b[i]));
            }
        } finally {
            clApi.releaseMemObject(aMem);
            clApi.releaseMemObject(bMem);
            clApi.releaseMemObject(outMem);
            clApi.releaseKernel(kernel);
            clApi.releaseProgram(program);
            clApi.releaseCommandQueue(commandQueue);
            clApi.releaseContext(context);
        }
    }
}
