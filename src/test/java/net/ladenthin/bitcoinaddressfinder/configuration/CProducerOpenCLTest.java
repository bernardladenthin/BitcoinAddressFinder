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
    public void defaults_enableProfiling_isFalse() {
        // arrange + act
        CProducerOpenCL config = new CProducerOpenCL();

        // assert
        assertThat(config.enableProfiling, is(false));
    }

    @Test
    public void defaults_noInlineHelpers_isFalse() {
        // arrange + act
        CProducerOpenCL config = new CProducerOpenCL();

        // assert
        assertThat(config.noInlineHelpers, is(false));
    }

    @Test
    public void jsonRoundTrip_noInlineHelpers_survivesSerialiseDeserialise() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CProducerOpenCL original = new CProducerOpenCL();
        original.noInlineHelpers = true;

        // act
        String json = mapper.writeValueAsString(original);
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.noInlineHelpers, is(true));
    }

    @Test
    public void jsonRoundTrip_enableProfiling_survivesSerialiseDeserialise() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CProducerOpenCL original = new CProducerOpenCL();
        original.enableProfiling = true;

        // act
        String json = mapper.writeValueAsString(original);
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.enableProfiling, is(true));
    }

    @Test
    public void jsonDeserialise_enableProfilingAbsent_defaultsToFalse() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = "{}";

        // act
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.enableProfiling, is(false));
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

    @Test
    public void defaults_useSafeGcdInverse_isTrue() {
        // arrange + act
        CProducerOpenCL config = new CProducerOpenCL();

        // assert
        assertThat(config.useSafeGcdInverse, is(true));
    }

    @Test
    public void jsonRoundTrip_useSafeGcdInverseFalse_survivesSerialiseDeserialise() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CProducerOpenCL original = new CProducerOpenCL();
        original.useSafeGcdInverse = false;

        // act
        String json = mapper.writeValueAsString(original);
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.useSafeGcdInverse, is(false));
    }

    @Test
    public void jsonDeserialise_useSafeGcdInverseAbsent_defaultsToTrue() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = "{}";

        // act
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.useSafeGcdInverse, is(true));
    }

    @Test
    public void defaults_kernelProfileStage_isFull() {
        // arrange + act
        CProducerOpenCL config = new CProducerOpenCL();

        // assert
        assertThat(config.kernelProfileStage, is(KernelProfileStage.FULL));
    }

    @Test
    public void jsonRoundTrip_kernelProfileStage_survivesSerialiseDeserialise() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CProducerOpenCL original = new CProducerOpenCL();
        original.kernelProfileStage = KernelProfileStage.NO_HASH160;

        // act
        String json = mapper.writeValueAsString(original);
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.kernelProfileStage, is(KernelProfileStage.NO_HASH160));
    }

    @Test
    public void jsonDeserialise_kernelProfileStageAbsent_defaultsToFull() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = "{}";

        // act
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.kernelProfileStage, is(KernelProfileStage.FULL));
    }

    @Test
    public void defaults_logGpuDiagnostics_isFalse() {
        // arrange + act
        CProducerOpenCL config = new CProducerOpenCL();

        // assert
        assertThat(config.logGpuDiagnostics, is(false));
    }

    @Test
    public void jsonRoundTrip_logGpuDiagnostics_survivesSerialiseDeserialise() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CProducerOpenCL original = new CProducerOpenCL();
        original.logGpuDiagnostics = true;

        // act
        String json = mapper.writeValueAsString(original);
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.logGpuDiagnostics, is(true));
    }

    @Test
    public void defaults_useReducedRadixField_isTrue() {
        // arrange + act
        CProducerOpenCL config = new CProducerOpenCL();

        // assert: reduced-radix 2^26 is the default after the cross-device win was confirmed
        // (+22% RTX 3070 / +8% AMD RX 7900 XTX); see docs/performance.md.
        assertThat(config.useReducedRadixField, is(true));
    }

    @Test
    public void jsonRoundTrip_useReducedRadixFieldTrue_survivesSerialiseDeserialise() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CProducerOpenCL original = new CProducerOpenCL();
        original.useReducedRadixField = true;

        // act
        String json = mapper.writeValueAsString(original);
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.useReducedRadixField, is(true));
    }

    @Test
    public void jsonDeserialise_useReducedRadixFieldAbsent_defaultsToTrue() throws Exception {
        // arrange
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = "{}";

        // act
        CProducerOpenCL parsed = mapper.readValue(json, CProducerOpenCL.class);

        // assert
        assertThat(parsed.useReducedRadixField, is(true));
    }
}
