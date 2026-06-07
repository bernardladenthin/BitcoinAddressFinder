// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import net.ladenthin.bitcoinaddressfinder.EqualHashCodeToStringTestHelper;
import net.ladenthin.bitcoinaddressfinder.ToStringTest;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses42;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import nl.altindag.log.LogCaptor;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.base.LegacyAddress;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.internal.CryptoUtils;
import org.junit.jupiter.api.Test;

public class PublicKeyBytesTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
    protected final KeyUtility keyUtility = new KeyUtility(network, byteBufferUtility);

    @Test
    public void publicKeyBytes_fromPublicKey_matchesExpectedHashes() {
        // arrange
        ECKey keyUncompressed = new TestAddresses42(1, false).getECKeys().get(0);
        ECKey keyCompressed = new TestAddresses42(1, true).getECKeys().get(0);

        // act
        PublicKeyBytes publicKeyBytesUncompressed =
                new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey());
        PublicKeyBytes publicKeyBytesCompressed = new PublicKeyBytes(
                keyUncompressed.getPrivKey(), keyUncompressed.getPubKey(), keyCompressed.getPubKey());

        // assert
        assertThat(publicKeyBytesUncompressed.getUncompressed(), is(equalTo(keyUncompressed.getPubKey())));
        assertThat(publicKeyBytesUncompressed.getUncompressedKeyHash(), is(equalTo(keyUncompressed.getPubKeyHash())));
        assertThat(
                CryptoUtils.sha256hash160(publicKeyBytesCompressed.getUncompressed()),
                is(equalTo(keyUncompressed.getPubKeyHash())));
        assertThat(publicKeyBytesCompressed.getUncompressedKeyHash(), is(equalTo(keyUncompressed.getPubKeyHash())));
        assertThat(
                publicKeyBytesUncompressed.getUncompressedKeyHashAsBase58(keyUtility),
                is(equalTo(LegacyAddress.fromPubKeyHash(network, keyUncompressed.getPubKeyHash())
                        .toBase58())));
        assertThat(
                publicKeyBytesCompressed.getUncompressedKeyHashAsBase58(keyUtility),
                is(equalTo(LegacyAddress.fromPubKeyHash(network, keyUncompressed.getPubKeyHash())
                        .toBase58())));

        assertThat(publicKeyBytesUncompressed.getCompressed(), is(equalTo(keyCompressed.getPubKey())));
        assertThat(publicKeyBytesUncompressed.getCompressedKeyHash(), is(equalTo(keyCompressed.getPubKeyHash())));
        assertThat(
                CryptoUtils.sha256hash160(publicKeyBytesCompressed.getCompressed()),
                is(equalTo(keyCompressed.getPubKeyHash())));
        assertThat(publicKeyBytesCompressed.getCompressedKeyHash(), is(equalTo(keyCompressed.getPubKeyHash())));
        assertThat(
                publicKeyBytesUncompressed.getCompressedKeyHashAsBase58(keyUtility),
                is(equalTo(LegacyAddress.fromPubKeyHash(network, keyCompressed.getPubKeyHash())
                        .toBase58())));
        assertThat(
                publicKeyBytesCompressed.getCompressedKeyHashAsBase58(keyUtility),
                is(equalTo(LegacyAddress.fromPubKeyHash(network, keyCompressed.getPubKeyHash())
                        .toBase58())));
    }

    @Test
    public void publicKeyBytes_toStringEqualsAndHashCode_consistent() {
        // arrange
        ECKey keyUncompressed = new TestAddresses42(1, false).getECKeys().get(0);
        ECKey keyCompressed = new TestAddresses42(1, true).getECKeys().get(0);

        // act
        PublicKeyBytes publicKeyBytesUncompressed =
                new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey());
        PublicKeyBytes publicKeyBytesUncompressed2 =
                new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey());
        PublicKeyBytes publicKeyBytesCompressed = new PublicKeyBytes(
                keyUncompressed.getPrivKey(), keyUncompressed.getPubKey(), keyCompressed.getPubKey());
        PublicKeyBytes publicKeyBytesCompressed2 = new PublicKeyBytes(
                keyUncompressed.getPrivKey(), keyUncompressed.getPubKey(), keyCompressed.getPubKey());

        // assert
        EqualHashCodeToStringTestHelper equalHashCodeToStringTestHelper = new EqualHashCodeToStringTestHelper(
                publicKeyBytesUncompressed,
                publicKeyBytesUncompressed2,
                publicKeyBytesCompressed,
                publicKeyBytesCompressed2);
        equalHashCodeToStringTestHelper.assertEqualsHashCodeToStringAIsEqualToB();

        // toString
        assertThat(publicKeyBytesUncompressed.toString(), is(equalTo(publicKeyBytesCompressed.toString())));
        assertThat(
                publicKeyBytesUncompressed.toString(),
                is(
                        equalTo(
                                // Lombok @ToString format: Class(field=value); paren-delimited,
                                // secretKey-only per @ToString(onlyExplicitlyIncluded = true).
                                "PublicKeyBytes(secretKey=24250429618215260598957696001935175135959229619080974590971174872813112994997)")));
    }

    @Test
    public void maxPrivateKeyAsHexString_isEqualToConstant() {
        // arrange
        String maxPrivateKeyAsHexString =
                Hex.encodeHexString(ByteBufferUtility.bigIntegerToBytes(Secp256k1Constants.MAX_PRIVATE_KEY));
        // act

        // assert
        assertThat(
                maxPrivateKeyAsHexString.toLowerCase(),
                is(equalTo("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141".toLowerCase())));
    }

    // <editor-fold defaultstate="collapsed" desc="Tests for runtimePublicKeyCalculationCheck">
    @Test
    public void runtimePublicKeyCalculationCheck_validKey_returnsTrue() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] uncompressed = ecKey.getPubKey();
        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secretKey, uncompressed); // compressed is derived

        try (LogCaptor logCaptor = LogCaptor.forClass(PublicKeyBytes.class)) {
            // act
            boolean result = publicKeyBytes.runtimePublicKeyCalculationCheck();

            // assert
            assertThat(result, is(true));
            assertThat(logCaptor.getErrorLogs(), is(empty()));
        }
    }

    @Test
    public void runtimePublicKeyCalculationCheck_invalidCompressedHash_returnsFalse() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] uncompressed = ecKey.getPubKey();
        byte[] wrongCompressedHash =
                new byte[OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES]; // all-zero hash (invalid)

        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secretKey, uncompressed, wrongCompressedHash);

        try (LogCaptor logCaptor = LogCaptor.forClass(PublicKeyBytes.class)) {
            // act
            boolean result = publicKeyBytes.runtimePublicKeyCalculationCheck();

            // assert
            assertThat(result, is(false));
            assertThat(logCaptor.getErrorLogs(), hasItem(containsString("fromPrivateCompressed.getPubKeyHash()")));
        }
    }

    @Test
    public void runtimePublicKeyCalculationCheck_invalidUncompressedHash_returnsFalse() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] uncompressed = ecKey.getPubKey();
        byte[] compressed = ECKey.fromPrivate(secretKey, true).getPubKey();
        byte[] wrongUncompressedHash =
                new byte[OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES]; // all-zero hash (invalid)

        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secretKey, wrongUncompressedHash, compressed);

        try (LogCaptor logCaptor = LogCaptor.forClass(PublicKeyBytes.class)) {
            // act
            boolean result = publicKeyBytes.runtimePublicKeyCalculationCheck();

            // assert
            assertThat(result, is(false));
            assertThat(logCaptor.getErrorLogs(), hasItem(containsString("fromPrivateUncompressed.getPubKeyHash()")));
        }
    }

    @Test
    public void runtimePublicKeyCalculationCheck_invalidCompressedAndUncompressed_returnsFalse() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        byte[] uncompressedWrong = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES]; // all-zero hash (invalid)
        byte[] compressedWrong = new byte[PublicKeyBytes.PUBLIC_KEY_COMPRESSED_BYTES]; // all-zero hash (invalid)

        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secretKey, uncompressedWrong, compressedWrong);

        try (LogCaptor logCaptor = LogCaptor.forClass(PublicKeyBytes.class)) {
            // act
            boolean result = publicKeyBytes.runtimePublicKeyCalculationCheck();

            // assert
            assertThat(result, is(false));
            assertThat(logCaptor.getErrorLogs(), hasItem(containsString("fromPrivateUncompressed.getPubKeyHash()")));
            assertThat(logCaptor.getErrorLogs(), hasItem(containsString("fromPrivateCompressed.getPubKeyHash()")));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Tests for assembleUncompressedPublicKey">
    @Test
    public void assembleUncompressedPublicKey_validXY_correctlyAssembles() {
        // arrange
        byte[] x = new byte[OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES];
        byte[] y = new byte[OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES];
        for (int i = 0; i < OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES; i++) {
            x[i] = (byte) i;
            y[i] = (byte) (i + OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES);
        }

        // act
        byte[] result = PublicKeyBytes.assembleUncompressedPublicKey(x, y);

        // assert
        assertThat(result[0], is((byte) OpenClKernelConstants.SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT));
        for (int i = 0; i < OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES; i++) {
            assertThat(
                    "X coordinate mismatch at index " + i,
                    result[i + OpenClKernelConstants.SEC_PREFIX_NUM_BYTES],
                    is(x[i]));
            assertThat(
                    "Y coordinate mismatch at index " + i,
                    result[
                            i
                                    + OpenClKernelConstants.SEC_PREFIX_NUM_BYTES
                                    + OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES],
                    is(y[i]));
        }
    }

    @Test
    public void assembleUncompressedPublicKey_allZeros_createsValidKey() {
        // arrange
        byte[] x = new byte[OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES];
        byte[] y = new byte[OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES];

        // act
        byte[] result = PublicKeyBytes.assembleUncompressedPublicKey(x, y);

        // assert
        assertThat(result[0], is((byte) OpenClKernelConstants.SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT));
        for (int i = OpenClKernelConstants.SEC_PREFIX_NUM_BYTES; i < result.length; i++) {
            assertThat("Expected zero at index " + i, result[i], is((byte) 0x00));
        }
    }

    @Test
    public void assembleUncompressedPublicKey_validXAndY_assemblesCorrectly() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] pubKey = ecKey.getPubKey(); // full uncompressed pubkey (parity + X + Y)

        // extract X and Y from real ECKey
        byte[] x = Arrays.copyOfRange(
                pubKey,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES + OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES);
        byte[] y = Arrays.copyOfRange(
                pubKey,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES + OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES + OpenClKernelConstants.TWO_COORDINATES_NUM_BYTES);

        // act
        byte[] assembledUncompressed = PublicKeyBytes.assembleUncompressedPublicKey(x, y);

        // assert
        assertThat(assembledUncompressed, is(equalTo(pubKey)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="toString">
    @ToStringTest
    @Test
    public void toString_whenCalled_containsClassNameAndPrivateKey() throws IOException {
        // arrange
        ECKey keyUncompressed = new TestAddresses42(1, false).getECKeys().get(0);

        // act
        PublicKeyBytes publicKeyBytesUncompressed =
                new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey());
        String toStringOutput = publicKeyBytesUncompressed.toString();

        assertThat(toStringOutput, not(emptyOrNullString()));
        // Lombok @ToString format: Class(field=value); paren-delimited.
        assertThat(toStringOutput, matchesPattern("PublicKeyBytes\\(secretKey=\\d+\\)"));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="fromPrivate">
    @Test
    public void fromPrivate_validSecretKey_returnsPublicKeyBytesWithCorrectKey() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");

        // act
        PublicKeyBytes result = PublicKeyBytes.fromPrivate(secretKey);

        // assert
        assertThat(result.getSecretKey(), is(equalTo(secretKey)));
    }

    @Test
    public void fromPrivate_validSecretKey_returnsMatchingUncompressedPubKey() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);

        // act
        PublicKeyBytes result = PublicKeyBytes.fromPrivate(secretKey);

        // assert
        assertThat(result.getUncompressed(), is(equalTo(ecKey.getPubKey())));
    }

    @Test
    public void fromPrivate_validSecretKey_returnsMatchingCompressedPubKey() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKeyCompressed = ECKey.fromPrivate(secretKey, true);

        // act
        PublicKeyBytes result = PublicKeyBytes.fromPrivate(secretKey);

        // assert
        assertThat(result.getCompressed(), is(equalTo(ecKeyCompressed.getPubKey())));
    }

    @Test
    public void fromPrivate_minValidPrivateKey_noExceptionThrown() {
        // arrange
        BigInteger secretKey = Secp256k1Constants.MIN_VALID_PRIVATE_KEY;

        // act
        PublicKeyBytes result = PublicKeyBytes.fromPrivate(secretKey);

        // assert
        assertThat(result.getSecretKey(), is(equalTo(secretKey)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getSecretKey">
    @Test
    public void getSecretKey_constructedWithKnownKey_returnsCorrectKey() {
        // arrange
        BigInteger secretKey = new BigInteger("42");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        PublicKeyBytes sut = new PublicKeyBytes(secretKey, ecKey.getPubKey());

        // act
        BigInteger result = sut.getSecretKey();

        // assert
        assertThat(result, is(equalTo(secretKey)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getCompressed">
    @Test
    public void getCompressed_constructedFromUncompressed_returnsValidCompressedKey() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKeyUncompressed = ECKey.fromPrivate(secretKey, false);
        ECKey ecKeyCompressed = ECKey.fromPrivate(secretKey, true);
        PublicKeyBytes sut = new PublicKeyBytes(secretKey, ecKeyUncompressed.getPubKey());

        // act
        byte[] result = sut.getCompressed();

        // assert
        assertThat(result, is(equalTo(ecKeyCompressed.getPubKey())));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getUncompressed">
    @Test
    public void getUncompressed_constructedWithUncompressed_returnsSameArray() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] uncompressed = ecKey.getPubKey();
        PublicKeyBytes sut = new PublicKeyBytes(secretKey, uncompressed);

        // act
        byte[] result = sut.getUncompressed();

        // assert
        assertThat(result, is(equalTo(uncompressed)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isOutsidePrivateKeyRange">
    @Test
    public void isOutsidePrivateKeyRange_validKey_returnsFalse() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        PublicKeyBytes sut = new PublicKeyBytes(secretKey, ecKey.getPubKey());

        // act
        boolean result = sut.isOutsidePrivateKeyRange();

        // assert
        assertThat(result, is(false));
    }

    @Test
    public void isOutsidePrivateKeyRange_keyAboveMax_returnsTrue() {
        // arrange
        BigInteger secretKey = Secp256k1Constants.MAX_PRIVATE_KEY.add(BigInteger.ONE);
        byte[] dummyUncompressed = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES];
        PublicKeyBytes sut = new PublicKeyBytes(secretKey, dummyUncompressed);

        // act
        boolean result = sut.isOutsidePrivateKeyRange();

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void isOutsidePrivateKeyRange_keyBelowMin_returnsTrue() {
        // arrange
        BigInteger secretKey = BigInteger.ZERO;
        byte[] dummyUncompressed = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES];
        PublicKeyBytes sut = new PublicKeyBytes(secretKey, dummyUncompressed);

        // act
        boolean result = sut.isOutsidePrivateKeyRange();

        // assert
        assertThat(result, is(true));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Hash160">
    @Test
    public void hash160Fast_knownInput_matchesCryptoUtilsResult() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] pubKey = ecKey.getPubKey();

        // act
        byte[] fastResult = new Hash160().hash(pubKey);

        // assert
        byte[] expected = CryptoUtils.sha256hash160(pubKey);
        assertThat(fastResult, is(equalTo(expected)));
    }

    @Test
    public void hash160Fast_compressedKey_matchesCryptoUtilsResult() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, true);
        byte[] pubKey = ecKey.getPubKey();

        // act
        byte[] fastResult = new Hash160().hash(pubKey);

        // assert
        byte[] expected = CryptoUtils.sha256hash160(pubKey);
        assertThat(fastResult, is(equalTo(expected)));
    }

    @Test
    public void hash160Fast_resultLength_isRipemd160HashLength() {
        // arrange
        byte[] input = new byte[] {0x01, 0x02, 0x03};

        // act
        byte[] result = new Hash160().hash(input);

        // assert
        assertThat(result.length, is(equalTo(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="createCompressedBytes">
    @Test
    public void createCompressedBytes_evenYCoordinate_prefixIs02() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] uncompressed = ecKey.getPubKey();

        // pre-assert
        boolean lastByteIsEven = uncompressed[PublicKeyBytes.LAST_Y_COORDINATE_BYTE_INDEX] % 2 == 0;

        // act
        byte[] compressed = PublicKeyBytes.createCompressedBytes(uncompressed);

        // assert
        if (lastByteIsEven) {
            assertThat(compressed[0], is((byte) OpenClKernelConstants.SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y));
        } else {
            assertThat(compressed[0], is((byte) OpenClKernelConstants.SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y));
        }
    }

    @Test
    public void createCompressedBytes_knownKey_matchesECKeyCompressed() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKeyUncompressed = ECKey.fromPrivate(secretKey, false);
        ECKey ecKeyCompressed = ECKey.fromPrivate(secretKey, true);

        // act
        byte[] compressed = PublicKeyBytes.createCompressedBytes(ecKeyUncompressed.getPubKey());

        // assert
        assertThat(compressed, is(equalTo(ecKeyCompressed.getPubKey())));
    }

    @Test
    public void createCompressedBytes_resultLength_isCompressedKeyLength() {
        // arrange
        BigInteger secretKey = new BigInteger("42");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);

        // act
        byte[] compressed = PublicKeyBytes.createCompressedBytes(ecKey.getPubKey());

        // assert
        assertThat(compressed.length, is(equalTo(PublicKeyBytes.PUBLIC_KEY_COMPRESSED_BYTES)));
    }

    @Test
    public void createCompressedBytes_xCoordinate_matchesUncompressedXCoordinate() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] uncompressed = ecKey.getPubKey();

        // act
        byte[] compressed = PublicKeyBytes.createCompressedBytes(uncompressed);

        // assert
        byte[] xFromUncompressed = Arrays.copyOfRange(
                uncompressed,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES + OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES);
        byte[] xFromCompressed = Arrays.copyOfRange(
                compressed,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES + OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES);
        assertThat(xFromCompressed, is(equalTo(xFromUncompressed)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="four-arg constructor">
    @Test
    public void constructor_fourArgs_setsAllFields() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKeyUncompressed = ECKey.fromPrivate(secretKey, false);
        ECKey ecKeyCompressed = ECKey.fromPrivate(secretKey, true);
        byte[] uncompressedKeyHash = CryptoUtils.sha256hash160(ecKeyUncompressed.getPubKey());
        byte[] compressedKeyHash = CryptoUtils.sha256hash160(ecKeyCompressed.getPubKey());

        // act
        PublicKeyBytes sut =
                new PublicKeyBytes(secretKey, ecKeyUncompressed.getPubKey(), uncompressedKeyHash, compressedKeyHash);

        // assert
        assertThat(sut.getSecretKey(), is(equalTo(secretKey)));
        assertThat(sut.getUncompressedKeyHash(), is(equalTo(uncompressedKeyHash)));
        assertThat(sut.getCompressedKeyHash(), is(equalTo(compressedKeyHash)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isAllCoordinateBytesZero">
    @Test
    public void isAllCoordinateBytesZero_validKey_returnsFalse() {
        // arrange
        byte[] validUncompressedKey = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES];
        validUncompressedKey[0] = OpenClKernelConstants.SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT;
        validUncompressedKey[1] = 0x01; // at least one non-zero coordinate byte

        // act
        boolean result = PublicKeyBytes.isAllCoordinateBytesZero(validUncompressedKey);

        // assert
        assertThat(result, is(false));
    }

    @Test
    public void isAllCoordinateBytesZero_allCoordinateBytesZero_returnsTrue() {
        // arrange
        byte[] invalidUncompressedKey = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES];
        invalidUncompressedKey[0] = OpenClKernelConstants.SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT; // prefix byte set

        // act
        boolean result = PublicKeyBytes.isAllCoordinateBytesZero(invalidUncompressedKey);

        // assert
        assertThat(result, is(true));
    }

    @Test
    public void isAllCoordinateBytesZero_validKeyOnlyLastByteNonZero_returnsFalse() {
        // arrange
        byte[] validUncompressedKey = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES];
        validUncompressedKey[0] = OpenClKernelConstants.SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT;
        validUncompressedKey[validUncompressedKey.length - 1] = 0x01; // last coordinate byte non-zero

        // act
        boolean result = PublicKeyBytes.isAllCoordinateBytesZero(validUncompressedKey);

        // assert
        assertThat(result, is(false));
    }
    // </editor-fold>
}
