// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import org.junit.jupiter.api.Test;

/**
 * Smoke-loads every {@code examples/config_Find_*.json} fixture through
 * {@link Main#loadConfiguration(Path)} and asserts that the
 * {@code awaitTerminateSeconds} / {@code awaitQueueEmptySeconds} /
 * {@code statisticsRateWindowSeconds} fields are explicitly set in each file
 * (i.e. the example configs document these fields rather than hiding them
 * behind production defaults).
 *
 * <p>This test fails-loud if any future contributor strips the fields back
 * out — the documented contract is that all Find examples carry them.
 */
public class ConfigFixturesParseTest {

    private static final long EXPECTED_AWAIT_TERMINATE_SECONDS = 365L * 1000L * 24L * 3600L;
    private static final long EXPECTED_AWAIT_QUEUE_EMPTY_SECONDS = 60L;
    private static final int EXPECTED_STATISTICS_RATE_WINDOW_SECONDS = 60;

    public ConfigFixturesParseTest() {}

    @Test
    public void allFindExampleConfigs_carryExplicitAwaitTimeouts() throws Exception {
        for (String name : new String[] {
            "config_Find_1OpenCLDevice.json",
            "config_Find_1OpenCLDeviceAnd2CPUProducer.json",
            "config_Find_8CPUProducer.json",
            "config_Find_1CPUProducerBip39.json",
            "config_Find_SecretsFile.json"
        }) {
            Path file = Path.of("examples", name);
            String raw = Files.readString(file);
            assertThat(
                    name + " missing awaitTerminateSeconds",
                    raw.contains("\"awaitTerminateSeconds\""),
                    is(equalTo(true)));
            assertThat(
                    name + " missing awaitQueueEmptySeconds",
                    raw.contains("\"awaitQueueEmptySeconds\""),
                    is(equalTo(true)));
            assertThat(
                    name + " missing statisticsRateWindowSeconds",
                    raw.contains("\"statisticsRateWindowSeconds\""),
                    is(equalTo(true)));

            CConfiguration parsed = Main.loadConfiguration(file);
            assertThat(name + " finder", parsed.finder, is(notNullValue()));
            assertThat(
                    name + " awaitTerminateSeconds",
                    parsed.finder.awaitTerminateSeconds,
                    is(equalTo(EXPECTED_AWAIT_TERMINATE_SECONDS)));
            assertThat(name + " consumerJava", parsed.finder.consumerJava, is(notNullValue()));
            assertThat(
                    name + " awaitQueueEmptySeconds",
                    parsed.finder.consumerJava.awaitQueueEmptySeconds,
                    is(equalTo(EXPECTED_AWAIT_QUEUE_EMPTY_SECONDS)));
            assertThat(
                    name + " statisticsRateWindowSeconds",
                    parsed.finder.consumerJava.statisticsRateWindowSeconds,
                    is(equalTo(EXPECTED_STATISTICS_RATE_WINDOW_SECONDS)));
        }
    }

    /**
     * The tuning example carries a nested {@code finder} rather than a top-level one, so it is not
     * part of the loop above. It is smoke-loaded here for the same reason: an example config that
     * no longer parses is a broken promise to whoever copies it.
     */
    @Test
    public void tuneConfigurationExampleConfig_parses() throws Exception {
        Path file = Path.of("examples", "config_TuneConfiguration.json");

        CConfiguration parsed = Main.loadConfiguration(file);

        assertThat("tuneConfiguration", parsed.tuneConfiguration, is(notNullValue()));
        assertThat("tuneConfiguration.finder", parsed.tuneConfiguration.finder, is(notNullValue()));
        assertThat(
                "tuneConfiguration.finder.consumerJava",
                parsed.tuneConfiguration.finder.consumerJava,
                is(notNullValue()));
        assertThat("producerOpenCL", parsed.tuneConfiguration.finder.producerOpenCL.size(), is(equalTo(1)));
    }

    /**
     * The tuning example must sweep {@code batchSizeInBits} all the way to the hard framework
     * maximum, {@link OpenClKernelConstants#BIT_COUNT_FOR_MAX_CHUNKS_ARRAY}. Anchoring the assertion
     * on the constant (rather than a literal 24) means the fixture and the framework cap can never
     * silently drift apart: raising the cap without extending the example, or trimming the example
     * below the cap, fails here. Sweeping the top of the range is safe because a candidate too large
     * for the current device is caught and skipped (see {@code TuneConfiguration.runArm} and
     * {@code OpenCLContextAllocationFailureTest}).
     */
    @Test
    public void tuneConfigurationExampleConfig_sweepsBatchSizeUpToTechnicalMaximum() throws Exception {
        Path file = Path.of("examples", "config_TuneConfiguration.json");

        CConfiguration parsed = Main.loadConfiguration(file);

        assertThat("tuneConfiguration", parsed.tuneConfiguration, is(notNullValue()));
        int highestCandidate = Collections.max(parsed.tuneConfiguration.batchSizeInBitsCandidates);
        assertThat(
                "example config must sweep batchSizeInBits up to the framework maximum",
                highestCandidate,
                is(equalTo(OpenClKernelConstants.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY)));
    }
}
