// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.io;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.model.AddressToCoin;
import net.ladenthin.bitcoinaddressfinder.model.AddressType;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticKey;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses42;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2PKH;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2SH;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2WPKH;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.StaticUnsupportedAddress;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import org.apache.commons.codec.DecoderException;
import org.bitcoinj.base.Base58;
import org.bitcoinj.base.Coin;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class AddressTxtLineTest {

    private final TestAddresses42 testAddresses = new TestAddresses42(0, false);

    private final StaticKey staticKey = new StaticKey();
    private final KeyUtility keyUtility = new KeyUtility(testAddresses.network, new ByteBufferUtility(false));

    private void assertThatDefaultCoinIsSet(AddressToCoin addressToCoin) {
        assertThat(addressToCoin.coin(), is(equalTo(AddressTxtLine.DEFAULT_COIN)));
    }

    // <editor-fold defaultstate="collapsed" desc="fromLine">
    @Test
    public void fromLine_addressLineIsEmpty_throwsAddressFormatNotAcceptedException() {
        // act
        try {
            new AddressTxtLine().fromLine("", keyUtility);
            fail("Expected AddressFormatNotAcceptedException");
        } catch (AddressFormatNotAcceptedException e) {
            // assert
            assertThat(e.getMessage(), containsString(AddressTxtLine.REASON_EMPTY));
        }
    }

    @Test
    public void fromLine_addressLineStartsWithIgnoreLineSign_throwsAddressFormatNotAcceptedException() {
        // act
        try {
            new AddressTxtLine().fromLine(AddressTxtLine.IGNORE_LINE_PREFIX + " test", keyUtility);
            fail("Expected AddressFormatNotAcceptedException");
        } catch (AddressFormatNotAcceptedException e) {
            // assert
            assertThat(e.getMessage(), containsString(AddressTxtLine.REASON_IGNORE_PREFIX));
        }
    }

    @Test
    public void fromLine_addressLineIsOnlyIgnoreLineSign_throwsAddressFormatNotAcceptedException() {
        // act
        try {
            new AddressTxtLine().fromLine(AddressTxtLine.IGNORE_LINE_PREFIX, keyUtility);
            fail("Expected AddressFormatNotAcceptedException");
        } catch (AddressFormatNotAcceptedException e) {
            // assert
            assertThat(e.getMessage(), containsString(AddressTxtLine.REASON_IGNORE_PREFIX));
        }
    }

    @Test
    public void fromLine_uncompressedBitcoinAddressGiven_returnHash160AndDefaultCoin()
            throws AddressFormatNotAcceptedException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(staticKey.publicKeyUncompressed, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(addressToCoin.hash160(), is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    public void fromLine_compressedBitcoinAddressGiven_returnHash160AndDefaultCoin()
            throws AddressFormatNotAcceptedException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(staticKey.publicKeyCompressed, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(addressToCoin.hash160(), is(equalTo(staticKey.byteBufferPublicKeyCompressed)));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR)
    public void fromLine_uncompressedBitcoinAddressGivenWithValidAmount_returnHash160AndSpecifiedCoin(
            String addressSeparator) throws AddressFormatNotAcceptedException {
        // arrange
        long coin = 123987L;

        // act
        AddressToCoin addressToCoin =
                new AddressTxtLine().fromLine(staticKey.publicKeyUncompressed + addressSeparator + coin, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(addressToCoin.hash160(), is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
        assertThat(addressToCoin.coin(), is(equalTo(Coin.valueOf(coin))));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR)
    public void fromLine_uncompressedBitcoinAddressGivenWithInvalidAmount_returnHash160AndDefaultCoin(
            String addressSeparator) throws AddressFormatNotAcceptedException {
        // act
        AddressToCoin addressToCoin =
                new AddressTxtLine().fromLine(staticKey.publicKeyUncompressed + addressSeparator + "XYZ", keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(addressToCoin.hash160(), is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    public void fromLine_addressLineStartsWithAddressHeader_throwsAddressFormatNotAcceptedException() {
        // act
        try {
            new AddressTxtLine().fromLine(AddressTxtLine.ADDRESS_HEADER, keyUtility);
            fail("Expected AddressFormatNotAcceptedException");
        } catch (AddressFormatNotAcceptedException e) {
            // assert
            assertThat(e.getMessage(), containsString(AddressTxtLine.REASON_ADDRESS_HEADER));
        }
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_STATIC_UNSUPPORTED_ADDRESSES)
    public void fromLine_staticUnsupportedAddress_throwsAddressFormatNotAcceptedException(
            StaticUnsupportedAddress address) {
        // act
        try {
            new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);
            fail("Expected AddressFormatNotAcceptedException");
        } catch (AddressFormatNotAcceptedException e) {
            // assert
            assertThat(e.getMessage(), not(emptyOrNullString()));
        }
    }

    // <editor-fold defaultstate="collapsed" desc="staticaddresses.enums">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_STATIC_P2PKH_ADDRESSES)
    public void fromLine_staticP2PKHAddress_returnPublicKeyHash(P2PKH address)
            throws AddressFormatNotAcceptedException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(
                new ByteBufferUtility(true).getHexFromByteBuffer(addressToCoin.hash160()),
                is(equalTo(address.getPublicKeyHashAsHex())));
        assertThat(addressToCoin.hash160(), is(equalTo(address.getPublicKeyHashAsByteBuffer())));
        assertThat(addressToCoin.type(), is(AddressType.P2PKH_OR_P2SH));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_STATIC_P2SH_ADDRESSES)
    public void fromLine_staticP2SHAddress_returnScriptHash(P2SH address) throws AddressFormatNotAcceptedException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(
                new ByteBufferUtility(true).getHexFromByteBuffer(addressToCoin.hash160()),
                is(equalTo(address.getScriptHashAsHex())));
        assertThat(addressToCoin.hash160(), is(equalTo(address.getScriptHashAsByteBuffer())));
        assertThat(addressToCoin.type(), is(AddressType.P2PKH_OR_P2SH));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_STATIC_P2WPKH_ADDRESSES)
    public void fromLine_staticP2WPKHAddress_returnWitnessProgram(P2WPKH address)
            throws AddressFormatNotAcceptedException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(
                new ByteBufferUtility(true).getHexFromByteBuffer(addressToCoin.hash160()),
                is(equalTo(address.getWitnessProgramAsHex())));
        assertThat(addressToCoin.hash160(), is(equalTo(address.getWitnessProgramAsByteBuffer())));
        assertThat(addressToCoin.type(), is(AddressType.P2WPKH));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_INVALID_P2WPKH_ADDRESSES_VALID_BASE58)
    public void fromLine_invalidP2WPKHAddressWithValidBase58Given_parseAnyway(String base58, String hash)
            throws AddressFormatNotAcceptedException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(new ByteBufferUtility(true).getHexFromByteBuffer(addressToCoin.hash160()), is(equalTo(hash)));
        assertThatDefaultCoinIsSet(addressToCoin);
    }
    // </editor-fold>

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_INVALID_BECH32_WITNESS_VERSION_2)
    public void fromLine_invalidBech32WitnessVersion2_throwsAddressFormatNotAcceptedException(String base58) {
        // act
        try {
            new AddressTxtLine().fromLine(base58, keyUtility);
            fail("Expected AddressFormatNotAcceptedException");
        } catch (AddressFormatNotAcceptedException e) {
            // assert
            assertThat(e.getMessage(), containsString(AddressTxtLine.REASON_UNSUPPORTED_WITNESS_VERSION));
        }
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_INVALID_BASE58)
    public void fromLine_invalidP2WPKHAddressWithInvalidBase58Given_throwsAddressFormatNotAcceptedException(
            String base58) {
        // act
        try {
            new AddressTxtLine().fromLine(base58, keyUtility);
            fail("Expected AddressFormatNotAcceptedException");
        } catch (AddressFormatNotAcceptedException e) {
            // assert
            assertThat(e.getMessage(), containsString(AddressTxtLine.REASON_INVALID_BASE58));
        }
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BITCOIN_CASH_ADDRESSES_CHECKSUM_INVALID)
    public void fromLine_bitcoinCashAddressChecksumInvalid_parseAnyway(String base58, String expectedHash160)
            throws AddressFormatNotAcceptedException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        String hash160AsHex = keyUtility.byteBufferUtility().getHexFromByteBuffer(addressToCoin.hash160());
        assertThat(hash160AsHex, is(equalTo(expectedHash160)));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BITCOIN_CASH_ADDRESSES_INTERNAL_PURPOSE)
    public void fromLine_bitcoinCashAddressInternalPurpose_throwsAddressFormatNotAcceptedException(String base58) {
        // act
        try {
            new AddressTxtLine().fromLine(base58, keyUtility);
            fail("Expected AddressFormatNotAcceptedException");
        } catch (AddressFormatNotAcceptedException e) {
            // assert
            assertThat(e.getMessage(), containsString(AddressTxtLine.REASON_P2MS_NOT_SUPPORTED));
        }
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_BITCOIN_ADDRESSES_CORRECT_BASE_58)
    public void fromLine_bitcoinAddressChecksumInvalid_parseAnyway(String base58, String expectedHash160)
            throws AddressFormatNotAcceptedException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        String hash160AsHex = keyUtility.byteBufferUtility().getHexFromByteBuffer(addressToCoin.hash160());
        assertThat(hash160AsHex, is(equalTo(expectedHash160)));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_CORRECT_BASE_58)
    public void fromLine_correctBase58_hash160equals(String base58, String expectedHash160)
            throws AddressFormatNotAcceptedException, DecoderException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        String hash160AsHex = keyUtility.byteBufferUtility().getHexFromByteBuffer(addressToCoin.hash160());
        assertThat(hash160AsHex, is(equalTo(expectedHash160)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="parseBase58Address">
    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_SRC_POS)
    public void parseBase58Address_correctBase58UseHigherSrcPos_copiedPartial(int versionBytes)
            throws DecoderException {
        // arrange
        String encoded = Base58.encode(Hex.decode("1f" + "ffffffffffffffffffffffffffffffffffffffff"));

        // act
        AddressToCoin addressToCoin = new AddressTxtLine()
                .parseBase58Address(encoded, versionBytes, AddressTxtLine.CHECKSUM_BYTES_REGULAR, keyUtility);

        // assert
        byte[] hash160 = keyUtility.byteBufferUtility().byteBufferToBytes(addressToCoin.hash160());
        String hash160AsHex = Hex.toHexString(hash160);
        int expectedLastIndex = 40 - 1 - 2 * versionBytes + 2;
        assertThat(hash160AsHex.lastIndexOf("f"), is(equalTo(expectedLastIndex)));
    }
    // </editor-fold>

}
