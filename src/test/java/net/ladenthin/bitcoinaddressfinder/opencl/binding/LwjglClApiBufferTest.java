// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import org.junit.jupiter.api.Test;
import org.lwjgl.opencl.CL10;

/**
 * Round-trips host payloads through device memory using {@link LwjglClApi}.
 *
 * <p>These tests exist because the migration had to change <em>how</em> host data reaches the device.
 * The previous binding could hand a Java array straight to the driver, so a filter was uploaded with
 * a single {@code CL_MEM_COPY_HOST_PTR} allocation. LWJGL can only transfer from direct buffers, and
 * staging a whole payload off-heap is not an option here: the 16-bit fingerprint filter reaches
 * multiple gigabytes at the Full-DB tier, beyond what a Java {@code byte[]} can even represent. The
 * replacement writes through a small reusable staging buffer instead.
 *
 * <p>That makes chunk arithmetic — offsets, the final short chunk, element widths — load-bearing for
 * correctness of every filter upload. A payload deliberately larger than one chunk is therefore
 * round-tripped and compared byte for byte; an off-by-one in the offset would silently corrupt a
 * filter and show up only as missed addresses.
 *
 * <p>{@code @OpenCLTest}: needs a real device to write to.
 */
class LwjglClApiBufferTest {

    /**
     * Payload size in bytes, chosen to exceed the implementation's staging buffer so at least one
     * chunk boundary is crossed and the second chunk's device offset is exercised.
     */
    private static final int PAYLOAD_BYTES = 12 * 1024 * 1024;

    /** Number of 16-bit elements used for the short-payload round trip. */
    private static final int SHORT_ELEMENT_COUNT = 7 * 1024 * 1024;

    private final ClApi clApi = new LwjglClApi(
            new ClErrorChecker(new ClErrorCodeResolver()), new OpenClLibraryLoader(new LwjglClLibraryInitializer()));

    // <editor-fold defaultstate="collapsed" desc="writeBufferChunked">
    @Test
    @OpenCLTest
    void writeBufferChunked_payloadLargerThanOneChunk_roundTripsUnchanged() {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailable();

        // arrange
        final byte[] source = new byte[PAYLOAD_BYTES];
        for (int i = 0; i < source.length; i++) {
            source[i] = (byte) (i * 31);
        }

        // act
        final byte[] readBack = roundTripBytes(source);

        // assert
        assertArrayEquals(source, readBack);
    }

    @Test
    @OpenCLTest
    void writeBufferChunked_shortPayload_roundTripsInNativeByteOrder() {
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailable();

        // arrange
        final short[] source = new short[SHORT_ELEMENT_COUNT];
        for (int i = 0; i < source.length; i++) {
            source[i] = (short) (i * 7);
        }

        // act
        final short[] readBack = roundTripShorts(source);

        // assert
        assertThat(readBack.length, is(equalTo(source.length)));
        for (int i = 0; i < source.length; i++) {
            assertThat("element " + i, readBack[i], is(equalTo(source[i])));
        }
    }
    // </editor-fold>

    /**
     * Writes a byte payload to a device buffer and reads it back.
     *
     * @param source the payload to round-trip
     * @return the bytes read back from the device
     */
    private byte[] roundTripBytes(byte[] source) {
        final ClPlatformId platform = clApi.getPlatformIds().get(0);
        final List<ClDeviceId> devices = clApi.getDeviceIds(platform, CL10.CL_DEVICE_TYPE_ALL);
        final ClContext context = clApi.createContext(platform, devices.get(0));
        try {
            final ClCommandQueue queue = clApi.createCommandQueue(context, devices.get(0), false);
            try {
                final ClMem mem = clApi.createBuffer(context, ClBufferFlags.READ_WRITE, source.length);
                try {
                    clApi.writeBufferChunked(queue, mem, source);

                    final ByteBuffer destination =
                            ByteBuffer.allocateDirect(source.length).order(ByteOrder.nativeOrder());
                    clApi.enqueueReadBuffer(queue, mem, 0L, destination, false);
                    clApi.finish(queue);

                    final byte[] readBack = new byte[source.length];
                    destination.position(0);
                    destination.get(readBack);
                    return readBack;
                } finally {
                    clApi.releaseMemObject(mem);
                }
            } finally {
                clApi.releaseCommandQueue(queue);
            }
        } finally {
            clApi.releaseContext(context);
        }
    }

    /**
     * Writes a 16-bit payload to a device buffer and reads it back.
     *
     * @param source the payload to round-trip
     * @return the values read back from the device
     */
    private short[] roundTripShorts(short[] source) {
        final ClPlatformId platform = clApi.getPlatformIds().get(0);
        final List<ClDeviceId> devices = clApi.getDeviceIds(platform, CL10.CL_DEVICE_TYPE_ALL);
        final ClContext context = clApi.createContext(platform, devices.get(0));
        final long byteSize = (long) source.length * Short.BYTES;
        try {
            final ClCommandQueue queue = clApi.createCommandQueue(context, devices.get(0), false);
            try {
                final ClMem mem = clApi.createBuffer(context, ClBufferFlags.READ_WRITE, byteSize);
                try {
                    clApi.writeBufferChunked(queue, mem, source);

                    final ByteBuffer destination =
                            ByteBuffer.allocateDirect((int) byteSize).order(ByteOrder.nativeOrder());
                    clApi.enqueueReadBuffer(queue, mem, 0L, destination, false);
                    clApi.finish(queue);

                    final short[] readBack = new short[source.length];
                    destination.position(0);
                    destination.asShortBuffer().get(readBack);
                    return readBack;
                } finally {
                    clApi.releaseMemObject(mem);
                }
            } finally {
                clApi.releaseCommandQueue(queue);
            }
        } finally {
            clApi.releaseContext(context);
        }
    }
}
