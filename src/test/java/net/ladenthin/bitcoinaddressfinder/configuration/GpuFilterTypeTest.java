// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Keeps {@link GpuFilterType}'s wire values identical to the {@code GPU_FILTER_TYPE_*} defines in
 * the OpenCL source.
 *
 * <h2>Why parse the kernel source instead of restating the numbers</h2>
 * The enum and the {@code #define}s are two copies of one contract, edited in different files by
 * different reflexes. A test that merely asserted {@code FUSE_8.getWireValue() == 0} would restate
 * the Java side and pin nothing: renumbering the OpenCL defines alone would leave it green.
 *
 * <p>Drift here is silent. The kernel would probe a Fuse-8 buffer as {@code ushort*}, reading two
 * adjacent slots as one 16-bit fingerprint, which almost never matches — so the filter would reject
 * nearly every candidate including funded addresses, while the scan kept running and reported
 * nothing. That is indistinguishable from an honest empty result, so it has to be caught at build
 * time rather than observed in production.
 */
class GpuFilterTypeTest {

    /** The kernel source is a resource, so this test works from a jar as well as from target/classes. */
    private static final String KERNEL_RESOURCE = "/inc_ecc_secp256k1custom.cl";

    private static final Pattern DEFINE =
            Pattern.compile("^\\s*#define\\s+(GPU_FILTER_TYPE_\\w+)\\s+(\\d+)u?\\s*$", Pattern.MULTILINE);

    private static Map<String, Integer> parseKernelDefines() throws IOException {
        final String source;
        try (InputStream in = GpuFilterTypeTest.class.getResourceAsStream(KERNEL_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("kernel resource not found: " + KERNEL_RESOURCE);
            }
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final Map<String, Integer> defines = new HashMap<>();
        final Matcher m = DEFINE.matcher(source);
        while (m.find()) {
            defines.put(m.group(1), Integer.parseInt(m.group(2)));
        }
        return defines;
    }

    /** Maps an enum constant to the define name the kernel uses for it. */
    private static String defineNameFor(GpuFilterType type) {
        return switch (type) {
            case FUSE_8 -> "GPU_FILTER_TYPE_FUSE8";
            case FUSE_16 -> "GPU_FILTER_TYPE_FUSE16";
        };
    }

    @Test
    void everyEnumConstant_hasAMatchingKernelDefine_withTheSameValue() throws IOException {
        final Map<String, Integer> defines = parseKernelDefines();
        assertThat("the kernel must declare filter-type defines at all", defines.isEmpty(), is(false));

        for (GpuFilterType type : GpuFilterType.values()) {
            final String name = defineNameFor(type);
            assertThat("kernel is missing a define for " + type, defines, hasKey(name));
            assertThat(
                    "wire value of " + type + " must equal " + name + " in the kernel source",
                    defines.get(name),
                    is(equalTo(type.getWireValue())));
        }
    }

    /**
     * The reverse direction: a define added to the kernel without a matching enum constant means the
     * kernel supports a filter the configuration cannot select — harmless — but it just as easily
     * signals a half-finished rename, where the enum still points at the old number.
     */
    @Test
    void kernelDeclaresNoFilterTypeBeyondTheEnum() throws IOException {
        final Set<String> expected = new HashSet<>();
        for (GpuFilterType type : GpuFilterType.values()) {
            expected.add(defineNameFor(type));
        }
        assertThat(
                "kernel declares a GPU_FILTER_TYPE define with no corresponding GpuFilterType constant",
                parseKernelDefines().keySet(),
                is(equalTo(expected)));
    }

    @Test
    void wireValues_areDistinct() {
        final Set<Integer> seen = new HashSet<>();
        for (GpuFilterType type : GpuFilterType.values()) {
            assertThat("duplicate wire value for " + type, seen.add(type.getWireValue()), is(true));
        }
    }

    /**
     * The default must stay the widely-compatible filter. Fuse-16 needs twice the VRAM in a single
     * allocation, and {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE} is a quarter of total VRAM on NVIDIA —
     * 2047 MB on an 8 GB card, which the Full DB tier exceeds at 2.25 B/entry. Defaulting to
     * Fuse-16 would turn a working configuration into an allocation failure on that hardware.
     */
    @Test
    void defaultOnProducerConfig_isTheCompatibleFilter() {
        assertThat(new CProducerOpenCL().gpuFilterType, is(GpuFilterType.FUSE_8));
    }

    @Test
    void fuse16_isNotTheSameWireValueAsFuse8() {
        assertThat(GpuFilterType.FUSE_16.getWireValue(), is(not(equalTo(GpuFilterType.FUSE_8.getWireValue()))));
    }
}
