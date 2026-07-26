// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CCommand;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.CTuneConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.GpuFilterType;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.consumer.Consumer;
import net.ladenthin.bitcoinaddressfinder.consumer.ConsumerJava;
import net.ladenthin.bitcoinaddressfinder.core.FireAndForget;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.engine.Finder;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLBuilder;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDevice;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLDeviceTopology;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLPlatform;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.PrngAddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse16GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8AddressPresence;
import net.ladenthin.bitcoinaddressfinder.persistence.inmemory.BinaryFuse8GpuFilterData;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.producer.Producer;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerJava;
import net.ladenthin.bitcoinaddressfinder.producer.ProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.MultilineLogger;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.jocl.CL;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures end-to-end pipeline throughput on the operator's own machine, sweeps the tunable
 * producer parameters, and prints the winning configuration as a ready-to-paste JSON fragment.
 *
 * <h2>Why this command exists</h2>
 * Every filter and grid recommendation in {@code docs/filter-selection.md} is built from
 * <b>component</b> measurements composed arithmetically: filter probe latency, false-positive rate,
 * verification cost. Each term was measured carefully in isolation and the total was then derived —
 * {@code total = probe + fpr × verification}. What no run has ever done is check the composed
 * prediction against what the assembled pipeline actually delivers. This command closes that gap.
 * It also removes the second, quieter problem with those numbers: they were measured on one
 * developer's RTX 3070 against one NVMe. Verification cost alone ranges from 4.1 µs warm to
 * 292.7 µs cold — a 70× spread that no published table can stand in for. Rather than asking users
 * to trust hardware they do not own, this lets them measure their own.
 *
 * <h2>Why the grid sweep touches no database</h2>
 * The sweep builds its pre-filter from {@link PrngAddressIterable} rather than from LMDB, and the
 * consumer is given a lookup that never matches. This is not a shortcut, it is what the measurement
 * calls for. Grid throughput is dominated by EC point multiplication (~57 %) and hashing (~43 %) on
 * the device; the consumer only ever sees filter survivors, a fraction of a percent of candidates.
 * What the filter <em>contains</em> therefore cannot affect timing — only its <b>size</b> (VRAM
 * occupancy, probe bandwidth) and its <b>false-positive rate</b> (how many candidates cross PCIe)
 * can, and a synthetic source reproduces both exactly. Decoupling from storage removes a multi-pass
 * LMDB walk whose cost is page-cache noise rather than pipeline behaviour, and lets the command run
 * on a machine where the database has not been imported yet. The filter is sized to
 * {@code targetDatabaseEntries} — the database the operator <em>intends</em> to run against.
 *
 * <p><b>An assumption this command does not verify:</b> whether the optimal
 * {@code batchSizeInBits} / {@code keysPerWorkItem} depends on filter size at all. It is plausible
 * that it does not, since the filter probe is a small tail in a kernel dominated by EC arithmetic —
 * but that has not been measured, here or anywhere else in this project. The filter is sized to the
 * user's real target precisely so the question does not have to be answered: if the optimum does
 * turn out to be size-dependent, the sweep was run at the size that matters.
 *
 * <h2>Why the filter is built once and only the producer restarts</h2>
 * A Binary Fuse build is a multi-pass peeling algorithm at roughly 29 B/entry — ~44 s per 100 M
 * entries, storage or no storage. Rebuilding it per arm would dominate a sweep whose arms are
 * 20 s each and make the command unusable at realistic database sizes. So the consumer (which owns
 * the lookup) and the filter payload are created once, and each arm creates, initialises, starts
 * and stops only the <b>producer</b>.
 *
 * <h2>What is measured versus what is documented</h2>
 * The report labels every number. Throughput and verification cost are measured here. The two
 * false-positive rates are documented constants ({@link #FUSE_8_FALSE_POSITIVE_RATE},
 * {@link #FUSE_16_FALSE_POSITIVE_RATE}) — they are properties of the filter construction, identical
 * on every machine, so measuring them locally would add noise rather than information.
 *
 * @see net.ladenthin.bitcoinaddressfinder.configuration.CTuneConfiguration
 */
@ToString
public class TuneConfiguration implements Runnable, Interruptable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TuneConfiguration.class);

    /**
     * Documented false-positive rate of a Binary Fuse 8 filter (0.3874 %).
     *
     * <p>A property of the construction — 8-bit fingerprints, 3-wise peeling — not of the host, so
     * it is a constant here rather than something the tuner measures. See
     * {@code docs/filter-selection.md}.
     */
    public static final double FUSE_8_FALSE_POSITIVE_RATE = 0.003874d;

    /** Documented false-positive rate of a Binary Fuse 16 filter (0.0016 %). See {@code docs/filter-selection.md}. */
    public static final double FUSE_16_FALSE_POSITIVE_RATE = 0.000016d;

    /** Documented mean probe latency of a Binary Fuse 8 filter, in nanoseconds. */
    public static final double FUSE_8_PROBE_NANOS = 76.7d;

    /** Documented mean probe latency of a Binary Fuse 16 filter, in nanoseconds. */
    public static final double FUSE_16_PROBE_NANOS = 80.4d;

    /**
     * Documented verification cost against a <b>warm</b> page cache, in microseconds. Used as the
     * fallback for the filter recommendation when no database is configured to measure.
     */
    public static final double VERIFICATION_COST_WARM_MICROS = 4.1d;

    /** Documented verification cost against a <b>cold</b> page cache, in microseconds. */
    public static final double VERIFICATION_COST_COLD_MICROS = 292.7d;

    /**
     * Seed for the synthetic filter's member set. Any fixed value works; it is pinned so two runs
     * of the tuner build a bit-for-bit identical filter and their arms stay comparable.
     */
    private static final long FILTER_SEED = 0x5EED_F117_E12A_0001L;

    /**
     * Seed for the verification-cost probe set. Deliberately different from {@link #FILTER_SEED} so
     * the probes are non-members: verification cost is the price of a false positive, and a false
     * positive is by construction an address the database does not hold.
     */
    private static final long PROBE_SEED = 0x5EED_F117_E12A_0002L;

    private final CTuneConfiguration cTuneConfiguration;

    /** Cancellation flag; cleared by {@link #interrupt()}. Uninformative in an aggregate toString. */
    @ToString.Exclude
    private final AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * Counted down by {@link #interrupt()}. Every wait in this command is an
     * {@link CountDownLatch#await(long, TimeUnit)} on this latch rather than a sleep, so a
     * shutdown request cuts the current warmup or measurement window short immediately instead of
     * being noticed only at the end of it.
     */
    @ToString.Exclude
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    /** One entry per swept candidate pair, in sweep order. */
    @ToString.Exclude
    private final List<ArmResult> results = new ArrayList<>();

    /**
     * Per-device breakdown of {@link #results}, one entry per swept device in measurement order.
     *
     * <p>{@link #results} stays the flat list of every arm and {@link #winner} the best arm overall;
     * this is what a multi-device run needs on top, because the settings a device should run with
     * are its own optimum, not the fastest device's.
     */
    private final List<DeviceSweep> deviceSweeps = new ArrayList<>();

    private @Nullable ArmResult winner;

    /** Measured mean verification cost in microseconds, or {@code null} when no database was configured. */
    private @Nullable Double measuredVerificationCostMicros;

    private @Nullable GpuFilterType recommendedFilterType;

    private String recommendedConfigurationJson = "";

    /**
     * Creates a new tuner.
     *
     * @param cTuneConfiguration the tuning configuration, carrying the base {@link CFinder}
     */
    public TuneConfiguration(CTuneConfiguration cTuneConfiguration) {
        this.cTuneConfiguration = cTuneConfiguration;
    }

    /**
     * Result of one swept candidate pair.
     *
     * <p>{@link #candidatesPerSecond()} is the headline figure and the one the winner is chosen on:
     * candidate keys pushed through the whole pipeline per second, derived from the producers' own
     * dispatched-batch counter in {@link RuntimeStatistics} times the grid size. It is reported
     * rather than {@link #addressesCheckedPerSecond()} because in GPU compact mode the consumer
     * only ever sees filter survivors — a fraction of a percent — so the consumer-side odometer
     * measures the filter's selectivity, not the pipeline's rate.
     *
     * @param batchSizeInBits          the swept grid size, {@code 2^batchSizeInBits} candidates per batch
     * @param keysPerWorkItem          the swept inner-loop count; inert for CPU producers
     * @param candidatesPerSecond      candidate keys per second through the full pipeline (measured)
     * @param addressesCheckedPerSecond address lookups per second reaching the consumer (measured);
     *                                 two per candidate at full transfer, far fewer in compact mode
     * @param elapsedSeconds           the actual measurement window, which may be short if interrupted
     * @param failure                  a short description when the arm did not run, otherwise {@code null}
     */
    public record ArmResult(
            int batchSizeInBits,
            int keysPerWorkItem,
            double candidatesPerSecond,
            double addressesCheckedPerSecond,
            double elapsedSeconds,
            @Nullable String failure) {

        /**
         * Returns whether this arm produced a usable measurement.
         *
         * @return {@code true} if the arm ran and moved at least one candidate
         */
        public boolean succeeded() {
            return failure == null && candidatesPerSecond > 0.0d;
        }

        /**
         * Returns whether this arm ran without error but never completed a batch inside its
         * measurement window.
         *
         * <p>Distinct from a {@link #failure()}: nothing was rejected, the grid is simply so large
         * relative to this device's speed that one launch outlasts {@code secondsPerArm}. Seen at
         * large {@code batchSizeInBits} combined with tiny {@code keysPerWorkItem} — on an
         * integrated GPU, {@code batchSizeInBits=22, keysPerWorkItem=1} is ~4.2 M candidates at
         * ~105 k/s, about 40 s against a 20 s window. Reported separately because a bare
         * {@code 0.00} is indistinguishable from a driver rejection at a glance, and the two call
         * for different responses.
         *
         * @return {@code true} if the arm produced no throughput yet did not fail
         */
        public boolean tooSlowToMeasure() {
            return failure == null && candidatesPerSecond <= 0.0d;
        }
    }

    /**
     * One device's slice of the sweep: every arm measured on it, and its own winner.
     *
     * @param deviceLabel how the device is identified in the report
     * @param arms        the arms measured on this device, in measurement order
     * @param winner      the best arm on this device, or {@code null} if none succeeded
     */
    public record DeviceSweep(
            String deviceLabel,
            List<ArmResult> arms,
            @Nullable ArmResult winner) {}

    /**
     * Returns the per-arm results, one entry per swept candidate pair, in sweep order.
     *
     * @return an unmodifiable snapshot of the arm results
     */
    public List<ArmResult> getResults() {
        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    /**
     * Returns the per-device breakdown, one entry per swept device in measurement order.
     *
     * @return each device's arms and its own winner
     */
    public List<DeviceSweep> getDeviceSweeps() {
        return Collections.unmodifiableList(deviceSweeps);
    }

    /**
     * Returns the fastest arm.
     *
     * @return the winning arm, or {@code null} if no arm produced a usable measurement
     */
    public @Nullable ArmResult getWinner() {
        return winner;
    }

    /**
     * Returns the verification cost measured against the configured database.
     *
     * @return the mean cost of one non-member lookup in microseconds, or {@code null} when no
     *     database was configured and the documented values were used instead
     */
    public @Nullable Double getMeasuredVerificationCostMicros() {
        return measuredVerificationCostMicros;
    }

    /**
     * Returns the recommended GPU pre-filter width.
     *
     * @return the recommended filter type, or {@code null} if the run did not reach that stage
     */
    public @Nullable GpuFilterType getRecommendedFilterType() {
        return recommendedFilterType;
    }

    /**
     * Returns the winning configuration as a JSON document.
     *
     * <p>A complete {@link CConfiguration} with {@code command = Find}, not a fragment of one, so
     * it can be saved and run directly.
     *
     * @return the recommended configuration as JSON, or an empty string if the run did not complete
     */
    public String getRecommendedConfigurationJson() {
        return recommendedConfigurationJson;
    }

    @Override
    public void run() {
        CFinder cFinder = Objects.requireNonNull(
                cTuneConfiguration.finder, "tuneConfiguration.finder is required: it is the configuration being tuned");
        CConsumerJava cConsumerJava =
                Objects.requireNonNull(cFinder.consumerJava, "tuneConfiguration.finder.consumerJava is required");

        // Stage 1 runs first, before anything else has touched the page cache: the point of this
        // number is the storage's state as the operator will actually meet it, and building a
        // 100 M-entry filter beforehand would evict exactly the pages being measured.
        measuredVerificationCostMicros = measureVerificationCostMicros(cConsumerJava);

        List<CProducer> templates = resolveTemplates(cFinder);
        RuntimeStatistics runtimeStatistics = new RuntimeStatistics();
        KeyUtility keyUtility = keyUtility();
        BitHelper bitHelper = new BitHelper();

        ConsumerJava consumer = new ConsumerJava(
                cConsumerJava,
                keyUtility,
                new PersistenceUtils(new NetworkParameterFactory().getNetwork()),
                runtimeStatistics);
        // The scan hot path needs *a* lookup, but not a truthful one — see NEVER_PRESENT.
        consumer.initWithLookup(NEVER_PRESENT);
        consumer.startConsumer();

        // Finder is used for exactly one thing: instantiating and registering the configured key
        // producers. Its consumer/producer lifecycle is not reusable here (startConsumer() is bound
        // to initLMDB(), and its one-shot GPU payload upload discards the payload after the first
        // producer generation), but the key-producer wiring is, and duplicating that six-strategy
        // dispatch would be the one part of this command guaranteed to drift out of sync.
        Finder finder = new Finder(cFinder);
        finder.startKeyProducer();

        ExecutorService producerExecutor = Executors.newCachedThreadPool();
        try {
            // Built once and reused across every device: the filter is the expensive part (~44 s per
            // 100 M entries), the sweep is the part that has to be repeated per device.
            FilterPayload payload = buildFilterPayload(templates.get(0));
            if (templates.size() > 1) {
                LOGGER.info(
                        "Sweeping {} devices in turn, {} arms each — roughly {} minutes in total. Devices are"
                                + " measured one at a time on purpose: two running at once contend for host memory"
                                + " bandwidth, and an integrated GPU can lose several times its solo throughput"
                                + " that way.",
                        templates.size(),
                        cTuneConfiguration.batchSizeInBitsCandidates.size()
                                * cTuneConfiguration.keysPerWorkItemCandidates.size(),
                        Math.round(templates.size()
                                * cTuneConfiguration.batchSizeInBitsCandidates.size()
                                * cTuneConfiguration.keysPerWorkItemCandidates.size()
                                * (cTuneConfiguration.warmupSecondsPerArm + cTuneConfiguration.secondsPerArm)
                                / 60.0d));
            }
            for (CProducer template : templates) {
                if (!shouldRun.get()) {
                    break;
                }
                int armsBefore = results.size();
                sweepGrid(
                        template,
                        finder,
                        consumer,
                        runtimeStatistics,
                        producerExecutor,
                        payload,
                        keyUtility,
                        bitHelper);
                List<ArmResult> deviceArms = new ArrayList<>(results.subList(armsBefore, results.size()));
                ArmResult deviceWinner = pickWinner(deviceArms);
                deviceSweeps.add(new DeviceSweep(describeTemplateDevice(template), deviceArms, deviceWinner));
                // Each device keeps its own optimum. Picking one winner across devices would hand the
                // slower card the faster card's settings — on a contributor's laptop that would have
                // been 24/2048 for a device whose own optimum was 23/2048.
                applyWinnerTo(template, deviceWinner);
            }
            winner = pickWinner(results);
            recommendedFilterType = resolveFilterType(
                    templates.get(0), finder, consumer, runtimeStatistics, producerExecutor, keyUtility, bitHelper);
            recommendedConfigurationJson = buildRecommendedConfigurationJson(cFinder, templates);
            // One record per line: the report is a formatted table, and the log pattern's CRLF guard
            // would otherwise fold all 40-odd rows onto one line separated by " | ".
            new MultilineLogger().info(LOGGER, buildReport(templates.get(0)));
        } finally {
            finder.interrupt();
            consumer.interrupt();
            producerExecutor.shutdownNow();
        }
    }

    // -----------------------------------------------------------------------------------------
    // Stage 1 — verification cost
    // -----------------------------------------------------------------------------------------

    /**
     * Measures what one LMDB lookup costs on the configured database in its current page-cache
     * state, or returns {@code null} when no database is configured.
     *
     * <p>This is the term that varies most between deployments (4.1 µs warm to 292.7 µs cold) and
     * the one the filter recommendation is most sensitive to, which is why it is measured rather
     * than assumed whenever a database is available to measure it on.
     *
     * @param cConsumerJava the consumer configuration carrying the read-only LMDB settings
     * @return the mean cost of one non-member lookup in microseconds, or {@code null} if no
     *     database directory is configured or it does not exist
     */
    private @Nullable Double measureVerificationCostMicros(CConsumerJava cConsumerJava) {
        CLMDBConfigurationReadOnly configured = cConsumerJava.lmdbConfigurationReadOnly;
        String directory = configured.lmdbDirectory;
        if (directory.isBlank() || !new File(directory).isDirectory()) {
            LOGGER.info(
                    "No LMDB database configured (lmdbDirectory='{}'), skipping the verification-cost measurement. "
                            + "The filter recommendation will use the documented values instead and the report will "
                            + "mark them as ESTIMATED.",
                    directory);
            return null;
        }

        int samples = Math.max(1, cTuneConfiguration.verificationCostSamples);
        // A defensive copy rather than the user's object: disableAddressLookup would short-circuit
        // containsAddress() to false without touching storage, which would "measure" a lookup that
        // never happened, and the stats logging is noise in the middle of a timing loop.
        CLMDBConfigurationReadOnly probeConfiguration = new CLMDBConfigurationReadOnly();
        probeConfiguration.lmdbDirectory = directory;
        probeConfiguration.useProxyOptimal = configured.useProxyOptimal;
        probeConfiguration.useNoReadAhead = configured.useNoReadAhead;
        probeConfiguration.disableAddressLookup = false;
        probeConfiguration.logStatsOnInit = false;
        probeConfiguration.logStatsOnClose = false;

        PersistenceUtils persistenceUtils = new PersistenceUtils(new NetworkParameterFactory().getNetwork());
        try (LMDBPersistence lmdb = new LMDBPersistence(probeConfiguration, persistenceUtils)) {
            lmdb.init();
            LOGGER.info("Measuring verification cost with {} non-member lookups against {} ...", samples, directory);
            long totalNanos = 0L;
            // One direct buffer, refilled per probe — LMDB requires a direct buffer, and reusing it
            // mirrors the consumer's own thread-local buffer so the measurement excludes an
            // allocation the real hot path does not pay either.
            ByteBuffer probe = ByteBuffer.allocateDirect(PrngAddressIterable.BYTES_PER_ADDRESS);
            for (int i = 0; i < samples; i++) {
                probe.clear();
                probe.put(PrngAddressIterable.addressAt(PROBE_SEED, i));
                probe.flip();
                long before = System.nanoTime();
                // The return value is intentionally discarded: the probe seed guarantees a non-member,
                // so what is being timed is the cost of the lookup itself, not its boolean answer.
                lmdb.containsAddress(probe);
                totalNanos += System.nanoTime() - before;
            }
            double micros = totalNanos / (samples * 1_000.0d);
            LOGGER.info("Measured verification cost: {} us per lookup (mean of {}).", format(micros), samples);
            return micros;
        }
    }

    // -----------------------------------------------------------------------------------------
    // Stage 2 — grid sweep
    // -----------------------------------------------------------------------------------------

    private void sweepGrid(
            CProducer template,
            Finder finder,
            ConsumerJava consumer,
            RuntimeStatistics runtimeStatistics,
            ExecutorService producerExecutor,
            FilterPayload payload,
            KeyUtility keyUtility,
            BitHelper bitHelper) {
        List<Integer> batchCandidates = cTuneConfiguration.batchSizeInBitsCandidates;
        List<Integer> keysPerWorkItemCandidates = cTuneConfiguration.keysPerWorkItemCandidates;
        int armCount = batchCandidates.size() * keysPerWorkItemCandidates.size();
        LOGGER.info(
                "Sweeping {} arms ({} batchSizeInBits × {} keysPerWorkItem), {} s warmup + {} s measurement each.",
                armCount,
                batchCandidates.size(),
                keysPerWorkItemCandidates.size(),
                cTuneConfiguration.warmupSecondsPerArm,
                cTuneConfiguration.secondsPerArm);

        int armIndex = 0;
        for (int batchSizeInBits : batchCandidates) {
            for (int keysPerWorkItem : keysPerWorkItemCandidates) {
                armIndex++;
                if (!shouldRun.get()) {
                    LOGGER.warn("Sweep interrupted after {} of {} arms.", armIndex - 1, armCount);
                    return;
                }
                ArmResult result = runArm(
                        batchSizeInBits,
                        keysPerWorkItem,
                        template,
                        finder,
                        consumer,
                        runtimeStatistics,
                        producerExecutor,
                        payload,
                        keyUtility,
                        bitHelper);
                results.add(result);
                LOGGER.info("Arm {}/{}: {}", armIndex, armCount, describeArm(result));
            }
        }

        extendSweepBeyondTheCandidates(
                template,
                finder,
                consumer,
                runtimeStatistics,
                producerExecutor,
                payload,
                keyUtility,
                bitHelper,
                Collections.max(batchCandidates),
                Collections.max(keysPerWorkItemCandidates));
    }

    /**
     * Keeps measuring past the configured candidates while the winner sits on the edge of what was
     * swept, so a truncated list yields the real optimum instead of a lower bound labelled "Winner".
     *
     * <p>See {@link #nextExtensionArm} for which edges are worth continuing past and which are the
     * end of the road. The loop stops as soon as an extra arm fails to beat the winner, so the usual
     * cost is one wasted arm rather than a second sweep; {@code maxExtensionArms} bounds the
     * pathological case where every step keeps improving.
     */
    private void extendSweepBeyondTheCandidates(
            CProducer template,
            Finder finder,
            ConsumerJava consumer,
            RuntimeStatistics runtimeStatistics,
            ExecutorService producerExecutor,
            FilterPayload payload,
            KeyUtility keyUtility,
            BitHelper bitHelper,
            int largestSweptBatchSizeInBits,
            int largestSweptKeysPerWorkItem) {
        if (!cTuneConfiguration.extendSweepWhenWinnerIsAtTheEdge) {
            return;
        }
        boolean openCl = template instanceof CProducerOpenCL;
        int largestBatchSizeInBits = largestSweptBatchSizeInBits;
        int largestKeysPerWorkItem = largestSweptKeysPerWorkItem;

        for (int extension = 1; extension <= cTuneConfiguration.maxExtensionArms; extension++) {
            if (!shouldRun.get()) {
                return;
            }
            ArmResult currentWinner = pickWinner(results);
            if (currentWinner == null) {
                return;
            }
            SweepExtension next =
                    nextExtensionArm(currentWinner, largestKeysPerWorkItem, largestBatchSizeInBits, openCl);
            if (next == null) {
                return;
            }
            LOGGER.info(
                    "Winner is at the largest {} swept ({}); continuing with {}={} rather than reporting a lower"
                            + " bound. Extension arm {}/{}.",
                    next.axis(),
                    "keysPerWorkItem".equals(next.axis()) ? largestKeysPerWorkItem : largestBatchSizeInBits,
                    next.axis(),
                    "keysPerWorkItem".equals(next.axis()) ? next.keysPerWorkItem() : next.batchSizeInBits(),
                    extension,
                    cTuneConfiguration.maxExtensionArms);

            ArmResult result = runArm(
                    next.batchSizeInBits(),
                    next.keysPerWorkItem(),
                    template,
                    finder,
                    consumer,
                    runtimeStatistics,
                    producerExecutor,
                    payload,
                    keyUtility,
                    bitHelper);
            results.add(result);
            LOGGER.info("Extension arm: {}", describeArm(result));

            largestBatchSizeInBits = Math.max(largestBatchSizeInBits, next.batchSizeInBits());
            largestKeysPerWorkItem = Math.max(largestKeysPerWorkItem, next.keysPerWorkItem());

            if (!result.succeeded() || result.candidatesPerSecond() <= currentWinner.candidatesPerSecond()) {
                LOGGER.info("Extension stopped: the extra arm did not beat the winner. The optimum was inside range.");
                return;
            }
        }
        LOGGER.info(
                "Extension stopped after {} arms (maxExtensionArms). The winner may still be a lower bound;"
                        + " raise maxExtensionArms or widen the candidate lists to look further.",
                cTuneConfiguration.maxExtensionArms);
    }

    /**
     * Runs a single arm: build, init, start, measure, stop — only the producer.
     *
     * <p>The consumer, its lookup and the filter payload outlive every arm; recreating them per arm
     * is what would make the sweep unusable.
     *
     * @return the arm's result, carrying a failure description instead of a rate when it did not run
     */
    @FireAndForget("the arm's producer is stopped explicitly below via interrupt/waitTillProducerNotRunning")
    private ArmResult runArm(
            int batchSizeInBits,
            int keysPerWorkItem,
            CProducer template,
            Finder finder,
            ConsumerJava consumer,
            RuntimeStatistics runtimeStatistics,
            ExecutorService producerExecutor,
            FilterPayload payload,
            KeyUtility keyUtility,
            BitHelper bitHelper) {
        // The template is mutated in place rather than deep-copied: arms are strictly sequential,
        // each producer is created after the mutation and released before the next, and the object
        // is the very one serialised into the recommendation at the end.
        template.batchSizeInBits = batchSizeInBits;
        if (template instanceof CProducerOpenCL cProducerOpenCL) {
            cProducerOpenCL.keysPerWorkItem = keysPerWorkItem;
        }

        @Nullable Producer producer = null;
        try {
            bitHelper.assertBatchSizeInBitsIsInRange(batchSizeInBits);
            KeyProducer keyProducer = finder.getKeyProducer(template);
            producer =
                    createProducer(template, consumer, keyUtility, keyProducer, bitHelper, runtimeStatistics, payload);
            producer.initProducer();
            // Fire-and-forget: the producer is a Runnable stopped explicitly in the finally block
            // (interrupt + waitTillProducerNotRunning), so execute() (void) is used rather than
            // submit() — there is no Future to observe.
            producerExecutor.execute(producer);

            awaitSeconds(cTuneConfiguration.warmupSecondsPerArm);

            long batchesBefore = totalBatches(runtimeStatistics);
            long checkedBefore = consumer.getCheckedKeys();
            long startNanos = System.nanoTime();
            awaitSeconds(cTuneConfiguration.secondsPerArm);
            long elapsedNanos = System.nanoTime() - startNanos;
            long batchesDelta = totalBatches(runtimeStatistics) - batchesBefore;
            long checkedDelta = consumer.getCheckedKeys() - checkedBefore;

            double elapsedSeconds = elapsedNanos / 1_000_000_000.0d;
            double candidatesPerSecond =
                    elapsedSeconds > 0.0d ? batchesDelta * (double) (1L << batchSizeInBits) / elapsedSeconds : 0.0d;
            double addressesCheckedPerSecond = elapsedSeconds > 0.0d ? checkedDelta / elapsedSeconds : 0.0d;
            // The producer runs on another thread and never propagates its failures: a fatal one
            // ends its loop quietly, and a per-secret one is logged and skipped while the producer
            // stays alive. Either way this thread sees only a rate. Ask the producer instead of
            // trusting the number.
            return armResultFor(
                    batchSizeInBits,
                    keysPerWorkItem,
                    candidatesPerSecond,
                    addressesCheckedPerSecond,
                    elapsedSeconds,
                    producer.getLastFailure());
        } catch (Exception | OutOfMemoryError e) {
            // A grid that is too large for the device is an expected outcome, not a fatal one: the
            // sweep deliberately probes high batchSizeInBits values up to the framework cap
            // BIT_COUNT_FOR_MAX_CHUNKS_ARRAY (24), whose output buffer scales as 2^bits (~1.8 GB at
            // 24) and can exceed what a smaller card allocates. Such an arm must be recorded as
            // unusable and the sweep must continue to the next candidate.
            //
            // The device rejection surfaces as a JOCL CLException — a RuntimeException carrying a
            // status such as CL_INVALID_BUFFER_SIZE / CL_MEM_OBJECT_ALLOCATION_FAILURE — which
            // catch (Exception) already covers. This was confirmed on real hardware, not assumed:
            // OpenCLContextAllocationFailureTest requests an impossible allocation and asserts the
            // thrown type is a CLException, never an OutOfMemoryError. OutOfMemoryError is caught too
            // only as belt-and-suspenders in case a host-side staging allocation runs out first; an
            // OOM from a failed native/device allocation leaves the JVM heap intact, so continuing is
            // safe. releaseProducer in the finally frees whatever was allocated.
            LOGGER.warn(
                    "Arm batchSizeInBits={} keysPerWorkItem={} failed (likely too large for this device); "
                            + "recording it as unusable and continuing.",
                    batchSizeInBits,
                    keysPerWorkItem,
                    e);
            return new ArmResult(
                    batchSizeInBits,
                    keysPerWorkItem,
                    0.0d,
                    0.0d,
                    0.0d,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (producer != null) {
                producer.interrupt();
                producer.waitTillProducerNotRunning();
                producer.releaseProducer();
            }
        }
    }

    /**
     * Builds an arm's result, downgrading it to a failure when the producer reported any failure
     * during the measurement window.
     *
     * <p>A rate measured while the device was throwing errors is not a measurement of anything, and
     * printing it beside healthy arms actively misleads: a real sweep on an Intel Arc recorded
     * {@code 1,022,288 candidates/s} for an arm during which the producer hit
     * {@code CL_OUT_OF_RESOURCES} 40 times. It never died — each failure was caught per secret and
     * skipped — so nothing in that row said so, and it was the arm immediately before every larger
     * grid began failing. The one number that would have explained the cascade was the one presented
     * as normal.
     *
     * <p>The partial rate is deliberately discarded rather than reported alongside the failure.
     * Keeping it would invite exactly the comparison it cannot support, since how much of the window
     * was productive is unknown.
     *
     * @param batchSizeInBits            the arm's grid size exponent
     * @param keysPerWorkItem            the arm's keys per work item
     * @param candidatesPerSecond        the measured candidate rate, used only if the producer survived
     * @param addressesCheckedPerSecond  the measured lookup rate, used only if the producer survived
     * @param elapsedSeconds             the measured window length
     * @param producerFailure            the producer's last failure, or {@code null} if it hit none
     * @return a measured result, or a failed one carrying the producer's cause
     */
    static ArmResult armResultFor(
            int batchSizeInBits,
            int keysPerWorkItem,
            double candidatesPerSecond,
            double addressesCheckedPerSecond,
            double elapsedSeconds,
            @Nullable Throwable producerFailure) {
        if (producerFailure != null) {
            return new ArmResult(
                    batchSizeInBits,
                    keysPerWorkItem,
                    0.0d,
                    0.0d,
                    elapsedSeconds,
                    producerFailure.getClass().getSimpleName() + ": " + producerFailure.getMessage());
        }
        return new ArmResult(
                batchSizeInBits, keysPerWorkItem, candidatesPerSecond, addressesCheckedPerSecond, elapsedSeconds, null);
    }

    private static Producer createProducer(
            CProducer template,
            Consumer consumer,
            KeyUtility keyUtility,
            KeyProducer keyProducer,
            BitHelper bitHelper,
            RuntimeStatistics runtimeStatistics,
            FilterPayload payload) {
        if (template instanceof CProducerOpenCL cProducerOpenCL) {
            ProducerOpenCL producerOpenCL = new ProducerOpenCL(
                    cProducerOpenCL, consumer, keyUtility, keyProducer, bitHelper, runtimeStatistics);
            payload.stageOn(producerOpenCL);
            return producerOpenCL;
        }
        if (template instanceof CProducerJava cProducerJava) {
            return new ProducerJava(cProducerJava, consumer, keyUtility, keyProducer, bitHelper, runtimeStatistics);
        }
        throw new IllegalArgumentException("Unsupported producer template type for the sweep: "
                + template.getClass().getName() + "; expected a CProducerOpenCL or CProducerJava.");
    }

    private static long totalBatches(RuntimeStatistics runtimeStatistics) {
        return runtimeStatistics.batchesByProducerSnapshot().values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private static @Nullable ArmResult pickWinner(Collection<ArmResult> results) {
        return results.stream()
                .filter(ArmResult::succeeded)
                .max((left, right) -> Double.compare(left.candidatesPerSecond(), right.candidatesPerSecond()))
                .orElse(null);
    }

    // -----------------------------------------------------------------------------------------
    // Stage 3 — filter recommendation
    // -----------------------------------------------------------------------------------------

    /**
     * Derives the recommended filter width from {@code total = probe + fpr × verificationCost}, or
     * measures the two widths empirically when {@code sweepFilterTypes} is set.
     */
    private @Nullable GpuFilterType resolveFilterType(
            CProducer template,
            Finder finder,
            ConsumerJava consumer,
            RuntimeStatistics runtimeStatistics,
            ExecutorService producerExecutor,
            KeyUtility keyUtility,
            BitHelper bitHelper) {
        if (cTuneConfiguration.sweepFilterTypes
                && template instanceof CProducerOpenCL cProducerOpenCL
                && winner != null
                && shouldRun.get()) {
            return measureFilterType(
                    cProducerOpenCL,
                    finder,
                    consumer,
                    runtimeStatistics,
                    producerExecutor,
                    keyUtility,
                    bitHelper,
                    Objects.requireNonNull(winner));
        }
        return deriveFilterType(effectiveVerificationCostMicros());
    }

    /**
     * Returns the filter width with the lower total cost per candidate.
     *
     * @param verificationCostMicros the cost of one verification, measured or documented
     * @return the width whose {@code probe + fpr × verification} is smaller
     */
    @VisibleForTesting
    static GpuFilterType deriveFilterType(double verificationCostMicros) {
        return totalCostNanos(GpuFilterType.FUSE_8, verificationCostMicros)
                        <= totalCostNanos(GpuFilterType.FUSE_16, verificationCostMicros)
                ? GpuFilterType.FUSE_8
                : GpuFilterType.FUSE_16;
    }

    /**
     * Total per-candidate cost of a filter width: its own probe plus the verification its false
     * positives force.
     *
     * @param type                   the filter width
     * @param verificationCostMicros the cost of one verification
     * @return the total cost in nanoseconds
     */
    @VisibleForTesting
    static double totalCostNanos(GpuFilterType type, double verificationCostMicros) {
        double probe = type == GpuFilterType.FUSE_16 ? FUSE_16_PROBE_NANOS : FUSE_8_PROBE_NANOS;
        double fpr = falsePositiveRate(type);
        return probe + fpr * verificationCostMicros * 1_000.0d;
    }

    private static double falsePositiveRate(GpuFilterType type) {
        return type == GpuFilterType.FUSE_16 ? FUSE_16_FALSE_POSITIVE_RATE : FUSE_8_FALSE_POSITIVE_RATE;
    }

    private double effectiveVerificationCostMicros() {
        Double measured = measuredVerificationCostMicros;
        return measured != null ? measured : VERIFICATION_COST_WARM_MICROS;
    }

    /** Runs the winning grid once per filter width and returns the faster one. */
    private GpuFilterType measureFilterType(
            CProducerOpenCL cProducerOpenCL,
            Finder finder,
            ConsumerJava consumer,
            RuntimeStatistics runtimeStatistics,
            ExecutorService producerExecutor,
            KeyUtility keyUtility,
            BitHelper bitHelper,
            ArmResult best) {
        GpuFilterType fastest = cProducerOpenCL.gpuFilterType;
        double fastestRate = -1.0d;
        for (GpuFilterType candidate : GpuFilterType.values()) {
            if (!shouldRun.get()) {
                break;
            }
            LOGGER.info(
                    "Rebuilding the pre-filter as {} to measure it empirically (this is the opt-in cost of "
                            + "sweepFilterTypes) ...",
                    candidate);
            cProducerOpenCL.gpuFilterType = candidate;
            FilterPayload payload = buildFilterPayload(cProducerOpenCL);
            ArmResult result = runArm(
                    best.batchSizeInBits(),
                    best.keysPerWorkItem(),
                    cProducerOpenCL,
                    finder,
                    consumer,
                    runtimeStatistics,
                    producerExecutor,
                    payload,
                    keyUtility,
                    bitHelper);
            LOGGER.info("Filter arm {}: {}", candidate, describeArm(result));
            if (result.succeeded() && result.candidatesPerSecond() > fastestRate) {
                fastestRate = result.candidatesPerSecond();
                fastest = candidate;
            }
        }
        cProducerOpenCL.gpuFilterType = fastest;
        return fastest;
    }

    // -----------------------------------------------------------------------------------------
    // Synthetic filter
    // -----------------------------------------------------------------------------------------

    /**
     * Builds the GPU pre-filter once, sized to {@code targetDatabaseEntries}, from a deterministic
     * PRNG source rather than from LMDB. Returns an empty payload when the template is not an
     * OpenCL producer in compact mode, because then nothing is uploaded to a device at all.
     */
    private FilterPayload buildFilterPayload(CProducer template) {
        if (!(template instanceof CProducerOpenCL cProducerOpenCL)
                || !cProducerOpenCL.enableGpuFilter
                || cProducerOpenCL.transferAll) {
            return FilterPayload.none();
        }
        long entries = cTuneConfiguration.targetDatabaseEntries;
        GpuFilterType type = cProducerOpenCL.gpuFilterType;
        LOGGER.info("Building a synthetic {} pre-filter for {} entries (no database is read) ...", type, entries);
        long before = System.nanoTime();
        PrngAddressIterable source = new PrngAddressIterable(FILTER_SEED, entries);
        FilterPayload payload = type == GpuFilterType.FUSE_16
                ? new FilterPayload(
                        null, BinaryFuse16AddressPresence.populateFrom(source).toGpuFilterData())
                : new FilterPayload(
                        BinaryFuse8AddressPresence.populateFrom(source).toGpuFilterData(), null);
        LOGGER.info("... filter built in {} s.", format((System.nanoTime() - before) / 1_000_000_000.0d));
        return payload;
    }

    /**
     * The one built pre-filter, staged onto every arm's freshly created producer.
     *
     * <p>At most one width is ever present: the width <em>is</em> the filter, and a payload staged
     * at one width but probed at the other yields silent false negatives rather than an error.
     */
    private record FilterPayload(
            @Nullable BinaryFuse8GpuFilterData data8,
            @Nullable BinaryFuse16GpuFilterData data16) {

        static FilterPayload none() {
            return new FilterPayload(null, null);
        }

        void stageOn(ProducerOpenCL producer) {
            BinaryFuse16GpuFilterData local16 = data16;
            if (local16 != null) {
                long seed = local16.seed();
                producer.setGpuFilter16(
                        local16.fingerprints(),
                        (int) seed,
                        (int) (seed >>> 32),
                        local16.segmentLength(),
                        local16.segmentLengthMask(),
                        local16.segmentCountLength());
                return;
            }
            BinaryFuse8GpuFilterData local8 = data8;
            if (local8 != null) {
                long seed = local8.seed();
                producer.setGpuFilter(
                        local8.fingerprints(),
                        (int) seed,
                        (int) (seed >>> 32),
                        local8.segmentLength(),
                        local8.segmentLengthMask(),
                        local8.segmentCountLength());
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Stage 4 — output
    // -----------------------------------------------------------------------------------------

    /**
     * Emits the configuration the operator should now run: every swept device carrying its own
     * measured values.
     *
     * <p>Each template was updated with its device's winner during the sweep, so this only has to
     * settle the filter type and serialise. That matters for a multi-device machine — the entries
     * differ from one another, which is the whole point, and a reader must not have to wonder which
     * of them was actually measured.
     *
     * @param cFinder   the configuration being tuned, whose producers now carry the winning values
     * @param templates the swept producers, in measurement order
     * @return the ready-to-run configuration as JSON, or an empty string when nothing was measured
     */
    private String buildRecommendedConfigurationJson(CFinder cFinder, List<CProducer> templates) {
        if (winner == null) {
            return "";
        }
        for (CProducer template : templates) {
            if (template instanceof CProducerOpenCL cProducerOpenCL) {
                GpuFilterType type = recommendedFilterType;
                if (type != null && cProducerOpenCL.enableGpuFilter && !cProducerOpenCL.transferAll) {
                    cProducerOpenCL.gpuFilterType = type;
                }
            }
        }
        giveEveryProducerItsOwnKeyProducer(cFinder);
        CConfiguration recommended = new CConfiguration();
        recommended.command = CCommand.Find;
        recommended.finder = cFinder;
        try {
            return configurationMapper().writeValueAsString(recommended);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialise the recommended configuration to JSON (devices=" + templates.size()
                            + ", recommendedFilterType=" + recommendedFilterType + "): " + e.getMessage(),
                    e);
        }
    }

    /**
     * Ensures no two producers in the emitted configuration reference the same key producer.
     *
     * <p>Sharing one is accepted at runtime — the lookup is by id and nothing forbids it — but every
     * shipped multi-producer example gives each its own, so that each device draws from an
     * independent stream rather than contending on one. Automatically detected devices all start out
     * pointing at the same key producer, so without this a two-GPU recommendation would ship a
     * convention violation the operator never chose.
     *
     * @param cFinder the configuration to normalise in place
     */
    private static void giveEveryProducerItsOwnKeyProducer(CFinder cFinder) {
        if (cFinder.producerOpenCL.size() < 2 || cFinder.keyProducerJavaRandom.isEmpty()) {
            return;
        }
        CKeyProducerJavaRandom prototype = cFinder.keyProducerJavaRandom.get(0);
        String baseId = prototype.keyProducerId;
        if (baseId == null) {
            return;
        }
        ObjectMapper mapper = configurationMapper();
        for (int index = 1; index < cFinder.producerOpenCL.size(); index++) {
            CProducerOpenCL producer = cFinder.producerOpenCL.get(index);
            if (!baseId.equals(producer.keyProducerId)) {
                continue;
            }
            String ownId = baseId + "_" + (index + 1);
            try {
                CKeyProducerJavaRandom copy =
                        mapper.readValue(mapper.writeValueAsString(prototype), CKeyProducerJavaRandom.class);
                copy.keyProducerId = ownId;
                cFinder.keyProducerJavaRandom.add(copy);
                producer.keyProducerId = ownId;
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to derive a key producer for device " + index, e);
            }
        }
    }

    /**
     * Renders the per-device winner section: one line per swept device naming its own optimum, so a
     * multi-GPU report shows that a slower card wanted different settings than the single global
     * winner. The flat arms table above does not attribute rows to devices, and the emitted JSON is
     * the only other place the per-device optimum appears — a reader of the report alone would
     * otherwise never learn it.
     *
     * <p>Returns an empty string when fewer than two devices were swept: with one device the global
     * {@code Winner} line already states it, and repeating it here would only add noise.
     *
     * @param deviceSweeps the per-device breakdown, in sweep order
     * @return the rendered section (terminated by a newline), or an empty string for a single device
     */
    @VisibleForTesting
    static String renderPerDeviceWinners(List<DeviceSweep> deviceSweeps) {
        if (deviceSweeps.size() < 2) {
            return "";
        }
        StringBuilder section = new StringBuilder(256);
        section.append("Winner per device (MEASURED):\n");
        for (DeviceSweep sweep : deviceSweeps) {
            ArmResult best = sweep.winner();
            if (best == null) {
                section.append(String.format(
                        Locale.ROOT, "  %s: no arm produced a usable measurement%n", sweep.deviceLabel()));
            } else {
                section.append(String.format(
                        Locale.ROOT,
                        "  %s: batchSizeInBits=%d keysPerWorkItem=%d at %s candidates/s%n",
                        sweep.deviceLabel(),
                        best.batchSizeInBits(),
                        best.keysPerWorkItem(),
                        format(best.candidatesPerSecond())));
            }
        }
        return section.toString();
    }

    private String buildReport(CProducer template) {
        StringBuilder report = new StringBuilder(2_048);
        report.append("\n########## BEGIN TuneConfiguration report ##########\n");

        boolean openCl = template instanceof CProducerOpenCL;
        if (!openCl) {
            report.append("Producer: CPU (producerJava). keysPerWorkItem has no meaning for a CPU producer, so the\n")
                    .append("candidates in that column collapse to repeated measurements of the same arm.\n\n");
        }

        report.append("Arms (all figures MEASURED on this machine):\n")
                .append(String.format(
                        Locale.ROOT,
                        "  %-16s %-16s %18s %18s%n",
                        "batchSizeInBits",
                        "keysPerWorkItem",
                        "candidates/s",
                        "addresses checked/s"));
        for (ArmResult result : results) {
            report.append(String.format(
                    Locale.ROOT,
                    "  %-16d %-16d %18s %18s",
                    result.batchSizeInBits(),
                    result.keysPerWorkItem(),
                    format(result.candidatesPerSecond()),
                    format(result.addressesCheckedPerSecond())));
            if (result.failure() != null) {
                report.append("   FAILED: ").append(result.failure());
            } else if (result.tooSlowToMeasure()) {
                report.append("   NOT MEASURED: ").append(tooSlowExplanation(result));
            }
            report.append('\n');
        }

        ArmResult best = winner;
        report.append('\n');
        if (best == null) {
            report.append("No arm produced a usable measurement; nothing can be recommended.\n");
        } else {
            report.append(String.format(
                    Locale.ROOT,
                    "Winner (MEASURED): batchSizeInBits=%d keysPerWorkItem=%d at %s candidates/s.%n",
                    best.batchSizeInBits(),
                    best.keysPerWorkItem(),
                    format(best.candidatesPerSecond())));
            String boundaryNote = winnerBoundaryNote(best);
            if (!boundaryNote.isEmpty()) {
                report.append(boundaryNote).append('\n');
            }
        }

        String perDeviceWinners = renderPerDeviceWinners(deviceSweeps);
        if (!perDeviceWinners.isEmpty()) {
            report.append('\n').append(perDeviceWinners);
        }

        report.append('\n').append("Filter choice - total = probe + fpr x verification:\n");
        Double measured = measuredVerificationCostMicros;
        if (measured != null) {
            report.append(String.format(
                    Locale.ROOT, "  verification cost   %s us   MEASURED on this database%n", format(measured)));
        } else {
            report.append(String.format(
                            Locale.ROOT,
                            "  verification cost   %s us   ESTIMATED (documented warm value; no database was%n",
                            format(VERIFICATION_COST_WARM_MICROS)))
                    .append(String.format(
                            Locale.ROOT,
                            "                                    configured to measure. The documented cold value is%n"
                                    + "                                    %s us - a 70x spread, so measure it if the%n"
                                    + "                                    filter choice matters to you.)%n",
                            format(VERIFICATION_COST_COLD_MICROS)));
        }
        double verification = effectiveVerificationCostMicros();
        report.append(String.format(
                        Locale.ROOT,
                        "  FUSE_8              probe %s ns, fpr %s   DOCUMENTED constants -> total %s ns%n",
                        format(FUSE_8_PROBE_NANOS),
                        formatRate(FUSE_8_FALSE_POSITIVE_RATE),
                        format(totalCostNanos(GpuFilterType.FUSE_8, verification))))
                .append(String.format(
                        Locale.ROOT,
                        "  FUSE_16             probe %s ns, fpr %s   DOCUMENTED constants -> total %s ns%n",
                        format(FUSE_16_PROBE_NANOS),
                        formatRate(FUSE_16_FALSE_POSITIVE_RATE),
                        format(totalCostNanos(GpuFilterType.FUSE_16, verification))))
                .append("  Recommended: ")
                .append(recommendedFilterType)
                .append(
                        cTuneConfiguration.sweepFilterTypes
                                ? "   (MEASURED empirically)"
                                : "   (DERIVED, not measured)")
                .append('\n');

        if (!recommendedConfigurationJson.isEmpty()) {
            report.append("\nPaste-ready configuration:\n")
                    .append(recommendedConfigurationJson)
                    .append('\n');
        }
        report.append("########## END TuneConfiguration report ##########\n");
        return report.toString();
    }

    /**
     * An OpenCL device the sweep can target, reduced to what the tuner needs to address and describe
     * it.
     *
     * @param platformIndex  the platform's position in the enumeration, as {@code producerOpenCL} means it
     * @param deviceIndex    the device's position <em>within that platform</em>
     * @param deviceName     the device's reported name, used to label its section of the report
     * @param deviceType     the raw {@code CL_DEVICE_TYPE} bit field
     * @param physicalDeviceFingerprint a fingerprint identifying the physical GPU, or {@code null}
     *     when it could not be determined; two entries with the same non-null fingerprint are the same
     *     physical card seen through two OpenCL platforms
     */
    public record DetectedDevice(
            int platformIndex,
            int deviceIndex,
            String deviceName,
            long deviceType,
            @Nullable String physicalDeviceFingerprint) {

        /**
         * Convenience constructor for callers and tests that have no PCIe topology to supply.
         *
         * @param platformIndex the platform's position in the enumeration
         * @param deviceIndex   the device's position within that platform
         * @param deviceName    the device's reported name
         * @param deviceType    the raw {@code CL_DEVICE_TYPE} bit field
         */
        public DetectedDevice(int platformIndex, int deviceIndex, String deviceName, long deviceType) {
            this(platformIndex, deviceIndex, deviceName, deviceType, null);
        }

        /**
         * Returns whether this is a GPU.
         *
         * <p>A CPU OpenCL runtime — pocl, Intel's CPU ICD — enumerates as an ordinary device, but
         * measuring it duplicates what {@code producerJava} already does while costing a full sweep
         * of wall-clock. The project's own OpenCL CI job runs on pocl, so this is not hypothetical.
         *
         * @return {@code true} if the device reports the GPU type bit
         */
        public boolean isGpu() {
            return (deviceType & CL.CL_DEVICE_TYPE_GPU) != 0L;
        }
    }

    /**
     * Flattens the platform/device hierarchy into the GPUs the sweep should measure, in enumeration
     * order.
     *
     * @param platforms the platforms as reported by {@code OpenCLBuilder}
     * @return every GPU, carrying the indices {@code producerOpenCL} addresses it by
     */
    @VisibleForTesting
    static List<DetectedDevice> gpuDevicesOf(List<OpenCLPlatform> platforms) {
        List<DetectedDevice> devices = new ArrayList<>();
        for (int platformIndex = 0; platformIndex < platforms.size(); platformIndex++) {
            List<OpenCLDevice> platformDevices = platforms.get(platformIndex).openCLDevices();
            for (int deviceIndex = 0; deviceIndex < platformDevices.size(); deviceIndex++) {
                OpenCLDevice device = platformDevices.get(deviceIndex);
                DetectedDevice detected =
                        new DetectedDevice(platformIndex, deviceIndex, device.deviceName(), device.deviceType());
                if (detected.isGpu()) {
                    devices.add(new DetectedDevice(
                            platformIndex,
                            deviceIndex,
                            device.deviceName(),
                            device.deviceType(),
                            OpenCLDeviceTopology.physicalDeviceFingerprintOf(device)));
                }
            }
        }
        return dedupePhysicalDevices(devices);
    }

    /**
     * Collapses device entries that are the same physical GPU seen through more than one OpenCL
     * platform, so a duplicate ICD registration does not make the sweep measure — and the emitted
     * configuration then drive — one card several times over.
     *
     * <p>Two entries are the same physical GPU only when they share a non-null
     * {@link DetectedDevice#physicalDeviceFingerprint() fingerprint}. A {@code null} fingerprint means the
     * topology is unknown, so such a device is always kept: merging on anything weaker (the device
     * name) would wrongly collapse a rig of identical cards, which reports identical names but occupies
     * distinct PCIe slots. When a name recurs with unknown topology the ambiguity is only logged, not
     * acted on.
     *
     * @param devices the detected GPUs, in enumeration order
     * @return the physically distinct GPUs, first occurrence kept, in enumeration order
     */
    @VisibleForTesting
    static List<DetectedDevice> dedupePhysicalDevices(List<DetectedDevice> devices) {
        List<DetectedDevice> unique = new ArrayList<>();
        Map<String, DetectedDevice> firstByFingerprint = new HashMap<>();
        Set<String> namesKept = new HashSet<>();
        Set<String> namesWarned = new HashSet<>();
        for (DetectedDevice device : devices) {
            String fingerprint = device.physicalDeviceFingerprint();
            if (fingerprint != null) {
                DetectedDevice first = firstByFingerprint.get(fingerprint);
                if (first != null) {
                    LOGGER.info(
                            "Skipping {} (platformIndex={}, deviceIndex={}): it is the same physical GPU as {}"
                                    + " (platformIndex={}, deviceIndex={}, identity {}) exposed by a second OpenCL"
                                    + " platform. Sweeping it once.",
                            device.deviceName(),
                            device.platformIndex(),
                            device.deviceIndex(),
                            first.deviceName(),
                            first.platformIndex(),
                            first.deviceIndex(),
                            fingerprint);
                    continue;
                }
                firstByFingerprint.put(fingerprint, device);
                namesKept.add(device.deviceName());
                unique.add(device);
                continue;
            }
            // Topology unknown: keep the device (never merge on the weaker name signal), but warn once
            // per name when it recurs — it may be the same card seen through a second ICD and would
            // then be swept, and later driven, more than once.
            if (!namesKept.add(device.deviceName()) && namesWarned.add(device.deviceName())) {
                LOGGER.warn(
                        "{} appears under more than one OpenCL platform but its PCIe topology is unavailable,"
                                + " so it cannot be confirmed as one physical GPU. It may be swept and later"
                                + " driven more than once; configure producerOpenCL explicitly to pick one.",
                        device.deviceName());
            }
            unique.add(device);
        }
        return unique;
    }

    /**
     * Builds one independent producer template per device, each addressing its own device and
     * carrying every other setting from the prototype.
     *
     * <p>The copy is a JSON round-trip rather than field-by-field assignment. {@code CProducerOpenCL}
     * has upwards of fifteen settings and gains more over time; a hand-written copy silently drops
     * whichever one was added last, and the failure would be a device measured under settings the
     * operator never asked for — the exact class of quietly-wrong result this command exists to
     * remove.
     *
     * @param devices   the devices to target
     * @param prototype the settings every template inherits
     * @return one template per device, independent of the prototype and of each other
     */
    @VisibleForTesting
    static List<CProducerOpenCL> templatesForDevices(List<DetectedDevice> devices, CProducerOpenCL prototype) {
        ObjectMapper mapper = configurationMapper();
        String serialisedPrototype;
        try {
            serialisedPrototype = mapper.writeValueAsString(prototype);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to copy the producer template for multi-device tuning", e);
        }
        List<CProducerOpenCL> templates = new ArrayList<>();
        for (DetectedDevice device : devices) {
            try {
                CProducerOpenCL copy = mapper.readValue(serialisedPrototype, CProducerOpenCL.class);
                copy.platformIndex = device.platformIndex();
                copy.deviceIndex = device.deviceIndex();
                templates.add(copy);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(
                        "Failed to copy the producer template for device " + device.deviceName(), e);
            }
        }
        return templates;
    }

    /**
     * The mapper used for both copying templates and emitting the recommendation.
     *
     * <p>Serialises the public fields only, never the getters: the configuration POJOs are plain
     * field carriers, but {@code CProducer} also exposes the derived {@code getOverallWorkSize()},
     * which Jackson would write as {@code "overallWorkSize"} — a property no field matches, so
     * reading the result back fails with {@code UnrecognizedPropertyException}.
     *
     * @return a mapper that round-trips the configuration POJOs
     */
    private static ObjectMapper configurationMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        return mapper;
    }

    /**
     * One further arm the sweep should measure because the grid's winner landed on the edge of what
     * was swept.
     *
     * @param batchSizeInBits the grid size exponent to try next
     * @param keysPerWorkItem the keys per work item to try next
     * @param axis            which axis is being extended, for the log line
     */
    public record SweepExtension(int batchSizeInBits, int keysPerWorkItem, String axis) {}

    /**
     * Decides whether the sweep should keep going past the candidates it was given, and with what.
     *
     * <h2>Why extend instead of advising a re-run</h2>
     * A winner sitting on the largest value tried is a <em>lower bound</em>, not a peak: the sweep
     * stopped looking, not the hardware. Both of a contributor's first sweeps won at the shipped
     * list's last {@code keysPerWorkItem} of 256; widening it by hand afterwards gained 17 % on
     * their RTX 500 and 104 % on their Arc. Documentation saying "widen and re-run" only reaches
     * someone who reads it before starting, and a 20-minute measurement is run once. So the sweep
     * continues on its own, at the cost of one arm per step rather than a whole second run.
     *
     * <h2>Edge of the list versus edge of the possible</h2>
     * Only the first is worth continuing past, and conflating them is what would make this useless.
     * {@code batchSizeInBits} tops out at {@link OpenClKernelConstants#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY}
     * (24), which the shipped candidate list already reaches, so a winner there is at the maximum
     * the framework supports rather than at a truncation — extending would only produce a rejected
     * arm, and flagging it would fire on most default runs until nobody read it any more.
     * {@code keysPerWorkItem} has its own hard bound: {@code OpenClTask} rejects a value above
     * {@code 2^batchSizeInBits}, since work items are {@code grid / keysPerWorkItem}.
     *
     * <p>A CPU producer is excluded outright — {@code keysPerWorkItem} is inert there, so extending
     * along it would spend real minutes re-measuring one arm.
     *
     * <p><b>What this deliberately does not do</b> is re-explore the other axis after extending:
     * it follows the winner's own row. The optimum could in principle move to a different
     * {@code batchSizeInBits} once {@code keysPerWorkItem} grows, and this would not find that. A
     * full second cross product would cost as much as the sweep itself, which is the thing being
     * avoided.
     *
     * @param winner                        the best arm the sweep has produced so far
     * @param largestSweptKeysPerWorkItem   the largest {@code keysPerWorkItem} measured so far
     * @param largestSweptBatchSizeInBits   the largest {@code batchSizeInBits} measured so far
     * @param openCl                        whether the swept producer is GPU-backed
     * @return the next arm to measure, or {@code null} when the sweep should stop
     */
    @VisibleForTesting
    static @Nullable SweepExtension nextExtensionArm(
            ArmResult winner, int largestSweptKeysPerWorkItem, int largestSweptBatchSizeInBits, boolean openCl) {
        if (!openCl || !winner.succeeded()) {
            return null;
        }
        if (winner.keysPerWorkItem() == largestSweptKeysPerWorkItem) {
            long doubled = (long) largestSweptKeysPerWorkItem * 2L;
            // OpenClTask requires keysPerWorkItem <= 2^batchSizeInBits (work items = grid / kpwi).
            if (doubled <= (1L << winner.batchSizeInBits())) {
                return new SweepExtension(winner.batchSizeInBits(), (int) doubled, "keysPerWorkItem");
            }
            return null;
        }
        if (winner.batchSizeInBits() == largestSweptBatchSizeInBits
                && winner.batchSizeInBits() < OpenClKernelConstants.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY) {
            return new SweepExtension(winner.batchSizeInBits() + 1, winner.keysPerWorkItem(), "batchSizeInBits");
        }
        return null;
    }

    /**
     * Explains, in the report, that a winner at the top of the {@code batchSizeInBits} range is
     * there because the range ends, not because the sweep stopped early.
     *
     * <p>Without this a reader sees the winner in the table's last row and cannot tell whether the
     * measurement was truncated. Saying so only at the framework maximum keeps the note meaningful:
     * every other edge is now extended automatically rather than reported.
     *
     * @param winner the winning arm
     * @return the note to append after the winner line, or an empty string when there is nothing to
     *         explain
     */
    @VisibleForTesting
    static String winnerBoundaryNote(ArmResult winner) {
        if (winner.batchSizeInBits() == OpenClKernelConstants.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY) {
            return "  batchSizeInBits=" + OpenClKernelConstants.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY
                    + " is the framework maximum (BIT_COUNT_FOR_MAX_CHUNKS_ARRAY), so this axis had no"
                    + " room left to explore.";
        }
        return "";
    }

    /**
     * Names a template's device for the report.
     *
     * @param template the producer being swept
     * @return a human-readable device label
     */
    @VisibleForTesting
    static String describeTemplateDevice(CProducer template) {
        if (template instanceof CProducerOpenCL cProducerOpenCL) {
            return "GPU platformIndex=" + cProducerOpenCL.platformIndex + " deviceIndex=" + cProducerOpenCL.deviceIndex;
        }
        return "CPU (producerJava)";
    }

    /**
     * Writes a device's own winning values back into its producer, so the emitted configuration
     * describes what was measured on that device rather than on another one.
     *
     * @param template the producer to update
     * @param winner   that device's winning arm, or {@code null} if it produced none
     */
    @VisibleForTesting
    static void applyWinnerTo(CProducer template, @Nullable ArmResult winner) {
        if (winner == null) {
            return;
        }
        template.batchSizeInBits = winner.batchSizeInBits();
        if (template instanceof CProducerOpenCL cProducerOpenCL) {
            cProducerOpenCL.keysPerWorkItem = winner.keysPerWorkItem();
        }
    }

    @VisibleForTesting
    static String describeArm(ArmResult result) {
        if (result.failure() != null) {
            return "batchSizeInBits=" + result.batchSizeInBits() + " keysPerWorkItem=" + result.keysPerWorkItem()
                    + " FAILED: " + result.failure();
        }
        if (result.tooSlowToMeasure()) {
            return "batchSizeInBits=" + result.batchSizeInBits() + " keysPerWorkItem=" + result.keysPerWorkItem()
                    + " NOT MEASURED: " + tooSlowExplanation(result);
        }
        return "batchSizeInBits=" + result.batchSizeInBits() + " keysPerWorkItem=" + result.keysPerWorkItem() + " -> "
                + format(result.candidatesPerSecond()) + " candidates/s (" + format(result.addressesCheckedPerSecond())
                + " addresses checked/s over " + format(result.elapsedSeconds()) + " s)";
    }

    /**
     * Explains an arm that produced no throughput without failing, so it is not mistaken for a
     * driver rejection.
     *
     * @param result the arm, which must satisfy {@link ArmResult#tooSlowToMeasure()}
     * @return the explanation appended after {@code NOT MEASURED:}
     */
    @VisibleForTesting
    static String tooSlowExplanation(ArmResult result) {
        return "no batch completed within the " + format(result.elapsedSeconds())
                + " s window; one launch of 2^" + result.batchSizeInBits()
                + " candidates outlasts it at this keysPerWorkItem. Raise secondsPerArm or lower"
                + " batchSizeInBits if this combination matters to you. Not a device or driver error.";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%,.2f", value);
    }

    /**
     * Formats a false-positive rate in plain decimal. {@code String.valueOf} renders
     * {@link #FUSE_16_FALSE_POSITIVE_RATE} as {@code 1.6E-5}, which reads as a typo next to
     * Fuse-8's {@code 0.003874} in the same column.
     */
    private static String formatRate(double rate) {
        return String.format(Locale.ROOT, "%.6f", rate);
    }

    // -----------------------------------------------------------------------------------------
    // Wiring helpers
    // -----------------------------------------------------------------------------------------

    /**
     * The consumer's lookup during a tuning run: nothing is ever present.
     *
     * <p>Not an oversight and not a stub for something better. The alternative — handing the
     * consumer the same synthetic filter the GPU probes — would make every filter survivor verify
     * as a hit, so the run would spend its time deriving keys and logging fabricated finds. Against
     * a real database the correct answer for synthetic candidates is "absent" anyway; this returns
     * that answer without a database. The consumer's odometer still advances (it is incremented
     * around the lookup, not inside it), so the address-checked rate stays meaningful. The one cost
     * this omits is the CPU-side filter probe on survivors — tens of nanoseconds against the
     * microsecond-scale EC and hash work per candidate, and in compact mode paid on well under 1 %
     * of candidates.
     */
    private static final AddressPresence NEVER_PRESENT = new AddressPresence() {
        @Override
        public boolean containsAddress(ByteBuffer hash160) {
            return false;
        }

        @Override
        public boolean requiresBackend() {
            return false;
        }
    };

    private static KeyUtility keyUtility() {
        Network network = new NetworkParameterFactory().getNetwork();
        return new KeyUtility(network, new ByteBufferUtility(false));
    }

    /**
     * Determines which producers the sweep measures — every one of them, in order.
     *
     * <h2>Every configured device, not just the first</h2>
     * This used to return {@code producerOpenCL.get(0)} and ignore the rest without a word. Someone
     * with two GPUs naturally lists both, waited twenty minutes, and received a full-looking report
     * for one device plus a paste-ready configuration whose second entry still held their guesses —
     * indistinguishable from the measured one. On a contributor's laptop that guess was 2.3× off
     * that device's optimum, and nothing in the output could have revealed it.
     *
     * <h2>Nothing configured means "measure what I have"</h2>
     * An empty producer list used to be an error. It is instead the case where the operator does not
     * know what to write, which is the majority of first runs, so the tuner enumerates the machine's
     * GPUs and sweeps each. Listing entries explicitly remains the override: an operator who names
     * one device gets exactly that device.
     *
     * @param cFinder the configuration being tuned
     * @return the producers to sweep, in the order they will be measured
     */
    private List<CProducer> resolveTemplates(CFinder cFinder) {
        if (!cFinder.producerOpenCL.isEmpty()) {
            hintAtUndetectedDevices(cFinder.producerOpenCL.size());
            return new ArrayList<>(cFinder.producerOpenCL);
        }
        if (!cFinder.producerJava.isEmpty()) {
            return new ArrayList<>(cFinder.producerJava);
        }
        List<CProducerOpenCL> discovered = discoverTemplates(cFinder);
        if (!discovered.isEmpty()) {
            // The discovered templates become the configuration's own producers, so the emitted
            // recommendation describes the run that was actually measured rather than the empty
            // list it started from.
            cFinder.producerOpenCL.addAll(discovered);
            return new ArrayList<>(discovered);
        }
        throw new IllegalArgumentException(
                "tuneConfiguration.finder has no producerOpenCL or producerJava entry and no OpenCL GPU could be"
                        + " detected to sweep automatically. Configure a producer, or install an OpenCL runtime"
                        + " and verify it with the OpenCLInfo command.");
    }

    /**
     * Builds one template per detected GPU, inheriting the settings of a configured OpenCL producer
     * when there is one and the defaults otherwise.
     *
     * @param cFinder the configuration being tuned
     * @return a template per detected GPU, empty when none could be detected
     */
    private List<CProducerOpenCL> discoverTemplates(CFinder cFinder) {
        if (!OpenCLBuilder.isOpenClNativeLibraryLoaded()) {
            return Collections.emptyList();
        }
        List<DetectedDevice> devices = gpuDevicesOf(new OpenCLBuilder().build());
        if (devices.isEmpty()) {
            return Collections.emptyList();
        }
        CProducerOpenCL prototype = new CProducerOpenCL();
        prototype.keyProducerId = firstKeyProducerId(cFinder);
        LOGGER.info(
                "No producer configured; sweeping every detected GPU instead. {} found: {}.",
                devices.size(),
                devices.stream()
                        // The identity is logged for every device, not only for merged ones: without it
                        // there is no way to tell "no duplicate present" from "no fingerprint could be
                        // read", and those call for very different follow-up.
                        .map(d -> d.deviceName() + " (platformIndex=" + d.platformIndex() + ", deviceIndex="
                                + d.deviceIndex() + ", identity "
                                + (d.physicalDeviceFingerprint() == null ? "unknown" : d.physicalDeviceFingerprint())
                                + ")")
                        .collect(Collectors.joining("; ")));
        return templatesForDevices(devices, prototype);
    }

    /**
     * Points out devices the operator's explicit configuration leaves unmeasured.
     *
     * <p>Explicit configuration wins — but someone who wrote one entry on a two-GPU machine most
     * likely did not know the tuner could do both, and would otherwise never find out.
     *
     * @param configuredCount how many OpenCL producers the configuration names
     */
    private void hintAtUndetectedDevices(int configuredCount) {
        if (!OpenCLBuilder.isOpenClNativeLibraryLoaded()) {
            return;
        }
        int detected = gpuDevicesOf(new OpenCLBuilder().build()).size();
        if (detected > configuredCount) {
            LOGGER.info(
                    "{} producerOpenCL entries configured, but {} GPUs are present. Only the configured ones are"
                            + " swept. Remove the producerOpenCL entries entirely to have every GPU measured.",
                    configuredCount,
                    detected);
        }
    }

    /**
     * Returns an id of a configured key producer, for templates that have none of their own.
     *
     * @param cFinder the configuration being tuned
     * @return the first configured key producer id
     */
    private static String firstKeyProducerId(CFinder cFinder) {
        List<@Nullable String> ids = new ArrayList<>();
        cFinder.keyProducerJavaRandom.forEach(c -> ids.add(c.keyProducerId));
        cFinder.keyProducerJavaIncremental.forEach(c -> ids.add(c.keyProducerId));
        cFinder.keyProducerJavaBip39.forEach(c -> ids.add(c.keyProducerId));
        for (String id : ids) {
            if (id != null) {
                return id;
            }
        }
        throw new IllegalArgumentException(
                "tuneConfiguration.finder needs at least one key producer (for example keyProducerJavaRandom) so the"
                        + " automatically detected devices have a source of secrets to measure with.");
    }

    /**
     * Waits for the given number of seconds, or until {@link #interrupt()} is called.
     *
     * <p>An await on the stop latch rather than a sleep so a shutdown request cuts the current
     * window short instead of being noticed only once it expires — a 20 s arm would otherwise make
     * Ctrl-C feel unresponsive.
     *
     * @param seconds the window length; {@code 0} returns immediately
     */
    private void awaitSeconds(int seconds) throws InterruptedException {
        if (seconds <= 0) {
            return;
        }
        boolean stopped = stopLatch.await(seconds, TimeUnit.SECONDS);
        if (stopped) {
            LOGGER.info("Stop requested; the current measurement window was cut short.");
        }
    }

    @Override
    public void interrupt() {
        LOGGER.info("interrupt called: stopping the tuning sweep.");
        shouldRun.set(false);
        stopLatch.countDown();
    }
}
