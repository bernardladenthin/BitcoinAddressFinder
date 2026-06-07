// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaReceiver;
import net.ladenthin.bitcoinaddressfinder.secret.NoMoreSecretsAvailableException;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import nl.altindag.log.LogCaptor;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AbstractKeyProducerQueueBufferedTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();
    private ExecutorService executorService;

    @BeforeEach
    public void setUp() {
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    public void tearDown() {
        executorService.shutdownNow();
    }

    static class TestKeyProducer extends AbstractKeyProducerQueueBuffered<CKeyProducerJavaReceiver> {
        private int readTimeoutMs = TestTimeProvider.DEFAULT_SOCKET_TIMEOUT;

        public TestKeyProducer(CKeyProducerJavaReceiver config, KeyUtility keyUtility) {
            super(config, keyUtility);
        }

        public TestKeyProducer(CKeyProducerJavaReceiver config, KeyUtility keyUtility, BlockingQueue<byte[]> queue) {
            super(config, keyUtility, queue);
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        @Override
        protected int getReadTimeout() {
            return readTimeoutMs;
        }

        @Override
        public void interrupt() {
            signalShutdown();
        }
    }

    private TestKeyProducer createTestKeyProducer(CKeyProducerJavaReceiver config) {
        return new TestKeyProducer(config, keyUtility);
    }

    @Test
    public void createSecrets_returnsSecret_whenAvailableInQueue() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);

        byte[] secret = new KeyProducerTestUtility().createFilledSecret((byte) 0xAB);
        BigInteger expectedSecret = new BigInteger(1, secret);
        producer.addSecret(secret);

        BigInteger[] secrets = producer.createSecrets(1, true);

        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(equalTo(expectedSecret)));
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

        assertThat(secrets.length, is(2));
        for (BigInteger bi : secrets) {
            assertThat(bi, is(equalTo(expectedSecret)));
        }
    }

    @Test
    public void createSecrets_throwsException_onInvalidLength() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);

        byte[] invalidSecret = new KeyProducerTestUtility().createInvalidSecret();
        producer.addSecret(invalidSecret);

        assertThrows(NoMoreSecretsAvailableException.class, () -> producer.createSecrets(1, true)); // should throw
    }

    @Test
    public void createSecrets_throwsException_onTimeout() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);

        // Do not add anything to queue
        assertThrows(NoMoreSecretsAvailableException.class, () -> producer.createSecrets(1, true)); // should timeout
    }

    @Test
    public void createSecrets_throwsException_whenShouldStopSet() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);

        producer.shouldStop = true;
        assertThrows(
                NoMoreSecretsAvailableException.class,
                () -> producer.createSecrets(1, true)); // should throw immediately
    }

    @Test
    public void createSecrets_logsSecret_whenEnabled() throws Exception {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        config.logReceivedSecret = true;

        TestKeyProducer producer = createTestKeyProducer(config);

        byte[] secret = new KeyProducerTestUtility().createFilledSecret((byte) 0xAB);
        BigInteger expectedSecret = new BigInteger(1, secret);

        String expectedHex = keyUtility.bigIntegerToFixedLengthHex(expectedSecret);

        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractKeyProducerQueueBuffered.class)) {
            producer.addSecret(secret);
            producer.createSecrets(1, true);

            // Verify the formatted message was emitted at INFO level.
            assertThat(logCaptor.getInfoLogs(), hasItem("Received key: " + expectedHex));
        }
    }

    @Test()
    public void addSecret_throwsWhenQueueFull() {
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();

        BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(1);
        TestKeyProducer producer = new TestKeyProducer(config, keyUtility, queue);

        byte[] secret = new KeyProducerTestUtility().createFilledSecret((byte) 0xAB);

        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractKeyProducerQueueBuffered.class)) {
            producer.addSecret(secret); // fills queue
            producer.addSecret(secret); // must fail

            // The formatted message uses the {} placeholder with the byte[] reference --
            // because byte[].toString() prints '[B@<hash>' the formatted log line is
            // non-deterministic, but the prefix is stable. Match the prefix only.
            assertThat(
                    logCaptor.getErrorLogs().stream()
                            .anyMatch(s -> s.startsWith("Secret queue is full, ignore secret: ")),
                    is(true));
        }
    }

    @Test
    public void createSecrets_negativeTimeout_blocksUntilAddSecret() throws Exception {
        // arrange
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);
        producer.setReadTimeoutMs(-1);

        byte[] secret = new KeyProducerTestUtility().createFilledSecret((byte) 0xAB);
        BigInteger expectedSecret = new BigInteger(1, secret);

        // act
        Future<BigInteger[]> future = executorService.submit(() -> producer.createSecrets(1, true));

        Thread.sleep(TestTimeProvider.DEFAULT_DELAY);
        assertThat("createSecrets must block on an empty queue when timeout < 0", future.isDone(), is(false));

        producer.addSecret(secret);

        BigInteger[] secrets = future.get(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);

        // assert
        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(equalTo(expectedSecret)));
    }

    @Test
    public void interrupt_unblocksBlockedConsumer_negativeTimeout() throws Exception {
        // arrange
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);
        producer.setReadTimeoutMs(-1);

        // act
        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return producer.createSecrets(1, true);
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        Thread.sleep(TestTimeProvider.DEFAULT_DELAY);
        assertThat("createSecrets must be blocked before interrupt()", future.isDone(), is(false));

        producer.interrupt();

        // assert
        BigInteger[] result = future.get(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat("createSecrets must return after interrupt", result, is((BigInteger[]) null));
    }

    @Test
    public void interrupt_unblocksBlockedConsumer_positiveTimeout() throws Exception {
        // arrange
        // With a positive but long timeout, interrupt() should still wake the
        // consumer immediately rather than making it wait out the full timeout.
        CKeyProducerJavaReceiver config = new CKeyProducerJavaReceiver();
        TestKeyProducer producer = createTestKeyProducer(config);
        final int longTimeoutMs = 60_000;
        producer.setReadTimeoutMs(longTimeoutMs);

        // act
        long start = System.currentTimeMillis();
        Future<BigInteger[]> future = executorService.submit(() -> {
            try {
                return producer.createSecrets(1, true);
            } catch (NoMoreSecretsAvailableException e) {
                return null;
            }
        });

        Thread.sleep(TestTimeProvider.DEFAULT_DELAY);
        producer.interrupt();

        BigInteger[] result = future.get(TestTimeProvider.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);
        long elapsedMs = System.currentTimeMillis() - start;

        // assert
        assertThat(result, is((BigInteger[]) null));
        if (elapsedMs >= longTimeoutMs) {
            fail("interrupt() did not wake the blocked consumer; createSecrets waited " + elapsedMs
                    + " ms (timeout was " + longTimeoutMs + " ms)");
        }
    }
}
