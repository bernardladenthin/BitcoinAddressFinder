// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Which filter the OpenCL producer probes on the device to decide what to transfer back.
 *
 * <h2>What this setting actually controls</h2>
 * The GPU filter is a <b>pre</b>-filter: it decides which generated candidates cross PCIe at all.
 * It is independent of {@code addressLookupBackend}, which decides how the <em>consumer</em>
 * checks the survivors. The two form a cascade, and because the filters hash independently, the
 * second stage rejects what the first let through — the compound false-positive rate is the
 * product, not the minimum.
 *
 * <h2>How to choose</h2>
 * The GPU has compute to spare; the consumer is single-threaded by design (to avoid LMDB
 * contention). The pre-filter's job is therefore <b>not</b> to answer quickly but to hand as
 * little as possible to that single thread. What matters is false-positive rate per VRAM byte:
 *
 * <table border="1">
 * <caption>Measured at 1e8 entries</caption>
 * <tr><th>Type</th><th>VRAM</th><th>FPR</th><th>Candidates reaching the CPU per 1e6</th></tr>
 * <tr><td>{@link #FUSE_8}</td><td>1.126 B/entry</td><td>0.387 %</td><td>3 874</td></tr>
 * <tr><td>{@link #FUSE_16}</td><td>2.252 B/entry</td><td>0.0016 %</td><td>16</td></tr>
 * </table>
 *
 * <p>{@link #FUSE_16} costs twice the VRAM and roughly a 6 % longer probe, and cuts the load on
 * the bottleneck by ~240&times;. Prefer it whenever it fits.
 *
 * <p><b>It may not fit.</b> The filter is a single OpenCL allocation, bounded by
 * {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE} rather than by total VRAM, and that bound is
 * vendor-dependent — 2047 MB of 8191 on an RTX 3070 (the spec minimum, a quarter of VRAM) against
 * 20876 MB of 24560 on an RX 7900 XTX. At 2.25 B/entry the NVIDIA limit admits ~909 M entries, so
 * the 1.377 B-entry Full DB needs {@link #FUSE_8} there and fits {@link #FUSE_16} comfortably on
 * the AMD card. Run {@code {"command":"OpenCLInfo"}} to read your device's limit.
 *
 * <h2>Why blocked Bloom is not offered here</h2>
 * It probes 1.6–2.3&times; faster on the device — all {@code k} bits sit in one coalesced 64-byte
 * transaction where a fuse lookup makes three scattered reads — but it passes ~2&times; as many
 * candidates at equal footprint, which doubles the load on the single consumer thread. Optimising
 * the term that is not the bottleneck at the cost of the term that is makes the pipeline slower,
 * measured 31.3 ms against 16.6 ms per million candidates. The device-side probe exists
 * ({@code blockedbloom_contains}) and is benchmarked; it is deliberately not wired in here.
 */
public enum GpuFilterType {

    /**
     * Binary Fuse 8 — ~1.13 B/entry, ~0.39 % false positives. The compatibility choice: it fits
     * under every device allocation limit measured so far, including the Full DB tier on an 8 GB
     * NVIDIA card.
     *
     * <p>Wire value {@code 0}; must match {@code GPU_FILTER_TYPE_FUSE8} in the OpenCL source.
     */
    FUSE_8(0),

    /**
     * Binary Fuse 16 — ~2.25 B/entry, ~0.0016 % false positives. Hands ~240&times; less work to
     * the consumer thread; prefer it when the device allocation limit has room.
     *
     * <p>Wire value {@code 1}; must match {@code GPU_FILTER_TYPE_FUSE16} in the OpenCL source.
     */
    FUSE_16(1);

    private final int wireValue;

    GpuFilterType(int wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the integer passed to the kernel as its {@code filter_type} argument.
     *
     * @return the wire value, matching the {@code GPU_FILTER_TYPE_*} defines in the OpenCL source
     */
    public int getWireValue() {
        return wireValue;
    }
}
