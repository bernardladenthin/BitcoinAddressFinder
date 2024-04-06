// @formatter:off
/**
 * Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
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
import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class MockKeyProducerTest {
    
    private final NetworkParameters networkParameters = MainNetParams.get();
    private final KeyUtility keyUtility = new KeyUtility(networkParameters, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();
    /**
     * This random is fine to produce with lower private key bits: 1; 0; 1; 0
     */
    private final Random random = new Random(1);
    
    // <editor-fold defaultstate="collapsed" desc="createSecrets ReturnStartSecretOnlyIsTrue">
    @Test
    public void createSecrets_maximumBitLengthIs1BatchSizeIs0ReturnStartSecretOnlyIsTrue_returnMinPrivateKey() throws IOException, NoMoreSecretsAvailableException {
        // arrange
        int maximumBitLength = 1;
        int batchSizeInBits = 0;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        
        // act
        BigInteger[] createSecrets = mockKeyProducer.createSecrets(batchSizeInBits, true);
        
        // assert
        assertThat(createSecrets.length, is(equalTo(1)));
        assertThat(createSecrets[0], is(equalTo(PublicKeyBytes.MIN_PRIVATE_KEY)));
    }
    
    @Test
    public void createSecrets_maximumBitLengthIs1BatchSizeIs1ReturnStartSecretOnlyIsTrue_returnMinPrivateKey() throws IOException, NoMoreSecretsAvailableException {
        // arrange
        int maximumBitLength = 1;
        int batchSizeInBits = 1;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        
        // act
        BigInteger[] createSecrets = mockKeyProducer.createSecrets(batchSizeInBits, true);
        
        // assert
        assertThat(createSecrets.length, is(equalTo(1)));
        assertThat(createSecrets[0], is(equalTo(PublicKeyBytes.MIN_PRIVATE_KEY)));
    }
    
    @Test
    public void createSecrets_maximumBitLengthIs1BatchSizeIs2ReturnStartSecretOnlyIsTrue_returnMinPrivateKey() throws IOException, NoMoreSecretsAvailableException {
        // arrange
        int maximumBitLength = 1;
        int batchSizeInBits = 2;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        
        // act
        BigInteger[] createSecrets = mockKeyProducer.createSecrets(batchSizeInBits, true);
        
        // assert
        assertThat(createSecrets.length, is(equalTo(1)));
        assertThat(createSecrets[0], is(equalTo(PublicKeyBytes.MIN_PRIVATE_KEY)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="createSecrets ReturnStartSecretOnlyIsFalse">
    @Test
    public void createSecrets_maximumBitLengthIs1BatchSizeIs0ReturnStartSecretOnlyIsFalse_returnMinPrivateKey() throws IOException, NoMoreSecretsAvailableException {
        // arrange
        int maximumBitLength = 1;
        int batchSizeInBits = 0;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        
        // act
        BigInteger[] createSecrets = mockKeyProducer.createSecrets(batchSizeInBits, false);
        
        // assert
        assertThat(createSecrets.length, is(equalTo(bitHelper.convertBitsToSize(batchSizeInBits))));
        assertThat(createSecrets[0], is(equalTo(PublicKeyBytes.MIN_PRIVATE_KEY)));
    }
    
    @Test
    public void createSecrets_maximumBitLengthIs1BatchSizeIs1ReturnStartSecretOnlyIsFalse_returnMinPrivateKey() throws IOException, NoMoreSecretsAvailableException {
        // arrange
        int maximumBitLength = 1;
        int batchSizeInBits = 1;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        
        // act
        BigInteger[] createSecrets = mockKeyProducer.createSecrets(1, false);
        
        // assert
        assertThat(createSecrets.length, is(equalTo(bitHelper.convertBitsToSize(batchSizeInBits))));
        assertThat(createSecrets[0], is(equalTo(BigInteger.ONE)));
        assertThat(createSecrets[1], is(equalTo(BigInteger.ZERO)));
    }
    
    @Test
    public void createSecrets_maximumBitLengthIs1BatchSizeIs2ReturnStartSecretOnlyIsFalse_returnMinPrivateKey() throws IOException, NoMoreSecretsAvailableException {
        // arrange
        int maximumBitLength = 1;
        int batchSizeInBits = 2;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        
        // act
        BigInteger[] createSecrets = mockKeyProducer.createSecrets(batchSizeInBits, false);
        
        // assert
        assertThat(createSecrets.length, is(equalTo(bitHelper.convertBitsToSize(batchSizeInBits))));
        assertThat(createSecrets[0], is(equalTo(BigInteger.ONE)));
        assertThat(createSecrets[1], is(equalTo(BigInteger.ZERO)));
        assertThat(createSecrets[2], is(equalTo(BigInteger.ONE)));
        assertThat(createSecrets[3], is(equalTo(BigInteger.ZERO)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="createStatisticsMessage">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BIT_SIZES_LOWER_THAN_25, location = CommonDataProvider.class)
    public void createSecrets_parameterBatchSizeInBitsFromDataProviderAndReturnStartSecretOnlyTrue_returnExpectedSecrets(int maximumBitLength) throws NoMoreSecretsAvailableException {
        // arrange
        int batchSizeInBits = 2;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        
        // act
        BigInteger[] createSecrets = mockKeyProducer.createSecrets(batchSizeInBits, true);
        
        // assert
        assertThat(createSecrets.length, is(equalTo(1)));
    }
    
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BIT_SIZES_LOWER_THAN_25, location = CommonDataProvider.class)
    public void createSecrets_parameterBatchSizeInBitsFromDataProviderAndReturnStartSecretOnlyFalse_returnExpectedSecrets(int maximumBitLength) throws NoMoreSecretsAvailableException {
        // arrange
        int batchSizeInBits = 2;
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        
        // act
        BigInteger[] createSecrets = mockKeyProducer.createSecrets(batchSizeInBits, false);
        
        // assert
        assertThat(createSecrets.length, is(equalTo(bitHelper.convertBitsToSize(batchSizeInBits))));
    }
    // </editor-fold>
}
