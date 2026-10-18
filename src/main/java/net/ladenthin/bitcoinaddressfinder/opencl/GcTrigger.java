// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.opencl;

/**
 * Requests a garbage collection. Extracted behind an interface for one reason only: so the
 * "reclaim the heap before a large device upload" behaviour can be unit-tested by injecting a mock
 * and verifying it was invoked, without a real {@code System.gc()} (which is a hint the test could
 * not observe or assert on).
 *
 * <h2>Why this exists in the upload path</h2>
 * Immediately after building a Full-DB filter, the JVM heap still holds the filter's transient
 * construction garbage (Binary Fuse peeling peaks at ~29 B/entry ≈ 40&nbsp;GB at the
 * 1.377&nbsp;B tier). On an RX 7900 XTX that pressure made {@code clCreateBuffer} for the 3.1&nbsp;GB
 * fingerprint buffer fail with {@code CL_MEM_OBJECT_ALLOCATION_FAILURE} although VRAM was abundant:
 * 33.9&nbsp;GiB live heap failed, 2.9&nbsp;GiB after a collection succeeded, and the same command
 * passed with this collection in place. This mirrors what the JDK itself does before a large
 * direct-buffer allocation ({@code java.nio.Bits.reserveMemory} calls {@code System.gc()} for exactly
 * this native-memory-pressure reason).
 *
 * <p>That measurement was taken with the previous binding, which created the buffer with
 * {@code CL_MEM_COPY_HOST_PTR} and so had the driver pin a host staging region for the whole
 * payload. The upload now allocates a plain read-only buffer and fills it in bounded slices
 * ({@code ClApi.writeBufferChunked}); whether the driver still needs the collection there, and what
 * the collection costs on such a heap, has not been re-measured. It is kept as a safeguard until it
 * is (see {@code TODO.md}).
 */
@FunctionalInterface
public interface GcTrigger {

    /** The production trigger: a plain {@link System#gc()} hint. */
    GcTrigger SYSTEM = System::gc;

    /** Requests a garbage collection (best-effort; {@code System.gc()} is only a hint). */
    void requestGc();
}
