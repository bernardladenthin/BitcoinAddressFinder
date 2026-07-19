// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
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
import net.ladenthin.bitcoinaddressfinder.configuration.GpuFilterType;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import org.junit.jupiter.api.Test;

/**
 * Covers the two host-side mechanisms that keep the GPU pre-filter's fingerprint <b>width</b>
 * honest: the little-endian flattening of the 16-bit slot array, and the dispatch-time guard that
 * refuses to launch when the configured width disagrees with the uploaded one.
 *
 * <p><b>Why both matter, and why they are tested rather than trusted.</b> The device cannot detect
 * either fault. It takes one {@code uchar*} buffer for both widths and casts it to {@code ushort*}
 * when told to; a wrongly ordered or wrongly interpreted buffer yields fingerprints that almost
 * never match, so the filter rejects nearly everything — including funded addresses — while the
 * scan keeps running and reports nothing. That is indistinguishable from an honest empty result:
 * the failure mode is <b>silent false negatives</b>, not an error, which is the one outcome this
 * project cannot tolerate.
 */
public class OpenCLContextGpuFilterWidthTest {

    private final BitHelper bitHelper = new BitHelper();

    // <editor-fold defaultstate="collapsed" desc="toLittleEndianBytes">

    /**
     * The empty filter is a real configuration (an empty address database), and it reaches the
     * upload path. Flattening must yield an empty image rather than throwing or padding — the
     * placeholder padding is the buffer allocator's job, not the encoder's.
     */
    @Test
    public void toLittleEndianBytes_emptyArray_returnsEmptyImage() {
        assertThat(OpenCLContext.toLittleEndianBytes(new short[0]).length, is(equalTo(0)));
    }

    /**
     * Low byte first. If the two bytes were ever emitted the other way round, every single device
     * probe would read a byte-swapped fingerprint and miss — this single assertion is the whole
     * endianness contract in miniature.
     */
    @Test
    public void toLittleEndianBytes_singleValue_writesLowByteFirst() {
        byte[] bytes = OpenCLContext.toLittleEndianBytes(new short[] {(short) 0x1234});

        assertThat(bytes, is(equalTo(new byte[] {(byte) 0x34, (byte) 0x12})));
    }

    /**
     * A multi-slot vector with explicit expected bytes: pins both the per-slot byte order and the
     * slot order. A correct byte order combined with a reversed slot order would still send every
     * fingerprint to the wrong index.
     */
    @Test
    public void toLittleEndianBytes_multipleValues_writesEachSlotLowByteFirstInOrder() {
        short[] values = {(short) 0x0000, (short) 0x0001, (short) 0x0100, (short) 0xABCD};

        byte[] bytes = OpenCLContext.toLittleEndianBytes(values);

        assertThat(bytes, is(equalTo(new byte[] {
            (byte) 0x00, (byte) 0x00,
            (byte) 0x01, (byte) 0x00,
            (byte) 0x00, (byte) 0x01,
            (byte) 0xCD, (byte) 0xAB
        })));
    }

    /**
     * Java {@code short} is signed, the device reads {@code ushort}. Slot values with the high bit
     * set are ordinary fingerprints (half of them are), so a sign-extending or otherwise
     * value-mangling encoder would corrupt half the filter. These are the values that would expose
     * such a bug.
     */
    @Test
    public void toLittleEndianBytes_highBitSetValues_areCopiedVerbatim() {
        short[] values = {(short) 0x8000, (short) 0xFFFF, (short) 0x80FF, (short) 0xFF00};

        byte[] bytes = OpenCLContext.toLittleEndianBytes(values);

        assertThat(bytes, is(equalTo(new byte[] {
            (byte) 0x00, (byte) 0x80,
            (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0x80,
            (byte) 0x00, (byte) 0xFF
        })));
    }

    /**
     * Length must be exactly {@code 2 * n}. The kernel derives its slot index from the metadata's
     * {@code segCountLen} and indexes the buffer directly, so a short image is an out-of-bounds
     * read and a long one silently shifts nothing into place — neither is reported by the device.
     */
    @Test
    public void toLittleEndianBytes_lengthIsExactlyTwoBytesPerSlot() {
        for (int n : new int[] {0, 1, 2, 7, 1024}) {
            assertThat(
                    "slot count " + n,
                    OpenCLContext.toLittleEndianBytes(new short[n]).length,
                    is(equalTo(n * Short.BYTES)));
        }
    }

    /**
     * Round-trips the encoded image back through a little-endian {@code ShortBuffer}: an
     * independent statement of "the device reads back exactly the values the CPU filter holds",
     * covering a wide value range rather than the handful of literals above.
     */
    @Test
    public void toLittleEndianBytes_roundTripsThroughLittleEndianShortBuffer() {
        short[] values = new short[512];
        for (int i = 0; i < values.length; i++) {
            values[i] = (short) (i * 127);
        }

        byte[] bytes = OpenCLContext.toLittleEndianBytes(values);

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < values.length; i++) {
            assertThat("slot " + i, buffer.getShort(i * Short.BYTES), is(equalTo(values[i])));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="createKeys width guard">

    /**
     * The guard sits behind {@code createKeys}, which first dereferences the device objects created
     * by {@code init()}; it therefore cannot be reached without a real OpenCL device and these
     * tests self-skip when none is present (same pattern as
     * {@code OpenCLCompactOutputIntegrationTest}). The grid is capped to the device's safe range so
     * a CPU runtime such as pocl in CI exercises the same path.
     */
    private CProducerOpenCL compactConfig(int batchSizeInBits, GpuFilterType configuredType) {
        CProducerOpenCL cfg = new CProducerOpenCL();
        cfg.batchSizeInBits = batchSizeInBits;
        cfg.keysPerWorkItem = 1;
        cfg.enableGpuFilter = true;
        cfg.transferAll = false;
        cfg.gpuFilterType = configuredType;
        return cfg;
    }

    private static AddressIterable fiveEntryFilterSource() {
        List<byte[]> entries = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            byte[] h = new byte[20];
            h[0] = (byte) i;
            h[4] = (byte) (i * 7);
            entries.add(h);
        }
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

    private static void upload8(OpenCLContext context) {
        BinaryFuse8GpuFilterData data =
                BinaryFuse8AddressPresence.populateFrom(fiveEntryFilterSource()).toGpuFilterData();
        long seed = data.seed();
        context.uploadGpuFilter(
                data.fingerprints(),
                (int) seed,
                (int) (seed >>> 32),
                data.segmentLength(),
                data.segmentLengthMask(),
                data.segmentCountLength());
    }

    private static void upload16(OpenCLContext context) {
        BinaryFuse16GpuFilterData data = BinaryFuse16AddressPresence.populateFrom(fiveEntryFilterSource())
                .toGpuFilterData();
        long seed = data.seed();
        context.uploadGpuFilter16(
                data.fingerprints(),
                (int) seed,
                (int) (seed >>> 32),
                data.segmentLength(),
                data.segmentLengthMask(),
                data.segmentCountLength());
    }

    private int batchSizeInBits(OpenCLPlatformAssume assume) {
        return Math.min(8, assume.maxGridBitsForAvailableDevice());
    }

    /**
     * A Fuse-16 payload probed as {@code FUSE_8} must refuse to dispatch. Without the guard the run
     * proceeds and reports nothing: the kernel reads two adjacent 16-bit slot halves as one 8-bit
     * fingerprint, so stored addresses are dropped silently.
     *
     * <p>The message is asserted to name <b>both</b> widths because it is the only diagnostic an
     * operator gets — a mismatch is otherwise invisible, and "which side is wrong" is exactly what
     * the operator has to decide (fix the config, or upload the other width).
     */
    @OpenCLTest
    @Test
    public void createKeys_uploaded16ButConfigured8_throwsNamingBothWidths() throws IOException {
        OpenCLPlatformAssume assume = new OpenCLPlatformAssume();
        assume.assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        OpenCLContext context =
                new OpenCLContext(compactConfig(Math.max(1, batchSizeInBits(assume)), GpuFilterType.FUSE_8), bitHelper);
        try {
            context.init();
            upload16(context);

            IllegalStateException ex =
                    assertThrows(IllegalStateException.class, () -> context.createKeys(BigInteger.ONE));

            assertThat(ex.getMessage(), containsString(GpuFilterType.FUSE_8.toString()));
            assertThat(ex.getMessage(), containsString(GpuFilterType.FUSE_16.toString()));
        } finally {
            context.close();
        }
    }

    /**
     * The mirror case: a Fuse-8 payload probed as {@code FUSE_16}. Kept separate from the case
     * above because the guard compares two independently set fields, and an implementation that
     * only caught one direction would still let the other silently drop funded addresses.
     */
    @OpenCLTest
    @Test
    public void createKeys_uploaded8ButConfigured16_throwsNamingBothWidths() throws IOException {
        OpenCLPlatformAssume assume = new OpenCLPlatformAssume();
        assume.assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        OpenCLContext context = new OpenCLContext(
                compactConfig(Math.max(1, batchSizeInBits(assume)), GpuFilterType.FUSE_16), bitHelper);
        try {
            context.init();
            upload8(context);

            IllegalStateException ex =
                    assertThrows(IllegalStateException.class, () -> context.createKeys(BigInteger.ONE));

            assertThat(ex.getMessage(), containsString(GpuFilterType.FUSE_16.toString()));
            assertThat(ex.getMessage(), containsString(GpuFilterType.FUSE_8.toString()));
        } finally {
            context.close();
        }
    }

    /**
     * The guard must not be trigger-happy: matching width and payload dispatch normally. Without
     * this, a guard that always threw would look "safe" while disabling compact mode entirely.
     */
    @OpenCLTest
    @Test
    public void createKeys_uploadedAndConfiguredWidthsAgree_dispatches() throws IOException {
        OpenCLPlatformAssume assume = new OpenCLPlatformAssume();
        assume.assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        OpenCLContext context = new OpenCLContext(
                compactConfig(Math.max(1, batchSizeInBits(assume)), GpuFilterType.FUSE_16), bitHelper);
        try {
            context.init();
            upload16(context);

            try (OpenCLGridResult result = context.createKeys(BigInteger.ONE)) {
                assertThat(result.getResult().capacity() > 0, is(true));
            }
        } finally {
            context.close();
        }
    }

    /**
     * Documents the actual behaviour when compact mode is configured but no filter was ever
     * uploaded: the context falls back to <b>full transfer</b> and does not throw, because the
     * transfer-mode flag is computed from "a filter was uploaded" before the width comparison is
     * reached. That is safe (nothing is filtered away, so no address can be missed) but it means
     * the guard's "absent" wording is not reachable through the public API. Pinning it here keeps a
     * future change from turning this benign fallback into a hard failure — or, worse, into a
     * compact dispatch against the placeholder filter, which would drop everything.
     */
    @OpenCLTest
    @Test
    public void createKeys_compactConfiguredButNoFilterUploaded_fallsBackToFullTransfer() throws IOException {
        OpenCLPlatformAssume assume = new OpenCLPlatformAssume();
        assume.assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        final int bits = Math.max(1, batchSizeInBits(assume));
        OpenCLContext context = new OpenCLContext(compactConfig(bits, GpuFilterType.FUSE_16), bitHelper);
        try {
            context.init();

            try (OpenCLGridResult result = context.createKeys(BigInteger.ONE)) {
                // full transfer -> every work item comes back, nothing was filtered
                assertThat(result.getPublicKeyBytes().length, is(equalTo(1 << bits)));
            }
        } finally {
            context.close();
        }
    }
    // </editor-fold>
}
