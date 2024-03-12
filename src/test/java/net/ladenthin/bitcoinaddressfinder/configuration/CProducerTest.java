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
import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticKey;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;

public class CProducerTest {

    private final StaticKey staticKey = new StaticKey();
    
    @Before
    public void init() throws IOException {
    }

    // <editor-fold defaultstate="collapsed" desc="assertGridNumBitsCorrect">
    @Test(expected = IllegalArgumentException.class)
    public void assertGridNumBitsCorrect_gridNumBitsSetNegative_exceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = -1;

        // act, assert
        cProducer.assertGridNumBitsCorrect();
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void assertGridNumBitsCorrect_gridNumBitsSetOverMaximum_exceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = PublicKeyBytes.BIT_COUNT_FOR_MAX_COORDINATE_PAIRS_ARRAY + 1;

        // act, assert
        cProducer.assertGridNumBitsCorrect();
    }
    
    @Test
    public void assertGridNumBitsCorrect_gridNumBitsNotSet_noExceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();

        // act, assert
        cProducer.assertGridNumBitsCorrect();
    }

    @Test
    public void assertGridNumBitsCorrect_gridNumBitsSetToZero_noExceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 0;

        // act, assert
        cProducer.assertGridNumBitsCorrect();
    }

    @Test
    public void assertGridNumBitsCorrect_gridNumBitsSetToOne_noExceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 1;

        // act, assert
        cProducer.assertGridNumBitsCorrect();
    }

    @Test
    public void assertGridNumBitsCorrect_gridNumBitsSetToTwo_noExceptionThrown() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 2;

        // act, assert
        cProducer.assertGridNumBitsCorrect();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getKillBits">
    @Test
    public void getKillBits_gridNumBitsNotSet_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(0L))));
    }

    @Test
    public void getKillBits_gridNumBitsSetToZero_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 0;

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(0L))));
    }

    @Test
    public void getKillBits_gridNumBitsSetToOne_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 1;

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(1L))));
    }

    @Test
    public void getKillBits_gridNumBitsSetToTwo_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 2;

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(3L))));
    }

    @Test
    public void getKillBits_gridNumBitsSetToThree_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 3;

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(7L))));
    }

    @Test
    public void getKillBits_gridNumBitsSetToEight_killBitsEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 8;

        // act, assert
        assertThat(cProducer.getKillBits(), is(equalTo(BigInteger.valueOf(255L))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getWorkSize">
    @Test
    public void getWorkSize_gridNumBitsNotSet_workSizeEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();

        // act, assert
        assertThat(cProducer.getWorkSize(), is(equalTo(1)));
    }

    @Test
    public void getWorkSize_gridNumBitsSetToZero_workSizeEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 0;

        // act, assert
        assertThat(cProducer.getWorkSize(), is(equalTo(1)));
    }

    @Test
    public void getWorkSize_gridNumBitsSetToOne_workSizeEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 1;

        // act, assert
        assertThat(cProducer.getWorkSize(), is(equalTo(2)));
    }

    @Test
    public void getWorkSize_gridNumBitsSetToTwo_workSizeEqualsExpectation() throws IOException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 2;

        // act, assert
        assertThat(cProducer.getWorkSize(), is(equalTo(4)));
    }
    // </editor-fold>


}
