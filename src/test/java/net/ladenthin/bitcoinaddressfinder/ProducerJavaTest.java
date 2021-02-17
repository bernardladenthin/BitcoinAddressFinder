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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(DataProviderRunner.class)
public class ProducerJavaTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);

    protected final NetworkParameters networkParameters = MainNetParams.get();
    protected final KeyUtility keyUtility = new KeyUtility(networkParameters, new ByteBufferUtility(false));

    @Test
    public void produceKeys_GridNumBitsEqualsKeyMaxNumBits_noExceptionThrown() throws IOException, InterruptedException {
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.gridNumBits = 2;
        cProducerJava.privateKeyMaxNumBits = 2;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        ProducerJava producerJava = new ProducerJava(cProducerJava, shouldRun, mockConsumer, keyUtility, random);

        // act
        producerJava.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0).length, is(equalTo(4)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[1], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[2], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(2)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[3], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(3)))));
    }

    @Test
    public void produceKeys_KeyMaxNumBitsLowerThanGridNumBits_produceGridNumBitsNevertheless() throws IOException, InterruptedException {
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.gridNumBits = 4;
        cProducerJava.privateKeyMaxNumBits = 3;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        ProducerJava producerJava = new ProducerJava(cProducerJava, shouldRun, mockConsumer, keyUtility, random);

        // act
        producerJava.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0).length, is(equalTo(16)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[1], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[2], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(2)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[3], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(3)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[4], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(4)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[5], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(5)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[6], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(6)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[7], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(7)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[8], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(8)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[9], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(9)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[10], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(10)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[11], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(11)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[12], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(12)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[13], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(13)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[14], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(14)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[15], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(15)))));
    }

    @Test
    public void produceKeys_privateKeyMaxNumBitsIsTooLow_noKeysGenerated() throws IOException, InterruptedException {
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.gridNumBits = 10;
        cProducerJava.privateKeyMaxNumBits = 2;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(0);
        ProducerJava producerJava = new ProducerJava(cProducerJava, shouldRun, mockConsumer, keyUtility, random);

        // act
        producerJava.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(0)));
    }

    @Test
    public void produceKeys_SomeBitRanges_consumerContainsData() throws IOException, InterruptedException {
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.gridNumBits = 3;
        cProducerJava.privateKeyMaxNumBits = 6;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(2);
        ProducerJava producerJava = new ProducerJava(cProducerJava, shouldRun, mockConsumer, keyUtility, random);

        // act
        producerJava.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0).length, is(equalTo(8)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(56)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[1], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(57)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[2], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(58)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[3], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(59)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[4], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(60)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[5], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(61)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[6], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(62)))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[7], is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(63)))));
    }

}
