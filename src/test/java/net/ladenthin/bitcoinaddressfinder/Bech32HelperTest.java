// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
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

import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2PKH;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2WPKH;
import org.bitcoinj.base.Bech32;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

public class Bech32HelperTest {

    private final KeyUtility keyUtility = new KeyUtility(new NetworkParameterFactory().getNetwork(), new ByteBufferUtility(false));

    // <editor-fold defaultstate="collapsed" desc="decodeBech32CharsetToValues">
    @Test
    public void decodeBech32CharsetToValues_fullCharset_returnsValuesZeroToThirtyOne() {
        // arrange
        Bech32Helper sut = new Bech32Helper();
        byte[] expected = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31};

        // act
        byte[] result = sut.decodeBech32CharsetToValues(Bech32Helper.CHARSET);

        // assert
        assertThat(result, is(expected));
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeBech32CharsetToValues_invalidCharacter_throwsException() {
        // arrange
        Bech32Helper sut = new Bech32Helper();

        // act
        // 'i' is not part of the Bech32 character set (excluded to avoid visual ambiguity with '1')
        sut.decodeBech32CharsetToValues("i");
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="extractPKHFromBitcoinCashAddress">
    @Test
    public void extractPKHFromBitcoinCashAddress_withoutPrefix_returnsCorrectHash160() throws ReflectiveOperationException {
        // arrange
        P2PKH address = P2PKH.BitcoinCash;

        // act
        byte[] hash160 = new Bech32Helper().extractPKHFromBitcoinCashAddress(address.getPublicAddress());

        // assert
        ByteBuffer buffer = keyUtility.byteBufferUtility().byteArrayToByteBuffer(hash160);
        String actualHashHex = keyUtility.byteBufferUtility().getHexFromByteBuffer(buffer);
        assertThat(actualHashHex, is(equalTo(address.getPublicKeyHashAsHex())));
    }

    @Test
    public void extractPKHFromBitcoinCashAddress_withPrefix_returnsCorrectHash160() throws ReflectiveOperationException {
        // arrange
        P2PKH address = P2PKH.BitcoinCashWithPrefix;

        // pre-assert
        assertThat(address.getPublicAddress(), startsWith(AddressTxtLine.BITCOIN_CASH_PREFIX));

        // act
        byte[] hash160 = new Bech32Helper().extractPKHFromBitcoinCashAddress(address.getPublicAddress());

        // assert
        ByteBuffer buffer = keyUtility.byteBufferUtility().byteArrayToByteBuffer(hash160);
        String actualHashHex = keyUtility.byteBufferUtility().getHexFromByteBuffer(buffer);
        assertThat(actualHashHex, is(equalTo(address.getPublicKeyHashAsHex())));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getWitnessPrograms">
    @Test
    public void getWitnessPrograms_validBech32Data_returnsWitnessProgram() throws ReflectiveOperationException {
        // arrange
        P2WPKH address = P2WPKH.Bitcoin;
        Bech32.Bech32Data bechData = Bech32.decode(address.getPublicAddress());
        Bech32Helper sut = new Bech32Helper();

        // act
        byte[] witnessProgram = sut.getWitnessPrograms(bechData);

        // assert
        assertThat(witnessProgram, is(address.getWitnessProgram()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getWitnessVersion">
    @Test
    public void getWitnessVersion_validBech32Data_returnsZero() throws ReflectiveOperationException {
        // arrange
        P2WPKH address = P2WPKH.Bitcoin;
        Bech32.Bech32Data bechData = Bech32.decode(address.getPublicAddress());
        Bech32Helper sut = new Bech32Helper();

        // act
        Short witnessVersion = sut.getWitnessVersion(bechData);

        // assert
        assertThat(witnessVersion, is((short) 0));
    }
    // </editor-fold>
}
