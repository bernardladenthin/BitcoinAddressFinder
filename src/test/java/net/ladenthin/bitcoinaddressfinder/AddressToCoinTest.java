// @formatter:off
/**
 * Copyright 2021 Bernard Ladenthin bernard.ladenthin@gmail.com
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

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses42;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class AddressToCoinTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(true));
    
    @Test
    public void createAddressToCoin_publicKeyGiven_ToStringAndEqualsAndHashCode() throws IOException, InterruptedException {
        // arrange
        ECKey keyUncompressed = new TestAddresses42(1, false).getECKeys().get(0);
        ECKey keyCompressed = new TestAddresses42(1, true).getECKeys().get(0);
        
        AddressToCoin addressToCoinUncompressed = new AddressToCoin(keyUtility.byteBufferUtility().byteArrayToByteBuffer(keyUncompressed.getPubKeyHash()), Coin.COIN, AddressType.P2PKH_OR_P2SH);
        AddressToCoin addressToCoinUncompressed2 = new AddressToCoin(keyUtility.byteBufferUtility().byteArrayToByteBuffer(keyUncompressed.getPubKeyHash()), Coin.COIN, AddressType.P2PKH_OR_P2SH);
        
        AddressToCoin addressToCoinCompressed = new AddressToCoin(keyUtility.byteBufferUtility().byteArrayToByteBuffer(keyCompressed.getPubKeyHash()), Coin.COIN, AddressType.P2PKH_OR_P2SH);
        AddressToCoin addressToCoinCompressed2 = new AddressToCoin(keyUtility.byteBufferUtility().byteArrayToByteBuffer(keyCompressed.getPubKeyHash()), Coin.COIN, AddressType.P2PKH_OR_P2SH);
        
        // assert
        EqualHashCodeToStringTestHelper equalHashCodeToStringTestHelper = new EqualHashCodeToStringTestHelper(addressToCoinUncompressed, addressToCoinUncompressed2, addressToCoinCompressed, addressToCoinCompressed2);
        equalHashCodeToStringTestHelper.assertEqualsHashCodeToStringAIsDifferentToB();

        assertThat(addressToCoinUncompressed.toString(), is(equalTo("AddressToCoin{hash160=73d6a3b07f488e12f9175716f95c5e18c265693f, coin=100000000, type=P2PKH_OR_P2SH}")));
        assertThat(addressToCoinCompressed.toString(), is(equalTo("AddressToCoin{hash160=6970dea35c48e1c78e931117fab833354cddf9b4, coin=100000000, type=P2PKH_OR_P2SH}")));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createAddressToCoin_invalidAddressSizeGiven_ToStringAndEqualsAndHashCode() throws IOException, InterruptedException {
        // arrange
        ByteBuffer byteBuffer32bytes = keyUtility.byteBufferUtility().getByteBufferFromHex("0000000000000000000000000000000000000000000000000000000000000000");
        // act
        new AddressToCoin(byteBuffer32bytes, Coin.COIN, AddressType.P2PKH_OR_P2SH);
        // assert
    }
    
}
