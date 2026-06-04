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
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
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
