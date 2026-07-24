// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.persistence.lmdb;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.ladenthin.bitcoinaddressfinder.LMDBPlatformAssume;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for issue #50: the close-during-read use-after-unmap that crashed a straggler
 * {@code ConsumerJava} thread with {@code SIGSEGV (SEGV_MAPERR)} in {@code mdb_txn_renew0} when
 * {@code ConsumerJava.interrupt()} closed the LMDB env after {@code awaitTermination} timed out
 * with a reader still inside {@code containsAddress -> mdb_txn_begin}.
 *
 * <p>The fix lives in {@link LMDBPersistence}: point-lookup reads take a read lock across their
 * whole native transaction and {@link LMDBPersistence#close()} takes the write lock, so the mmap
 * can never be unmapped while a reader is mid-native-call. A read that arrives after the close
 * observes the {@code closed} flag under the read lock and returns "absent" instead of touching
 * the torn-down mmap.
 *
 * <p>These tests run by default (gated only by {@link LMDBPlatformAssume}); the exhaustive
 * crash-rate reproduction lives in the opt-in {@link LmdbReaderSlotChurnStressTest} /
 * {@link LmdbCrashReproDriverTest} harness.
 */
public class LMDBPersistenceCloseDuringReadTest {

    private static final int HASH160_BYTES = OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES;

    /** Modest reader fleet — enough to overlap the close, small enough to stay fast. */
    private static final int READER_THREADS = 16;

    /** Let readers saturate the native read path before the close race. */
    private static final int WARMUP_MILLIS = 200;

    private static final int READER_JOIN_MILLIS = 2000;

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);

    /** After {@link LMDBPersistence#close()} a lookup must return {@code false}, never crash or throw. */
    @Test
    public void containsAddress_afterClose_returnsFalseWithoutThrowing() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();
        LMDBPersistence persistence = openReadOnly();
        ByteBuffer key = keyBuffer(LmdbReaderSlotChurnStressTest.buildHash160Corpus(1)[0]);

        persistence.close();

        assertThat(persistence.containsAddress(key), is(equalTo(false)));
    }

    /** After close a value lookup must yield {@link Coin#ZERO}, never dereference the unmapped mmap. */
    @Test
    public void getAmount_afterClose_returnsZeroWithoutThrowing() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();
        LMDBPersistence persistence = openReadOnly();
        ByteBuffer key = keyBuffer(LmdbReaderSlotChurnStressTest.buildHash160Corpus(1)[0]);

        persistence.close();

        assertThat(persistence.getAmount(key), is(equalTo(Coin.ZERO)));
    }

    /** A second {@link LMDBPersistence#close()} is a no-op, not a double-free / native error. */
    @Test
    public void close_calledTwice_isIdempotent() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();
        LMDBPersistence persistence = openReadOnly();

        persistence.close();
        persistence.close();
    }

    /**
     * The actual issue #50 race, as a default-running regression: hammer {@code containsAddress}
     * from many threads, then close the env <b>while reads are in flight</b>. Before the fix this
     * crashed the JVM; with the read/write-lock coordination the close waits for in-flight native
     * reads, later reads return "absent", and no thread raises an unexpected throwable.
     *
     * <p>Deliberately raw {@link Thread}s: the readers must be genuinely concurrent with the close,
     * which is exactly the coordination {@link LMDBPersistence} now provides.
     */
    @Test
    public void closeDuringConcurrentReads_survivesAndClosesCleanly() throws Exception {
        new LMDBPlatformAssume().assumeLMDBExecution();
        LMDBPersistence persistence = openReadOnly();
        byte[][] corpus = LmdbReaderSlotChurnStressTest.buildHash160Corpus(256);

        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicBoolean closing = new AtomicBoolean(false);
        final AtomicLong reads = new AtomicLong();
        final List<Throwable> unexpected = new CopyOnWriteArrayList<>();
        final List<Thread> readers = new ArrayList<>(READER_THREADS);

        for (int i = 0; i < READER_THREADS; i++) {
            final long seed = i;
            Thread reader = new Thread(
                    () -> {
                        final ByteBuffer key = ByteBuffer.allocateDirect(HASH160_BYTES);
                        final Random random = new Random(seed);
                        while (!stop.get()) {
                            try {
                                key.rewind();
                                key.put(corpus[random.nextInt(corpus.length)]);
                                key.flip();
                                persistence.containsAddress(key);
                                reads.incrementAndGet();
                            } catch (Throwable t) {
                                // Once close() has been requested the guarded lookup returns false
                                // rather than throwing, so nothing is expected here even post-close;
                                // record anything seen while the env is still open as a real defect.
                                if (!closing.get()) {
                                    unexpected.add(t);
                                }
                                return;
                            }
                        }
                    },
                    "lmdb-reader-" + seed);
            reader.setDaemon(true);
            reader.start();
            readers.add(reader);
        }

        Thread.sleep(WARMUP_MILLIS);

        // THE RACE: close the env out from under live readers (no drain first — that was the bug).
        closing.set(true);
        persistence.close();

        stop.set(true);
        for (Thread reader : readers) {
            reader.join(READER_JOIN_MILLIS);
        }

        assertThat("readers should have exercised the native read path", reads.get() > 0L, is(equalTo(true)));
        assertThat("close-during-read raised unexpected throwables: " + unexpected, unexpected.isEmpty(), is(true));
        assertThat("env must be closed after close()", persistence.isClosed(), is(equalTo(true)));
    }

    private LMDBPersistence openReadOnly() throws Exception {
        File lmdbDirectory = LmdbReaderSlotChurnStressTest.buildTestLmdb(folder, persistenceUtils);
        CLMDBConfigurationReadOnly config = new CLMDBConfigurationReadOnly();
        config.lmdbDirectory = lmdbDirectory.getAbsolutePath();
        LMDBPersistence persistence = new LMDBPersistence(config, persistenceUtils);
        persistence.init();
        return persistence;
    }

    private static ByteBuffer keyBuffer(byte[] hash160) {
        ByteBuffer key = ByteBuffer.allocateDirect(HASH160_BYTES);
        key.put(hash160);
        key.flip();
        return key;
    }
}
