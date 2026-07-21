// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.ByteConversion;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class LMDBPersistenceTest {

    @TempDir
    public Path folder;

    private final Random random = new Random(1337);

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
    private final KeyUtility keyUtility = new KeyUtility(network, byteBufferUtility);
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(network);

    /**
     * The increase should happen a few times. See {@link #TOO_MUCH_KEYS_EXPECTED_1MiB_INCREASES}.
     */
    private static final int TOO_MUCH_KEYS_FOR_1MiB = 1024 * 128;

    /**
     * See {@link #TOO_MUCH_KEYS_FOR_1MiB}.
     */
    private static final int TOO_MUCH_KEYS_EXPECTED_1MiB_INCREASES = 5;

    // <editor-fold defaultstate="collapsed" desc="use static amount">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_LMDB_AMOUNTS)
    public void putNewAmount_putNewAmount_correctAmountStored(
            boolean useStaticAmount, long staticAmount, long amount, long expectedAmount) throws IOException {
        // arrange
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        cLMDBConfigurationWrite.useStaticAmount = useStaticAmount;
        cLMDBConfigurationWrite.staticAmount = staticAmount;

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();

            // create key
            BigInteger secret = keyUtility.createSecret(Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS, random);
            ECKey ecKey = keyUtility.createECKey(secret, true);
            byte[] hash160 = ecKey.getPubKeyHash();
            ByteBuffer hash160ByteBuffer = byteBufferUtility.byteArrayToByteBuffer(hash160);

            // act
            lmdbPersistence.putNewAmount(hash160ByteBuffer, Coin.valueOf(amount));

            // assert
            Coin amountInLmdb = lmdbPersistence.getAmount(hash160ByteBuffer);
            assertThat(amountInLmdb.getValue(), is(equalTo(expectedAmount)));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="putNewAmounts (batch)">
    /** The batch write (one transaction for many entries) must store every entry with its own amount. */
    @Test
    public void putNewAmounts_batchOfEntries_allStoredWithCorrectAmounts() throws IOException {
        // arrange
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        cLMDBConfigurationWrite.useStaticAmount = false;

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();

            int count = 1_000;
            List<ByteBuffer> hash160s = new ArrayList<>(count);
            List<Coin> amounts = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                BigInteger secret = keyUtility.createSecret(Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS, random);
                ECKey ecKey = keyUtility.createECKey(secret, true);
                hash160s.add(byteBufferUtility.byteArrayToByteBuffer(ecKey.getPubKeyHash()));
                amounts.add(Coin.valueOf(i + 1L));
            }

            // act — a single transaction for the whole batch
            lmdbPersistence.putNewAmounts(hash160s, amounts);

            // assert
            assertThat(lmdbPersistence.count(), is(equalTo((long) count)));
            for (int i = 0; i < count; i++) {
                assertThat(lmdbPersistence.getAmount(hash160s.get(i)).getValue(), is(equalTo(i + 1L)));
            }
        }
    }

    /** The two parallel lists must be the same length; otherwise the pairing is undefined. */
    @Test
    public void putNewAmounts_mismatchedListSizes_throwsIllegalArgumentException() throws IOException {
        // arrange
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();

            List<ByteBuffer> hash160s = new ArrayList<>();
            hash160s.add(byteBufferUtility.byteArrayToByteBuffer(new byte[20]));
            List<Coin> amounts = new ArrayList<>();

            // act, assert
            assertThrows(IllegalArgumentException.class, () -> lmdbPersistence.putNewAmounts(hash160s, amounts));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="requiresBackend">
    @Test
    public void requiresBackend_isTrue_becauseLmdbIsQueriedDirectlyThroughItsLiveEnv() throws IOException {
        // arrange
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();

            // assert: LMDB is the backing storage and is read on every containsAddress call,
            // so the orchestrator must keep its env open (must NOT close it as if it were a
            // self-contained in-memory snapshot). Returning false here closes the env that
            // LMDB_ONLY reads from -> Env$AlreadyClosedException on every lookup.
            assertThat(lmdbPersistence.requiresBackend(), is(true));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getDatabaseSize initial and filled">
    @Test
    public void getDatabaseSize_initialLMDBSetTo1MiB_returnInitialDatabaseSize() throws IOException {
        // arrange
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();

            // act
            long databaseSize = lmdbPersistence.getDatabaseSize();

            // assert
            assertThat(databaseSize, is(equalTo(new ByteConversion().mibToBytes(1L))));
        }
    }

    @Test
    public void getDatabaseSize_valuesAdded_returnInitialDatabaseSize() throws IOException {
        // arrange
        int keysToAdd = 1024 * 16;
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();

            fillWithRandomKeys(keysToAdd, lmdbPersistence);

            // act
            long databaseSize = lmdbPersistence.getDatabaseSize();

            // assert
            assertThat(databaseSize, is(equalTo(new ByteConversion().mibToBytes(1L))));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getDatabaseSize increaseDatabaseSize and filled">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_LMDB_INCREASE_SIZE)
    public void getDatabaseSize_initialLMDBSetTo1MiB_increaseDatabaseSize_returnResizedDatabaseSize(long increaseSize)
            throws IOException {
        // arrange
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();

            // act
            lmdbPersistence.increaseDatabaseSize(increaseSize);

            // assert
            long databaseSize = lmdbPersistence.getDatabaseSize();
            assertThat(databaseSize, is(equalTo(new ByteConversion().mibToBytes(1L) + increaseSize)));
        }
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_LMDB_INCREASE_SIZE)
    public void getDatabaseSize_valuesAdded_increaseDatabaseSize_returnResizedDatabaseSize(long increaseSize)
            throws IOException {
        // arrange
        int keysToAdd = 1024 * 16;
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();

            fillWithRandomKeys(keysToAdd, lmdbPersistence);

            // act
            lmdbPersistence.increaseDatabaseSize(increaseSize);

            // assert
            long databaseSize = lmdbPersistence.getDatabaseSize();
            assertThat(databaseSize, is(equalTo(new ByteConversion().mibToBytes(1L) + increaseSize)));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="increaseDatabaseSize">
    @Test
    public void putNewAmount_initialLMDBSetTo1MiB_fillWithTooMuchValues_exceptionThrown() throws IOException {
        // arrange
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        cLMDBConfigurationWrite.increaseMapAutomatically = false;
        cLMDBConfigurationWrite.increaseSizeInMiB = 1;

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();

            // pre assert
            assertThat(lmdbPersistence.getDatabaseSize(), is(equalTo(new ByteConversion().mibToBytes(1L))));
            assertThat(lmdbPersistence.getIncreasedCounter(), is(equalTo(new ByteConversion().mibToBytes(0L))));
            assertThat(lmdbPersistence.getIncreasedSum(), is(equalTo(new ByteConversion().mibToBytes(0L))));

            // act, assert
            assertThrows(
                    org.lmdbjava.Env.MapFullException.class,
                    () -> fillWithRandomKeys(TOO_MUCH_KEYS_FOR_1MiB, lmdbPersistence));
        }
    }

    @Test
    public void putNewAmount_initialLMDBSetTo1MiB_fillWithTooMuchValues_increaseDatabaseSizeAndNoExceptionThrown()
            throws IOException {
        // arrange
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        cLMDBConfigurationWrite.increaseMapAutomatically = true;
        cLMDBConfigurationWrite.increaseSizeInMiB = 1;

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();

            // pre assert
            assertThat(lmdbPersistence.getDatabaseSize(), is(equalTo(new ByteConversion().mibToBytes(1L))));
            assertThat(lmdbPersistence.getIncreasedCounter(), is(equalTo(new ByteConversion().mibToBytes(0L))));
            assertThat(lmdbPersistence.getIncreasedSum(), is(equalTo(new ByteConversion().mibToBytes(0L))));

            // act
            fillWithRandomKeys(TOO_MUCH_KEYS_FOR_1MiB, lmdbPersistence);

            // post assert
            assertThat(
                    lmdbPersistence.getDatabaseSize(),
                    is(equalTo(new ByteConversion().mibToBytes(1L)
                            + (new ByteConversion().mibToBytes(cLMDBConfigurationWrite.increaseSizeInMiB))
                                    * TOO_MUCH_KEYS_EXPECTED_1MiB_INCREASES)));
            assertThat(
                    lmdbPersistence.getIncreasedCounter(), is(equalTo((long) TOO_MUCH_KEYS_EXPECTED_1MiB_INCREASES)));
            assertThat(
                    lmdbPersistence.getIncreasedSum(),
                    is(equalTo(new ByteConversion()
                            .mibToBytes(cLMDBConfigurationWrite.increaseSizeInMiB
                                    * TOO_MUCH_KEYS_EXPECTED_1MiB_INCREASES))));
        }
    }
    // </editor-fold>

    @Test
    public void forEachAddress_visitsSameEntriesAsAddressesStream() throws IOException {
        // arrange
        int keysToAdd = 5000;
        File lmdbFolder = Files.createDirectory(folder.resolve("lmdb")).toFile();

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 16;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();

        try (LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils)) {
            lmdbPersistence.init();
            fillWithRandomKeys(keysToAdd, lmdbPersistence);

            // reference set collected via the default Stream-based addresses() path
            Set<Long> viaStream = new HashSet<>();
            try (Stream<ByteBuffer> stream = lmdbPersistence.addresses()) {
                stream.forEach(bb -> viaStream.add(bb.getLong(bb.position())));
            }

            // act: the raw-cursor forEachAddress override must visit exactly the same entries
            Set<Long> viaForEach = new HashSet<>();
            int[] visited = {0};
            lmdbPersistence.forEachAddress(bb -> {
                visited[0]++;
                viaForEach.add(bb.getLong(bb.position()));
            });

            // assert
            assertThat(
                    "forEachAddress must visit count() entries",
                    (long) visited[0],
                    is(equalTo(lmdbPersistence.count())));
            assertThat("forEachAddress must visit the same entries as addresses()", viaForEach, is(equalTo(viaStream)));
        }
    }

    /**
     * {@code close()} must be safe to call when {@code init()} never ran (or threw before opening
     * the environment). This is not a theoretical case: {@code close()} runs in the caller's
     * {@code finally} block, so a failed {@code init()} — for example an
     * {@code InaccessibleObjectException} when the JVM is launched without the required
     * {@code --add-opens} — is immediately followed by {@code close()}. If {@code close()} raised its
     * own {@code NullPointerException} there, it would replace the real cause in the stack trace and
     * leave the operator debugging the wrong failure. This test fails if the null-guard is dropped.
     */
    @Test
    public void close_withoutInit_doesNotThrow_soItCannotMaskAnInitFailure() {
        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.lmdbDirectory =
                folder.resolve("never-opened").toFile().getAbsolutePath();

        LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils);
        // No init() call — the environment was never opened. close() must be a quiet no-op.
        lmdbPersistence.close();
    }

    private void fillWithRandomKeys(int keysToAdd, LMDBPersistence lmdbPersistence) {
        // arrange - fill
        for (int i = 0; i < keysToAdd; i++) {
            BigInteger secret = keyUtility.createSecret(Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS, random);
            ECKey ecKey = keyUtility.createECKey(secret, true);
            byte[] hash160 = ecKey.getPubKeyHash();
            ByteBuffer hash160ByteBuffer = byteBufferUtility.byteArrayToByteBuffer(hash160);

            lmdbPersistence.putNewAmount(hash160ByteBuffer, Coin.SATOSHI);
        }
    }
}
