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
import net.ladenthin.bitcoinaddressfinder.staticaddresses.*;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.StaticKey;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.Coin;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class AddressTxtLineTest {

    private static final TestAddresses42 testAddresses = new TestAddresses42(0, false);

    StaticKey staticKey = new StaticKey();

    KeyUtility keyUtility = new KeyUtility(testAddresses.networkParameters, new ByteBufferUtility(false));

    @Before
    public void init() throws IOException {
    }

    private void assertThatDefaultCoinIsSet(AddressToCoin addressToCoin) {
        assertThat(addressToCoin.getCoin(), is(equalTo(AddressTxtLine.DEFAULT_COIN)));
    }

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
    public void fromLine_uncompressedBitcoinAddressGiven_ReturnHash160AndDefaultCoin() throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(staticKey.publicKeyUncompressed, keyUtility);

        // assert
        assertThat(addressToCoin.getHash160(), is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    public void fromLine_compressedBitcoinAddressGiven_ReturnHash160AndDefaultCoin() throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(staticKey.publicKeyCompressed, keyUtility);

        // assert
        assertThat(addressToCoin.getHash160(), is(equalTo(staticKey.byteBufferPublicKeyCompressed)));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR, location = CommonDataProvider.class)
    public void fromLine_uncompressedBitcoinAddressGivenWithValidAmount_ReturnHash160AndDefaultCoin(String addressSeparator) throws IOException {
        // arrange
        String coin = "123987";
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(staticKey.publicKeyUncompressed + addressSeparator + coin, keyUtility);

        // assert
        assertThat(addressToCoin.getHash160(), is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
        assertThat(addressToCoin.getCoin(), is(equalTo(Coin.valueOf(Long.valueOf(coin)))));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_ADDRESS_SEPARATOR, location = CommonDataProvider.class)
    public void fromLine_uncompressedBitcoinAddressGivenWithInvalidAmount_ReturnHash160AndDefaultCoin(String addressSeparator) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(staticKey.publicKeyUncompressed + addressSeparator + "XYZ", keyUtility);

        // assert
        assertThat(addressToCoin.getHash160(), is(equalTo(staticKey.byteBufferPublicKeyUncompressed)));
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
    public void fromLine_StaticUnsupportedAddress_returnNull(StaticUnsupportedAddress address) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);

        // assert
        assertThat(addressToCoin, is(nullValue()));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_P2PKH_ADDRESSES, location = CommonDataProvider.class)
    public void fromLine_StaticP2PKHAddress_returnPublicKeyHash(StaticP2PKHAddress address) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);

        // assert
        assertThat(new ByteBufferUtility(true).getHexFromByteBuffer(addressToCoin.getHash160()), is(equalTo(address.getPublicKeyHashAsHex())));
        assertThat(addressToCoin.getHash160(), is(equalTo(address.getPublicKeyHashAsByteBuffer())));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_STATIC_P2SH_ADDRESSES, location = CommonDataProvider.class)
    public void fromLine_StaticP2SHAddress_returnScriptHash(StaticP2SHAddress address) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(address.getPublicAddress(), keyUtility);

        // assert
        assertThat(new ByteBufferUtility(true).getHexFromByteBuffer(addressToCoin.getHash160()), is(equalTo(address.getScriptHashAsHex())));
        assertThat(addressToCoin.getHash160(), is(equalTo(address.getScriptHashAsByteBuffer())));
        assertThatDefaultCoinIsSet(addressToCoin);
    }

    @Test(expected = AddressFormatException.class)
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BITCOIN_INVALID_P2WPKH_ADDRESSES, location = CommonDataProvider.class)
    public void fromLine_InvalidP2WPKHAddressGive_throwsException(String base58) throws IOException {
        // act
        new AddressTxtLine().fromLine(base58, keyUtility);
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BITCOIN_CASH_ADDRESSES_CHECKSUM_INVALID, location = CommonDataProvider.class)
    public void fromLine_bitcoinCashAddressChecksumInvalid_parseAnyway(String base58, String expectedHash160) throws IOException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // assert
        String hash160AsHex = keyUtility.byteBufferUtility.getHexFromByteBuffer(addressToCoin.getHash160());
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

        // assert
        String hash160AsHex = keyUtility.byteBufferUtility.getHexFromByteBuffer(addressToCoin.getHash160());
        assertThat(hash160AsHex, is(equalTo(expectedHash160)));
    }

    @Test(expected = AddressFormatException.InvalidCharacter.class)
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_BITCOIN_ADDRESSES_INVALID_BASE_58, location = CommonDataProvider.class)
    public void fromLine_bitcoinAddressInternalPurpose_throwsException(String base58) throws IOException {
        // act
        new AddressTxtLine().fromLine(base58, keyUtility);
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_CORRECT_BASE_58, location = CommonDataProvider.class)
    public void fromLine_correctBase58_hash160equals(String base58, String expectedHash160) throws IOException, DecoderException {
        // act
        AddressToCoin addressToCoin = new AddressTxtLine().fromLine(base58, keyUtility);

        // assert
        String hash160AsHex = keyUtility.byteBufferUtility.getHexFromByteBuffer(addressToCoin.getHash160());
        assertThat(hash160AsHex, is(equalTo(expectedHash160)));
    }

    @Test
    @UseDataProvider(value = CommonDataProvider.DATA_PROVIDER_SRC_POS, location = CommonDataProvider.class)
    public void fromLine_correctBase58UseHigherSrcPos_copiedPartial(int srcPos) throws IOException, DecoderException {
        // act
        String encoded = Base58.encode(Hex.decodeHex("1f" + "ffffffffffffffffffffffffffffffffffffffff"));

        byte[] hash160 = new AddressTxtLine().getHash160fromBase58AddressUnchecked(encoded, srcPos);

        // assert
        String hash160AsHex = org.bouncycastle.util.encoders.Hex.toHexString(hash160);
        int expectedLastIndex = 40 - 1 - 2 * srcPos + 2;
        assertThat(hash160AsHex.lastIndexOf("f"), is(equalTo(expectedLastIndex)));
    }
}
