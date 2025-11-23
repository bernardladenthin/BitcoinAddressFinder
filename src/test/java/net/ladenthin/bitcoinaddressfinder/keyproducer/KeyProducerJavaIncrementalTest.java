// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.math.BigInteger;

import net.ladenthin.bitcoinaddressfinder.*;

import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaIncremental;
import static org.junit.Assert.fail;

import org.bitcoinj.base.Network;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import org.slf4j.Logger;

/**
 * Tests for KeyProducerJavaIncremental with respect to key range boundaries and batch handling.
 *
 * <p>Behavior Matrix for createSecrets(batchSize, returnStartSecretOnly=false):
 * <table border="1" cellpadding="4" cellspacing="0">
 *   <tr>
 *     <th>Scenario</th>
 *     <th>currentValue position</th>
 *     <th>Batch Size</th>
 *     <th>Batch fully within range?</th>
 *     <th>Expected Result</th>
 *     <th>Exception?</th>
 *     <th>Related Test(s)</th>
 *   </tr>
 *   <tr>
 *     <td>1. Start of range, batch fits</td>
 *     <td>At startAddress (min)</td>
 *     <td>≤ keys remaining</td>
 *     <td>Yes</td>
 *     <td>Returns full batch sequential keys</td>
 *     <td>No</td>
 *     <td><code>createSecrets_returnStartSecretOnlyFalse_returnsBatchSecrets</code>, <br><code>createSecrets_currentValueAdvancesByBatchSize</code></td>
 *   </tr>
 *   <tr>
 *     <td>2. Inside range, batch fits</td>
 *     <td>Inside range (between start and end)</td>
 *     <td>≤ keys remaining</td>
 *     <td>Yes</td>
 *     <td>Returns full batch sequential keys</td>
 *     <td>No</td>
 *     <td><code>createSecrets_currentValueAdvancesByBatchSize</code></td>
 *   </tr>
 *   <tr>
 *     <td>3. Inside range, partial batch</td>
 *     <td>Inside range (between start and end)</td>
 *     <td>Partial batch (batch size &gt; keys remaining)</td>
 *     <td>No</td>
 *     <td>Throws NoMoreSecretsAvailableException</td>
 *     <td>Yes</td>
 *     <td><code>createSecrets_threeBatches_twoElementsEach_thirdThrowsException</code></td>
 *   </tr>
 *   <tr>
 *     <td>4. At end, batch size = 1, returnStartSecretOnly=true</td>
 *     <td>At endAddress (max)</td>
 *     <td>1</td>
 *     <td>Yes (single key)</td>
 *     <td>Returns single key (currentValue)</td>
 *     <td>No</td>
 *     <td><code>createSecrets_returnStartSecretOnlyTrue_returnsOneSecret</code></td>
 *   </tr>
 *   <tr>
 *     <td>5. At end, batch size > 1, partial batch not allowed</td>
 *     <td>At endAddress (max)</td>
 *     <td>Batch size &gt; 1</td>
 *     <td>No (batch overruns end)</td>
 *     <td>Throws NoMoreSecretsAvailableException</td>
 *     <td>Yes</td>
 *     <td><code>createSecrets_endAddressInclusive_butPartialBatchNotAllowed_throwsException</code></td>
 *   </tr>
 *   <tr>
 *     <td>6. Beyond end address</td>
 *     <td>Beyond endAddress</td>
 *     <td>Any</td>
 *     <td>N/A</td>
 *     <td>Throws NoMoreSecretsAvailableException immediately</td>
 *     <td>Yes</td>
 *     <td><code>createSecrets_startExceedsEnd_throwsException</code></td>
 *   </tr>
 *   <tr>
 *     <td>7. End address exactly on batch boundary</td>
 *     <td>At endAddress (max)</td>
 *     <td>Batch size divides range exactly</td>
 *     <td>Yes</td>
 *     <td>Returns all batches fully</td>
 *     <td>No</td>
 *     <td><code>createSecrets_endAddressExactlyAtBatchBoundary_allBatchesValid</code></td>
 *   </tr>
 *   <tr>
 *     <td>8. Partial batch allowed with returnStartSecretOnly=true at end</td>
 *     <td>At endAddress (max)</td>
 *     <td>Batch size &gt; keys remaining</td>
 *     <td>No</td>
 *     <td>Returns single start key without exception</td>
 *     <td>No</td>
 *     <td><code>createSecrets_endAddressInclusive_partialBatchAllowedWithReturnStartOnly_noException</code></td>
 *   </tr>
 *   <tr>
 *     <td>9. Batch larger than keys before end (batchExceedsEnd)</td>
 *     <td>Inside range or start</td>
 *     <td>Batch size &gt; keys remaining</td>
 *     <td>No</td>
 *     <td>Throws NoMoreSecretsAvailableException</td>
 *     <td>Yes</td>
 *     <td><code>createSecrets_batchExceedsEnd_throwsException</code></td>
 *   </tr>
 * </table>
 *
 * <p>Note: When <code>returnStartSecretOnly=true</code>, only the starting key of the batch is returned
 * regardless of batch size, allowing partial batch retrieval without exception.
 */
public class KeyProducerJavaIncrementalTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);

    private KeyUtility keyUtility;
    private BitHelper bitHelper;
    private String startHex;
    private String endHex;
    
    private Logger mockLogger;

    @Before
    public void setUp() {
        keyUtility = new KeyUtility(network, byteBufferUtility);
        bitHelper = new BitHelper();
        startHex = PublicKeyBytes.MIN_VALID_PRIVATE_KEY_HEX;
        endHex = "000000000000000000000000000000000000000000000000000000000000000A";
        mockLogger = mock(Logger.class);
    }

    private KeyProducerJavaIncremental createKeyProducerJavaIncremental(String start, String end) {
        CKeyProducerJavaIncremental config = new CKeyProducerJavaIncremental();
        config.startAddress = start;
        config.endAddress = end;
        return new KeyProducerJavaIncremental(config, keyUtility, bitHelper, mockLogger);
    }

    /**
     * Verifies that when requesting only the start secret, exactly one secret is returned,
     * regardless of batch size. This is important for scenarios where only the initial key is needed.
     */
    @Test
    public void createSecrets_returnStartSecretOnlyTrue_returnsOneSecret() throws Exception {
        KeyProducerJavaIncremental producer = createKeyProducerJavaIncremental(startHex, endHex);
        BigInteger[] secrets = producer.createSecrets(5, true);
        assertThat(secrets.length, is(equalTo(1)));
        assertThat(secrets[0], is(equalTo(new BigInteger(startHex, BitHelper.RADIX_HEX))));
    }

    /**
     * Tests that when requesting a full batch of secrets, the returned array has the requested batch size,
     * and that the keys are sequential starting from the configured start address.
     */
    @Test
    public void createSecrets_returnStartSecretOnlyFalse_returnsBatchSecrets() throws Exception {
        KeyProducerJavaIncremental producer = createKeyProducerJavaIncremental(startHex, endHex);
        int batchSize = 5;
        BigInteger[] secrets = producer.createSecrets(batchSize, false);
        assertThat(secrets.length, is(equalTo(batchSize)));

        BigInteger expected = new BigInteger(startHex, BitHelper.RADIX_HEX);
        for (int i = 0; i < batchSize; i++) {
            assertThat(secrets[i], is(equalTo(expected)));
            expected = expected.add(BigInteger.ONE);
        }
    }

    /**
     * Ensures that if the start key is greater than the configured end key,
     * the method immediately throws NoMoreSecretsAvailableException.
     * This prevents invalid key ranges.
     */
    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_startExceedsEnd_throwsException() throws Exception {
        KeyProducerJavaIncremental producer = createKeyProducerJavaIncremental(
                "0000000000000000000000000000000000000000000000000000000000000010",
                "0000000000000000000000000000000000000000000000000000000000000005"
        );
        producer.createSecrets(1, true);
    }

    /**
     * Tests that requesting a batch larger than the available keys before the end address
     * causes a NoMoreSecretsAvailableException to be thrown.
     */
    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_batchExceedsEnd_throwsException() throws Exception {
        KeyProducerJavaIncremental producer = createKeyProducerJavaIncremental(
                startHex,
                "0000000000000000000000000000000000000000000000000000000000000003"
        );
        producer.createSecrets(5, false);
    }

    /**
     * Verifies that the internal currentValue advances exactly by the batch size
     * after each call to createSecrets, ensuring consistent sequential key generation.
     */
    @Test
    public void createSecrets_currentValueAdvancesByBatchSize() throws Exception {
        KeyProducerJavaIncremental producer = createKeyProducerJavaIncremental(startHex, endHex);
        int batchSize = 3;
        
        BigInteger expectedFirstBatchStart = new BigInteger(startHex, BitHelper.RADIX_HEX);
        
        BigInteger[] secrets1 = producer.createSecrets(batchSize, false);
        verifySecrets(secrets1, expectedFirstBatchStart);
        
        BigInteger expectedSecondBatchStart = expectedFirstBatchStart.add(BigInteger.valueOf(batchSize));
        BigInteger[] secrets2 = producer.createSecrets(batchSize, false);
        verifySecrets(secrets2, expectedSecondBatchStart);
    }
    
    private void verifySecrets(BigInteger[] secrets, BigInteger expectedStart) {
        for (int i = 0; i < secrets.length; i++) {
            assertThat(secrets[i], is(equalTo(expectedStart.add(BigInteger.valueOf(i)))));
        }
    }
    
    /**
     * Tests the case where the end address is exactly at the boundary of a batch.
     * Confirms that all batches complete successfully without exceptions.
     */
    @Test
    public void createSecrets_endAddressExactlyAtBatchBoundary_allBatchesValid() throws Exception {
        // Setup: start=1, end=4 (allowed keys: 1, 2, 3, 4)
        KeyProducerJavaIncremental producer = createKeyProducerJavaIncremental(
            "0000000000000000000000000000000000000000000000000000000000000001",
            "0000000000000000000000000000000000000000000000000000000000000004"
        );
        int batchSize = 2;

        // 1st batch: [1, 2]
        BigInteger[] batch1 = producer.createSecrets(batchSize, false);
        assertThat(batch1.length, is(equalTo(batchSize)));
        assertThat(batch1[0], is(equalTo(BigInteger.ONE)));
        assertThat(batch1[1], is(equalTo(BigInteger.TWO)));

        // 2nd batch: [3, 4]
        BigInteger[] batch2 = producer.createSecrets(batchSize, false);
        assertThat(batch2.length, is(equalTo(batchSize)));
        assertThat(batch2[0], is(equalTo(BigInteger.valueOf(3))));
        assertThat(batch2[1], is(equalTo(BigInteger.valueOf(4))));
    }
    
    /**
     * Tests that if a batch would partially exceed the end address, an exception is thrown.
     * This test confirms partial batches are not allowed when returnStartSecretOnly is false.
     */
    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_threeBatches_twoElementsEach_thirdThrowsException() throws Exception {
        // Setup: start = 1, end = 5 (allowed keys: 1, 2, 3, 4, 5)
        KeyProducerJavaIncremental producer = createKeyProducerJavaIncremental(
            "0000000000000000000000000000000000000000000000000000000000000001",
            "0000000000000000000000000000000000000000000000000000000000000004"
        );
        int batchSize = 2;

        assertTwoBatchedOk(producer, batchSize);

        // 3rd batch: should throw NoMoreSecretsAvailableException because
        // the next values would be 5 and 6, but 6 exceeds the end address
        producer.createSecrets(batchSize, false);
    }
    
    /**
     * Similar to above, but with the end address included.
     * Demonstrates that partial batches beyond the end are disallowed and cause an exception,
     * even if the end address itself is technically within the allowed range.
     */
    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_endAddressInclusive_butPartialBatchNotAllowed_throwsException() throws Exception {
        // Setup: start=1, end=5 (allowed keys: 1, 2, 3, 4, 5)
        KeyProducerJavaIncremental producer = createKeyProducerJavaIncremental(
            "0000000000000000000000000000000000000000000000000000000000000001",
            "0000000000000000000000000000000000000000000000000000000000000005"
        );
        int batchSize = 2;

        assertTwoBatchedOk(producer, batchSize);

        // 3rd batch: tries [5, 6], but 6 > end (5), so exception thrown
        producer.createSecrets(batchSize, false);
    }
    
    /**
     * Tests that if partial batches are allowed by setting returnStartSecretOnly=true,
     * the last single key within the range can still be retrieved without exception,
     * even if a full batch would exceed the end address.
     */
    @Test
    public void createSecrets_endAddressInclusive_partialBatchAllowedWithReturnStartOnly_noException() throws Exception {
        // Setup: start=1, end=5 (allowed keys: 1, 2, 3, 4, 5)
        KeyProducerJavaIncremental producer = createKeyProducerJavaIncremental(
            "0000000000000000000000000000000000000000000000000000000000000001",
            "0000000000000000000000000000000000000000000000000000000000000005"
        );
        int batchSize = 2;

        assertTwoBatchedOk(producer, batchSize);

        // 3rd batch: returnStartSecretOnly = true, so only start value [5] is returned without exception
        BigInteger[] batch3 = producer.createSecrets(batchSize, true);
        assertThat(batch3.length, is(equalTo(1)));
        assertThat(batch3[0], is(equalTo(BigInteger.valueOf(5))));
    }

    private void assertTwoBatchedOk(KeyProducerJavaIncremental producer, int batchSize) {
        try {
            // 1st batch: [1, 2] – OK
            BigInteger[] batch1 = producer.createSecrets(batchSize, false);
            assertThat(batch1.length, is(equalTo(batchSize)));
            assertThat(batch1[0], is(equalTo(BigInteger.ONE)));
            assertThat(batch1[1], is(equalTo(BigInteger.TWO)));

            // 2nd batch: [3, 4] – OK
            BigInteger[] batch2 = producer.createSecrets(batchSize, false);
            assertThat(batch2.length, is(equalTo(batchSize)));
            assertThat(batch2[0], is(equalTo(BigInteger.valueOf(3))));
            assertThat(batch2[1], is(equalTo(BigInteger.valueOf(4))));
        } catch (NoMoreSecretsAvailableException e) {
            fail("Exception thrown too early: " + e.getMessage());
        }
    }
}