// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

import java.util.List;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import org.junit.jupiter.api.Test;
import org.lwjgl.opencl.CL10;

/**
 * Reads back the device binary of a built program through {@link LwjglClApi#getProgramBinary}. The
 * binary is the input to {@code ptxas -v} for register/spill statistics on NVIDIA, so the test only
 * asserts what every OpenCL implementation must deliver for a successfully built program: a
 * non-empty binary.
 */
class LwjglClApiProgramBinaryTest {

    private static final String TRIVIAL_KERNEL = "__kernel void k(__global int* a) { a[get_global_id(0)] = 1; }";

    private final ClApi clApi = new LwjglClApi(
            new ClErrorChecker(new ClErrorCodeResolver()), new OpenClLibraryLoader(new LwjglClLibraryInitializer()));

    // <editor-fold defaultstate="collapsed" desc="getProgramBinary">
    @Test
    @OpenCLTest
    void getProgramBinary_builtProgram_returnsNonEmptyBinary() {
        new OpenCLPlatformAssume().assumeOneOpenClDeviceAvailable();

        // arrange
        final ClPlatformId platform = clApi.getPlatformIds().get(0);
        final List<ClDeviceId> devices = clApi.getDeviceIds(platform, CL10.CL_DEVICE_TYPE_ALL);
        final ClContext context = clApi.createContext(platform, devices.get(0));
        try {
            final ClProgram program = clApi.createProgramWithSource(context, new String[] {TRIVIAL_KERNEL});
            try {
                clApi.buildProgram(program, devices.get(0), "");

                // act
                final byte[] binary = clApi.getProgramBinary(program);

                // assert
                assertThat(binary.length, greaterThan(0));
            } finally {
                clApi.releaseProgram(program);
            }
        } finally {
            clApi.releaseContext(context);
        }
    }
    // </editor-fold>
}
