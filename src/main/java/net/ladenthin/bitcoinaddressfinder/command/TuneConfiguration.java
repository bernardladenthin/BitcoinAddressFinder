// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CCommand;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.CTuneConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.GpuFilterType;
import net.ladenthin.bitcoinaddressfinder.consumer.ConsumerJava;
import net.ladenthin.bitcoinaddressfinder.core.FireAndForget;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.engine.Finder;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducer;
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
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
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
    }

    /**
     * Returns the per-arm results, one entry per swept candidate pair, in sweep order.
     *
     * @return an unmodifiable snapshot of the arm results
     */
    public List<ArmResult> getResults() {
        return Collections.unmodifiableList(new ArrayList<>(results));
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

        CProducer template = resolveTemplate(cFinder);
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
            FilterPayload payload = buildFilterPayload(template);
            sweepGrid(template, finder, consumer, runtimeStatistics, producerExecutor, payload, keyUtility, bitHelper);
            winner = pickWinner(results);
            recommendedFilterType = resolveFilterType(
                    template, finder, consumer, runtimeStatistics, producerExecutor, keyUtility, bitHelper);
            recommendedConfigurationJson = buildRecommendedConfigurationJson(cFinder, template);
            LOGGER.info(buildReport(template));
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
                boolean unused = lmdb.containsAddress(probe);
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
    }

    /**
     * Runs a single arm: build, init, start, measure, stop — only the producer.
     *
     * <p>The consumer, its lookup and the filter payload outlive every arm; recreating them per arm
     * is what would make the sweep unusable.
     *
     * @return the arm's result, carrying a failure description instead of a rate when it did not run
     */
    @SuppressWarnings("FutureReturnValueIgnored")
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
            Object unused = producerExecutor.submit(producer);

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
            return new ArmResult(
                    batchSizeInBits,
                    keysPerWorkItem,
                    candidatesPerSecond,
                    addressesCheckedPerSecond,
                    elapsedSeconds,
                    null);
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

    private static Producer createProducer(
            CProducer template,
            ConsumerJava consumer,
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
        return new ProducerJava(
                (CProducerJava) template, consumer, keyUtility, keyProducer, bitHelper, runtimeStatistics);
    }

    private static long totalBatches(RuntimeStatistics runtimeStatistics) {
        return runtimeStatistics.batchesByProducerSnapshot().values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private static @Nullable ArmResult pickWinner(List<ArmResult> results) {
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

    private String buildRecommendedConfigurationJson(CFinder cFinder, CProducer template) {
        ArmResult best = winner;
        if (best == null) {
            return "";
        }
        template.batchSizeInBits = best.batchSizeInBits();
        if (template instanceof CProducerOpenCL cProducerOpenCL) {
            cProducerOpenCL.keysPerWorkItem = best.keysPerWorkItem();
            GpuFilterType type = recommendedFilterType;
            if (type != null && cProducerOpenCL.enableGpuFilter && !cProducerOpenCL.transferAll) {
                cProducerOpenCL.gpuFilterType = type;
            }
        }
        CConfiguration recommended = new CConfiguration();
        recommended.command = CCommand.Find;
        recommended.finder = cFinder;
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            // Serialise the public fields only, never the getters. The configuration POJOs are
            // plain field carriers, but CProducer also exposes the derived getOverallWorkSize();
            // with default visibility Jackson writes it as "overallWorkSize", and reading that file
            // back fails with UnrecognizedPropertyException because no such field exists. The whole
            // point of this output is that the user can paste and run it, so it must round-trip.
            mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
            mapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
            mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.PUBLIC_ONLY);
            return mapper.writeValueAsString(recommended);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise the recommended configuration to JSON", e);
        }
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

    private static String describeArm(ArmResult result) {
        if (result.failure() != null) {
            return "batchSizeInBits=" + result.batchSizeInBits() + " keysPerWorkItem=" + result.keysPerWorkItem()
                    + " FAILED: " + result.failure();
        }
        return "batchSizeInBits=" + result.batchSizeInBits() + " keysPerWorkItem=" + result.keysPerWorkItem() + " -> "
                + format(result.candidatesPerSecond()) + " candidates/s (" + format(result.addressesCheckedPerSecond())
                + " addresses checked/s over " + format(result.elapsedSeconds()) + " s)";
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

    private static CProducer resolveTemplate(CFinder cFinder) {
        if (!cFinder.producerOpenCL.isEmpty()) {
            return cFinder.producerOpenCL.get(0);
        }
        if (!cFinder.producerJava.isEmpty()) {
            return cFinder.producerJava.get(0);
        }
        throw new IllegalArgumentException(
                "tuneConfiguration.finder needs at least one producerOpenCL or producerJava entry to use as the "
                        + "sweep template; the sweep varies its batchSizeInBits / keysPerWorkItem and carries every "
                        + "other field through unchanged.");
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
