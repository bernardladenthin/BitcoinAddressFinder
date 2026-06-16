// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.KernelProfileStage;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import nl.altindag.log.LogCaptor;
import nl.altindag.log.model.LogEvent;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class OpenCLContextTest {

    private final BitHelper bitHelper = new BitHelper();

    /** Minimal in-memory {@link AddressIterable} of 20-byte hash160 snapshots for filter builds. */
    private static AddressIterable iterableOf(List<byte[]> entries) {
        return new AddressIterable() {
            @Override
            public Stream<ByteBuffer> addresses() {
                return entries.stream().map(ByteBuffer::wrap);
            }

            @Override
            public long count() {
                return entries.size();
            }
        };
    }

    private static AddressIterable fiveEntryFilterSource() {
        List<byte[]> entries = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            byte[] h = new byte[20];
            h[0] = (byte) i;
            h[4] = (byte) (i * 7);
            entries.add(h);
        }
        return iterableOf(entries);
    }

    private static void uploadPayload(OpenCLContext context, BinaryFuse8GpuFilterData data) {
        long seed = data.seed();
        context.uploadGpuFilter(
                data.fingerprints(),
                (int) seed,
                (int) (seed >>> 32),
                data.segmentLength(),
                data.segmentLengthMask(),
                data.segmentCountLength());
    }

    // <editor-fold defaultstate="collapsed" desc="constructor">
    @Test
    public void constructor_defaultConstructor_noExceptionThrown() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();

        // act
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);

        // assert
        assertThat(openCLContext, is(notNullValue()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="buildOptions">
    @Test
    public void buildOptions_useSafeGcdInverseTrue_omitsLegacyDefine() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.useSafeGcdInverse = true;
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);

        // act
        String options = openCLContext.buildOptions();

        // assert
        assertThat(options, not(containsString(OpenCLContext.LEGACY_BINARY_GCD_INV_MOD_BUILD_OPTION)));
    }

    @Test
    public void buildOptions_useSafeGcdInverseFalse_appendsLegacyDefine() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.useSafeGcdInverse = false;
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);

        // act
        String options = openCLContext.buildOptions();

        // assert
        assertThat(options, containsString(OpenCLContext.LEGACY_BINARY_GCD_INV_MOD_BUILD_OPTION));
    }

    @Test
    public void buildOptions_defaultConfiguration_usesSafeGcd() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);

        // act
        String options = openCLContext.buildOptions();

        // assert
        assertThat(options, not(containsString(OpenCLContext.LEGACY_BINARY_GCD_INV_MOD_BUILD_OPTION)));
    }

    @Test
    public void buildOptions_kernelProfileStageFull_omitsProfileDefines() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.kernelProfileStage = KernelProfileStage.FULL;
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);

        // act
        String options = openCLContext.buildOptions();

        // assert
        assertThat(options, not(containsString(OpenCLContext.PROFILE_SKIP_SECOND_HASH160_BUILD_OPTION)));
        assertThat(options, not(containsString(OpenCLContext.PROFILE_SKIP_HASH160_BUILD_OPTION)));
    }

    @Test
    public void buildOptions_kernelProfileStageOneHash160_appendsSkipSecondHashDefine() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.kernelProfileStage = KernelProfileStage.ONE_HASH160;
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);

        // act
        String options = openCLContext.buildOptions();

        // assert: the single-chain define is present; the both-chains define is not (it is not a
        // substring of "-D PROFILE_SKIP_SECOND_HASH160").
        assertThat(options, containsString(OpenCLContext.PROFILE_SKIP_SECOND_HASH160_BUILD_OPTION));
        assertThat(options, not(containsString(OpenCLContext.PROFILE_SKIP_HASH160_BUILD_OPTION)));
    }

    @Test
    public void buildOptions_kernelProfileStageNoHash160_appendsSkipHashDefine() {
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.kernelProfileStage = KernelProfileStage.NO_HASH160;
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);

        // act
        String options = openCLContext.buildOptions();

        // assert
        assertThat(options, containsString(OpenCLContext.PROFILE_SKIP_HASH160_BUILD_OPTION));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="kernelProfileStage builds and runs">
    /**
     * Every {@link KernelProfileStage} must compile and run on the device — this guards the
     * {@code #ifdef PROFILE_*} guards in the kernel so the profiling suite (GpuFuse8FilterBenchmark
     * {@code -p kernelProfileStage=...}) never breaks. Timing/correctness is not asserted here (the
     * non-FULL modes intentionally emit incorrect hashes); only that the kernel builds and a launch
     * completes and returns a result buffer.
     *
     * @param stage the kernel profiling stage under test
     */
    @ParameterizedTest
    @EnumSource(KernelProfileStage.class)
    @OpenCLTest
    public void kernelProfileStage_buildsAndRuns(KernelProfileStage stage) throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.batchSizeInBits = 8;
        cProducerOpenCL.keysPerWorkItem = 1;
        cProducerOpenCL.kernelProfileStage = stage;
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);
        try {
            openCLContext.init();

            // act
            try (OpenCLGridResult result = openCLContext.createKeys(BigInteger.valueOf(0x1234_5678L))) {
                // assert: a launch completed and produced a readable result buffer
                assertThat(result.getResult().capacity(), is(greaterThan(0)));
            }
        } finally {
            openCLContext.close();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="init">
    @OpenCLTest
    @Test
    public void init_defaultConfiguration_logsSelectedDeviceInfo() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);

        try (LogCaptor logCaptor = LogCaptor.forClass(OpenCLContext.class)) {
            // act
            openCLContext.init();

            // assert
            assertThat(
                    logCaptor.getLogEvents().stream()
                            .filter(e -> "INFO".equals(e.getLevel()))
                            .map(LogEvent::getFormattedMessage)
                            .anyMatch(m -> m.startsWith("Selected OpenCL device:")
                                    && m.contains("--- Info for OpenCL device:")),
                    is(true));
        } finally {
            openCLContext.close();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="device byte-order guard">
    @Test
    public void assertDeviceByteOrderSupported_littleEndian_doesNotThrow() {
        // little-endian matches GPU_NATIVE_WORD_ORDER -> no exception
        OpenCLContext.assertDeviceByteOrderSupported(java.nio.ByteOrder.LITTLE_ENDIAN, "test-le-device");
    }

    @Test
    public void assertDeviceByteOrderSupported_bigEndian_throws() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> OpenCLContext.assertDeviceByteOrderSupported(java.nio.ByteOrder.BIG_ENDIAN, "test-be-device"));
        assertThat(ex.getMessage(), containsString("test-be-device"));
        assertThat(ex.getMessage(), containsString("BIG_ENDIAN"));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="compact-mode version guard">
    @Test
    public void assertCompactModeDeviceVersionSupported_notRequested_doesNotThrow() {
        // compact mode not requested -> version is irrelevant
        OpenCLContext.assertCompactModeDeviceVersionSupported(false, new ComparableVersion("1.2"), "test-old-device");
    }

    @Test
    public void assertCompactModeDeviceVersionSupported_requested_version2dot0_doesNotThrow() {
        OpenCLContext.assertCompactModeDeviceVersionSupported(true, new ComparableVersion("2.0"), "test-device");
    }

    @Test
    public void assertCompactModeDeviceVersionSupported_requested_version3dot0_doesNotThrow() {
        // 3.0 is a superset of 2.0 — should also pass
        OpenCLContext.assertCompactModeDeviceVersionSupported(true, new ComparableVersion("3.0"), "test-device");
    }

    @Test
    public void assertCompactModeDeviceVersionSupported_requested_version1dot2_throws() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> OpenCLContext.assertCompactModeDeviceVersionSupported(
                        true, new ComparableVersion("1.2"), "test-old-device"));
        assertThat(ex.getMessage(), containsString("test-old-device"));
        assertThat(ex.getMessage(), containsString("2.0"));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="uploadGpuFilter / isInitialized (Step D)">
    @OpenCLTest
    @Test
    public void uploadGpuFilter_andClose_doesNotThrow() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);
        BinaryFuse8GpuFilterData data =
                BinaryFuse8AddressPresence.populateFrom(fiveEntryFilterSource()).toGpuFilterData();

        // act + assert (no exception)
        openCLContext.init();
        uploadPayload(openCLContext, data);
        openCLContext.close();
    }

    @OpenCLTest
    @Test
    public void isInitialized_falseBeforeInit_trueAfterInit_falseAfterRelease() throws IOException {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        // arrange
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        OpenCLContext openCLContext = new OpenCLContext(cProducerOpenCL, bitHelper);
        BinaryFuse8GpuFilterData data =
                BinaryFuse8AddressPresence.populateFrom(fiveEntryFilterSource()).toGpuFilterData();

        // assert: false before init
        assertThat(openCLContext.isInitialized(), is(false));

        // act: init + upload
        openCLContext.init();
        assertThat(openCLContext.isInitialized(), is(true));
        uploadPayload(openCLContext, data);
        assertThat(openCLContext.isInitialized(), is(true));

        // act: release
        openCLContext.close();
        assertThat(openCLContext.isInitialized(), is(false));
    }
    // </editor-fold>
}
