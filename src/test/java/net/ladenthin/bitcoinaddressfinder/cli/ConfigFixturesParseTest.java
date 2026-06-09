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
import net.ladenthin.bitcoinaddressfinder.configuration.CConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Smoke-loads every {@code examples/config_Find_*.json} fixture through
 * {@link Main#loadConfiguration(Path)} and asserts that the new
 * {@code awaitTerminateSeconds} / {@code awaitQueueEmptySeconds} fields
 * are explicitly set in each file (i.e. the example configs document
 * the timeout fields rather than hiding them behind production defaults).
 *
 * <p>This test fails-loud if any future contributor strips the fields back
 * out — the documented contract is that all Find examples carry the timeouts.
 */
public class ConfigFixturesParseTest {

    private static final long EXPECTED_AWAIT_TERMINATE_SECONDS = 365L * 1000L * 24L * 3600L;
    private static final long EXPECTED_AWAIT_QUEUE_EMPTY_SECONDS = 60L;

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
        }
    }
}
