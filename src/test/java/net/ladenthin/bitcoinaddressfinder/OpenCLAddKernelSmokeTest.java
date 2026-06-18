// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_WRITE_ONLY;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clFinish;
import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;
import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clReleaseProgram;
import static org.jocl.CL.clSetKernelArg;
import static org.jocl.CL.setExceptionsEnabled;

import java.util.List;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;
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
        setExceptionsEnabled(true);
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
     * @param openCLPlatform the platform owning the device (provides the context properties)
     * @param openCLDevice   the device to build and run the kernel on
     */
    private void runAddKernelOnDevice(OpenCLPlatform openCLPlatform, OpenCLDevice openCLDevice) {
        final int[] a = new int[ELEMENT_COUNT];
        final int[] b = new int[ELEMENT_COUNT];
        final int[] out = new int[ELEMENT_COUNT];
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            a[i] = i;
            b[i] = 2 * i + 1;
        }

        final cl_device_id deviceId = openCLDevice.device();
        final cl_context context =
                clCreateContext(openCLPlatform.contextProperties(), 1, new cl_device_id[] {deviceId}, null, null, null);
        final cl_command_queue commandQueue =
                clCreateCommandQueueWithProperties(context, deviceId, new cl_queue_properties(), null);
        final cl_program program = clCreateProgramWithSource(context, 1, new String[] {ADD_KERNEL_SOURCE}, null, null);
        clBuildProgram(program, 0, null, BUILD_OPTIONS, null, null);
        final cl_kernel kernel = clCreateKernel(program, KERNEL_NAME, null);

        final long intBufferBytes = (long) Sizeof.cl_int * ELEMENT_COUNT;
        final cl_mem aMem =
                clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, intBufferBytes, Pointer.to(a), null);
        final cl_mem bMem =
                clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, intBufferBytes, Pointer.to(b), null);
        final cl_mem outMem = clCreateBuffer(context, CL_MEM_WRITE_ONLY, intBufferBytes, null, null);
        try {
            clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(aMem));
            clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(bMem));
            clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(outMem));

            clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, new long[] {ELEMENT_COUNT}, null, 0, null, null);
            clEnqueueReadBuffer(commandQueue, outMem, CL_TRUE, 0, intBufferBytes, Pointer.to(out), 0, null, null);
            clFinish(commandQueue);

            for (int i = 0; i < ELEMENT_COUNT; i++) {
                assertThat(
                        "add kernel result on device " + openCLDevice.deviceName() + " at index " + i,
                        out[i],
                        is(a[i] + b[i]));
            }
        } finally {
            clReleaseMemObject(aMem);
            clReleaseMemObject(bMem);
            clReleaseMemObject(outMem);
            clReleaseKernel(kernel);
            clReleaseProgram(program);
            clReleaseCommandQueue(commandQueue);
            clReleaseContext(context);
        }
    }
}
