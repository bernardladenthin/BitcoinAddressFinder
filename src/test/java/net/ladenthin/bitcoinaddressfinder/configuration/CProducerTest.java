// @formatter:off
/**
 * Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder.configuration;

import java.io.IOException;
import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;

public class CProducerTest {
    
    private final BitHelper bitHelper = new BitHelper();
    
    @Before
    public void init() throws IOException {
    }

    // <editor-fold defaultstate="collapsed" desc="assertBatchSizeInBitsCorrect">
    @Test(expected = IllegalArgumentException.class)
    public void assertBatchSizeInBitsCorrect_batchSizeInBitsSetNegative_exceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = -1;

        // act, assert
        bitHelper.assertBatchSizeInBitsIsInRange(cProducer.batchSizeInBits);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void assertBatchSizeInBitsCorrect_batchSizeInBitsSetOverMaximum_exceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = PublicKeyBytes.BIT_COUNT_FOR_MAX_COORDINATE_PAIRS_ARRAY + 1;

        // act, assert
        bitHelper.assertBatchSizeInBitsIsInRange(cProducer.batchSizeInBits);
    }
    
    @Test
    public void assertBatchSizeInBitsCorrect_batchSizeInBitsNotSet_noExceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();

        // act, assert
        bitHelper.assertBatchSizeInBitsIsInRange(cProducer.batchSizeInBits);
    }

    @Test
    public void assertBatchSizeInBitsCorrect_batchSizeInBitsSetToZero_noExceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 0;

        // act, assert
        bitHelper.assertBatchSizeInBitsIsInRange(cProducer.batchSizeInBits);
    }

    @Test
    public void assertBatchSizeInBitsCorrect_batchSizeInBitsSetToOne_noExceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 1;

        // act, assert
        bitHelper.assertBatchSizeInBitsIsInRange(cProducer.batchSizeInBits);
    }

    @Test
    public void assertBatchSizeInBitsCorrect_batchSizeInBitsSetToTwo_noExceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 2;

        // act, assert
        bitHelper.assertBatchSizeInBitsIsInRange(cProducer.batchSizeInBits);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getKillBits">
    @Test
    public void getKillBits_batchSizeInBitsNotSet_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(0L))));
    }

    @Test
    public void getKillBits_batchSizeInBitsSetToZero_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 0;

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(0L))));
    }

    @Test
    public void getKillBits_batchSizeInBitsSetToOne_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 1;

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(1L))));
    }

    @Test
    public void getKillBits_batchSizeInBitsSetToTwo_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 2;

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(3L))));
    }

    @Test
    public void getKillBits_batchSizeInBitsSetToThree_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 3;

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(7L))));
    }

    @Test
    public void getKillBits_batchSizeInBitsSetToEight_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 8;

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(255L))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getBatchSize">
    @Test
    public void getBatchSize_batchSizeInBitsNotSet_batchSizeEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();

        // act, assert
        assertThat(bitHelper.convertBitsToSize(cProducer.batchSizeInBits), is(equalTo(1)));
    }

    @Test
    public void getBatchSize_batchSizeInBitsSetToZero_batchSizeEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 0;

        // act, assert
        assertThat(bitHelper.convertBitsToSize(cProducer.batchSizeInBits), is(equalTo(1)));
    }

    @Test
    public void getBatchSize_batchSizeInBitsSetToOne_batchSizeEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 1;

        // act, assert
        assertThat(bitHelper.convertBitsToSize(cProducer.batchSizeInBits), is(equalTo(2)));
    }

    @Test
    public void getBatchSize_batchSizeInBitsSetToTwo_batchSizeEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 2;

        // act, assert
        assertThat(bitHelper.convertBitsToSize(cProducer.batchSizeInBits), is(equalTo(4)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="default parameter for batchSizeInBits">
    @Test
    public void batchSizeInBits_configurationConstantsSet_isValidDefaultValue() throws IOException {
        CProducer cProducer = new CProducer();
        bitHelper.assertBatchSizeInBitsIsInRange(cProducer.batchSizeInBits);
    }
    // </editor-fold>
}
