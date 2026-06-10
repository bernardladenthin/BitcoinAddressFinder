// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandomAlgorithm;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.secret.NoMoreSecretsAvailableException;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class KeyProducerJavaRandomTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    String keyProducerId = "exampleId";

    private KeyProducerJavaRandom createKeyProducerJavaRandom(CKeyProducerJavaRandom cKeyProducerJavaRandom) {
        return new KeyProducerJavaRandom(cKeyProducerJavaRandom, keyUtility, bitHelper);
    }

    // <editor-fold defaultstate="collapsed" desc="createStatisticsMessage">
    @Test
    public void createSecrets_parameterBatchSizeInBitsZeroAndReturnStartSecretOnlyTrue_returnExpectedSecrets()
            throws NoMoreSecretsAvailableException {
        // arrange
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = keyProducerId;
        cKeyProducerJavaRandom.randomAlgorithm = CKeyProducerJavaRandomAlgorithm.RANDOM_CUSTOM_SEED;
        cKeyProducerJavaRandom.customSeed = 0L;
        cKeyProducerJavaRandom.privateKeyMaxNumBits = Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS;

        KeyProducerJavaRandom keyProducerJavaRandom = createKeyProducerJavaRandom(cKeyProducerJavaRandom);

        int overallWorkSize = bitHelper.convertBitsToSize(0);

        // act
        BigInteger[] result = keyProducerJavaRandom.createSecrets(overallWorkSize, true);

        // assert
        assertThat(result.length, is(equalTo(1)));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BIT_SIZES_AT_MOST_MAX)
    public void
            createSecrets_parameterBatchSizeInBitsFromDataProviderAndReturnStartSecretOnlyTrue_returnExpectedSecrets(
                    int batchSizeInBits) throws NoMoreSecretsAvailableException {
        // arrange
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = keyProducerId;
        cKeyProducerJavaRandom.randomAlgorithm = CKeyProducerJavaRandomAlgorithm.RANDOM_CUSTOM_SEED;
        cKeyProducerJavaRandom.customSeed = 0L;
        cKeyProducerJavaRandom.privateKeyMaxNumBits = Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS;

        KeyProducerJavaRandom keyProducerJavaRandom = createKeyProducerJavaRandom(cKeyProducerJavaRandom);

        int overallWorkSize = bitHelper.convertBitsToSize(batchSizeInBits);

        // act
        BigInteger[] result = keyProducerJavaRandom.createSecrets(overallWorkSize, true);

        // assert
        assertThat(result.length, is(equalTo(1)));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BIT_SIZES_AT_MOST_MAX)
    public void
            createSecrets_parameterBatchSizeInBitsFromDataProviderAndReturnStartSecretOnlyFalse_returnExpectedSecrets(
                    int batchSizeInBits) throws NoMoreSecretsAvailableException {
        // arrange
        CKeyProducerJavaRandom cKeyProducerJavaRandom = new CKeyProducerJavaRandom();
        cKeyProducerJavaRandom.keyProducerId = keyProducerId;
        cKeyProducerJavaRandom.randomAlgorithm = CKeyProducerJavaRandomAlgorithm.RANDOM_CUSTOM_SEED;
        cKeyProducerJavaRandom.customSeed = 0L;
        cKeyProducerJavaRandom.privateKeyMaxNumBits = Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS;

        KeyProducerJavaRandom keyProducerJavaRandom = createKeyProducerJavaRandom(cKeyProducerJavaRandom);

        int overallWorkSize = bitHelper.convertBitsToSize(batchSizeInBits);

        // act
        BigInteger[] result = keyProducerJavaRandom.createSecrets(overallWorkSize, false);

        // assert
        assertThat(result.length, is(equalTo(bitHelper.convertBitsToSize(batchSizeInBits))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="testAllRNGs">
    private BigInteger[] generateSecrets(CKeyProducerJavaRandomAlgorithm instance, @Nullable Long customSeed)
            throws NoMoreSecretsAvailableException {
        CKeyProducerJavaRandom config = new CKeyProducerJavaRandom();
        config.keyProducerId = keyProducerId;
        config.randomAlgorithm = instance;
        config.customSeed = customSeed;
        config.privateKeyMaxNumBits = Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS;
        KeyProducerJavaRandom producer = createKeyProducerJavaRandom(config);
        return producer.createSecrets(bitHelper.convertBitsToSize(0), true);
    }

    @Test
    public void testSecureRandom() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomAlgorithm.SECURE_RANDOM, null);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testRandomSeedCurrentTimeMillis() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomAlgorithm.RANDOM_CURRENT_TIME_MILLIS_SEED, null);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testRandomCustomSeed_default() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomAlgorithm.RANDOM_CUSTOM_SEED, null);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testRandomCustomSeed_fixed() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomAlgorithm.RANDOM_CUSTOM_SEED, 123456789L);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testSha1Prng_default() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomAlgorithm.SHA1_PRNG, null);
        assertThat(result.length, is(equalTo(1)));
    }

    @Test
    public void testSha1Prng_fixed() throws NoMoreSecretsAvailableException {
        BigInteger[] result = generateSecrets(CKeyProducerJavaRandomAlgorithm.SHA1_PRNG, 987654321L);
        assertThat(result.length, is(equalTo(1)));
    }
    // </editor-fold>
}
