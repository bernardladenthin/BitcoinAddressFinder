// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL11;
import org.lwjgl.opencl.CL12;

/**
 * Tests {@link ClDeviceInfoFormatter}.
 *
 * <p>The previous binding shipped {@code CL.stringFor_*} helpers that rendered OpenCL bit fields as
 * their symbolic names; LWJGL has no equivalent, so the project has to provide them. The expected
 * strings below are exactly what the old helpers produced, because {@code OpenCLDevice.toStringPretty()}
 * is compared line by line against a fixed sample in {@code OpenCLDeviceTest} — a formatting change
 * here silently breaks that test, and worse, changes user-visible device reports.
 *
 * <p>Note the trailing space in every non-empty expectation: the old helpers terminated each flag
 * name with one, and {@link ClDeviceInfoFormatterJoclParityTest} holds the new implementation to
 * that byte for byte.
 *
 * <p>Pure bit-field rendering, so no OpenCL runtime is required.
 */
class ClDeviceInfoFormatterTest {

    private final ClDeviceInfoFormatter formatter = new ClDeviceInfoFormatter();

    // <editor-fold defaultstate="collapsed" desc="formatDeviceType">
    @Test
    void formatDeviceType_gpu_returnsSymbolicName() {
        // act, assert
        assertThat(formatter.formatDeviceType(CL10.CL_DEVICE_TYPE_GPU), is(equalTo("CL_DEVICE_TYPE_GPU ")));
    }

    @Test
    void formatDeviceType_cpu_returnsSymbolicName() {
        // act, assert
        assertThat(formatter.formatDeviceType(CL10.CL_DEVICE_TYPE_CPU), is(equalTo("CL_DEVICE_TYPE_CPU ")));
    }

    @Test
    void formatDeviceType_severalBitsSet_returnsNamesInAscendingBitOrder() {
        // arrange
        final long deviceType = CL10.CL_DEVICE_TYPE_CPU | CL10.CL_DEVICE_TYPE_GPU;

        // act, assert
        assertThat(formatter.formatDeviceType(deviceType), is(equalTo("CL_DEVICE_TYPE_CPU CL_DEVICE_TYPE_GPU ")));
    }

    @Test
    void formatDeviceType_customType_returnsSymbolicName() {
        // act, assert
        assertThat(formatter.formatDeviceType(CL12.CL_DEVICE_TYPE_CUSTOM), is(equalTo("CL_DEVICE_TYPE_CUSTOM ")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="formatLocalMemType">
    @Test
    void formatLocalMemType_local_returnsSymbolicName() {
        // act, assert
        assertThat(formatter.formatLocalMemType(CL10.CL_LOCAL), is(equalTo("CL_LOCAL")));
    }

    @Test
    void formatLocalMemType_global_returnsSymbolicName() {
        // act, assert
        assertThat(formatter.formatLocalMemType(CL10.CL_GLOBAL), is(equalTo("CL_GLOBAL")));
    }

    @Test
    void formatLocalMemType_noneValue_reportedAsInvalid() {
        // CL_NONE shares the value 0 but is not a valid local-memory type; a parity sweep against
        // the previous binding showed it reports this as invalid rather than as a name.
        // act, assert
        assertThat(formatter.formatLocalMemType(CL10.CL_NONE), is(equalTo("INVALID cl_device_local_mem_type: 0")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="formatCommandQueueProperties">
    @Test
    void formatCommandQueueProperties_outOfOrderAndProfiling_returnsBothNames() {
        // arrange
        final long properties = CL10.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE | CL10.CL_QUEUE_PROFILING_ENABLE;

        // act, assert
        assertThat(
                formatter.formatCommandQueueProperties(properties),
                is(equalTo("CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE CL_QUEUE_PROFILING_ENABLE ")));
    }

    @Test
    void formatCommandQueueProperties_noBitSet_returnsNoneLiteral() {
        // act, assert
        assertThat(formatter.formatCommandQueueProperties(0L), is(equalTo("(none)")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="formatDeviceFpConfig">
    @Test
    void formatDeviceFpConfig_fullNvidiaConfig_returnsNamesInAscendingBitOrder() {
        // arrange
        final long fpConfig = CL10.CL_FP_DENORM
                | CL10.CL_FP_INF_NAN
                | CL10.CL_FP_ROUND_TO_NEAREST
                | CL10.CL_FP_ROUND_TO_ZERO
                | CL10.CL_FP_ROUND_TO_INF
                | CL10.CL_FP_FMA
                | CL12.CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT;

        // act, assert
        assertThat(
                formatter.formatDeviceFpConfig(fpConfig),
                is(equalTo("CL_FP_DENORM CL_FP_INF_NAN CL_FP_ROUND_TO_NEAREST CL_FP_ROUND_TO_ZERO"
                        + " CL_FP_ROUND_TO_INF CL_FP_FMA CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT ")));
    }

    @Test
    void formatDeviceFpConfig_softFloat_isOrderedBeforeCorrectlyRounded() {
        // arrange
        final long fpConfig = CL11.CL_FP_SOFT_FLOAT | CL12.CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT;

        // act, assert
        assertThat(
                formatter.formatDeviceFpConfig(fpConfig),
                is(equalTo("CL_FP_SOFT_FLOAT CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT ")));
    }
    // </editor-fold>
}
