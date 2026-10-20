// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.lwjgl.opencl.CL10;

class OpenClLocalWorkSizeTest {

    /** CL_DEVICE_VERSION of the pocl CPU device in CI and in the measurement of the crash. */
    private static final String POCL_CPU_DEVICE_VERSION =
            "OpenCL 3.0 PoCL HSTR: cpu-x86_64-pc-linux-gnu-skylake-avx512";

    /** pocl's default work-group limit on its CPU device, the value that overflows its thread stack. */
    private static final long POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE = 4096L;

    /** A key grid of 2^20 work items (batchSizeInBits 20, keysPerWorkItem 1). */
    private static final long GLOBAL_WORK_SIZE = 1L << 20;

    // <editor-fold defaultstate="collapsed" desc="isPoclCpuDevice">
    @Test
    void isPoclCpuDevice_poclPlatformCpuDevice_isTrue() {
        assertThat(
                OpenClLocalWorkSize.isPoclCpuDevice(
                        OpenClLocalWorkSize.POCL_PLATFORM_NAME, POCL_CPU_DEVICE_VERSION, CL10.CL_DEVICE_TYPE_CPU),
                is(true));
    }

    @Test
    void isPoclCpuDevice_platformRenamedButPoclDeviceVersion_isTrue() {
        // the vendor string is no help: pocl's CPU device reports the CPU's vendor_id (GenuineIntel)
        assertThat(
                OpenClLocalWorkSize.isPoclCpuDevice(
                        "Some ICD platform", POCL_CPU_DEVICE_VERSION, CL10.CL_DEVICE_TYPE_CPU),
                is(true));
    }

    @Test
    void isPoclCpuDevice_poclGpuBackend_isFalse() {
        // pocl's CUDA and other backends do not run the CPU work-group function that overflows
        assertThat(
                OpenClLocalWorkSize.isPoclCpuDevice(
                        OpenClLocalWorkSize.POCL_PLATFORM_NAME,
                        "OpenCL 3.0 PoCL HSTR: CUDA-sm_86",
                        CL10.CL_DEVICE_TYPE_GPU),
                is(false));
    }

    @Test
    void isPoclCpuDevice_intelCpuRuntime_isFalse() {
        assertThat(
                OpenClLocalWorkSize.isPoclCpuDevice("Intel(R) OpenCL", "OpenCL 3.0 (Build 0)", CL10.CL_DEVICE_TYPE_CPU),
                is(false));
    }

    @Test
    void isPoclCpuDevice_nvidiaGpu_isFalse() {
        assertThat(
                OpenClLocalWorkSize.isPoclCpuDevice("NVIDIA CUDA", "OpenCL 3.0 CUDA", CL10.CL_DEVICE_TYPE_GPU),
                is(false));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="choose">
    @Test
    void choose_notAPoclCpuDevice_leavesTheChoiceToTheDriver() {
        assertThat(
                OpenClLocalWorkSize.choose(GLOBAL_WORK_SIZE, POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE, false),
                is(equalTo(OptionalLong.empty())));
    }

    @Test
    void choose_poclCpuDeviceDefaultLimit_isCappedAt256() {
        assertThat(
                OpenClLocalWorkSize.choose(GLOBAL_WORK_SIZE, POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE, true),
                is(equalTo(OptionalLong.of(OpenClLocalWorkSize.POCL_CPU_MAX_LOCAL_WORK_SIZE))));
    }

    @Test
    void choose_poclCpuDeviceKernelLimitBelowTheCap_isTheKernelLimit() {
        // e.g. POCL_MAX_WORK_GROUP_SIZE=128 set by the user: the queried kernel limit already says so
        assertThat(OpenClLocalWorkSize.choose(GLOBAL_WORK_SIZE, 128L, true), is(equalTo(OptionalLong.of(128L))));
    }

    @Test
    void choose_poclCpuDeviceKernelLimitNotAPowerOfTwo_isTheNextLowerPowerOfTwo() {
        assertThat(OpenClLocalWorkSize.choose(GLOBAL_WORK_SIZE, 200L, true), is(equalTo(OptionalLong.of(128L))));
    }

    @Test
    void choose_poclCpuDeviceGlobalSizeBelowTheCap_isTheGlobalSize() {
        assertThat(
                OpenClLocalWorkSize.choose(64L, POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE, true),
                is(equalTo(OptionalLong.of(64L))));
    }

    @Test
    void choose_poclCpuDeviceGlobalSizeNotAPowerOfTwo_dividesTheGlobalSize() {
        // OpenCL 1.x requires the local size to divide the global size
        assertThat(
                OpenClLocalWorkSize.choose(96L, POCL_DEFAULT_KERNEL_WORK_GROUP_SIZE, true),
                is(equalTo(OptionalLong.of(32L))));
    }

    @Test
    void choose_nonPositiveSizes_areRefused() {
        assertThrows(IllegalArgumentException.class, () -> OpenClLocalWorkSize.choose(0L, 256L, true));
        assertThrows(IllegalArgumentException.class, () -> OpenClLocalWorkSize.choose(1024L, 0L, true));
    }
    // </editor-fold>
}
