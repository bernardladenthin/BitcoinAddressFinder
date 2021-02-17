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
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.junit.Rule;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class LMDBPersistenceTest {
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    
    private Random random = new Random(1337);
    
    private final NetworkParameters networkParameters = MainNetParams.get();
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
    private final KeyUtility keyUtility = new KeyUtility(networkParameters, byteBufferUtility);
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(networkParameters);
    
    /**
     * The increase should happen a few times. See {@link #TOO_MUCH_KEYS_EXPECTED_1MiB_INCREASES}.
     */
    private final static int TOO_MUCH_KEYS_FOR_1MiB = 1024*128;
    
    /**
     * See {@link #TOO_MUCH_KEYS_FOR_1MiB}.
     */
    private final static int TOO_MUCH_KEYS_EXPECTED_1MiB_INCREASES = 5;
    
    
    // <editor-fold defaultstate="collapsed" desc="use static amount">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_LMDB_AMOUNTS, location = CommonDataProvider.class)
    public void putNewAmount_putNewAmount_correctAmountStored(boolean useStaticAmount, long staticAmount, long amount, long expectedAmount) throws IOException {
        // arrange
        File lmdbFolder = folder.newFolder("lmdb");
        
        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        cLMDBConfigurationWrite.useStaticAmount = useStaticAmount;
        cLMDBConfigurationWrite.staticAmount = staticAmount;
        
        LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils);
        lmdbPersistence.init();
        
        // create key
        BigInteger secret = keyUtility.createSecret(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS, random);
        ECKey ecKey = keyUtility.createECKey(secret, true);
        byte[] hash160 = ecKey.getPubKeyHash();
        ByteBuffer hash160ByteBuffer = byteBufferUtility.byteArrayToByteBuffer(hash160);
        
        // act
        lmdbPersistence.putNewAmount(hash160ByteBuffer, Coin.valueOf(amount));
        
        // assert
        Coin amountInLmdb = lmdbPersistence.getAmount(hash160ByteBuffer);
        assertThat(amountInLmdb.getValue(), is(equalTo(expectedAmount)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getDatabaseSize initial and filled">
    @Test
    public void getDatabaseSize_initialLMDBSetTo1MiB_returnInitialDatabaseSize() throws IOException {
        // arrange
        File lmdbFolder = folder.newFolder("lmdb");
        
        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        
        LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils);
        lmdbPersistence.init();
        
        // act
        long databaseSize = lmdbPersistence.getDatabaseSize();
        
        // assert
        assertThat(databaseSize, is(equalTo(new ByteConversion().mibToBytes(1L))));
    }
    
    @Test
    public void getDatabaseSize_valuesAdded_returnInitialDatabaseSize() throws IOException {
        // arrange
        int keysToAdd = 1024*16;
        File lmdbFolder = folder.newFolder("lmdb");
        
        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        
        LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils);
        lmdbPersistence.init();
        
        fillWithRandomKeys(keysToAdd, lmdbPersistence);
        
        // act
        long databaseSize = lmdbPersistence.getDatabaseSize();
        
        // assert
        assertThat(databaseSize, is(equalTo(new ByteConversion().mibToBytes(1L))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getDatabaseSize increaseDatabaseSize and filled">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_LMDB_INCREASE_SIZE, location = CommonDataProvider.class)
    public void getDatabaseSize_initialLMDBSetTo1MiB_increaseDatabaseSize_returnResizedDatabaseSize(long increaseSize) throws IOException {
        // arrange
        File lmdbFolder = folder.newFolder("lmdb");
        
        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        
        LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils);
        lmdbPersistence.init();
        
        // act
        lmdbPersistence.increaseDatabaseSize(increaseSize);
        
        // assert
        long databaseSize = lmdbPersistence.getDatabaseSize();
        assertThat(databaseSize, is(equalTo(new ByteConversion().mibToBytes(1L)+increaseSize)));
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_LMDB_INCREASE_SIZE, location = CommonDataProvider.class)
    public void getDatabaseSize_valuesAdded_increaseDatabaseSize_returnResizedDatabaseSize(long increaseSize) throws IOException {
        // arrange
        int keysToAdd = 1024*16;
        File lmdbFolder = folder.newFolder("lmdb");
        
        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        
        LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils);
        lmdbPersistence.init();
        
        fillWithRandomKeys(keysToAdd, lmdbPersistence);
        
        // act
        lmdbPersistence.increaseDatabaseSize(increaseSize);
        
        // assert
        long databaseSize = lmdbPersistence.getDatabaseSize();
        assertThat(databaseSize, is(equalTo(new ByteConversion().mibToBytes(1L)+increaseSize)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="increaseDatabaseSize">
    @Test(expected = org.lmdbjava.Env.MapFullException.class)
    public void putNewAmount_initialLMDBSetTo1MiB_fillWithTooMuchValues_exceptionThrown() throws IOException {
        // arrange
        File lmdbFolder = folder.newFolder("lmdb");
        
        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        cLMDBConfigurationWrite.increaseMapAutomatically = false;
        cLMDBConfigurationWrite.increaseSizeInMiB = 1;
        
        LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils);
        lmdbPersistence.init();
        
        // pre assert
        assertThat(lmdbPersistence.getDatabaseSize(), is(equalTo(new ByteConversion().mibToBytes(1L))));
        assertThat(lmdbPersistence.getIncreasedCounter(), is(equalTo(new ByteConversion().mibToBytes(0L))));
        assertThat(lmdbPersistence.getIncreasedSum(), is(equalTo(new ByteConversion().mibToBytes(0L))));
        
        // act, assert
        fillWithRandomKeys(TOO_MUCH_KEYS_FOR_1MiB, lmdbPersistence);
    }

    @Test
    public void putNewAmount_initialLMDBSetTo1MiB_fillWithTooMuchValues_increaseDatabaseSizeAndNoExceptionThrown() throws IOException {
        // arrange
        File lmdbFolder = folder.newFolder("lmdb");

        CLMDBConfigurationWrite cLMDBConfigurationWrite = new CLMDBConfigurationWrite();
        cLMDBConfigurationWrite.initialMapSizeInMiB = 1;
        cLMDBConfigurationWrite.lmdbDirectory = lmdbFolder.getAbsolutePath();
        cLMDBConfigurationWrite.increaseMapAutomatically = true;
        cLMDBConfigurationWrite.increaseSizeInMiB = 1;

        LMDBPersistence lmdbPersistence = new LMDBPersistence(cLMDBConfigurationWrite, persistenceUtils);
        lmdbPersistence.init();

        // pre assert
        assertThat(lmdbPersistence.getDatabaseSize(), is(equalTo(new ByteConversion().mibToBytes(1L))));
        assertThat(lmdbPersistence.getIncreasedCounter(), is(equalTo(new ByteConversion().mibToBytes(0L))));
        assertThat(lmdbPersistence.getIncreasedSum(), is(equalTo(new ByteConversion().mibToBytes(0L))));

        // act
        fillWithRandomKeys(TOO_MUCH_KEYS_FOR_1MiB, lmdbPersistence);

        // post assert
        assertThat(lmdbPersistence.getDatabaseSize(), is(equalTo(new ByteConversion().mibToBytes(1L) + (new ByteConversion().mibToBytes(cLMDBConfigurationWrite.increaseSizeInMiB)) * TOO_MUCH_KEYS_EXPECTED_1MiB_INCREASES)));
        assertThat(lmdbPersistence.getIncreasedCounter(), is(equalTo((long) TOO_MUCH_KEYS_EXPECTED_1MiB_INCREASES)));
        assertThat(lmdbPersistence.getIncreasedSum(), is(equalTo(new ByteConversion().mibToBytes(cLMDBConfigurationWrite.increaseSizeInMiB * TOO_MUCH_KEYS_EXPECTED_1MiB_INCREASES))));
    }
    // </editor-fold>
    
    private void fillWithRandomKeys(int keysToAdd, LMDBPersistence lmdbPersistence) {
        // arrange - fill
        for (int i = 0; i < keysToAdd; i++) {
            BigInteger secret = keyUtility.createSecret(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS, random);
            ECKey ecKey = keyUtility.createECKey(secret, true);
            byte[] hash160 = ecKey.getPubKeyHash();
            ByteBuffer hash160ByteBuffer = byteBufferUtility.byteArrayToByteBuffer(hash160);
            
            lmdbPersistence.putNewAmount(hash160ByteBuffer, Coin.SATOSHI);
        }
    }
}
