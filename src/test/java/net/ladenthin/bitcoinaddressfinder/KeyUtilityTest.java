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
import net.ladenthin.bitcoinaddressfinder.keyproducer.NoMoreSecretsAvailableException;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticKey;
import org.bitcoinj.base.LegacyAddress;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.MnemonicException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(DataProviderRunner.class)
public class KeyUtilityTest {

    private final StaticKey staticKey = new StaticKey();
    private final Network network = new NetworkParameterFactory().getNetwork();

    // <editor-fold defaultstate="collapsed" desc="createECKey">
    @Test
    public void createECKey_uncompressedKey_returnsCorrectPublicKeyHash() throws IOException {
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
    public void createECKey_compressedKey_returnsCorrectPublicKeyHash() throws IOException {
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
    public void getHash160ByteBufferFromBase58String_uncompressedPublicKeyAddress_returnsExpectedByteBuffer() throws IOException {
        // act
        ByteBuffer byteBufferPublicKeyUncompressed = new KeyUtility(network, new ByteBufferUtility(false)).getHash160ByteBufferFromBase58String(staticKey.publicKeyUncompressed);

        // assert
        assertThat(byteBufferPublicKeyUncompressed, is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
    }

    @Test
    public void getHash160ByteBufferFromBase58String_compressedPublicKeyAddress_returnsExpectedByteBuffer() throws IOException {
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
    public void getHexFromByteBuffer_uncompressedPublicKeyHash_returnsExpectedHex() throws IOException {
        // act
        String hexPublicKeyUncompressed = new ByteBufferUtility(false).getHexFromByteBuffer(staticKey.byteBufferPublicKeyUncompressed);

        // assert
        assertThat(hexPublicKeyUncompressed, is(equalTo(staticKey.publicKeyUncompressedHash160Hex)));
    }

    @Test
    public void getHexFromByteBuffer_compressedPublicKeyHash_returnsExpectedHex() throws IOException {
        // act
        String hexPublicKeyCompressed = new ByteBufferUtility(false).getHexFromByteBuffer(staticKey.byteBufferPublicKeyCompressed);

        // assert
        assertThat(hexPublicKeyCompressed, is(equalTo(staticKey.publicKeyCompressedHash160Hex)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getByteBufferFromHex">
    @Test
    public void getByteBufferFromHex_uncompressedPublicKeyHashHex_returnsExpectedByteBuffer() throws IOException {
        // act
        ByteBuffer byteBufferPublicKeyUncompressed = new ByteBufferUtility(false).getByteBufferFromHex(staticKey.publicKeyUncompressedHash160Hex);

        // assert
        assertThat(byteBufferPublicKeyUncompressed, is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
    }

    @Test
    public void getByteBufferFromHex_compressedPublicKeyHashHex_returnsExpectedByteBuffer() throws IOException {
        // act
        ByteBuffer byteBufferPublicKeyCompressed = new ByteBufferUtility(false).getByteBufferFromHex(staticKey.publicKeyCompressedHash160Hex);

        // assert
        assertThat(byteBufferPublicKeyCompressed, is(equalTo(staticKey.byteBufferPublicKeyCompressed)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="createSecret">
    @Test
    public void createSecret_maxBitLength_returnsNonEmptySecret() throws IOException {
        // act
        BigInteger secret = new KeyUtility(network, new ByteBufferUtility(false)).createSecret(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS, new Random(42));

        // assert
        assertThat(secret.toString(), is(not(equalTo(""))));
    }

    @Test
    public void createSecret_zeroBits_returnsZero() {
        // act
        BigInteger secret = new KeyUtility(network, new ByteBufferUtility(false)).createSecret(0, new Random(123));

        // assert
        assertThat(secret, is(equalTo(BigInteger.ZERO)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="createKeyDetails">
    @Test
    public void createKeyDetails_uncompressedKey_returnsExpectedDetails() throws IOException, MnemonicException.MnemonicLengthException {
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
    public void createKeyDetails_compressedKey_returnsExpectedDetails() throws IOException, MnemonicException.MnemonicLengthException {
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


    // <editor-fold defaultstate="collapsed" desc="ECKey.fromPrivate: boundaries">
    @Test
    public void ecKey_fromPrivate_randomValidInRange_succeeds() {
        // arrange
        BigInteger randomValidKey = PublicKeyBytes.MIN_VALID_PRIVATE_KEY.add(BigInteger.valueOf(123456));

        // act
        ECKey ecKey = ECKey.fromPrivate(randomValidKey, false);

        // assert
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


    // <editor-fold defaultstate="collapsed" desc="intToByteArray">
    @Test
    public void intToByteArray_zero_returnsFourZeroBytes() {
        // arrange
        byte[] expected = new byte[4];

        // act
        byte[] result = KeyUtility.intToByteArray(0);

        // assert
        assertThat(result, is(expected));
    }

    @Test
    public void intToByteArray_knownValue_returnsExpectedBigEndianBytes() {
        // arrange
        byte[] expected = {0x12, 0x34, 0x56, 0x78};

        // act
        byte[] result = KeyUtility.intToByteArray(0x12345678);

        // assert
        assertThat(result, is(expected));
    }

    @Test
    public void intToByteArray_minusOne_returnsAllFfBytes() {
        // arrange
        byte[] expected = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        // act
        byte[] result = KeyUtility.intToByteArray(-1);

        // assert
        assertThat(result, is(expected));
    }

    @Test
    public void intToByteArray_intMaxValue_returnsExpectedBytes() {
        // arrange
        byte[] expected = {0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        // act
        byte[] result = KeyUtility.intToByteArray(Integer.MAX_VALUE);

        // assert
        assertThat(result, is(expected));
    }

    @Test
    public void intToByteArray_intMinValue_returnsExpectedBytes() {
        // arrange
        byte[] expected = {(byte) 0x80, 0x00, 0x00, 0x00};

        // act
        byte[] result = KeyUtility.intToByteArray(Integer.MIN_VALUE);

        // assert
        assertThat(result, is(expected));
    }

    @Test
    public void intToByteArray_withOffset_writesExpectedBytesAtCorrectPosition() {
        // arrange
        final int value = 0xCAFEBABE;
        final int bufferLength = 8;
        final int offset = 2;
        final int intByteLength = 4;
        byte[] buffer = new byte[bufferLength];
        byte[] expectedWritten = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

        // act
        KeyUtility.intToByteArray(value, buffer, offset);

        // assert
        assertThat(Arrays.copyOfRange(buffer, offset, offset + intByteLength), is(expectedWritten));
        assertThat(Arrays.copyOfRange(buffer, 0, offset), is(new byte[offset]));
        assertThat(Arrays.copyOfRange(buffer, offset + intByteLength, bufferLength), is(new byte[bufferLength - offset - intByteLength]));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="byteArrayToInt">
    @Test
    public void byteArrayToInt_allZeroBytes_returnsZero() {
        // arrange
        byte[] input = new byte[4];

        // act
        int result = KeyUtility.byteArrayToInt(input);

        // assert
        assertThat(result, is(equalTo(0)));
    }

    @Test
    public void byteArrayToInt_knownBytes_returnsExpectedInt() {
        // arrange
        byte[] input = {0x12, 0x34, 0x56, 0x78};

        // act
        int result = KeyUtility.byteArrayToInt(input);

        // assert
        assertThat(result, is(equalTo(0x12345678)));
    }

    @Test
    public void byteArrayToInt_allFfBytes_returnsMinusOne() {
        // arrange
        byte[] input = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        // act
        int result = KeyUtility.byteArrayToInt(input);

        // assert
        assertThat(result, is(equalTo(-1)));
    }

    @Test
    public void byteArrayToInt_maxIntBytes_returnsMaxInt() {
        // arrange
        byte[] input = {0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        // act
        int result = KeyUtility.byteArrayToInt(input);

        // assert
        assertThat(result, is(equalTo(Integer.MAX_VALUE)));
    }

    @Test
    public void byteArrayToInt_withOffset_readsExpectedInt() {
        // arrange
        final int original = 0xCAFEBABE;
        final int offset = 2;
        byte[] buffer = new byte[8];
        KeyUtility.intToByteArray(original, buffer, offset);

        // act
        int result = KeyUtility.byteArrayToInt(buffer, offset);

        // assert
        assertThat(result, is(equalTo(original)));
    }

    @Test
    public void intToByteArray_byteArrayToInt_roundTrip_zero_preservesValue() {
        // arrange
        final int original = 0;

        // act
        byte[] bytes = KeyUtility.intToByteArray(original);
        int result = KeyUtility.byteArrayToInt(bytes);

        // assert
        assertThat(result, is(equalTo(original)));
    }

    @Test
    public void intToByteArray_byteArrayToInt_roundTrip_intMaxValue_preservesValue() {
        // arrange
        final int original = Integer.MAX_VALUE;

        // act
        byte[] bytes = KeyUtility.intToByteArray(original);
        int result = KeyUtility.byteArrayToInt(bytes);

        // assert
        assertThat(result, is(equalTo(original)));
    }

    @Test
    public void intToByteArray_byteArrayToInt_roundTrip_intMinValue_preservesValue() {
        // arrange
        final int original = Integer.MIN_VALUE;

        // act
        byte[] bytes = KeyUtility.intToByteArray(original);
        int result = KeyUtility.byteArrayToInt(bytes);

        // assert
        assertThat(result, is(equalTo(original)));
    }

    @Test
    public void intToByteArray_byteArrayToInt_roundTrip_minusOne_preservesValue() {
        // arrange
        final int original = -1;

        // act
        byte[] bytes = KeyUtility.intToByteArray(original);
        int result = KeyUtility.byteArrayToInt(bytes);

        // assert
        assertThat(result, is(equalTo(original)));
    }

    @Test
    public void intToByteArray_byteArrayToInt_roundTrip_knownValue_preservesValue() {
        // arrange
        final int original = 0x12345678;

        // act
        byte[] bytes = KeyUtility.intToByteArray(original);
        int result = KeyUtility.byteArrayToInt(bytes);

        // assert
        assertThat(result, is(equalTo(original)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="byteArrayToIntArray">
    @Test
    public void byteArrayToIntArray_knownBytes_populatesIntAtOffset() {
        // arrange
        final int original = 0x0A0B0C0D;
        byte[] bytes = KeyUtility.intToByteArray(original);
        int[] result = new int[1];

        // act
        KeyUtility.byteArrayToIntArray(bytes, 0, result, 0);

        // assert
        assertThat(result[0], is(equalTo(original)));
    }

    @Test
    public void byteArrayToIntArray_withNonZeroIntArrayOffset_populatesCorrectSlot() {
        // arrange
        final int original = 0x0A0B0C0D;
        byte[] bytes = KeyUtility.intToByteArray(original);
        int[] result = new int[3];
        final int intOffset = 2;

        // act
        KeyUtility.byteArrayToIntArray(bytes, 0, result, intOffset);

        // assert
        assertThat(result[intOffset], is(equalTo(original)));
        assertThat(result[0], is(equalTo(0)));
        assertThat(result[1], is(equalTo(0)));
    }

    @Test
    public void byteArrayToIntArray_withNonZeroByteArrayOffset_readsFromCorrectPosition() {
        // arrange
        final int original = 0x0A0B0C0D;
        final int byteOffset = 4;
        byte[] bytes = new byte[8];
        KeyUtility.intToByteArray(original, bytes, byteOffset);
        int[] result = new int[1];

        // act
        KeyUtility.byteArrayToIntArray(bytes, byteOffset, result, 0);

        // assert
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
        SecretSupplier randomSupplier = new RandomSecretSupplier(random);
        int overallWorkSize = 10;
        boolean returnStartSecretOnly = true;

        // act
        BigInteger[] secrets = keyUtility.createSecrets(overallWorkSize, returnStartSecretOnly, privateKeyMaxNumBits, randomSupplier);

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
        SecretSupplier randomSupplier = new RandomSecretSupplier(random);
        int overallWorkSize = 5;
        boolean returnStartSecretOnly = false;

        // act
        BigInteger[] secrets = keyUtility.createSecrets(overallWorkSize, returnStartSecretOnly, privateKeyMaxNumBits, randomSupplier);

        // assert
        assertThat(secrets.length, is(overallWorkSize));
        assertThat(Arrays.asList(secrets), everyItem(is(notNullValue())));
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
        SecretSupplier randomSupplier = new RandomSecretSupplier(random);
        int overallWorkSize = 1;
        boolean returnStartSecretOnly = false;

        // act
        keyUtility.createSecrets(overallWorkSize, returnStartSecretOnly, privateKeyMaxNumBits, randomSupplier);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="bigIntegerToFixedLengthHex">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_LARGE_SECRETS_AS_HEX, location = CommonDataProvider.class)
    public void bigIntegerToFixedLengthHex_knownBigInteger_correctHex(String largeSecretsAsHex) {
        // arrange
        BigInteger input = new BigInteger(largeSecretsAsHex, 16);
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));

        // act
        String result = keyUtility.bigIntegerToFixedLengthHex(input);

        // assert
        assertThat(result, is(equalTo(largeSecretsAsHex)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="bigIntegerFromUnsignedByteArray">
    @Test
    public void bigIntegerFromUnsignedByteArray_exact32Bytes_constructsCorrectly() {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        byte[] buffer = new byte[32];
        buffer[30] = 0x12;
        buffer[31] = 0x34;
        BigInteger expected = new BigInteger("1234", 16);

        // act
        BigInteger result = keyUtility.bigIntegerFromUnsignedByteArray(buffer);

        // assert
        assertThat(result, is(equalTo(expected)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void bigIntegerFromUnsignedByteArray_wrongLength_throwsException() {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        byte[] invalid = new byte[31];

        // act
        keyUtility.bigIntegerFromUnsignedByteArray(invalid);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="toBase58">
    @Test
    public void toBase58_knownHash160_returnsExpectedBase58Address() {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        StaticKey key = new StaticKey();
        byte[] hash160 = new ByteBufferUtility(false).byteBufferToBytes(key.byteBufferPublicKeyUncompressed);

        // act
        String result = keyUtility.toBase58(hash160);

        // assert
        assertThat(result, is(equalTo(key.publicKeyUncompressed)));
    }

    @Test
    public void toBase58_compressedHash160_returnsExpectedBase58Address() {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        StaticKey key = new StaticKey();
        byte[] hash160 = new ByteBufferUtility(false).byteBufferToBytes(key.byteBufferPublicKeyCompressed);

        // act
        String result = keyUtility.toBase58(hash160);

        // assert
        assertThat(result, is(equalTo(key.publicKeyCompressed)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="addressToByteBuffer">
    @Test
    public void addressToByteBuffer_validAddress_returnsCorrectByteBuffer() {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        StaticKey key = new StaticKey();
        LegacyAddress address = LegacyAddress.fromBase58(key.publicKeyUncompressed, network);

        // act
        ByteBuffer result = keyUtility.addressToByteBuffer(address);

        // assert
        assertThat(result, is(equalTo(key.byteBufferPublicKeyUncompressed)));
    }

    @Test
    public void addressToByteBuffer_roundTripWithByteBufferToAddress_returnsOriginalAddress() {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        StaticKey key = new StaticKey();
        LegacyAddress originalAddress = LegacyAddress.fromBase58(key.publicKeyCompressed, network);

        // act
        ByteBuffer buffer = keyUtility.addressToByteBuffer(originalAddress);
        LegacyAddress roundTripped = keyUtility.byteBufferToAddress(buffer);

        // assert
        assertThat(roundTripped, is(equalTo(originalAddress)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="byteBufferToAddress">
    @Test
    public void byteBufferToAddress_validBuffer_returnsCorrectAddress() {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        StaticKey key = new StaticKey();

        // act
        LegacyAddress result = keyUtility.byteBufferToAddress(key.byteBufferPublicKeyCompressed);

        // assert
        assertThat(result.toBase58(), is(equalTo(key.publicKeyCompressed)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="createMnemonics">
    @Test
    public void createMnemonics_validPrivateKeyBytes_returnsNonEmptyMnemonicString() {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        StaticKey key = new StaticKey();

        // act
        String result = keyUtility.createMnemonics(key.privateKeyBytes);

        // assert
        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString("Mnemonic:"));
    }

    @Test
    public void createMnemonics_validPrivateKeyBytes_containsAllWordLists() {
        // arrange
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
        StaticKey key = new StaticKey();

        // act
        String result = keyUtility.createMnemonics(key.privateKeyBytes);

        // assert
        for (BIP39Wordlist wordList : BIP39Wordlist.values()) {
            assertThat(result, containsString(wordList.name()));
        }
    }
    // </editor-fold>
}
