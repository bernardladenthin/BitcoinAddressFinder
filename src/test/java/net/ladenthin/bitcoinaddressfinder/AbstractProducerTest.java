// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder;

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.base.Network;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;

@RunWith(DataProviderRunner.class)
public class AbstractProducerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @Test
    public void initProducer_configurationGiven_stateInitializedAndLogged() throws IOException, InterruptedException {
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        verifyInitProducer(abstractProducerTestImpl);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @Test
    public void releaseProducer_configurationGiven_stateInitializedAndLogged() throws IOException, InterruptedException {
        CProducer cProducer = new CProducer();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        verifyReleaseProducer(abstractProducerTestImpl);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="createSecretBase">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_CREATE_SECRET_BASE_LOGGED, location = CommonDataProvider.class)
    public void createSecretBase_secretGiven_bitsKilledAndLogged(String givenSecret, int batchSizeInBits, String expectedSecretBase, String logInfo0, String logTrace0, String logTrace1, String logTrace2, String logTrace3, String logTrace4) throws IOException, InterruptedException, DecoderException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = batchSizeInBits;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        Logger logger = mock(Logger.class);
        when(logger.isTraceEnabled()).thenReturn(true);
        abstractProducerTestImpl.setLogger(logger);

        BigInteger secret = new BigInteger(1, Hex.decodeHex(givenSecret));
        boolean logSecretBase = true;

        // act
        BigInteger secretBase = abstractProducerTestImpl.createSecretBase(secret, logSecretBase);

        // assert
        assertThat(keyUtility.bigIntegerToFixedLengthHex(secretBase), is(equalTo(expectedSecretBase)));

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).info(logCaptor.capture());
        verify(logger, times(5)).trace(logCaptor.capture());
        List<String> arguments = logCaptor.getAllValues();
        // assert log secret base
        {
            assertThat(arguments.get(0), is(equalTo(logInfo0)));
        }
        // assert log trace
        {
            assertThat(arguments.get(1), is(equalTo(logTrace0)));
            assertThat(arguments.get(2), is(equalTo(logTrace1)));
            assertThat(arguments.get(3), is(equalTo(logTrace2)));
            assertThat(arguments.get(4), is(equalTo(logTrace3)));
            assertThat(arguments.get(5), is(equalTo(logTrace4)));
        }
    }

    @Test
    public void createSecretBase_secretGivenAndLogSecretBaseDisabledTraceEnabled_bitsKilledAndLogged() throws IOException, InterruptedException, DecoderException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 2;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        Logger logger = mock(Logger.class);
        when(logger.isTraceEnabled()).thenReturn(true);
        abstractProducerTestImpl.setLogger(logger);

        BigInteger secret = new BigInteger(Hex.decodeHex("ABCDEF"));
        boolean logSecretBase = false;

        // act
        abstractProducerTestImpl.createSecretBase(secret, logSecretBase);

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        // assert
        // assert log secret base
        {
            verify(logger, times(0)).info(logCaptor.capture());
        }
        // assert log trace
        {
            verify(logger, times(5)).trace(logCaptor.capture());
        }
    }

    @Test
    public void createSecretBase_secretGivenAndLogSecretBaseEnabledTraceDisabled_bitsKilledAndLogged() throws IOException, InterruptedException, DecoderException {
        // arrange
        CProducer cProducer = new CProducer();
        cProducer.batchSizeInBits = 2;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(cProducer, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        Logger logger = mock(Logger.class);
        when(logger.isTraceEnabled()).thenReturn(false);
        abstractProducerTestImpl.setLogger(logger);

        BigInteger secret = new BigInteger(Hex.decodeHex("ABCDEF"));
        boolean logSecretBase = true;

        // act
        abstractProducerTestImpl.createSecretBase(secret, logSecretBase);

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        // assert
        // assert log secret base
        {
            verify(logger, times(1)).info(logCaptor.capture());
        }
        // assert log trace
        {
            verify(logger, times(0)).trace(logCaptor.capture());
        }
    }
    // </editor-fold>

    public static void verifyReleaseProducer(AbstractProducer abstractProducer) {
        Logger logger = mock(Logger.class);
        when(logger.isTraceEnabled()).thenReturn(true);
        abstractProducer.setLogger(logger);
        
        abstractProducer.initProducer();
        
        // act
        abstractProducer.releaseProducer();

        // assert
        assertThat(abstractProducer.state, is(equalTo(ProducerState.INITIALIZED)));
        
        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(2)).info(logCaptor.capture());
        List<String> arguments = logCaptor.getAllValues();
        // assert log initProducer
        {
            assertThat(arguments.get(1), is(equalTo("Release producer.")));
        }
    }

    public static void verifyInitProducer(AbstractProducer abstractProducer) {
        Logger logger = mock(Logger.class);
        when(logger.isTraceEnabled()).thenReturn(true);
        abstractProducer.setLogger(logger);
        
        // pre-assert
        assertThat(abstractProducer.state, is(equalTo(ProducerState.UNINITIALIZED)));
        
        // act
        abstractProducer.initProducer();

        // assert
        assertThat(abstractProducer.state, is(equalTo(ProducerState.INITIALIZED)));
        
        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).info(logCaptor.capture());
        List<String> arguments = logCaptor.getAllValues();
        // assert log initProducer
        {
            assertThat(arguments.get(0), is(equalTo("Init producer.")));
        }
    }
}
