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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CConsumerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses1337;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses42;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesFiles;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddressesLMDB;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.params.MainNetParams;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;

@RunWith(DataProviderRunner.class)
public class ConsumerJavaTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    
    private final NetworkParameters networkParameters = MainNetParams.get();
    private final KeyUtility keyUtility = new KeyUtility(networkParameters, new ByteBufferUtility(false));
    private final PersistenceUtils persistenceUtils = new PersistenceUtils(networkParameters);

    private ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);

    @Test(expected = org.lmdbjava.LmdbNativeException.class)
    public void initLMDB_lmdbNotExisting_noExceptionThrown() throws IOException {
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = folder.newFolder().getAbsolutePath();

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, shouldRun, keyUtility, persistenceUtils);
        consumerJava.initLMDB();
    }

    @Test
    public void startStatisticsTimer_noExceptionThrown() throws IOException, InterruptedException {
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.printStatisticsEveryNSeconds = 1;
        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, shouldRun, keyUtility, persistenceUtils);
        Logger logger = mock(Logger.class);
        consumerJava.setLogger(logger);

        // act
        consumerJava.startStatisticsTimer();

        // assert
        Thread.sleep(3900);
        consumerJava.timer.cancel();

        List<String> arguments = logCaptor.getAllValues();
        verify(logger, atLeast(3)).info(logCaptor.capture());

        assertThat(arguments.get(0), is(equalTo("Statistics: [Checked 0 M keys in 0 minutes] [0 k keys/second] [0 M keys/minute] [Times an empty consumer: 0] [Average contains time: 0 ms] [keys queue size: 0] [Hits: 0]")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void startStatisticsTimer_invalidparameter_throwsException() throws IOException {
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.printStatisticsEveryNSeconds = 0;

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, shouldRun, keyUtility, persistenceUtils);
        consumerJava.startStatisticsTimer();
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void runProber_testAddressGiven_hitExpected(boolean compressed, boolean useStaticAmount) throws IOException, InterruptedException, MnemonicException.MnemonicLengthException {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();

        TestAddressesFiles testAddresses = new TestAddressesFiles(compressed);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, false);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.runtimePublicKeyCalculationCheck = true;

        AtomicBoolean shouldRun = new AtomicBoolean(true);

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, shouldRun, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        Random randomForProducer = new Random(TestAddresses42.RANDOM_SEED);
        
        CProducerJava cProducerJava = new CProducerJava();
        ProducerJava producerJava = new ProducerJava(cProducerJava, shouldRun, consumerJava, keyUtility, randomForProducer);

        Logger logger = mock(Logger.class);
        consumerJava.setLogger(logger);
        producerJava.produceKeys();
        consumerJava.consumeKeys(createHash160ByteBuffer());

        // assert
        assertThat(consumerJava.hits.get(), is(equalTo(1L)));
        assertThat(consumerJava.vanityHits.get(), is(equalTo(0L)));
        verify(logger, times(6)).info(logCaptor.capture());

        List<String> arguments = logCaptor.getAllValues();

        ECKey key = new TestAddresses42(1, compressed).getECKeys().get(0);
        KeyUtility keyUtility = new KeyUtility(MainNetParams.get(), new ByteBufferUtility(false));
        
        PublicKeyBytes publicKeyBytes = PublicKeyBytes.fromPrivate(key.getPrivKey());
        
        // to prevent any exception in further hit message creation and a possible missing hit message, log the secret alone first that a recovery is possible
        String hitMessageSecretKey = ConsumerJava.HIT_SAFE_PREFIX + "publicKeyBytes.getSecretKey(): " + key.getPrivKey();
        assertThat(arguments.get(0), is(equalTo(hitMessageSecretKey)));
        
        String hitMessagePublicKeyBytesUncompressed = ConsumerJava.HIT_SAFE_PREFIX + "publicKeyBytes.getUncompressed(): " + Hex.encodeHexString(publicKeyBytes.getUncompressed());
        assertThat(arguments.get(1), is(equalTo(hitMessagePublicKeyBytesUncompressed)));
        
        String hitMessagePublicKeyBytesCompressed = ConsumerJava.HIT_SAFE_PREFIX + "publicKeyBytes.getCompressed(): " + Hex.encodeHexString(publicKeyBytes.getCompressed());
        assertThat(arguments.get(2), is(equalTo(hitMessagePublicKeyBytesCompressed)));
        
        String hitMessageHash160Uncompressed = ConsumerJava.HIT_SAFE_PREFIX + "hash160Uncompressed: " + Hex.encodeHexString(publicKeyBytes.getUncompressedKeyHash());
        assertThat(arguments.get(3), is(equalTo(hitMessageHash160Uncompressed)));
        
        String hitMessageHash160Compressed = ConsumerJava.HIT_SAFE_PREFIX + "hash160Compressed: " + Hex.encodeHexString(publicKeyBytes.getCompressedKeyHash());
        assertThat(arguments.get(4), is(equalTo(hitMessageHash160Compressed)));
        
        String hitMessageFull = ConsumerJava.HIT_PREFIX + keyUtility.createKeyDetails(key);
        assertThat(arguments.get(5), is(equalTo(hitMessageFull)));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_COMPRESSED_AND_STATIC_AMOUNT, location = CommonDataProvider.class)
    public void runProber_unknownAddressGiven_missExpected(boolean compressed, boolean useStaticAmount) throws IOException, InterruptedException, MnemonicException.MnemonicLengthException {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();

        TestAddressesFiles testAddresses = new TestAddressesFiles(compressed);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, useStaticAmount, false);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.runtimePublicKeyCalculationCheck = true;

        AtomicBoolean shouldRun = new AtomicBoolean(true);

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, shouldRun, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        Random randomForProducer = new Random(TestAddresses1337.RANDOM_SEED);
        
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.gridNumBits = 0;
        ProducerJava producerJava = new ProducerJava(cProducerJava, shouldRun, consumerJava, keyUtility, randomForProducer);

        Logger logger = mock(Logger.class);
        when(logger.isTraceEnabled()).thenReturn(true);
        consumerJava.setLogger(logger);
        producerJava.produceKeys();
        
        consumerJava.consumeKeys(createHash160ByteBuffer());

        // assert
        assertThat(consumerJava.hits.get(), is(equalTo(0L)));
        assertThat(consumerJava.vanityHits.get(), is(equalTo(0L)));
        verify(logger, times(2)).trace(logCaptor.capture());

        List<String> arguments = logCaptor.getAllValues();

        ECKey unknownKeyUncompressed = new TestAddresses1337(1, false).getECKeys().get(0);
        ECKey unknownKeyCompressed = new TestAddresses1337(1, true).getECKeys().get(0);
        KeyUtility keyUtility = new KeyUtility(MainNetParams.get(), new ByteBufferUtility(false));
        String missMessageUncompressed = ConsumerJava.MISS_PREFIX + keyUtility.createKeyDetails(unknownKeyUncompressed);
        String missMessageCompressed = ConsumerJava.MISS_PREFIX + keyUtility.createKeyDetails(unknownKeyCompressed);
        assertThat(arguments.get(0), is(equalTo(missMessageUncompressed)));
        assertThat(arguments.get(1), is(equalTo(missMessageCompressed)));
    }

    @Test
    public void consumeKeys_invalidSecretGiven_continueExpectedAndNoExceptionThrown() throws IOException, InterruptedException, DecoderException, MnemonicException.MnemonicLengthException {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();

        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.runtimePublicKeyCalculationCheck = true;

        AtomicBoolean shouldRun = new AtomicBoolean(true);

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, shouldRun, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        Logger logger = mock(Logger.class);
        consumerJava.setLogger(logger);

        PublicKeyBytes invalidPublicKeyBytes = PublicKeyBytes.INVALID_KEY_ONE;
        PublicKeyBytes[] publicKeyBytesArray = new PublicKeyBytes[]{invalidPublicKeyBytes};
        consumerJava.consumeKeys(publicKeyBytesArray);
        consumerJava.consumeKeys(createHash160ByteBuffer());
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_COMPRESSED, location = CommonDataProvider.class)
    public void consumeKeys_invalidPublicKeyHashUncompressedGiven_ThrowException(boolean compressed) throws IOException, InterruptedException, DecoderException, MnemonicException.MnemonicLengthException {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();

        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.runtimePublicKeyCalculationCheck = true;

        AtomicBoolean shouldRun = new AtomicBoolean(true);

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, shouldRun, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        Logger logger = mock(Logger.class);
        consumerJava.setLogger(logger);

        PublicKeyBytes invalidPublicKeyBytes = PublicKeyBytes.fromPrivate(BigInteger.valueOf(1337));
        // invalidate compressed or uncompressed
        if (compressed) {
            invalidPublicKeyBytes.getCompressed()[7] = 0;
        } else {
            invalidPublicKeyBytes.getUncompressed()[7] = 0;
        }
        PublicKeyBytes[] publicKeyBytesArray = new PublicKeyBytes[]{invalidPublicKeyBytes};
        consumerJava.consumeKeys(publicKeyBytesArray);
        consumerJava.consumeKeys(createHash160ByteBuffer());
        
        // assert
        assertThat(consumerJava.hits.get(), is(equalTo(0L)));
        assertThat(consumerJava.vanityHits.get(), is(equalTo(0L)));
        verify(logger, times(6)).error(logCaptor.capture());
        
        List<String> arguments = logCaptor.getAllValues();
        
        if (compressed) {
            assertThat(arguments.get(0), is(equalTo("fromPrivateCompressed.getPubKeyHash() != hash160Compressed")));
            assertThat(arguments.get(1), is(equalTo("getSecretKey: 1337")));
            assertThat(arguments.get(2), is(equalTo("pubKeyCompressed: 02db0c51cc634a0096374b0b895584a3ca2fb3bea4fd0ee2361f8db63a650fcee6")));
            assertThat(arguments.get(3), is(equalTo("pubKeyCompressedFromEcKey: 02db0c51cc634a4096374b0b895584a3ca2fb3bea4fd0ee2361f8db63a650fcee6")));
            assertThat(arguments.get(4), is(equalTo("hash160Compressed: a1039a5001eaccd75abb339b446b83b1ecf54ef7")));
            assertThat(arguments.get(5), is(equalTo("hash160CompressedFromEcKey: 879f5696d90c1c280fa3c7d77723ebc59d7ac108")));
        } else {
            assertThat(arguments.get(0), is(equalTo("fromPrivateUncompressed.getPubKeyHash() != hash160Uncompressed")));
            assertThat(arguments.get(1), is(equalTo("getSecretKey: 1337")));
            assertThat(arguments.get(2), is(equalTo("pubKeyUncompressed: 04db0c51cc634a0096374b0b895584a3ca2fb3bea4fd0ee2361f8db63a650fcee67ec0bd2baea1ae184bd16fd397b0e64d5d28257f85836486367fe33cc5b6e6a0")));
            assertThat(arguments.get(3), is(equalTo("pubKeyUncompressedFromEcKey: 04db0c51cc634a4096374b0b895584a3ca2fb3bea4fd0ee2361f8db63a650fcee67ec0bd2baea1ae184bd16fd397b0e64d5d28257f85836486367fe33cc5b6e6a0")));
            assertThat(arguments.get(4), is(equalTo("hash160Uncompressed: 1a69285cb42032d77801a15a30357d510b247100")));
            assertThat(arguments.get(5), is(equalTo("hash160UncompressedFromEcKey: e02e1cae178d3a2f84a5d897ee8b7ed6c0e2bbc4")));
        }
    }
    
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_COMPRESSED, location = CommonDataProvider.class)
    public void consumeKeys_testVanityPattern_patternMatches(boolean compressed) throws IOException, InterruptedException, DecoderException, MnemonicException.MnemonicLengthException {
        TestAddressesLMDB testAddressesLMDB = new TestAddressesLMDB();

        TestAddressesFiles testAddresses = new TestAddressesFiles(false);
        File lmdbFolderPath = testAddressesLMDB.createTestLMDB(folder, testAddresses, true, true);

        CConsumerJava cConsumerJava = new CConsumerJava();
        cConsumerJava.lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
        cConsumerJava.lmdbConfigurationReadOnly.lmdbDirectory = lmdbFolderPath.getAbsolutePath();
        cConsumerJava.runtimePublicKeyCalculationCheck = true;
        cConsumerJava.enableVanity = true;
        if (compressed) {
            // 1JYHzX3ndZEcnjrWSQ9VC7324TJ9BAoGy4
            cConsumerJava.vanityPattern = "1JYH.*";
        } else {
            // 14sNbmEhgiGX6BZe9Q5PCgTQT3576mniZt
            cConsumerJava.vanityPattern = "14sN.*";
        }

        AtomicBoolean shouldRun = new AtomicBoolean(true);

        ConsumerJava consumerJava = new ConsumerJava(cConsumerJava, shouldRun, keyUtility, persistenceUtils);
        consumerJava.initLMDB();

        Logger logger = mock(Logger.class);
        consumerJava.setLogger(logger);

        // https://privatekeys.pw/key/0000000000000000000000000000000000000000000000000000000000000049
        PublicKeyBytes publicKeyBytes = PublicKeyBytes.fromPrivate(BigInteger.valueOf(73));
        PublicKeyBytes[] publicKeyBytesArray = new PublicKeyBytes[]{publicKeyBytes};
        
        consumerJava.consumeKeys(publicKeyBytesArray);
        consumerJava.consumeKeys(createHash160ByteBuffer());
        
        // assert
        assertThat(consumerJava.hits.get(), is(equalTo(0L)));
        assertThat(consumerJava.vanityHits.get(), is(equalTo(1L)));
        verify(logger, times(6)).info(logCaptor.capture());
        
        List<String> arguments = logCaptor.getAllValues();
        
        if (compressed) {
            assertThat(arguments.get(0), is(equalTo("hit: safe log: publicKeyBytes.getSecretKey(): 73")));
            assertThat(arguments.get(1), is(equalTo("hit: safe log: publicKeyBytes.getUncompressed(): 04af3c423a95d9f5b3054754efa150ac39cd29552fe360257362dfdecef4053b45f98a3fd831eb2b749a93b0e6f35cfb40c8cd5aa667a15581bc2feded498fd9c6")));
            assertThat(arguments.get(2), is(equalTo("hit: safe log: publicKeyBytes.getCompressed(): 02af3c423a95d9f5b3054754efa150ac39cd29552fe360257362dfdecef4053b45")));
            assertThat(arguments.get(3), is(equalTo("hit: safe log: hash160Uncompressed: 2a6f34a72c181bdd4e6d91ffa69e84fd6c49b207")));
            assertThat(arguments.get(4), is(equalTo("hit: safe log: hash160Compressed: c065379323a549fc3547bcb1937d5dcb48df2396")));
            assertThat(arguments.get(5), is(equalTo("vanity pattern match: privateKeyBigInteger: [73] privateKeyBytes: [[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 73]] privateKeyHex: [0000000000000000000000000000000000000000000000000000000000000049] WiF: [KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU7fj3itoEY] publicKeyAsHex: [02af3c423a95d9f5b3054754efa150ac39cd29552fe360257362dfdecef4053b45] publicKeyHash160Hex: [c065379323a549fc3547bcb1937d5dcb48df2396] publicKeyHash160Base58: [1JYHzX3ndZEcnjrWSQ9VC7324TJ9BAoGy4] Compressed: [true] Mnemonic: [abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abuse, differ]")));
        } else {
            assertThat(arguments.get(0), is(equalTo("hit: safe log: publicKeyBytes.getSecretKey(): 73")));
            assertThat(arguments.get(1), is(equalTo("hit: safe log: publicKeyBytes.getUncompressed(): 04af3c423a95d9f5b3054754efa150ac39cd29552fe360257362dfdecef4053b45f98a3fd831eb2b749a93b0e6f35cfb40c8cd5aa667a15581bc2feded498fd9c6")));
            assertThat(arguments.get(2), is(equalTo("hit: safe log: publicKeyBytes.getCompressed(): 02af3c423a95d9f5b3054754efa150ac39cd29552fe360257362dfdecef4053b45")));
            assertThat(arguments.get(3), is(equalTo("hit: safe log: hash160Uncompressed: 2a6f34a72c181bdd4e6d91ffa69e84fd6c49b207")));
            assertThat(arguments.get(4), is(equalTo("hit: safe log: hash160Compressed: c065379323a549fc3547bcb1937d5dcb48df2396")));
            assertThat(arguments.get(5), is(equalTo("vanity pattern match: privateKeyBigInteger: [73] privateKeyBytes: [[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 73]] privateKeyHex: [0000000000000000000000000000000000000000000000000000000000000049] WiF: [5HpHagT65TZzG1PH3CSu63k8DbpvD8s5ip4nEB3kEsreJwwNRRr] publicKeyAsHex: [04af3c423a95d9f5b3054754efa150ac39cd29552fe360257362dfdecef4053b45f98a3fd831eb2b749a93b0e6f35cfb40c8cd5aa667a15581bc2feded498fd9c6] publicKeyHash160Hex: [2a6f34a72c181bdd4e6d91ffa69e84fd6c49b207] publicKeyHash160Base58: [14sNbmEhgiGX6BZe9Q5PCgTQT3576mniZt] Compressed: [false] Mnemonic: [abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abandon, abuse, differ]")));
        }
    }

    private ByteBuffer createHash160ByteBuffer() {
        ByteBuffer threadLocalReuseableByteBuffer = ByteBuffer.allocateDirect(PublicKeyBytes.HASH160_SIZE);
        return threadLocalReuseableByteBuffer;
    }
}
