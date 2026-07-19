// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for the {@code TuneConfiguration} command.
 *
 * <p>The tuner sweeps producer parameters and reports which combination actually moves the most
 * candidates per second through the whole pipeline on this machine.
 */
@ToString
@EqualsAndHashCode
public class CTuneConfiguration {

    /** Creates a new {@link CTuneConfiguration}. */
    public CTuneConfiguration() {}

    /**
     * The base configuration the sweep varies.
     *
     * <p>Deliberately the same {@link CFinder} type a real {@code Find} run takes, rather than a
     * parallel set of tuner-only fields. Every setting that is <em>not</em> swept — device indices,
     * {@code useSafeGcdInverse}, {@code maxResultReaderThreads}, the consumer's thread count, the
     * key producer — is therefore carried through unchanged, so the configuration that wins here is
     * the configuration the user then runs. A tuner with its own config shape would silently
     * measure a pipeline the user never executes.
     *
     * <p>The first {@code producerOpenCL} entry (or, when none is configured or no OpenCL device is
     * present, the first {@code producerJava} entry) supplies the template whose
     * {@code batchSizeInBits} / {@code keysPerWorkItem} the sweep overrides per arm.
     *
     * <p>{@code consumerJava.lmdbConfigurationReadOnly.lmdbDirectory} is optional here and is used
     * <b>only</b> by the verification-cost stage. The grid sweep never touches a database — see
     * {@link #targetDatabaseEntries}.
     */
    public @Nullable CFinder finder;

    /**
     * Measurement window per arm, in seconds.
     *
     * <p>Longer is steadier: the quantity being measured is a steady-state rate, and the arm's
     * first moments are contaminated by queue fill and clock ramp. 20 s was chosen as the point
     * where a GPU arm's rate stopped drifting between repeats; below ~5 s the ordering of adjacent
     * candidates is not reliably reproducible.
     */
    public int secondsPerArm = 20;

    /**
     * Discarded settling window before each arm's measurement window, in seconds.
     *
     * <p>Covers OpenCL kernel compilation for the new grid geometry, the first batches filling the
     * consumer queue, and GPU clock ramp. {@code 0} is permitted (the unit test uses it) but then
     * the first arm measured after a device change absorbs the ramp and reads low.
     */
    public int warmupSecondsPerArm = 5;

    /**
     * Number of addresses the synthetic filter is built for.
     *
     * <p><b>State the size of the database you intend to run against, not one you happen to have.</b>
     * The sweep builds its GPU pre-filter from a deterministic PRNG source rather than from LMDB,
     * because for grid throughput the filter's <em>contents</em> are irrelevant — only its size
     * (VRAM occupancy and probe bandwidth) and its false-positive rate (how many candidates cross
     * PCIe) affect timing. Decoupling from storage removes a multi-minute LMDB walk and lets the
     * command run with no database present at all.
     *
     * <p>Default is the Light DB tier (132 288 304 entries). The Full DB tier is 1 377 000 000.
     * Building the filter costs one multi-pass fuse peel at roughly 29 B/entry regardless of where
     * the addresses come from (~44 s per 100 M entries measured storage-free), which is why the
     * filter is built once and only the producer is restarted per arm.
     */
    public long targetDatabaseEntries = 132_288_304L;

    /**
     * Candidate {@code batchSizeInBits} values to sweep.
     *
     * <p>Each candidate is crossed with every {@link #keysPerWorkItemCandidates} entry, so the arm
     * count — and the run time, {@code arms × (warmupSecondsPerArm + secondsPerArm)} — is the
     * product of the two list lengths.
     */
    public List<Integer> batchSizeInBitsCandidates = new ArrayList<>(List.of(18, 19, 20, 21, 22));

    /**
     * Candidate {@code keysPerWorkItem} values to sweep. Must be powers of two.
     *
     * <p>Inert for CPU producers, which have no work-items; when the sweep degrades to a CPU
     * producer these candidates collapse to repeated measurements of the same arm and the report
     * says so.
     */
    public List<Integer> keysPerWorkItemCandidates = new ArrayList<>(List.of(1, 4, 16, 64, 256));

    /**
     * Whether to measure the {@code FUSE_8} / {@code FUSE_16} choice empirically instead of
     * deriving it.
     *
     * <p><b>Opt-in because it forces a second full filter build.</b> Switching {@code gpuFilterType}
     * changes the fingerprint width, so the filter cannot be reinterpreted and must be rebuilt from
     * scratch: roughly 150 s at the 132 M Light DB tier and 1 564 s (~26 min) at the 1.377 B Full DB
     * tier, on top of the sweep itself.
     *
     * <p>Left {@code false}, the recommendation is instead derived from the measured verification
     * cost and the two filters' documented, machine-independent false-positive rates
     * ({@code FUSE_8} 0.003874, {@code FUSE_16} 0.000016) via
     * {@code total = probe + fpr × verificationCost}. That derivation is cheap and, unlike
     * throughput, rests only on quantities that do not vary by host.
     */
    public boolean sweepFilterTypes = false;

    /**
     * Number of random non-member lookups used to measure verification cost.
     *
     * <p>Non-members on purpose: verification cost is what a filter <em>false positive</em> makes
     * the consumer pay, and a false positive is by definition an address that is not in the
     * database, so a member lookup would measure the wrong path. Ignored when no database is
     * configured.
     */
    public int verificationCostSamples = 2_000;
}
