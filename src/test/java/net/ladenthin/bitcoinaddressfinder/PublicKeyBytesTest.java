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

import org.junit.Test;

import java.io.IOException;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses42;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.MainNetParams;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class PublicKeyBytesTest {

    protected final NetworkParameters networkParameters = MainNetParams.get();
    protected final KeyUtility keyUtility = new KeyUtility(networkParameters, new ByteBufferUtility(true));
    
    @Test
    public void createPublicKeyBytes_publicKeyGiven_PublicKeyAndHashesEquals() throws IOException, InterruptedException {
        // arrange
        ECKey keyUncompressed = new TestAddresses42(1, false).getECKeys().get(0);
        ECKey keyCompressed = new TestAddresses42(1, true).getECKeys().get(0);
        
        // act
        PublicKeyBytes publicKeyBytesUncompressed = new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey());
        PublicKeyBytes publicKeyBytesCompressed = new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey(), keyCompressed.getPubKey());
        
        // assert
        assertThat(publicKeyBytesUncompressed.getUncompressed(), is(equalTo(keyUncompressed.getPubKey())));
        assertThat(publicKeyBytesUncompressed.getUncompressedKeyHash(), is(equalTo(keyUncompressed.getPubKeyHash())));
        assertThat(Utils.sha256hash160(publicKeyBytesCompressed.getUncompressed()), is(equalTo(keyUncompressed.getPubKeyHash())));
        assertThat(publicKeyBytesCompressed.getUncompressedKeyHash(), is(equalTo(keyUncompressed.getPubKeyHash())));
        assertThat(publicKeyBytesUncompressed.getUncompressedKeyHashAsBase58(keyUtility), is(equalTo(LegacyAddress.fromPubKeyHash(networkParameters, keyUncompressed.getPubKeyHash()).toBase58())));
        assertThat(publicKeyBytesCompressed.getUncompressedKeyHashAsBase58(keyUtility), is(equalTo(LegacyAddress.fromPubKeyHash(networkParameters, keyUncompressed.getPubKeyHash()).toBase58())));
        
        assertThat(publicKeyBytesUncompressed.getCompressed(), is(equalTo(keyCompressed.getPubKey())));
        assertThat(publicKeyBytesUncompressed.getCompressedKeyHash(), is(equalTo(keyCompressed.getPubKeyHash())));
        assertThat(Utils.sha256hash160(publicKeyBytesCompressed.getCompressed()), is(equalTo(keyCompressed.getPubKeyHash())));
        assertThat(publicKeyBytesCompressed.getCompressedKeyHash(), is(equalTo(keyCompressed.getPubKeyHash())));
        assertThat(publicKeyBytesUncompressed.getCompressedKeyHashAsBase58(keyUtility), is(equalTo(LegacyAddress.fromPubKeyHash(networkParameters, keyCompressed.getPubKeyHash()).toBase58())));
        assertThat(publicKeyBytesCompressed.getCompressedKeyHashAsBase58(keyUtility), is(equalTo(LegacyAddress.fromPubKeyHash(networkParameters, keyCompressed.getPubKeyHash()).toBase58())));
    }
    
    @Test
    public void createPublicKeyBytes_publicKeyGiven_ToStringAndEqualsAndHashCode() throws IOException, InterruptedException {
        // arrange
        ECKey keyUncompressed = new TestAddresses42(1, false).getECKeys().get(0);
        ECKey keyCompressed = new TestAddresses42(1, true).getECKeys().get(0);
        
        // act
        PublicKeyBytes publicKeyBytesUncompressed = new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey());
        PublicKeyBytes publicKeyBytesUncompressed2 = new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey());
        PublicKeyBytes publicKeyBytesCompressed = new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey(), keyCompressed.getPubKey());
        PublicKeyBytes publicKeyBytesCompressed2 = new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey(), keyCompressed.getPubKey());
        
        // assert
        EqualHashCodeToStringTestHelper equalHashCodeToStringTestHelper = new EqualHashCodeToStringTestHelper(publicKeyBytesUncompressed, publicKeyBytesUncompressed2, publicKeyBytesCompressed, publicKeyBytesCompressed2);
        equalHashCodeToStringTestHelper.assertEqualsHashCodeToStringAIsEqualToB();

        // toString
        assertThat(publicKeyBytesUncompressed.toString(), is(equalTo(publicKeyBytesCompressed.toString())));
        assertThat(publicKeyBytesUncompressed.toString(), is(equalTo("PublicKeyBytes(uncompressed=[4, -72, -92, -69, -93, 30, -111, -120, 55, 18, -89, 70, 66, 24, -63, 62, 52, 48, 39, 34, 88, -110, -57, 21, -14, 53, -101, 74, 58, 26, -82, 40, 22, 107, -6, 83, -14, -15, -1, 14, -5, 118, -120, 121, -19, -126, 80, -9, 111, -100, 126, -61, 59, -5, -41, 2, -78, 29, 121, -12, -40, -80, -68, 93, 4], compressed=[2, -72, -92, -69, -93, 30, -111, -120, 55, 18, -89, 70, 66, 24, -63, 62, 52, 48, 39, 34, 88, -110, -57, 21, -14, 53, -101, 74, 58, 26, -82, 40, 22], uncompressedKeyHash=[115, -42, -93, -80, 127, 72, -114, 18, -7, 23, 87, 22, -7, 92, 94, 24, -62, 101, 105, 63], compressedKeyHash=[105, 112, -34, -93, 92, 72, -31, -57, -114, -109, 17, 23, -6, -72, 51, 53, 76, -35, -7, -76], uncompressedKeyHashBase58=null, compressedKeyHashBase58=null, secretKey=24250429618215260598957696001935175135959229619080974590971174872813112994997)")));
    }
    
    @Test
    public void maxPrivateKeyAsHexString_isEqualToConstant() throws IOException, InterruptedException {
        // arrange
        String maxPrivateKeyAsHexString = Hex.encodeHexString(KeyUtility.bigIntegerToBytes(PublicKeyBytes.MAX_PRIVATE_KEY));
        // act
        
        // assert
        assertThat(maxPrivateKeyAsHexString.toLowerCase(), is(equalTo("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141".toLowerCase())));
    }
}
