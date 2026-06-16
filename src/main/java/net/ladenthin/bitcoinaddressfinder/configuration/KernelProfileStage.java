// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Compile-time profiling stage for the OpenCL key-generation kernel. Selects how much of the
 * per-key pipeline the kernel actually executes, so the cost of each stage can be attributed by
 * diffing the throughput of the modes (see {@code docs/performance.md}, "Stage attribution").
 *
 * <p>This is a <b>diagnostic</b> switch, not a production tuning knob: the non-{@link #FULL} modes
 * produce <b>incorrect</b> hash160 output (they short-circuit the hashing) and exist only to measure
 * where kernel time goes. Production runs must use {@link #FULL}. Each mode maps to a
 * {@code clBuildProgram} define in {@code OpenCLContext}; the kernel guards the stages with
 * {@code #ifdef PROFILE_*}, so {@link #FULL} (no define) is byte-for-byte the normal kernel.
 */
public enum KernelProfileStage {

    /** The complete kernel: EC point generation + both (uncompressed and compressed) hash160 chains. */
    FULL,

    /**
     * EC point generation + a single hash160 chain (the compressed chain is skipped and its slot is
     * filled from the uncompressed hash). Isolates the cost of one hash160 chain when diffed against
     * {@link #FULL}.
     */
    ONE_HASH160,

    /**
     * EC point generation only; both hash160 chains are skipped and their slots are filled from the
     * public-key X coordinate (so the EC result stays live and the filter/output path still runs).
     * Isolates the EC arithmetic cost.
     */
    NO_HASH160;
}
