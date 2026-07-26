// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.not;
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
import nl.altindag.log.LogCaptor;
import org.jocl.CL;
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

    /**
     * An empty producer list used to be an error. It is instead the state most first runs are in —
     * the operator does not yet know what to write — so the tuner enumerates the machine's GPUs and
     * measures each. Being told "configure a producer first" is of no use to someone who ran the
     * tuner precisely to find out what to configure.
     *
     * <p>Only the GPU case is asserted here. The remaining error path (nothing configured and no
     * OpenCL device present) still throws, but reaching it would require a machine without a GPU or
     * mocking the device enumeration, and it is the branch that changed least.
     */
    @Test
    @OpenCLTest
    public void run_noProducerConfiguredButGpuPresent_sweepsTheDetectedDevices() {
        // arrange
        new OpenCLPlatformAssume().assumeOpenClLibraryAvailableAndOneOpenCL2_0OrGreaterDeviceAvailable();
        CTuneConfiguration cTuneConfiguration = cpuTuneConfiguration();
        cTuneConfiguration.finder.producerJava.clear();
        cTuneConfiguration.batchSizeInBitsCandidates =
                List.of(Math.min(4, new OpenCLPlatformAssume().maxGridBitsForAvailableDevice()));
        cTuneConfiguration.extendSweepWhenWinnerIsAtTheEdge = false;

        // act
        TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
        tuneConfiguration.run();

        // assert: a device was found, swept, and reported under its own label with its own winner
        assertThat(tuneConfiguration.getDeviceSweeps(), is(not(empty())));
        assertThat(tuneConfiguration.getResults(), is(not(empty())));
        for (TuneConfiguration.DeviceSweep sweep : tuneConfiguration.getDeviceSweeps()) {
            assertThat(sweep.deviceLabel(), containsString("platformIndex="));
            assertThat(sweep.arms(), is(not(empty())));
        }
        // the discovered devices become the configuration's producers, so the recommendation
        // describes the run that was measured rather than the empty list it started from
        assertThat(cTuneConfiguration.finder.producerOpenCL, is(not(empty())));
    }

    @Test
    public void dedupePhysicalDevices_sameCardUnderTwoPlatforms_collapsesToOnePerPhysicalGpu() {
        // The real topology of a Windows AMD box with a duplicate ICD: two platforms, each exposing
        // both physical GPUs, so four entries for two cards. Same PCIe fingerprint => same card.
        List<TuneConfiguration.DetectedDevice> detected = List.of(
                new TuneConfiguration.DetectedDevice(0, 0, "gfx1100", CL.CL_DEVICE_TYPE_GPU, "amd-pcie:3:0:0"),
                new TuneConfiguration.DetectedDevice(0, 1, "gfx1036", CL.CL_DEVICE_TYPE_GPU, "amd-pcie:16:0:0"),
                new TuneConfiguration.DetectedDevice(1, 0, "gfx1100", CL.CL_DEVICE_TYPE_GPU, "amd-pcie:3:0:0"),
                new TuneConfiguration.DetectedDevice(1, 1, "gfx1036", CL.CL_DEVICE_TYPE_GPU, "amd-pcie:16:0:0"));

        List<TuneConfiguration.DetectedDevice> deduped = TuneConfiguration.dedupePhysicalDevices(detected);

        // Two physical GPUs, first occurrence of each kept (platform 0).
        assertThat(deduped, hasSize(2));
        assertThat(deduped.get(0).platformIndex(), is(equalTo(0)));
        assertThat(deduped.get(0).deviceName(), is(equalTo("gfx1100")));
        assertThat(deduped.get(1).platformIndex(), is(equalTo(0)));
        assertThat(deduped.get(1).deviceName(), is(equalTo("gfx1036")));
    }

    @Test
    public void dedupePhysicalDevices_identicalCardsInOnePlatform_areKept() {
        // A mining rig: several identical cards in one platform share a name but sit in distinct PCIe
        // slots. They must NOT be collapsed — distinct fingerprints keep them all.
        List<TuneConfiguration.DetectedDevice> detected = List.of(
                new TuneConfiguration.DetectedDevice(0, 0, "gfx1030", CL.CL_DEVICE_TYPE_GPU, "amd-pcie:1:0:0"),
                new TuneConfiguration.DetectedDevice(0, 1, "gfx1030", CL.CL_DEVICE_TYPE_GPU, "amd-pcie:2:0:0"),
                new TuneConfiguration.DetectedDevice(0, 2, "gfx1030", CL.CL_DEVICE_TYPE_GPU, "amd-pcie:3:0:0"));

        assertThat(TuneConfiguration.dedupePhysicalDevices(detected), hasSize(3));
    }

    @Test
    public void dedupePhysicalDevices_unknownTopology_keepsEveryDeviceUnmerged() {
        // No topology available (null fingerprint): the tuner must never merge on the weaker name
        // signal, so every entry is kept — falling back to today's behaviour, not regressing it.
        List<TuneConfiguration.DetectedDevice> detected = List.of(
                new TuneConfiguration.DetectedDevice(0, 0, "GPU", CL.CL_DEVICE_TYPE_GPU, null),
                new TuneConfiguration.DetectedDevice(1, 0, "GPU", CL.CL_DEVICE_TYPE_GPU, null));

        assertThat(TuneConfiguration.dedupePhysicalDevices(detected), hasSize(2));
    }

    /**
     * A shared fingerprint across <em>differently named</em> devices is a driver defect, not a
     * duplicate, and must not merge them.
     *
     * <p>The dangerous direction of this feature is asymmetric. A missing fingerprint costs nothing —
     * both entries are kept, which is what happened before de-duplication existed. A fingerprint that
     * is wrongly identical silently halves a multi-GPU machine, and nothing in the run reports it. The
     * only source verified on hardware so far is AMD's topology extension; the two Khronos paths
     * ({@code cl_khr_device_uuid}, {@code cl_khr_pci_bus_info}) have never executed on a real device,
     * because the one dual-ICD machine available reports neither. A driver handing every device the
     * same UUID is exactly the failure that would not be noticed.
     *
     * <p>The device name is therefore a <b>veto</b>, never a merge signal — merging on names would
     * still be wrong, since a rig of identical cards shares one. Requiring both to agree keeps the
     * legitimate case working (one card through two ICDs reports the same name; verified as
     * {@code gfx1100} on both platforms of an AMD box) and makes the pathological case impossible.
     * Should two ICDs ever name one card differently, the merge is skipped and the behaviour falls
     * back to sweeping both — a missed optimisation, not a loss.
     */
    @Test
    public void dedupePhysicalDevices_sameFingerprintButDifferentNames_keepsBothRatherThanTrustingIt() {
        List<TuneConfiguration.DetectedDevice> detected = List.of(
                new TuneConfiguration.DetectedDevice(0, 0, "Radeon RX 7900 XTX", CL.CL_DEVICE_TYPE_GPU, "uuid:dead"),
                new TuneConfiguration.DetectedDevice(0, 1, "Radeon Graphics", CL.CL_DEVICE_TYPE_GPU, "uuid:dead"));

        List<TuneConfiguration.DetectedDevice> unique = TuneConfiguration.dedupePhysicalDevices(detected);

        assertThat(unique, hasSize(2));
    }

    /** The legitimate case must keep working: one card, two ICDs, same fingerprint and same name. */
    @Test
    public void dedupePhysicalDevices_sameFingerprintAndSameName_stillCollapses() {
        List<TuneConfiguration.DetectedDevice> detected = List.of(
                new TuneConfiguration.DetectedDevice(0, 0, "gfx1100", CL.CL_DEVICE_TYPE_GPU, "amd-pcie:3:0:0"),
                new TuneConfiguration.DetectedDevice(1, 0, "gfx1100", CL.CL_DEVICE_TYPE_GPU, "amd-pcie:3:0:0"));

        List<TuneConfiguration.DetectedDevice> unique = TuneConfiguration.dedupePhysicalDevices(detected);

        assertThat(unique, hasSize(1));
        assertThat(unique.get(0).platformIndex(), is(equalTo(0)));
    }

    @Test
    public void renderPerDeviceWinners_multipleDevices_listsEachDevicesOwnWinner() {
        TuneConfiguration.ArmResult fastWin =
                new TuneConfiguration.ArmResult(18, 16, 22_000_000.0d, 44_000_000.0d, 3.0d, null);
        TuneConfiguration.ArmResult slowWin =
                new TuneConfiguration.ArmResult(19, 64, 2_900_000.0d, 5_800_000.0d, 3.0d, null);
        List<TuneConfiguration.DeviceSweep> sweeps = List.of(
                new TuneConfiguration.DeviceSweep(
                        "gfx1100 (platformIndex=0, deviceIndex=0)", List.of(fastWin), fastWin),
                new TuneConfiguration.DeviceSweep(
                        "gfx1036 (platformIndex=0, deviceIndex=1)", List.of(slowWin), slowWin));

        String rendered = TuneConfiguration.renderPerDeviceWinners(sweeps);

        assertThat(rendered, containsString("Winner per device (MEASURED):"));
        assertThat(
                rendered,
                containsString("gfx1100 (platformIndex=0, deviceIndex=0): batchSizeInBits=18 keysPerWorkItem=16"));
        assertThat(
                rendered,
                containsString("gfx1036 (platformIndex=0, deviceIndex=1): batchSizeInBits=19 keysPerWorkItem=64"));
    }

    @Test
    public void renderPerDeviceWinners_singleDevice_isEmptySoItDoesNotRepeatTheGlobalWinner() {
        TuneConfiguration.ArmResult win =
                new TuneConfiguration.ArmResult(18, 16, 22_000_000.0d, 44_000_000.0d, 3.0d, null);
        List<TuneConfiguration.DeviceSweep> sweeps = List.of(
                new TuneConfiguration.DeviceSweep("gfx1100 (platformIndex=0, deviceIndex=0)", List.of(win), win));

        assertThat(TuneConfiguration.renderPerDeviceWinners(sweeps), is(equalTo("")));
    }

    @Test
    public void renderPerDeviceWinners_deviceWithoutAWinner_reportsNoMeasurement() {
        TuneConfiguration.ArmResult win =
                new TuneConfiguration.ArmResult(18, 16, 22_000_000.0d, 44_000_000.0d, 3.0d, null);
        List<TuneConfiguration.DeviceSweep> sweeps = List.of(
                new TuneConfiguration.DeviceSweep("gfx1100 (platformIndex=0, deviceIndex=0)", List.of(win), win),
                new TuneConfiguration.DeviceSweep("dead (platformIndex=1, deviceIndex=0)", List.of(), null));

        assertThat(
                TuneConfiguration.renderPerDeviceWinners(sweeps),
                containsString("dead (platformIndex=1, deviceIndex=0): no arm produced a usable measurement"));
    }

    /**
     * Each device keeps its own optimum. A single winner applied across devices would hand the
     * slower card the faster card's settings — on a contributor's laptop the integrated GPU would
     * have received {@code 24/2048} when its own optimum was {@code 23/2048}.
     */
    @Test
    public void applyWinnerTo_openClProducer_writesThatDevicesOwnValues() {
        CProducerOpenCL producer = new CProducerOpenCL();

        TuneConfiguration.applyWinnerTo(
                producer, new TuneConfiguration.ArmResult(23, 2048, 36_481_091.96d, 1.0d, 20.0d, null));

        assertThat(producer.batchSizeInBits, is(equalTo(23)));
        assertThat(producer.keysPerWorkItem, is(equalTo(2048)));
    }

    @Test
    public void applyWinnerTo_deviceWithoutAUsableArm_leavesTheProducerUntouched() {
        CProducerOpenCL producer = new CProducerOpenCL();
        int originalBatchSizeInBits = producer.batchSizeInBits;

        TuneConfiguration.applyWinnerTo(producer, null);

        assertThat(producer.batchSizeInBits, is(equalTo(originalBatchSizeInBits)));
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
        // This test is about the grid enumeration alone: one arm per candidate pair. Automatic
        // extension deliberately adds arms beyond the candidates when the winner lands on the edge
        // — which a one-by-one sweep always does — so it is switched off here rather than folded
        // into the expected count. Extension has its own tests in the "sweep extension" fold.
        cTuneConfiguration.extendSweepWhenWinnerIsAtTheEdge = false;

        // act
        TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
        tuneConfiguration.run();

        // assert
        assertThat(tuneConfiguration.getResults(), hasSize(1));
        assertThat(tuneConfiguration.getResults().get(0).batchSizeInBits(), is(equalTo(gridBits)));
    }

    /**
     * The extension must actually run, not merely be decidable. Asserted on real hardware: a
     * one-candidate sweep necessarily wins at the edge, so leaving extension on has to produce more
     * arms than candidates — the thing the unit tests around {@link TuneConfiguration#nextExtensionArm}
     * cannot show, because they never start a producer.
     */
    @Test
    @OpenCLTest
    public void run_openClProducerWinningAtTheEdge_measuresBeyondTheConfiguredCandidates() {
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
        cTuneConfiguration.maxExtensionArms = 1;

        // act
        TuneConfiguration tuneConfiguration = new TuneConfiguration(cTuneConfiguration);
        tuneConfiguration.run();

        // assert
        assertThat(tuneConfiguration.getResults().size(), is(greaterThan(1)));
    }

    // </editor-fold>

    /**
     * A CPU-producer tuning configuration with no database and sub-second arms.
     *
     * <p>{@code warmupSecondsPerArm = 0} on purpose: warmup exists to absorb kernel compilation and
     * GPU clock ramp, neither of which a CPU producer has, so here it would only spend fork time.
     */
    // <editor-fold desc="auto-discovery defaults">
    /**
     * An auto-discovered device must sweep <b>with the GPU pre-filter on</b>.
     *
     * <p>{@code CProducerOpenCL} defaults it off, and building the discovery prototype from bare
     * defaults produced three faults at once on a real machine:
     *
     * <ul>
     *   <li>The tuner builds a filter payload for {@code targetDatabaseEntries} regardless — a minute
     *       of work at 141 M entries — and then never used it.
     *   <li>It measured full-transfer throughput while claiming to measure the pipeline the operator
     *       will run, which the guide and the emitted recommendation both put behind a filter.
     *   <li>Every candidate crossed back to the host, so the result reader materialised
     *       {@code 2^batchSizeInBits} {@code PublicKeyBytes} per grid and the sweep died with
     *       {@code OutOfMemoryError: Java heap space} at {@code batchSizeInBits=22} on a 15.4 GiB
     *       heap — long before the shipped candidate list reaches 24.
     * </ul>
     *
     * <p>The shipped example carried {@code enableGpuFilter: true} until its {@code producerOpenCL}
     * list was emptied to demonstrate auto-discovery; emptying it silently moved the example onto the
     * defaults and took the setting away with it.
     */
    @Test
    public void autoDiscoveryPrototype_enablesTheGpuFilterRatherThanInheritingTheOffDefault() {
        CProducerOpenCL prototype = TuneConfiguration.autoDiscoveryPrototype("tuneKeyProducer");

        assertThat(prototype.enableGpuFilter, is(true));
        assertThat(prototype.transferAll, is(false));
        assertThat(prototype.keyProducerId, is(equalTo("tuneKeyProducer")));
    }

    /**
     * An operator who turns the filter off explicitly keeps that choice, but must be told what it
     * costs before a 20-minute sweep dies on the heap rather than on the device.
     */
    @Test
    public void hostHeapWarning_filterOffAtALargeGrid_warnsAboutTheHostHeapNotTheDevice() {
        String warning = TuneConfiguration.hostHeapWarning(22, false, 8, 16L * 1024 * 1024 * 1024);

        assertThat(warning, not(equalTo("")));
        assertThat(warning, containsString("enableGpuFilter"));
        assertThat(warning, containsString("heap"));
    }

    @Test
    public void hostHeapWarning_filterOn_saysNothing() {
        assertThat(TuneConfiguration.hostHeapWarning(24, true, 8, 16L * 1024 * 1024 * 1024), is(equalTo("")));
    }

    /** A small grid transfers little, so the warning must not fire on every filterless run. */
    @Test
    public void hostHeapWarning_filterOffButSmallGrid_saysNothing() {
        assertThat(TuneConfiguration.hostHeapWarning(16, false, 8, 16L * 1024 * 1024 * 1024), is(equalTo("")));
    }
    // </editor-fold>

    // <editor-fold desc="multi device">
    /**
     * Every GPU the machine has must end up swept, because that is what someone with two cards
     * expects when they run a tuner once.
     *
     * <p>The old behaviour took {@code producerOpenCL.get(0)} silently: a dual-GPU configuration
     * produced a full-looking report for one device and a paste-ready configuration in which the
     * second entry still carried the user's guesses, visually indistinguishable from the measured
     * one. On a real contributor's machine that guess was 2.3× off its device's optimum.
     */
    @Test
    public void templatesForDevices_twoDevices_producesOneTemplateEachCarryingItsIndices() {
        CProducerOpenCL prototype = new CProducerOpenCL();
        prototype.keyProducerId = "tuneKeyProducer";
        List<TuneConfiguration.DetectedDevice> devices = List.of(
                new TuneConfiguration.DetectedDevice(0, 0, "NVIDIA RTX 500 Ada", CL.CL_DEVICE_TYPE_GPU),
                new TuneConfiguration.DetectedDevice(1, 0, "Intel(R) Arc(TM) Pro Graphics", CL.CL_DEVICE_TYPE_GPU));

        List<CProducerOpenCL> templates = TuneConfiguration.templatesForDevices(devices, prototype);

        assertThat(templates, hasSize(2));
        assertThat(templates.get(0).platformIndex, is(equalTo(0)));
        assertThat(templates.get(0).deviceIndex, is(equalTo(0)));
        assertThat(templates.get(1).platformIndex, is(equalTo(1)));
        assertThat(templates.get(1).deviceIndex, is(equalTo(0)));
    }

    /**
     * Everything the sweep does not vary has to survive the copy, or the measured configuration
     * would not be the configuration the operator then runs — the property the whole command rests
     * on.
     */
    @Test
    public void templatesForDevices_prototypeSettings_areCarriedIntoEveryTemplate() {
        CProducerOpenCL prototype = new CProducerOpenCL();
        prototype.keyProducerId = "tuneKeyProducer";
        prototype.maxResultReaderThreads = 7;
        prototype.useSafeGcdInverse = false;
        prototype.gpuFilterType = GpuFilterType.FUSE_16;

        List<CProducerOpenCL> templates = TuneConfiguration.templatesForDevices(
                List.of(new TuneConfiguration.DetectedDevice(2, 1, "GPU", CL.CL_DEVICE_TYPE_GPU)), prototype);

        assertThat(templates, hasSize(1));
        assertThat(templates.get(0).maxResultReaderThreads, is(equalTo(7)));
        assertThat(templates.get(0).useSafeGcdInverse, is(equalTo(false)));
        assertThat(templates.get(0).gpuFilterType, is(equalTo(GpuFilterType.FUSE_16)));
        assertThat(templates.get(0).keyProducerId, is(equalTo("tuneKeyProducer")));
    }

    /** A template must be an independent object, or tuning device 2 would overwrite device 1. */
    @Test
    public void templatesForDevices_templates_areIndependentOfTheProtoypeAndEachOther() {
        CProducerOpenCL prototype = new CProducerOpenCL();
        prototype.keyProducerId = "tuneKeyProducer";
        List<CProducerOpenCL> templates = TuneConfiguration.templatesForDevices(
                List.of(
                        new TuneConfiguration.DetectedDevice(0, 0, "A", CL.CL_DEVICE_TYPE_GPU),
                        new TuneConfiguration.DetectedDevice(1, 0, "B", CL.CL_DEVICE_TYPE_GPU)),
                prototype);

        templates.get(0).batchSizeInBits = 22;

        assertThat(templates.get(1).batchSizeInBits, is(not(equalTo(22))));
        assertThat(prototype.batchSizeInBits, is(not(equalTo(22))));
    }

    /**
     * Only GPUs. A CPU OpenCL runtime (pocl, Intel's CPU ICD) enumerates as a device but duplicates
     * what {@code producerJava} already does, so sweeping it spends real minutes measuring the wrong
     * thing — and our own OpenCL CI job runs on pocl.
     */
    @Test
    public void detectedDevice_cpuRuntime_isNotTreatedAsAGpu() {
        assertThat(new TuneConfiguration.DetectedDevice(0, 0, "pocl CPU", CL.CL_DEVICE_TYPE_CPU).isGpu(), is(false));
        assertThat(new TuneConfiguration.DetectedDevice(0, 0, "Radeon", CL.CL_DEVICE_TYPE_GPU).isGpu(), is(true));
    }
    // </editor-fold>

    // <editor-fold desc="sweep extension">
    /**
     * A sweep whose winner sits at the largest value it tried has measured a lower bound, not a
     * peak — and telling the user to run the whole thing again is not a fix, because they run it
     * once. Both of a contributor's first sweeps won at `keysPerWorkItem=256`, the last candidate in
     * the shipped list; widening it by hand afterwards gained 17 % on one GPU and 104 % on the
     * other. The sweep must therefore carry on by itself rather than emit advice.
     */
    @Test
    public void nextExtensionArm_winnerAtTheLargestSweptKeysPerWorkItem_continuesByDoubling() {
        TuneConfiguration.ArmResult winner =
                new TuneConfiguration.ArmResult(23, 256, 94_791_059.02d, 1_473_710.16d, 20.0d, null);

        TuneConfiguration.SweepExtension next = TuneConfiguration.nextExtensionArm(winner, 256, 24, true);

        assertThat(next, is(notNullValue()));
        assertThat(next.keysPerWorkItem(), is(equalTo(512)));
        assertThat(next.batchSizeInBits(), is(equalTo(23)));
    }

    /**
     * On a multi-device sweep the extension decision must be made against <b>the device being
     * swept</b>, never against the best arm across all devices.
     *
     * <p>Found on a dual-GPU machine: the slower integrated GPU won at the largest
     * {@code keysPerWorkItem} tried and should have been extended, but was not, because the decision
     * consulted the global winner — the discrete card's arm, which sat comfortably inside its range
     * and therefore reported "nothing to extend". The slower device's winner is then a silent lower
     * bound, which is the exact defect the extension exists to prevent.
     *
     * <p>Invisible on a single-GPU box, where the global winner and the device winner are the same
     * arm. Passing the device's own arms in makes the confusion structurally impossible rather than
     * merely fixed.
     */
    @Test
    public void nextExtensionArmForDevice_deviceWinnerAtTheEdgeWhileAFasterDeviceIsNot_stillExtends() {
        // This device peaked at the largest keysPerWorkItem it was given.
        List<TuneConfiguration.ArmResult> slowDeviceArms = List.of(
                new TuneConfiguration.ArmResult(21, 16, 400_000.0d, 1.0d, 20.0d, null),
                new TuneConfiguration.ArmResult(21, 64, 1_152_911.61d, 1.0d, 20.0d, null));

        TuneConfiguration.SweepExtension next =
                TuneConfiguration.nextExtensionArmForDevice(slowDeviceArms, 64, 21, true);

        assertThat(next, is(notNullValue()));
        assertThat(next.keysPerWorkItem(), is(equalTo(128)));
        assertThat(next.batchSizeInBits(), is(equalTo(21)));
    }

    /** A device whose own winner sits inside its range still must not be extended. */
    @Test
    public void nextExtensionArmForDevice_deviceWinnerInsideItsRange_doesNotExtend() {
        List<TuneConfiguration.ArmResult> fastDeviceArms = List.of(
                new TuneConfiguration.ArmResult(19, 4, 11_107_682.60d, 1.0d, 20.0d, null),
                new TuneConfiguration.ArmResult(19, 64, 5_000_000.0d, 1.0d, 20.0d, null));

        assertThat(TuneConfiguration.nextExtensionArmForDevice(fastDeviceArms, 64, 21, true), is(nullValue()));
    }

    @Test
    public void nextExtensionArmForDevice_deviceProducedNoUsableArm_doesNotExtend() {
        List<TuneConfiguration.ArmResult> failedOnly =
                List.of(new TuneConfiguration.ArmResult(21, 64, 0.0d, 0.0d, 0.0d, "CLException: CL_OUT_OF_RESOURCES"));

        assertThat(TuneConfiguration.nextExtensionArmForDevice(failedOnly, 64, 21, true), is(nullValue()));
    }

    @Test
    public void nextExtensionArm_winnerInsideTheSweptRange_doesNotContinue() {
        TuneConfiguration.ArmResult winner =
                new TuneConfiguration.ArmResult(23, 256, 94_791_059.02d, 1_473_710.16d, 20.0d, null);

        assertThat(TuneConfiguration.nextExtensionArm(winner, 2048, 24, true), is(nullValue()));
    }

    /**
     * {@code keysPerWorkItem} may not exceed the grid: {@code OpenClTask} rejects a value larger
     * than {@code 2^batchSizeInBits}. Extending into that would produce an arm the device refuses.
     */
    @Test
    public void nextExtensionArm_doublingWouldExceedTheGrid_doesNotContinue() {
        TuneConfiguration.ArmResult winner = new TuneConfiguration.ArmResult(4, 16, 1_000.0d, 10.0d, 20.0d, null);

        assertThat(TuneConfiguration.nextExtensionArm(winner, 16, 5, true), is(nullValue()));
    }

    @Test
    public void nextExtensionArm_winnerAtTheLargestSweptBatchSizeBelowTheCap_continuesByOneBit() {
        TuneConfiguration.ArmResult winner = new TuneConfiguration.ArmResult(20, 64, 60_076_715.26d, 1.0d, 20.0d, null);

        TuneConfiguration.SweepExtension next = TuneConfiguration.nextExtensionArm(winner, 2048, 20, true);

        assertThat(next, is(notNullValue()));
        assertThat(next.batchSizeInBits(), is(equalTo(21)));
        assertThat(next.keysPerWorkItem(), is(equalTo(64)));
    }

    /**
     * The distinction that keeps this from becoming noise: <b>the edge of your list</b> is worth
     * continuing past, <b>the edge of what the framework supports</b> is not. A winner at
     * {@code batchSizeInBits=24} is at {@code BIT_COUNT_FOR_MAX_CHUNKS_ARRAY} and there is nothing
     * above it — extending would produce a rejected arm, and warning about it would fire on most
     * default runs, which is how a warning stops being read.
     */
    @Test
    public void nextExtensionArm_winnerAtTheFrameworkMaximum_doesNotContinue() {
        TuneConfiguration.ArmResult winner =
                new TuneConfiguration.ArmResult(24, 2048, 110_725_506.61d, 1_727_398.44d, 20.0d, null);

        // at the largest swept batch size AND at the framework cap, and kpwi is inside its range
        assertThat(TuneConfiguration.nextExtensionArm(winner, 4096, 24, true), is(nullValue()));
    }

    /**
     * {@code keysPerWorkItem} has no meaning for a CPU producer — the candidates collapse to
     * repeated measurements of one arm — so extending along it would burn minutes measuring the same
     * thing repeatedly.
     */
    @Test
    public void nextExtensionArm_cpuProducer_doesNotContinue() {
        TuneConfiguration.ArmResult winner = new TuneConfiguration.ArmResult(20, 256, 1_000_000.0d, 1.0d, 20.0d, null);

        assertThat(TuneConfiguration.nextExtensionArm(winner, 256, 20, false), is(nullValue()));
    }

    /** A failed winner is no basis for deciding where to look next. */
    @Test
    public void nextExtensionArm_winnerIsAFailedArm_doesNotContinue() {
        TuneConfiguration.ArmResult failed =
                new TuneConfiguration.ArmResult(23, 256, 0.0d, 0.0d, 0.0d, "CLException: CL_OUT_OF_HOST_MEMORY");

        assertThat(TuneConfiguration.nextExtensionArm(failed, 256, 24, true), is(nullValue()));
    }

    /**
     * The report has to tell the two boundaries apart in words too, or a reader sees a winner at the
     * top of the table and cannot know whether the sweep stopped early or ran out of room.
     */
    @Test
    public void winnerBoundaryNote_winnerAtTheFrameworkMaximum_saysItIsTheMaximumNotATruncatedSweep() {
        String note = TuneConfiguration.winnerBoundaryNote(
                new TuneConfiguration.ArmResult(24, 2048, 110_725_506.61d, 1_727_398.44d, 20.0d, null));

        assertThat(note, containsString("framework maximum"));
        assertThat(note, not(containsString("truncated")));
    }

    @Test
    public void winnerBoundaryNote_winnerBelowTheFrameworkMaximum_saysNothing() {
        String note = TuneConfiguration.winnerBoundaryNote(
                new TuneConfiguration.ArmResult(21, 512, 97_828_095.12d, 1_524_704.05d, 20.0d, null));

        assertThat(note, is(equalTo("")));
    }
    // </editor-fold>

    // <editor-fold desc="report rendering">
    /**
     * The three arm outcomes must be distinguishable <b>in the rendered output</b>, not merely in
     * the model. Classification was already covered; what a user actually reads was not, and the
     * whole reason the Intel Arc investigation took as long as it did is that two different
     * situations printed the same way.
     */
    @Test
    public void describeArm_measuredArm_rendersTheRateAndNoFailureMarker() {
        String rendered = TuneConfiguration.describeArm(
                new TuneConfiguration.ArmResult(23, 2048, 36_481_091.96d, 565_148.11d, 20.0d, null));

        assertThat(rendered, containsString("batchSizeInBits=23"));
        assertThat(rendered, containsString("keysPerWorkItem=2048"));
        assertThat(rendered, containsString("36,481,091.96 candidates/s"));
        assertThat(rendered, not(containsString("FAILED")));
        assertThat(rendered, not(containsString("NOT MEASURED")));
    }

    @Test
    public void describeArm_driverRejectedArm_saysFailedAndCarriesTheCause() {
        String rendered = TuneConfiguration.describeArm(
                new TuneConfiguration.ArmResult(24, 1, 0.0d, 0.0d, 0.0d, "CLException: CL_OUT_OF_HOST_MEMORY"));

        assertThat(rendered, containsString("FAILED"));
        assertThat(rendered, containsString("CL_OUT_OF_HOST_MEMORY"));
        assertThat(rendered, not(containsString("NOT MEASURED")));
    }

    /**
     * A bare {@code 0.00} was read as a hardware failure by a user in the field. The rendered line
     * must say it is not one, and must say what to do about it.
     */
    @Test
    public void describeArm_armTooSlowForTheWindow_saysNotMeasuredAndExplainsTheRemedy() {
        String rendered =
                TuneConfiguration.describeArm(new TuneConfiguration.ArmResult(22, 1, 0.0d, 0.0d, 20.0d, null));

        assertThat(rendered, containsString("NOT MEASURED"));
        assertThat(rendered, not(containsString("FAILED")));
        // the two things a reader needs: that it is not an error, and the lever to pull
        assertThat(rendered, containsString("Not a device or driver error"));
        assertThat(rendered, containsString("secondsPerArm"));
    }

    /**
     * The report is emitted one log record per line rather than as one multi-line message, so it
     * stays readable under the log pattern's CRLF guard. Asserted end to end on a real sweep: a
     * reverted call site in {@code TuneConfiguration} would otherwise leave every unit test green
     * while users got a single ` | `-separated line back.
     */
    @Test
    public void run_cpuProducerWithoutDatabase_reportIsLoggedOneRecordPerLine() {
        // arrange
        CTuneConfiguration cTuneConfiguration = cpuTuneConfiguration();

        try (LogCaptor logCaptor = LogCaptor.forClass(TuneConfiguration.class)) {
            // act
            new TuneConfiguration(cTuneConfiguration).run();

            // assert
            List<String> infoLogs = logCaptor.getInfoLogs();
            assertThat(infoLogs, hasItem(equalTo("########## BEGIN TuneConfiguration report ##########")));
            assertThat(infoLogs, hasItem(equalTo("########## END TuneConfiguration report ##########")));
            assertThat(infoLogs, hasItem(containsString("Winner (MEASURED)")));
            assertThat(infoLogs, hasItem(containsString("batchSizeInBits  keysPerWorkItem")));
            // the property the split exists to preserve
            assertThat(infoLogs.stream().anyMatch(m -> m.indexOf('\n') >= 0 || m.indexOf('\r') >= 0), is(false));
        }
    }
    // </editor-fold>

    // <editor-fold desc="crashed producer">
    /**
     * An arm whose producer died part-way through the window must be recorded as a failure, not as
     * the throughput it managed before dying.
     *
     * <p>This is the defect a real sweep exposed: on an Intel Arc, the {@code batchSizeInBits=19,
     * keysPerWorkItem=8192} arm's producer hit {@code CL_OUT_OF_RESOURCES} seconds into its window
     * and stopped, yet the report presented "1,022,288 candidates/s" for it, indistinguishable from
     * a healthy measurement — the only tell was an {@code addresses checked} of 0.00, which is easy
     * to read past. A number produced by a producer that was dead for most of the window is not a
     * measurement of anything, and worse, it was the arm that preceded a cascade of failures whose
     * cause it would have identified.
     */
    @Test
    public void armResultFor_producerFailedDuringTheWindow_isRecordedAsFailedNotAsAMeasurement() {
        RuntimeException producerFailure = new RuntimeException("CL_OUT_OF_RESOURCES");

        TuneConfiguration.ArmResult result =
                TuneConfiguration.armResultFor(19, 8192, 1_022_288.82d, 0.0d, 20.0d, producerFailure);

        assertThat(result.succeeded(), is(false));
        assertThat(result.failure(), containsString("CL_OUT_OF_RESOURCES"));
        // the partial rate must not survive into the report as if it meant something
        assertThat(result.candidatesPerSecond(), is(0.0d));
    }

    /**
     * The counterpart: a producer that survived its window keeps the rate it measured. Without this,
     * the fix above would turn every arm into a failure.
     */
    @Test
    public void armResultFor_producerReportedNoFailure_keepsTheMeasuredRate() {
        TuneConfiguration.ArmResult result =
                TuneConfiguration.armResultFor(23, 2048, 36_481_091.96d, 565_148.11d, 20.0d, null);

        assertThat(result.succeeded(), is(true));
        assertThat(result.failure(), is(nullValue()));
        assertThat(result.candidatesPerSecond(), is(36_481_091.96d));
        assertThat(result.addressesCheckedPerSecond(), is(565_148.11d));
    }
    // </editor-fold>

    // <editor-fold desc="ArmResult classification">
    /**
     * An arm that produced no throughput without failing must be classified as "too slow to
     * measure", not lumped in with driver rejections. Field reports showed a bare {@code 0.00} row
     * being read as a hardware failure when in fact one launch simply outlasted the measurement
     * window (an integrated GPU at {@code batchSizeInBits=22, keysPerWorkItem=1} needs ~40 s against
     * a 20 s window). The two call for different responses, so they must not render alike.
     */
    @Test
    public void tooSlowToMeasure_zeroThroughputWithoutFailure_isDistinguishedFromAFailure() {
        TuneConfiguration.ArmResult tooSlow = new TuneConfiguration.ArmResult(22, 1, 0.0d, 0.0d, 20.0d, null);

        assertThat(tooSlow.tooSlowToMeasure(), is(true));
        assertThat(tooSlow.succeeded(), is(false));
    }

    @Test
    public void tooSlowToMeasure_driverRejectedArm_isNotClassifiedAsTooSlow() {
        TuneConfiguration.ArmResult failed =
                new TuneConfiguration.ArmResult(24, 1, 0.0d, 0.0d, 0.0d, "CL_OUT_OF_RESOURCES");

        assertThat(failed.tooSlowToMeasure(), is(false));
        assertThat(failed.succeeded(), is(false));
    }

    @Test
    public void tooSlowToMeasure_armThatProducedThroughput_isNotClassifiedAsTooSlow() {
        TuneConfiguration.ArmResult measured =
                new TuneConfiguration.ArmResult(20, 256, 17_877_410.33d, 278_229.79d, 20.0d, null);

        assertThat(measured.tooSlowToMeasure(), is(false));
        assertThat(measured.succeeded(), is(true));
    }
    // </editor-fold>

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
        // The default is the real Light DB tier (141 M entries). Auto-discovered devices now sweep
        // with the GPU pre-filter on, so that default would have every OpenCL test build a genuine
        // 141 M-entry Binary Fuse filter — several GB of peeling inside a 2 GB Surefire fork. The
        // filter's size is irrelevant to what these tests assert; only that one exists.
        cTuneConfiguration.targetDatabaseEntries = 1_000L;
        cTuneConfiguration.warmupSecondsPerArm = 0;
        cTuneConfiguration.secondsPerArm = SECONDS_PER_ARM;
        cTuneConfiguration.batchSizeInBitsCandidates = BATCH_SIZE_CANDIDATES;
        cTuneConfiguration.keysPerWorkItemCandidates = List.of(1);
        return cTuneConfiguration;
    }
}
