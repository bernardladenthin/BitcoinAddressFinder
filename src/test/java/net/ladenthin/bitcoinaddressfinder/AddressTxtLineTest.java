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
import java.io.IOException;
import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.*;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.*;
import org.apache.commons.codec.DecoderException;
import org.bouncycastle.util.encoders.Hex;
import org.bitcoinj.base.Base58;
import org.bitcoinj.base.Coin;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class AddressTxtLineTest {

    private final TestAddresses42 testAddresses = new TestAddresses42(0, false);

    private final StaticKey staticKey = new StaticKey();
    private final KeyUtility keyUtility = new KeyUtility(testAddresses.network, new ByteBufferUtility(false));

    private void assertThatDefaultCoinIsSet(AddressToCoin addressToCoin) {
        assertThat(addressToCoin.coin(), is(equalTo(AddressTxtLine.DEFAULT_COIN)));
    }

    // <editor-fold defaultstate="collapsed" desc="fromLine">
    @Test
    public void fromLine_addressLineIsEmpty_returnNull() throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine("", keyUtility);

        // assert
        assertThat(addressToCoin, is(nullValue()));
    }

    @Test
    public void fromLine_addressLineStartsWithIgnoreLineSign_returnNull() throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(AddressTxtLine.IGNORE_LINE_PREFIX + " test", keyUtility);

        // assert
        assertThat(addressToCoin, is(nullValue()));
    }

    @Test
    public void fromLine_uncompressedBitcoinAddressGiven_returnHash160AndDefaultCoin() throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(staticKey.publicKeyUncompressed, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(addressToCoin.hash160(), is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    public void fromLine_compressedBitcoinAddressGiven_returnHash160AndDefaultCoin() throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(staticKey.publicKeyCompressed, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(addressToCoin.hash160(), is(equalTo(staticKey.byteBufferPublicKeyCompressed)));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR, location = CommonDataProvider.class)
    public void fromLine_uncompressedBitcoinAddressGivenWithValidAmount_returnHash160AndSpecifiedCoin(String addressSeparator) throws IOException {
        // arrange
        long coin = 123987L;

        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(staticKey.publicKeyUncompressed + addressSeparator + coin, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(addressToCoin.hash160(), is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
        assertThat(addressToCoin.coin(), is(equalTo(Coin.valueOf(coin))));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR, location = CommonDataProvider.class)
    public void fromLine_uncompressedBitcoinAddressGivenWithInvalidAmount_returnHash160AndDefaultCoin(String addressSeparator) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(staticKey.publicKeyUncompressed + addressSeparator + "XYZ", keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(addressToCoin.hash160(), is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    public void fromLine_addressLineStartsWithAddressHeader_returnNull() throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(AddressTxtLine.ADDRESS_HEADER, keyUtility);

        // assert
        assertThat(addressToCoin, is(nullValue()));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_UNSUPPORTED_ADDRESSES, location = CommonDataProvider.class)
    public void fromLine_staticUnsupportedAddress_returnNull(StaticUnsupportedAddress address) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);

        // assert
        assertThat(addressToCoin, is(nullValue()));
    }

    // <editor-fold defaultstate="collapsed" desc="staticaddresses.enums">
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_P2PKH_ADDRESSES, location = CommonDataProvider.class)
    public void fromLine_staticP2PKHAddress_returnPublicKeyHash(P2PKH address) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(new ByteBufferUtility(true).getHexFromByteBuffer(addressToCoin.hash160()), is(equalTo(address.getPublicKeyHashAsHex())));
        assertThat(addressToCoin.hash160(), is(equalTo(address.getPublicKeyHashAsByteBuffer())));
        assertThat(addressToCoin.type(), is(AddressType.P2PKH_OR_P2SH));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_P2SH_ADDRESSES, location = CommonDataProvider.class)
    public void fromLine_staticP2SHAddress_returnScriptHash(P2SH address) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(new ByteBufferUtility(true).getHexFromByteBuffer(addressToCoin.hash160()), is(equalTo(address.getScriptHashAsHex())));
        assertThat(addressToCoin.hash160(), is(equalTo(address.getScriptHashAsByteBuffer())));
        assertThat(addressToCoin.type(), is(AddressType.P2PKH_OR_P2SH));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_P2WPKH_ADDRESSES, location = CommonDataProvider.class)
    public void fromLine_staticP2WPKHAddress_returnWitnessProgram(P2WPKH address) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(new ByteBufferUtility(true).getHexFromByteBuffer(addressToCoin.hash160()), is(equalTo(address.getWitnessProgramAsHex())));
        assertThat(addressToCoin.hash160(), is(equalTo(address.getWitnessProgramAsByteBuffer())));
        assertThat(addressToCoin.type(), is(AddressType.P2WPKH));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_INVALID_P2WPKH_ADDRESSES_VALID_BASE58, location = CommonDataProvider.class)
    public void fromLine_invalidP2WPKHAddressWithValidBase58Given_parseAnyway(String base58, String hash) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        assertThat(new ByteBufferUtility(true).getHexFromByteBuffer(addressToCoin.hash160()), is(equalTo(hash)));
        assertThatDefaultCoinIsSet(addressToCoin);
    }
    // </editor-fold>

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_INVALID_BECH32_WITNESS_VERSION_2, location = CommonDataProvider.class)
    public void fromLine_invalidBech32WitnessVersion2_returnsNull(String base58) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // assert
        assertThat(addressToCoin, is(nullValue()));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_INVALID_BASE58, location = CommonDataProvider.class)
    public void fromLine_invalidP2WPKHAddressWithInvalidBase58Given_returnsNull(String base58) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // assert
        assertThat(addressToCoin, is(nullValue()));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BITCOIN_CASH_ADDRESSES_CHECKSUM_INVALID, location = CommonDataProvider.class)
    public void fromLine_bitcoinCashAddressChecksumInvalid_parseAnyway(String base58, String expectedHash160) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        String hash160AsHex = keyUtility.byteBufferUtility().getHexFromByteBuffer(addressToCoin.hash160());
        assertThat(hash160AsHex, is(equalTo(expectedHash160)));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BITCOIN_CASH_ADDRESSES_INTERNAL_PURPOSE, location = CommonDataProvider.class)
    public void fromLine_bitcoinCashAddressInternalPurpose_parseAnyway(String base58) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // assert
        assertThat(addressToCoin, is(nullValue()));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BITCOIN_ADDRESSES_CORRECT_BASE_58, location = CommonDataProvider.class)
    public void fromLine_bitcoinAddressChecksumInvalid_parseAnyway(String base58, String expectedHash160) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // pre-assert
        assertThat(addressToCoin, is(notNullValue()));

        // assert
        String hash160AsHex = keyUtility.byteBufferUtility().getHexFromByteBuffer(addressToCoin.hash160());
        assertThat(hash160AsHex, is(equalTo(expectedHash160)));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_CORRECT_BASE_58, location = CommonDataProvider.class)
    public void fromLine_correctBase58_hash160equals(String base58, String expectedHash160) throws IOException, DecoderException {
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
    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_SRC_POS, location = CommonDataProvider.class)
    public void parseBase58Address_correctBase58UseHigherSrcPos_copiedPartial(int versionBytes) throws IOException, DecoderException {
        // arrange
        String encoded = Base58.encode(Hex.decode("1f" + "ffffffffffffffffffffffffffffffffffffffff"));

        // act
        AddressToCoin addressToCoin = new AddressTxtLine().parseBase58Address(encoded, versionBytes, AddressTxtLine.CHECKSUM_BYTES_REGULAR, keyUtility);

        // assert
        byte[] hash160 = keyUtility.byteBufferUtility().byteBufferToBytes(addressToCoin.hash160());
        String hash160AsHex = Hex.toHexString(hash160);
        int expectedLastIndex = 40 - 1 - 2 * versionBytes + 2;
        assertThat(hash160AsHex.lastIndexOf("f"), is(equalTo(expectedLastIndex)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="extractPKHFromBitcoinCashAddress">
    @Test
    public void extractPKHFromBitcoinCashAddress_withoutPrefix_returnsCorrectHash160() throws Exception {
        // arrange
        P2PKH address = P2PKH.BitcoinCash;

        // act
        byte[] hash160 = AddressTxtLine.extractPKHFromBitcoinCashAddress(address.getPublicAddress());

        // assert
        ByteBuffer buffer = keyUtility.byteBufferUtility().byteArrayToByteBuffer(hash160);
        String actualHashHex = keyUtility.byteBufferUtility().getHexFromByteBuffer(buffer);
        assertThat(actualHashHex, is(equalTo(address.getPublicKeyHashAsHex())));
    }

    @Test
    public void extractPKHFromBitcoinCashAddress_withPrefix_returnsCorrectHash160() throws Exception {
        // arrange
        P2PKH address = P2PKH.BitcoinCashWithPrefix;

        // pre-assert
        assertThat(address.getPublicAddress(), startsWith(AddressTxtLine.BITCOIN_CASH_PREFIX));

        // act
        byte[] hash160 = AddressTxtLine.extractPKHFromBitcoinCashAddress(address.getPublicAddress());

        // assert
        ByteBuffer buffer = keyUtility.byteBufferUtility().byteArrayToByteBuffer(hash160);
        String actualHashHex = keyUtility.byteBufferUtility().getHexFromByteBuffer(buffer);
        assertThat(actualHashHex, is(equalTo(address.getPublicKeyHashAsHex())));
    }
    // </editor-fold>
}
