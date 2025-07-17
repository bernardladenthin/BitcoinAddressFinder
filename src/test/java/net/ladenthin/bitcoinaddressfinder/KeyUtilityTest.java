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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticKey;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.ECKey;
import org.junit.Before;
import org.junit.Test;
import org.bitcoinj.crypto.MnemonicException;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(DataProviderRunner.class)
public class KeyUtilityTest {

    private final StaticKey staticKey = new StaticKey();
    private final Network network = new NetworkParameterFactory().getNetwork();
    
    @Before
    public void init() throws IOException {
    }

    // <editor-fold defaultstate="collapsed" desc="createECKey">
    @Test
    public void createECKey_TestUncompressed() throws IOException {
        // arrange
        BigInteger bigIntegerFromHex = new BigInteger(staticKey.privateKeyHex, 16);

        // act
        ECKey key = new KeyUtility(network, new ByteBufferUtility(false)).createECKey(bigIntegerFromHex, false);

        // assert
        byte[] hash160 = key.getPubKeyHash();
        ByteBuffer hash160AsByteBuffer = new ByteBufferUtility(false).byteArrayToByteBuffer(hash160);
        assertThat(key.isCompressed(), is(equalTo(Boolean.FALSE)));
        assertThat(hash160AsByteBuffer, is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
    }

    @Test
    public void createECKey_TestCompressed() throws IOException {
        // arrange
        BigInteger bigIntegerFromHex = new BigInteger(staticKey.privateKeyHex, 16);

        // act
        ECKey key = new KeyUtility(network, new ByteBufferUtility(false)).createECKey(bigIntegerFromHex, true);

        // assert
        byte[] hash160 = key.getPubKeyHash();
        ByteBuffer hash160AsByteBuffer = new ByteBufferUtility(false).byteArrayToByteBuffer(hash160);
        assertThat(key.isCompressed(), is(equalTo(Boolean.TRUE)));
        assertThat(hash160AsByteBuffer, is(equalTo(staticKey.byteBufferPublicKeyCompressed)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getHash160ByteBufferFromBase58String">
    @Test
    public void getHash160ByteBufferFromBase58String_TestUncompressed() throws IOException {
        // act
        ByteBuffer byteBufferPublicKeyUncompressed = new KeyUtility(network, new ByteBufferUtility(false)).getHash160ByteBufferFromBase58String(staticKey.publicKeyUncompressed);

        // assert
        assertThat(byteBufferPublicKeyUncompressed, is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
    }

    @Test
    public void getHash160ByteBufferFromBase58String_TestCompressed() throws IOException {
        // act
        ByteBuffer byteBufferPublicKeyCompressed = new KeyUtility(network, new ByteBufferUtility(false)).getHash160ByteBufferFromBase58String(staticKey.publicKeyCompressed);

        // assert
        assertThat(byteBufferPublicKeyCompressed, is(equalTo(staticKey.byteBufferPublicKeyCompressed)));
    }
    
    @Test
    public void byteBufferToAddress_isInverseOf_getHash160ByteBufferFromBase58String() {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        String originalBase58 = staticKey.publicKeyUncompressed;

        // act
        ByteBuffer hash160Buffer = keyUtility.getHash160ByteBufferFromBase58String(originalBase58);
        String backToBase58 = keyUtility.byteBufferToAddress(hash160Buffer).toBase58();

        // assert
        assertThat(backToBase58, is(equalTo(originalBase58)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getHexFromByteBuffer">
    @Test
    public void getHexFromByteBuffer_TestUncompressed() throws IOException {
        // act
        String hexPublicKeyUncompressed = new ByteBufferUtility(false).getHexFromByteBuffer(staticKey.byteBufferPublicKeyUncompressed);

        // assert
        assertThat(hexPublicKeyUncompressed, is(equalTo(staticKey.publicKeyUncompressedHash160Hex)));
    }

    @Test
    public void getHexFromByteBuffer_TestCompressed() throws IOException {
        // act
        String hexPublicKeyCompressed = new ByteBufferUtility(false).getHexFromByteBuffer(staticKey.byteBufferPublicKeyCompressed);

        // assert
        assertThat(hexPublicKeyCompressed, is(equalTo(staticKey.publicKeyCompressedHash160Hex)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getByteBufferFromHex">
    @Test
    public void getByteBufferFromHex_TestUncompressed() throws IOException {
        // act
        ByteBuffer byteBufferPublicKeyUncompressed = new ByteBufferUtility(false).getByteBufferFromHex(staticKey.publicKeyUncompressedHash160Hex);

        // assert
        assertThat(byteBufferPublicKeyUncompressed, is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
    }

    @Test
    public void getByteBufferFromHex_TestCompressed() throws IOException {
        // act
        ByteBuffer byteBufferPublicKeyCompressed = new ByteBufferUtility(false).getByteBufferFromHex(staticKey.publicKeyCompressedHash160Hex);

        // assert
        assertThat(byteBufferPublicKeyCompressed, is(equalTo(staticKey.byteBufferPublicKeyCompressed)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="createSecret">
    @Test
    public void createSecret() throws IOException {
        // act
        BigInteger secret = new KeyUtility(network, new ByteBufferUtility(false)).createSecret(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS, new Random(42));

        // assert
        assertThat(secret.toString(), is(not(equalTo(""))));
    }
    
    @Test
    public void createSecret_zeroBits_returnsZero() {
        BigInteger secret = new KeyUtility(network, new ByteBufferUtility(false)).createSecret(0, new Random(123));
        assertThat(secret, is(equalTo(BigInteger.ZERO)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="createKeyDetails">
    @Test
    public void createKeyDetails_Uncompressed() throws IOException, MnemonicException.MnemonicLengthException {
        // arrange
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(false);
        KeyUtility keyUtility = new KeyUtility(network, byteBufferUtility);

        BigInteger secret = new BigInteger(staticKey.privateKeyHex, 16);
        ECKey ecKey = keyUtility.createECKey(secret, false);

        // act
        String keyDetails = keyUtility.createKeyDetails(ecKey);

        // assert
        String mnemonics = keyUtility.createMnemonics(ecKey.getPrivKeyBytes());
        assertThat(keyDetails, is(equalTo("privateKeyBigInteger: [" + staticKey.privateKeyBigInteger + "] privateKeyBytes: [" + Arrays.toString(staticKey.privateKeyBytes) + "] privateKeyHex: [" + staticKey.privateKeyHex + "] WiF: [" + staticKey.privateKeyWiFUncompressed + "] publicKeyAsHex: [" + staticKey.publicKeyUncompressedHex + "] publicKeyHash160Hex: [" + staticKey.publicKeyUncompressedHash160Hex + "] publicKeyHash160Base58: [" + staticKey.publicKeyUncompressed + "] Compressed: [false] " + mnemonics)));
    }

    @Test
    public void createKeyDetails_Compressed() throws IOException, MnemonicException.MnemonicLengthException {
        // arrange
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(false);
        KeyUtility keyUtility = new KeyUtility(network, byteBufferUtility);

        BigInteger secret = new BigInteger(staticKey.privateKeyHex, 16);
        ECKey ecKey = keyUtility.createECKey(secret, true);

        // act
        String keyDetails = keyUtility.createKeyDetails(ecKey);

        // assert
        String mnemonics = keyUtility.createMnemonics(ecKey.getPrivKeyBytes());
        assertThat(keyDetails, is(equalTo("privateKeyBigInteger: [" + staticKey.privateKeyBigInteger + "] privateKeyBytes: [" + Arrays.toString(staticKey.privateKeyBytes) + "] privateKeyHex: [" + staticKey.privateKeyHex + "] WiF: [" + staticKey.privateKeyWiFCompressed + "] publicKeyAsHex: [" + staticKey.publicKeyCompressedHex + "] publicKeyHash160Hex: [" + staticKey.publicKeyCompressedHash160Hex + "] publicKeyHash160Base58: [" + staticKey.publicKeyCompressed + "] Compressed: [true] " + mnemonics)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="killBits">
    @Test
    public void killBits_valueWithAllBitsSetGiven_bitsKilled() throws IOException {
        // act
        BigInteger secret = new KeyUtility(network, new ByteBufferUtility(false)).killBits(BigInteger.valueOf(63L), BigInteger.valueOf(5L));

        // assert
        assertThat(secret, is(equalTo(BigInteger.valueOf(58))));
    }
    
    @Test
    public void killBits_valueWithNotAllBitsSetGiven_bitsKilled() throws IOException {
        // act
        BigInteger secret = new KeyUtility(network, new ByteBufferUtility(false)).killBits(BigInteger.valueOf(62L), BigInteger.valueOf(5L));

        // assert
        assertThat(secret, is(equalTo(BigInteger.valueOf(58))));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getMaxPrivateKeyForBatchSize">
    @Test
    public void getMaxPrivateKeyForBatchSize_batchSize0_returnsMaxPrivateKey() {
        // arrange
        int batchSizeInBits = 0;

        // act
        BigInteger result = KeyUtility.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        assertThat(result, is(equalTo(PublicKeyBytes.MAX_PRIVATE_KEY)));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_batchSize1_returnsMaxMinus1() {
        // arrange
        int batchSizeInBits = 1;

        // act
        BigInteger result = KeyUtility.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        assertThat(result.add(BigInteger.ONE), is(equalTo(PublicKeyBytes.MAX_PRIVATE_KEY)));
    }

    @Test
    public void getMaxPrivateKeyForBatchSize_maxAllowedBitSize_returnsMinimumSafeKey() {
        // arrange
        int batchSizeInBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS - 1;

        // act
        BigInteger result = KeyUtility.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // assert
        BigInteger offset = BigInteger.ONE.shiftLeft(batchSizeInBits);
        BigInteger expected = PublicKeyBytes.MAX_PRIVATE_KEY.subtract(offset).add(BigInteger.ONE);
        assertThat(result, is(equalTo(expected)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getMaxPrivateKeyForBatchSize_bitSizeNegative_throwsException() {
        // act
        KeyUtility.getMaxPrivateKeyForBatchSize(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getMaxPrivateKeyForBatchSize_bitSizeTooLarge_throwsException() {
        // act
        KeyUtility.getMaxPrivateKeyForBatchSize(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS + 1);
    }
    
    @Test(expected = IllegalStateException.class)
    public void getMaxPrivateKeyForBatchSize_tooLarge_throwsException() {
        // arrange
        int batchSizeInBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;

        // act
        KeyUtility.getMaxPrivateKeyForBatchSize(batchSizeInBits);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="isInvalidWithBatchSize">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_PRIVATE_KEYS_TOO_LARGE_WITH_CHUNK_SIZE, location = CommonDataProvider.class)
    public void isInvalidWithBatchSize_keyTooLarge_returnsTrue(BigInteger privateKey, int batchSizeInBits) {
        // arrange
        BigInteger maxAllowed = KeyUtility.getMaxPrivateKeyForBatchSize(batchSizeInBits);

        // act
        boolean isInvalid = KeyUtility.isInvalidWithBatchSize(privateKey, maxAllowed);

        // assert
        assertThat(isInvalid, is(true));
    }

    @Test
    public void isInvalidWithBatchSize_keyWithinLimit_returnsFalse() {
        // arrange
        int batchSizeInBits = 2;
        BigInteger maxAllowed = KeyUtility.getMaxPrivateKeyForBatchSize(batchSizeInBits);
        BigInteger validKey = maxAllowed.subtract(BigInteger.ONE);

        // act
        boolean isInvalid = KeyUtility.isInvalidWithBatchSize(validKey, maxAllowed);

        // assert
        assertThat(isInvalid, is(false));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="ECKey.fromPrivate: boundaries">
    @Test
    public void ecKey_fromPrivate_randomValidInRange_succeeds() {
        BigInteger randomValidKey = PublicKeyBytes.MIN_VALID_PRIVATE_KEY.add(BigInteger.valueOf(123456));
        ECKey ecKey = ECKey.fromPrivate(randomValidKey, false);
        assertThat(ecKey.getPrivKey(), is(equalTo(randomValidKey)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void ecKey_fromPrivate_minPrivateKey_throwsException() {
        // act
        ECKey.fromPrivate(PublicKeyBytes.MIN_PRIVATE_KEY, false);
    }
    
    @Ignore("bitcoinj.ECKey.fromPrivate(...) accepts values > MAX_PRIVATE_KEY without throwing an exception. " +
       "Test ignored because the library does not enforce the upper bound.")
    @Test(expected = IllegalArgumentException.class)
    public void ecKey_fromPrivate_maxPrivateKeyPlusOne_throwsException() {
        // act
        ECKey.fromPrivate(PublicKeyBytes.MAX_PRIVATE_KEY.add(BigInteger.ONE), false);
    }

    @Test
    public void ecKey_fromPrivate_minValidPrivateKey_noExceptionThrown() {
        // act
        ECKey ecKey = ECKey.fromPrivate(PublicKeyBytes.MIN_VALID_PRIVATE_KEY, false);

        // assert
        assertThat(ecKey.getPrivKey(), is(equalTo(PublicKeyBytes.MIN_VALID_PRIVATE_KEY)));
    }

    @Test
    public void ecKey_fromPrivate_maxPrivateKey_noExceptionThrown() {
        // act
        ECKey ecKey = ECKey.fromPrivate(PublicKeyBytes.MAX_PRIVATE_KEY, false);

        // assert
        assertThat(ecKey.getPrivKey(), is(equalTo(PublicKeyBytes.MAX_PRIVATE_KEY)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isOutsidePrivateKeyRange">
    @Test
    public void isOutsidePrivateKeyRange_minPrivateKey_returnsFalse() {
        // act
        boolean result = KeyUtility.isOutsidePrivateKeyRange(PublicKeyBytes.MIN_PRIVATE_KEY);

        // assert
        assertThat(result, is(true));
    }
    
    @Test
    public void isOutsidePrivateKeyRange_minValidPrivateKey_returnsFalse() {
        // act
        boolean result = KeyUtility.isOutsidePrivateKeyRange(PublicKeyBytes.MIN_VALID_PRIVATE_KEY);

        // assert
        assertThat(result, is(false));
    }

    @Test
    public void isOutsidePrivateKeyRange_maxPrivateKey_returnsFalse() {
        // act
        boolean result = KeyUtility.isOutsidePrivateKeyRange(PublicKeyBytes.MAX_PRIVATE_KEY);

        // assert
        assertThat(result, is(false));
    }

    @Test
    public void isOutsidePrivateKeyRange_zero_returnsTrue() {
        // act
        boolean result = KeyUtility.isOutsidePrivateKeyRange(BigInteger.ZERO);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void isOutsidePrivateKeyRange_belowMin_returnsTrue() {
        // arrange
        BigInteger invalidKey = PublicKeyBytes.MIN_PRIVATE_KEY.subtract(BigInteger.ONE);

        // act
        boolean result = KeyUtility.isOutsidePrivateKeyRange(invalidKey);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void isOutsidePrivateKeyRange_aboveMax_returnsTrue() {
        // arrange
        BigInteger invalidKey = PublicKeyBytes.MAX_PRIVATE_KEY.add(BigInteger.ONE);

        // act
        boolean result = KeyUtility.isOutsidePrivateKeyRange(invalidKey);

        // assert
        assertThat(result, is(true));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="returnValidPrivateKey">
    @Test
    public void returnValidPrivateKey_validKey_returnsSameKey() {
        BigInteger valid = PublicKeyBytes.MIN_PRIVATE_KEY.add(BigInteger.ONE);
        assertThat(KeyUtility.returnValidPrivateKey(valid), is(equalTo(valid)));
    }

    @Test
    public void returnValidPrivateKey_tooSmall_returnsReplacement() {
        BigInteger tooSmall = PublicKeyBytes.MIN_PRIVATE_KEY.subtract(BigInteger.ONE);
        assertThat(KeyUtility.returnValidPrivateKey(tooSmall), is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }

    @Test
    public void returnValidPrivateKey_tooLarge_returnsReplacement() {
        BigInteger tooLarge = PublicKeyBytes.MAX_PRIVATE_KEY.add(BigInteger.ONE);
        assertThat(KeyUtility.returnValidPrivateKey(tooLarge), is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="convertIntToBytesAndBack">
    @Test
    public void replaceInvalidPrivateKeys_mixedArray_replacesInvalids() {
        BigInteger[] secrets = new BigInteger[]{
            PublicKeyBytes.MIN_VALID_PRIVATE_KEY,                      // valid
            PublicKeyBytes.MAX_PRIVATE_KEY.add(BigInteger.ONE),        // invalid
            BigInteger.ZERO                                            // valid
        };

        KeyUtility.replaceInvalidPrivateKeys(secrets);

        assertThat(secrets[0], is(equalTo(PublicKeyBytes.MIN_VALID_PRIVATE_KEY)));
        assertThat(secrets[1], is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
        assertThat(secrets[2], is(equalTo(PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT)));
    }

    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="byteToIntAndViceVersa">
        @Test
    public void byteToIntAndViceVersa_roundTripConversion_successful() {
        // Test a range of values including edge cases
        int[] testValues = {
            0,
            1,
            -1,
            Integer.MAX_VALUE,
            Integer.MIN_VALUE,
            0x12345678,
            0x87654321
        };

        for (int original : testValues) {
            byte[] bytes = KeyUtility.intToByteArray(original);
            int result = KeyUtility.byteArrayToInt(bytes);

            assertThat("Failed round-trip for value: " + original, result, is(equalTo(original)));
        }
    }

    @Test
    public void byteToIntAndViceVersa_offsetRoundTripConversion_successful() {
        int original = 0xCAFEBABE;
        byte[] buffer = new byte[8];
        int offset = 2;

        // Write to buffer with offset
        KeyUtility.intToByteArray(original, buffer, offset);

        // Read from same offset
        int result = KeyUtility.byteArrayToInt(buffer, offset);

        assertThat(result, is(equalTo(original)));
    }

    @Test
    public void byteArrayToIntArray_roundTrip_successful() {
        int original = 0x0A0B0C0D;
        byte[] bytes = KeyUtility.intToByteArray(original);
        int[] result = new int[1];

        KeyUtility.byteArrayToIntArray(bytes, 0, result, 0);

        assertThat(result[0], is(equalTo(original)));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="createSecrets">
    @Test
    public void createSecrets_returnStartSecretOnlyTrue_returnsOneSecret() throws NoMoreSecretsAvailableException {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        int privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
        Random random = new Random(123);
        int overallWorkSize = 10;
        boolean returnStartSecretOnly = true;

        // act
        BigInteger[] secrets = keyUtility.createSecrets(overallWorkSize, returnStartSecretOnly, privateKeyMaxNumBits, random);

        // assert
        assertThat(secrets.length, is(1));
        assertThat(secrets[0], is(notNullValue()));
    }

    @Test
    public void createSecrets_returnStartSecretOnlyFalse_returnsAllSecrets() throws NoMoreSecretsAvailableException {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        int privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
        Random random = new Random(123);
        int overallWorkSize = 5;
        boolean returnStartSecretOnly = false;

        // act
        BigInteger[] secrets = keyUtility.createSecrets(overallWorkSize, returnStartSecretOnly, privateKeyMaxNumBits, random);

        // assert
        assertThat(secrets.length, is(overallWorkSize));
        for (BigInteger secret : secrets) {
            assertThat(secret, is(notNullValue()));
        }
    }

    @Test(expected = NoMoreSecretsAvailableException.class)
    public void createSecrets_zeroLength_throwsException() throws NoMoreSecretsAvailableException {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        int privateKeyMaxNumBits = PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS;
        Random random = new Random() {
            @Override
            public void nextBytes(byte[] bytes) {
                throw new NoMoreSecretsAvailableException();
            }
        };
        int overallWorkSize = 1;
        boolean returnStartSecretOnly = false;

        // act
        keyUtility.createSecrets(overallWorkSize, returnStartSecretOnly, privateKeyMaxNumBits, random);
    }
    // </editor-fold>
}
