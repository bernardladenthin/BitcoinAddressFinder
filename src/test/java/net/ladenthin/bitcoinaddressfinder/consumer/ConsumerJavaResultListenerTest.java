// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ladenthin.bitcoinaddressfinder.LMDBPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.ResultListener;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests that the consumer reports every checked batch, not only the ones that produced a hit.
 *
 * <p>This is the feedback channel the browser-driven scanning mode is built on. Reporting only hits
 * would leave a client unable to tell "range checked, nothing there" from "range never checked" —
 * indistinguishable states that make an automated from/to sweep unsafe, because a range silently
 * dropped on shutdown looks exactly like a clean one. One event per checked batch removes the
 * ambiguity and doubles as the completion signal.
 *
 * <p>The event is emitted after the batch has actually been checked against the database, so it is
 * exact: there is no dispatch-ahead lag for a client to compensate for.
 */
class ConsumerJavaResultListenerTest {

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);

    /** An arbitrary aligned base standing in for the start of a swept grid. */
    private static final BigInteger SECRET_BASE = BigInteger.valueOf(0x4000L);

    /** Collects every reported batch so the assertions can inspect them in order. */
    private static final class RecordingResultListener implements ResultListener {

        private final List<BatchResult> received = new CopyOnWriteArrayList<>();

        @Override
        public void onBatchChecked(BatchResult batchResult) {
            received.add(batchResult);
        }

        List<BatchResult> received() {
            return new ArrayList<>(received);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="onBatchChecked">
    @Test
    void consumeOneCycle_batchWithoutHit_reportsTheCheckedBatchAnyway() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();

        // arrange
        final RecordingResultListener listener = new RecordingResultListener();
        final ConsumerJava consumerJava = createConsumer(listener);
        try {
            consumerJava.initLMDB();
            final PublicKeyBytes[] batch = new PublicKeyBytes[] {PublicKeyBytes.fromPrivate(BigInteger.valueOf(73))};

            // act
            consumerJava.consumeKeys(batch, SECRET_BASE);
            consumerJava.consumeOneCycle(ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES));

            // pre-assert
            assertThat(listener.received(), hasSize(1));

            // assert
            final BatchResult reported = listener.received().get(0);
            assertThat(reported.secretBase(), is(equalTo(SECRET_BASE)));
            assertThat(reported.checkedCount(), is(equalTo(batch.length)));
            assertThat(reported.hits(), is(empty()));
        } finally {
            consumerJava.interrupt();
        }
    }

    @Test
    void consumeOneCycle_batchWithoutSecretBase_reportsTheBatchWithoutABase() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();

        // arrange
        final RecordingResultListener listener = new RecordingResultListener();
        final ConsumerJava consumerJava = createConsumer(listener);
        try {
            consumerJava.initLMDB();
            final PublicKeyBytes[] batch = new PublicKeyBytes[] {PublicKeyBytes.fromPrivate(BigInteger.valueOf(73))};

            // act: the non-increment path supplies independent secrets, so no common base exists
            consumerJava.consumeKeys(batch, null);
            consumerJava.consumeOneCycle(ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES));

            // pre-assert
            assertThat(listener.received(), hasSize(1));

            // assert
            assertThat(listener.received().get(0).secretBase(), is(nullValue()));
        } finally {
            consumerJava.interrupt();
        }
    }

    @Test
    void consumeOneCycle_twoBatches_reportsOneEventPerBatch() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();

        // arrange
        final RecordingResultListener listener = new RecordingResultListener();
        final ConsumerJava consumerJava = createConsumer(listener);
        try {
            consumerJava.initLMDB();
            final ByteBuffer reuseable = ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES);
            final BigInteger secondBase = SECRET_BASE.add(BigInteger.valueOf(0x4000L));

            // act
            consumerJava.consumeKeys(
                    new PublicKeyBytes[] {PublicKeyBytes.fromPrivate(BigInteger.valueOf(73))}, SECRET_BASE);
            consumerJava.consumeOneCycle(reuseable);
            consumerJava.consumeKeys(
                    new PublicKeyBytes[] {PublicKeyBytes.fromPrivate(BigInteger.valueOf(74))}, secondBase);
            consumerJava.consumeOneCycle(reuseable);

            // assert
            final List<BigInteger> reportedBases =
                    listener.received().stream().map(BatchResult::secretBase).toList();
            assertThat(reportedBases, contains(SECRET_BASE, secondBase));
        } finally {
            consumerJava.interrupt();
        }
    }

    @Test
    void consumeOneCycle_noListenerConfigured_noExceptionThrown() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();

        // arrange: without a configured listener nothing about the pipeline may change
        final ConsumerJava consumerJava = createConsumer();
        try {
            consumerJava.initLMDB();

            // act
            consumerJava.consumeKeys(
                    new PublicKeyBytes[] {PublicKeyBytes.fromPrivate(BigInteger.valueOf(73))}, SECRET_BASE);
            consumerJava.consumeOneCycle(ByteBuffer.allocateDirect(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES));

            // assert
            assertThat(consumerJava.persistence, is(notNullValue()));
        } finally {
            consumerJava.interrupt();
        }
    }
    // </editor-fold>

    /**
     * Builds a consumer backed by a small on-disk database.
     *
     * @param resultListeners the listeners to notify, possibly none
     * @return the consumer under test
     * @throws Exception if the test database cannot be created
     */
    private ConsumerJava createConsumer(ResultListener... resultListeners) throws Exception {
        final TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();
        final TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        final File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, false);

        final CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();

        return new ConsumerJava(cConsumerJava, keyUtility, persistenceUtils, List.of(resultListeners));
    }
}
