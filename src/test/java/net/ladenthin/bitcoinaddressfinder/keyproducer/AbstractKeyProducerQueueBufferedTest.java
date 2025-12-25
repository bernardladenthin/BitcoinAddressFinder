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


import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import org.bitcoinj.base.Network;

import static org.junit.Assert.assertEquals;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.ladenthin.bitcoinaddressfinder.BitHelper;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import org.slf4j.Logger;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaReceiver;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.eq;

public class AbstractKeyProducerQueueBufferedTest {
    
    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();
    private Logger mockLogger;
    
    @Before
    public void setUp() {
        mockLogger = mock(Logger.class);
    }
    
    static class TestKeyProducer extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaReceiver> {
        public TestKeyProducer(CKeyProducerJavaReceiver config, KeyUtility keyUtility, Logger logger) {
            super(config, keyUtility, logger);
        }
        public TestKeyProducer(CKeyProducerJavaReceiver config, KeyUtility keyUtility, Logger logger, BlockingQueue<byte[]> queue) {
            super(config, keyUtility, logger, queue);
        }

        @Override
        protected int getReadTimeout() {
            return TestTimeProvider.DEFAULT_SOCKET_TIMEOUT;
        }

        @Override
        public void interrupt() {
            shouldStop = true;
        }
    }

    private TestKeyProducer createTestKeyProducer(CKeyProducerJavaReceiver config) {
        return new TestKeyProducer(config, keyUtility, mockLogger);
    }

    @Test
    public void createSecrets_returnsSecret_whenAvailableInQueue() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);
        
        byte[] secret = new KeyProducerTestUtility().createFilledSecret((byte) 0xAB);
        BigInteger expectedSecret = new BigInteger(1, secret);
        producer.addSecret(secret);
        
        BigInteger[] secrets = producer.createSecrets(1, true);
        
        assertEquals(1, secrets.length);
        assertEquals(expectedSecret, secrets[0]);
    }
    
    @Test
    public void createSecrets_readsMultipleSecrets() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);
        
        byte[] secret = new KeyProducerTestUtility().createFilledSecret((byte) 0xAB);
        BigInteger expectedSecret = new BigInteger(1, secret);

        producer.addSecret(secret);
        producer.addSecret(secret);

        BigInteger[] secrets = producer.createSecrets(2, false);

        assertEquals(2, secrets.length);
        for (BigInteger bi : secrets) {
            assertEquals(expectedSecret, bi);
        }
    }
    
    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_throwsException_onInvalidLength() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);

        byte[] invalidSecret = new KeyProducerTestUtility().createInvalidSecret();
        producer.addSecret(invalidSecret);

        producer.createSecrets(1, true); // should throw
    }
    
    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_throwsException_onTimeout() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);

        // Do not add anything to queue
        producer.createSecrets(1, true); // should timeout
    }
    
    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_throwsException_whenShouldStopSet() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);

        producer.shouldStop = true;
        producer.createSecrets(1, true); // should throw immediately
    }
    
    @Test
    public void createSecrets_logsSecret_whenEnabled() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        config.logReceivedSecret = true;

        TestKeyProducer producer = createTestKeyProducer(config);

        byte[] secret = new KeyProducerTestUtility().createFilledSecret((byte) 0xAB);
        BigInteger expectedSecret = new BigInteger(1, secret);
        
        String expectedHex = keyUtility.bigIntegerToFixedLengthHex(expectedSecret);

        producer.addSecret(secret);
        producer.createSecrets(1, true);

        // Verify that logger.info was called with the expected message
        verify(mockLogger, times(1)).info(eq("Received key: {}"), eq(expectedHex));
    }

    @Test()
    public void addSecret_throwsWhenQueueFull() {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();

        BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(1);
        TestKeyProducer producer = new TestKeyProducer(config, keyUtility, mockLogger, queue);

        byte[] secret = new KeyProducerTestUtility().createFilledSecret((byte) 0xAB);

        producer.addSecret(secret); // fills queue
        producer.addSecret(secret); // must fail

        verify(mockLogger).error(
                eq("Secret queue is full, ignore secret: {}"),
                eq(secret)
        );
    }
}
