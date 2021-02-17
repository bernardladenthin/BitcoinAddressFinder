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
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducer;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
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

    private ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);

    protected final NetworkParameters networkParameters = MainNetParams.get();
    protected final KeyUtility keyUtility = new KeyUtility(networkParameters, new ByteBufferUtility(false));

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_CREATE_SECRET_BASE_LOGGED, location = CommonDataProvider.class)
    public void createSecretBase_secretGiven_bitsKilledAndLogged(String givenSecret, int gridNumBits, String expectedSecretBase, String logInfo0, String logTrace0, String logTrace1, String logTrace2, String logTrace3, String logTrace4) throws IOException, InterruptedException, DecoderException {
        // arrange
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = gridNumBits;
        cProducer.privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(shouldRun, mockConsumer, keyUtility, random);

        Logger logger = mock(Logger.class);
        when(logger.isTraceEnabled()).thenReturn(true);
        abstractProducerTestImpl.setLogger(logger);

        BigInteger secret = new BigInteger(Hex.decodeHex(givenSecret));
        boolean logSecretBase = true;

        // act
        BigInteger secretBase = abstractProducerTestImpl.createSecretBase(cProducer, secret, logSecretBase);

        // assert
        assertThat(Hex.encodeHexString(secretBase.toByteArray()), is(equalTo(expectedSecretBase)));

        List<String> arguments = logCaptor.getAllValues();
        // assert log secret base
        {
            verify(logger, times(1)).info(logCaptor.capture());
            assertThat(arguments.get(0), is(equalTo(logInfo0)));
        }
        // assert log trace
        {
            verify(logger, times(5)).trace(logCaptor.capture());
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
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 2;
        cProducer.privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(shouldRun, mockConsumer, keyUtility, random);

        Logger logger = mock(Logger.class);
        when(logger.isTraceEnabled()).thenReturn(true);
        abstractProducerTestImpl.setLogger(logger);

        BigInteger secret = new BigInteger(Hex.decodeHex("ABCDEF"));
        boolean logSecretBase = false;

        // act
        abstractProducerTestImpl.createSecretBase(cProducer, secret, logSecretBase);

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
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CProducer cProducer = new CProducer();
        cProducer.gridNumBits = 2;
        cProducer.privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        AbstractProducerTestImpl abstractProducerTestImpl = new AbstractProducerTestImpl(shouldRun, mockConsumer, keyUtility, random);

        Logger logger = mock(Logger.class);
        when(logger.isTraceEnabled()).thenReturn(false);
        abstractProducerTestImpl.setLogger(logger);

        BigInteger secret = new BigInteger(Hex.decodeHex("ABCDEF"));
        boolean logSecretBase = true;

        // act
        abstractProducerTestImpl.createSecretBase(cProducer, secret, logSecretBase);

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

}
