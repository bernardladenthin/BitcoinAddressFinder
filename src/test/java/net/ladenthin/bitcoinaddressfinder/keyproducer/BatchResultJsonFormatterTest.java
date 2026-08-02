// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.Hit;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link BatchResultJsonFormatter}.
 *
 * <p>The wire format is shared by every transport that reports results, so it is pinned here once
 * rather than re-asserted per transport. Clients parse these field names; changing one silently
 * breaks every consumer, which is why the assertions name each of them explicitly.
 */
class BatchResultJsonFormatterTest {

    private final BatchResultJsonFormatter formatter = new BatchResultJsonFormatter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // <editor-fold defaultstate="collapsed" desc="formatBatch">
    @Test
    void formatBatch_withoutHits_reportsTheRangeAndAnEmptyHitList() throws Exception {
        // act
        final JsonNode node = objectMapper.readTree(
                formatter.formatBatch(new BatchResult(BigInteger.valueOf(0x4000L), 4096, List.of())));

        // assert
        assertThat(node.get("type").asText(), is(equalTo("batch")));
        assertThat(node.get("secretBase").asText(), is(equalTo("4000")));
        assertThat(node.get("checkedCount").asInt(), is(equalTo(4096)));
        assertThat(node.get("hits").size(), is(equalTo(0)));
    }

    @Test
    void formatBatch_withoutSecretBase_reportsNull() throws Exception {
        // act
        final JsonNode node = objectMapper.readTree(formatter.formatBatch(new BatchResult(null, 1, List.of())));

        // assert
        assertThat(node.get("secretBase").isNull(), is(true));
    }

    @Test
    void formatBatch_withHit_reportsEveryHitField() throws Exception {
        // arrange
        final Hit hit = new Hit(BigInteger.valueOf(73), "aabb", "1TestAddress", true, false);

        // act
        final JsonNode node =
                objectMapper.readTree(formatter.formatBatch(new BatchResult(BigInteger.ONE, 1, List.of(hit))));

        // assert
        final JsonNode reported = node.get("hits").get(0);
        assertThat(reported.get("privateKey").asText(), is(equalTo("49")));
        assertThat(reported.get("hash160Hex").asText(), is(equalTo("aabb")));
        assertThat(reported.get("address").asText(), is(equalTo("1TestAddress")));
        assertThat(reported.get("compressed").asBoolean(), is(true));
        assertThat(reported.get("vanity").asBoolean(), is(false));
    }

    @Test
    void formatBatch_anyBatch_isASingleLine() throws Exception {
        // Line-delimited transports frame on the newline, so an embedded one would split a message.
        // act
        final String json = formatter.formatBatch(
                new BatchResult(BigInteger.ONE, 1, List.of(new Hit(BigInteger.ONE, "aa", "addr", false, false))));

        // assert
        assertThat(json.contains("\n"), is(false));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="formatConfiguration">
    @Test
    void formatConfiguration_whenCalled_reportsGridAndSecretLength() throws Exception {
        // act
        final JsonNode node = objectMapper.readTree(formatter.formatConfiguration(18, true));

        // assert
        assertThat(node.get("type").asText(), is(equalTo("config")));
        assertThat(node.get("batchSizeInBits").asInt(), is(equalTo(18)));
        assertThat(node.get("batchUsePrivateKeyIncrement").asBoolean(), is(true));
        assertThat(node.get("secretByteLength").asInt(), is(equalTo(32)));
    }
    // </editor-fold>
}
