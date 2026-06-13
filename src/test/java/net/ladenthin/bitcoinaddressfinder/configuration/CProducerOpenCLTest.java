// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CProducerOpenCL}, focused on the GPU-filter wire-format flags.
 */
public class CProducerOpenCLTest {

    @Test
    public void defaults_enableGpuFilterAndTransferAll_areFalse() {
        // arrange + act
        CProducerOpenCL config = new CProducerOpenCL();

        // assert
        assertThat(config.enableGpuFilter, is(false));
        assertThat(config.transferAll, is(false));
    }

    @Test
    public void jsonRoundTrip_defaultFlags_surviveSerialiseDeserialise() throws Exception {
        // arrange
        // CProducer exposes a derived read-only getter (getOverallWorkSize) that serialises
        // as a property but has no setter; disable FAIL_ON_UNKNOWN_PROPERTIES so the round trip
        // tolerates it (production config JSON never carries that derived field).
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CProducerOpenCL original = new CProducerOpenCL();

        // act
        String json = mapper.writeValueAsString(original);
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.enableGpuFilter, is(false));
        assertThat(parsed.transferAll, is(false));
    }

    @Test
    public void jsonRoundTrip_explicitTrueFlags_surviveSerialiseDeserialise() throws Exception {
        // arrange
        // CProducer exposes a derived read-only getter (getOverallWorkSize) that serialises
        // as a property but has no setter; disable FAIL_ON_UNKNOWN_PROPERTIES so the round trip
        // tolerates it (production config JSON never carries that derived field).
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CProducerOpenCL original = new CProducerOpenCL();
        original.enableGpuFilter = true;
        original.transferAll = true;

        // act
        String json = mapper.writeValueAsString(original);
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.enableGpuFilter, is(true));
        assertThat(parsed.transferAll, is(true));
    }

    @Test
    public void jsonDeserialise_flagsAbsent_defaultToFalse() throws Exception {
        // arrange
        // CProducer exposes a derived read-only getter (getOverallWorkSize) that serialises
        // as a property but has no setter; disable FAIL_ON_UNKNOWN_PROPERTIES so the round trip
        // tolerates it (production config JSON never carries that derived field).
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = "{}";

        // act
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.enableGpuFilter, is(false));
        assertThat(parsed.transferAll, is(false));
    }
}
