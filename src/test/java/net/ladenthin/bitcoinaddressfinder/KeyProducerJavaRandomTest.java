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
import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandomInstance;
import org.bitcoinj.base.Network;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class KeyProducerJavaRandomTest {
    
    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();
    
    String keyProducerId = "exampleId";
    
    // <editor-fold defaultstate="collapsed" desc="createStatisticsMessage">
    @Test
    public void createSecrets_parameterBatchSizeInBitsZeroAndReturnStartSecretOnlyTrue_returnExpectedSecrets() throws NoMoreSecretsAvailableException {
        // arrange
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = keyProducerId;
        cKeyProducerJavaRandom.keyProducerJavaRandomInstance = CKeyProducerJavaRandomInstance.RANDOM_CUSTOM_SEED;
        cKeyProducerJavaRandom.customSeed = 0L;
        cKeyProducerJavaRandom.privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
        
        KeyProducerJavaRandom keyProducerJavaRandom = new KeyProducerJavaRandom(cKeyProducerJavaRandom, keyUtility, bitHelper);
        
        int overallWorkSize = bitHelper.convertBitsToSize(0);
        
        // act
        BigInteger[] result = keyProducerJavaRandom.createSecrets(overallWorkSize, true);
        
        // assert
        assertThat(result.length, is(equalTo(1)));
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BIT_SIZES_AT_MOST_24, location = CommonDataProvider.class)
    public void createSecrets_parameterBatchSizeInBitsFromDataProviderAndReturnStartSecretOnlyTrue_returnExpectedSecrets(int batchSizeInBits) throws NoMoreSecretsAvailableException {
        // arrange
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = keyProducerId;
        cKeyProducerJavaRandom.keyProducerJavaRandomInstance = CKeyProducerJavaRandomInstance.RANDOM_CUSTOM_SEED;
        cKeyProducerJavaRandom.customSeed = 0L;
        cKeyProducerJavaRandom.privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
        
        KeyProducerJavaRandom keyProducerJavaRandom = new KeyProducerJavaRandom(cKeyProducerJavaRandom, keyUtility, bitHelper);
        
        int overallWorkSize = bitHelper.convertBitsToSize(batchSizeInBits);
        
        // act
        BigInteger[] result = keyProducerJavaRandom.createSecrets(overallWorkSize, true);
        
        // assert
        assertThat(result.length, is(equalTo(1)));
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BIT_SIZES_AT_MOST_24, location = CommonDataProvider.class)
    public void createSecrets_parameterBatchSizeInBitsFromDataProviderAndReturnStartSecretOnlyFalse_returnExpectedSecrets(int batchSizeInBits) throws NoMoreSecretsAvailableException {
        // arrange
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = keyProducerId;
        cKeyProducerJavaRandom.keyProducerJavaRandomInstance = CKeyProducerJavaRandomInstance.RANDOM_CUSTOM_SEED;
        cKeyProducerJavaRandom.customSeed = 0L;
        cKeyProducerJavaRandom.privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
                
        KeyProducerJavaRandom keyProducerJavaRandom = new KeyProducerJavaRandom(cKeyProducerJavaRandom, keyUtility, bitHelper);
        
        int overallWorkSize = bitHelper.convertBitsToSize(batchSizeInBits);
        
        // act
        BigInteger[] result = keyProducerJavaRandom.createSecrets(overallWorkSize, false);
        
        // assert
        assertThat(result.length, is(equalTo(bitHelper.convertBitsToSize(batchSizeInBits))));
    }
    // </editor-fold>
    
    
    // <editor-fold defaultstate="collapsed" desc="testAllRNGs">
    private BigInteger[] generateSecrets(CKeyProducerJavaRandomInstance instance, Long customSeed) throws NoMoreSecretsAvailableException {
        CKeyProducerJavaRandom config = new CKeyProducerJavaRandom();
        config.keyProducerId = keyProducerId;
        config.keyProducerJavaRandomInstance = instance;
        config.customSeed = customSeed;
        config.privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
        if (instance == CKeyProducerJavaRandomInstance.BIP39_SEED) {
            config.mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
            config.passphrase = "";
            config.bip32Path = CKeyProducerJavaRandom.DEFAULT_BIP32_PATH;
            config.creationTimeSeconds = 0L;
        }
        KeyProducerJavaRandom producer = new KeyProducerJavaRandom(config, keyUtility, bitHelper);
        return producer.createSecrets(bitHelper.convertBitsToSize(0), true);
    }

    @Test
    public void testSecureRandom() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomInstance.SECURE_RANDOM, null);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testRandomSeedCurrentTimeMillis() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomInstance.RANDOM_CURRENT_TIME_MILLIS_SEED, null);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testRandomCustomSeed_default() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomInstance.RANDOM_CUSTOM_SEED, null);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testRandomCustomSeed_fixed() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomInstance.RANDOM_CUSTOM_SEED, 123456789L);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testSha1Prng_default() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomInstance.SHA1_PRNG, null);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testSha1Prng_fixed() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomInstance.SHA1_PRNG, 987654321L);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testBip39Seed() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomInstance.BIP39_SEED, null);
        assertThat(result.length, is(equalTo(1)));
    }
    // </editor-fold>
    
    @Test
    public void testDefaultBip32PathConstant() {
        assertThat(CKeyProducerJavaRandom.DEFAULT_BIP32_PATH, is("M/44H/0H/0H/0"));
    }
}
