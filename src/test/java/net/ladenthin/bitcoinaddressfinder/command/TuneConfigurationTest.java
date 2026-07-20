// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.File;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.LMDBBase;
import net.ladenthin.bitcoinaddressfinder.LMDBPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.OpenCLTest;
import net.ladenthin.bitcoinaddressfinder.cli.Main;
import net.ladenthin.bitcoinaddressfinder.configuration.CCommand;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CFinder;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.configuration.CTuneConfiguration;
import net.ladenthin.bitcoinaddressfinder.configuration.GpuFilterType;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TuneConfiguration}.
 *
 * <h2>What these tests do and do not assert</h2>
 * They assert the <b>mechanism</b>: that every configured candidate pair produced an arm, that the
 * winner is one of the candidates actually swept, that the verification-cost stage yields a finite
 * positive number when a database is present and cleanly reports "not measured" when one is not,
 * and that the emitted JSON round-trips back into a {@link CConfiguration}.
 *
 * <p>They deliberately assert <b>nothing about the throughput figures themselves</b>. Those depend
 * on the CPU, GPU, thermal state and concurrent load of whatever machine happens to run the suite;
 * a CI container sharing a host with other jobs can differ by an order of magnitude between two
 * runs of the same commit. An assertion tight enough to catch a real regression would therefore be
 * flaky, and one loose enough to be stable (<i>throughput &gt; 0</i>) would be vacuous — it would
 * pass for any pipeline that moved a single key. Testing the measurement apparatus is the useful
 * thing to do here; validating the measurements is the job of the command itself, on the operator's
 * own hardware, which is precisely why the command exists.
 *
 * <p>Arms are sub-second and the sweeps are two candidates wide, so the whole class stays well
 * inside the Surefire per-fork timeout.
 */
public class TuneConfigurationTest extends LMDBBase {

    /** Two grid sizes is enough to prove the sweep enumerates; more only costs fork time. */
    private static final List<Integer> BATCH_SIZE_CANDIDATES = List.of(4, 5);

    private static final int SECONDS_PER_ARM = 1;

    public TuneConfigurationTest() {}

    // <editor-fold desc="grid sweep mechanism">

    @Test
    public void run_cpuProducerWithoutDatabase_producesOneArmPerCandidatePair() {
        // arrange
        CTuneConfiguration cTuneConfiguration = cpuTuneConfiguration();

        // act
        TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
        tuneConfiguration.run();

        // assert
        List<TuneConfiguration.ArmResult> results = tuneConfiguration.getResults();
        assertThat(results, hasSize(BATCH_SIZE_CANDIDATES.size()));
        assertThat(
                results.stream()
                        .map(TuneConfiguration.ArmResult::batchSizeInBits)
                        .toList(),
                contains(BATCH_SIZE_CANDIDATES.toArray(new Integer[0])));
    }

    @Test
    public void run_cpuProducerWithoutDatabase_winnerIsOneOfTheSweptCandidates() {
        // arrange
        CTuneConfiguration cTuneConfiguration = cpuTuneConfiguration();

        // act
        TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
        tuneConfiguration.run();

        // assert
        TuneConfiguration.ArmResult winner = tuneConfiguration.getWinner();
        assertThat(winner, is(notNullValue()));
        assertThat(winner.batchSizeInBits(), isIn(BATCH_SIZE_CANDIDATES));
        assertThat(winner, isIn(tuneConfiguration.getResults()));
    }

    @Test
    public void run_cpuProducerWithoutDatabase_emittedJsonParsesBackIntoAConfiguration() throws Exception {
        // arrange
        CTuneConfiguration cTuneConfiguration = cpuTuneConfiguration();

        // act
        TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
        tuneConfiguration.run();
        CConfiguration parsed = Main.fromJson(tuneConfiguration.getRecommendedConfigurationJson());

        // assert
        TuneConfiguration.ArmResult winner = tuneConfiguration.getWinner();
        assertThat(winner, is(notNullValue()));
        assertThat(parsed.command, is(equalTo(CCommand.Find)));
        assertThat(parsed.finder, is(notNullValue()));
        assertThat(parsed.finder.producerJava, hasSize(1));
        assertThat(parsed.finder.producerJava.get(0).batchSizeInBits, is(equalTo(winner.batchSizeInBits())));
    }

    @Test
    public void run_noProducerConfigured_throwsBecauseThereIsNoSweepTemplate() {
        // arrange
        CTuneConfiguration cTuneConfiguration = cpuTuneConfiguration();
        cTuneConfiguration.finder.producerJava.clear();

        // act + assert
        TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
        try {
            tuneConfiguration.run();
            throw new AssertionError("expected IllegalArgumentException for a configuration without any producer");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is(notNullValue()));
        }
    }

    // </editor-fold>

    // <editor-fold desc="verification cost stage">

    @Test
    public void run_withoutDatabase_reportsVerificationCostAsNotMeasured() {
        // arrange
        CTuneConfiguration cTuneConfiguration = cpuTuneConfiguration();

        // act
        TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
        tuneConfiguration.run();

        // assert
        assertThat(tuneConfiguration.getMeasuredVerificationCostMicros(), is(nullValue()));
    }

    @Test
    public void run_withDatabase_measuresAFinitePositiveVerificationCost() throws Exception {
        // arrange
        new LMDBPlatformAssume().assumeLMDBExecution();
        File lmdbDirectory =
                new TestAddressesLMDB().createTestLMDB(folder, new TestAddressesFiles(false), false, false);
        CTuneConfiguration cTuneConfiguration = cpuTuneConfiguration();
        cTuneConfiguration.finder.consumerJava.lmdbConfigurationReadOnly.lmdbDirectory =
                lmdbDirectory.getAbsolutePath();
        cTuneConfiguration.verificationCostSamples = 64;

        // act
        TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
        tuneConfiguration.run();

        // assert
        Double verificationCostMicros = tuneConfiguration.getMeasuredVerificationCostMicros();
        assertThat(verificationCostMicros, is(notNullValue()));
        assertThat(verificationCostMicros, is(greaterThan(0.0d)));
        assertThat(Double.isFinite(verificationCostMicros), is(equalTo(true)));
    }

    // </editor-fold>

    // <editor-fold desc="filter recommendation">

    @Test
    public void deriveFilterType_warmVerificationCost_prefersFuse16() {
        // The documented warm cost still leaves Fuse-8's 0.3874 % false positives dominating its
        // lower probe latency; this pins the arithmetic of the recommendation, which unlike the
        // throughput figures is machine-independent and therefore safe to assert.
        assertThat(
                TuneConfiguration.deriveFilterType(TuneConfiguration.VERIFICATION_COST_WARM_MICROS),
                is(equalTo(GpuFilterType.FUSE_16)));
    }

    @Test
    public void totalCostNanos_zeroVerificationCost_isTheBareProbeLatency() {
        assertThat(
                TuneConfiguration.totalCostNanos(GpuFilterType.FUSE_8, 0.0d),
                is(equalTo(TuneConfiguration.FUSE_8_PROBE_NANOS)));
        assertThat(
                TuneConfiguration.totalCostNanos(GpuFilterType.FUSE_16, 0.0d),
                is(equalTo(TuneConfiguration.FUSE_16_PROBE_NANOS)));
    }

    // </editor-fold>

    // <editor-fold desc="OpenCL">

    /**
     * The same mechanism assertions against an OpenCL template. Self-skips when no OpenCL 2.0+
     * device is present, which is the normal case on the plain CI matrix; the CPU tests above cover
     * the graceful-degradation path that machine takes.
     *
     * <p>Kept to a single arm with the GPU pre-filter disabled: this test exists to prove the
     * sweep drives an OpenCL producer through its per-arm lifecycle, and a filter build plus a
     * second kernel compile would add minutes without adding coverage of that.
     */
    @OpenCLTest
    @Test
    public void run_openClProducer_producesOneArmPerCandidatePair() {
        // arrange
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        int gridBits = Math.min(4, new OpenCLPlatformAssume().maxGridBitsForAvailableDevice());
        CTuneConfiguration cTuneConfiguration = cpuTuneConfiguration();
        cTuneConfiguration.finder.producerJava.clear();
        CProducerOpenCL cProducerOpenCL = new CProducerOpenCL();
        cProducerOpenCL.keyProducerId = "tuneKeyProducer";
        cProducerOpenCL.enableGpuFilter = false;
        cTuneConfiguration.finder.producerOpenCL.add(cProducerOpenCL);
        cTuneConfiguration.batchSizeInBitsCandidates = List.of(gridBits);
        cTuneConfiguration.keysPerWorkItemCandidates = List.of(1);

        // act
        TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
        tuneConfiguration.run();

        // assert
        assertThat(tuneConfiguration.getResults(), hasSize(1));
        assertThat(tuneConfiguration.getResults().get(0).batchSizeInBits(), is(equalTo(gridBits)));
    }

    // </editor-fold>

    /**
     * A CPU-producer tuning configuration with no database and sub-second arms.
     *
     * <p>{@code warmupSecondsPerArm = 0} on purpose: warmup exists to absorb kernel compilation and
     * GPU clock ramp, neither of which a CPU producer has, so here it would only spend fork time.
     */
    private CTuneConfiguration cpuTuneConfiguration() {
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = "tuneKeyProducer";

        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.keyProducerId = "tuneKeyProducer";

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.threads = 1;
        cConsumerJava.awaitQueueEmptySeconds = 10L;

        CFinder cFinder = new CFinder();
        cFinder.keyProducerJavaRandom.add(cKeyProducerJavaRandom);
        cFinder.producerJava.add(cProducerJava);
        cFinder.consumerJava = cConsumerJava;

        CTuneConfiguration cTuneConfiguration = new CTuneConfiguration();
        cTuneConfiguration.finder = cFinder;
        cTuneConfiguration.warmupSecondsPerArm = 0;
        cTuneConfiguration.secondsPerArm = SECONDS_PER_ARM;
        cTuneConfiguration.batchSizeInBitsCandidates = BATCH_SIZE_CANDIDATES;
        cTuneConfiguration.keysPerWorkItemCandidates = List.of(1);
        return cTuneConfiguration;
    }
}
