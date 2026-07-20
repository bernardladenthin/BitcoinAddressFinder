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
 * Uploading a multi-gigabyte fingerprint buffer with {@code CL_MEM_COPY_HOST_PTR} makes the OpenCL
 * driver pin a host-side staging region. Immediately after building a Full-DB filter, the JVM heap
 * still holds the filter's transient construction garbage (Binary Fuse peeling peaks at ~29 B/entry
 * ≈ 40&nbsp;GB at the 1.377&nbsp;B tier), and on some drivers — measured on an RX 7900 XTX — that
 * pressure makes {@code clCreateBuffer} fail with {@code CL_MEM_OBJECT_ALLOCATION_FAILURE} even
 * though VRAM is abundant. Reclaiming the heap first removes the failure. This mirrors what the JDK
 * itself does before a large direct-buffer allocation ({@code java.nio.Bits.reserveMemory} calls
 * {@code System.gc()} for exactly this native-memory-pressure reason).
 */
@FunctionalInterface
public interface GcTrigger {

    /** The production trigger: a plain {@link System#gc()} hint. */
    GcTrigger SYSTEM = System::gc;

    /** Requests a garbage collection (best-effort; {@code System.gc()} is only a hint). */
    void requestGc();
}
