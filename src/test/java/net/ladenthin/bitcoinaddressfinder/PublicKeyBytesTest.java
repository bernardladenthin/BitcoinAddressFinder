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
import java.math.BigInteger;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.TestAddresses42;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.base.LegacyAddress;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.internal.CryptoUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.slf4j.Logger;

public class PublicKeyBytesTest {

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
    protected final KeyUtility keyUtility = new KeyUtility(network, byteBufferUtility);
    
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
        assertThat(CryptoUtils.sha256hash160(publicKeyBytesCompressed.getUncompressed()), is(equalTo(keyUncompressed.getPubKeyHash())));
        assertThat(publicKeyBytesCompressed.getUncompressedKeyHash(), is(equalTo(keyUncompressed.getPubKeyHash())));
        assertThat(publicKeyBytesUncompressed.getUncompressedKeyHashAsBase58(keyUtility), is(equalTo(LegacyAddress.fromPubKeyHash(network, keyUncompressed.getPubKeyHash()).toBase58())));
        assertThat(publicKeyBytesCompressed.getUncompressedKeyHashAsBase58(keyUtility), is(equalTo(LegacyAddress.fromPubKeyHash(network, keyUncompressed.getPubKeyHash()).toBase58())));
        
        assertThat(publicKeyBytesUncompressed.getCompressed(), is(equalTo(keyCompressed.getPubKey())));
        assertThat(publicKeyBytesUncompressed.getCompressedKeyHash(), is(equalTo(keyCompressed.getPubKeyHash())));
        assertThat(CryptoUtils.sha256hash160(publicKeyBytesCompressed.getCompressed()), is(equalTo(keyCompressed.getPubKeyHash())));
        assertThat(publicKeyBytesCompressed.getCompressedKeyHash(), is(equalTo(keyCompressed.getPubKeyHash())));
        assertThat(publicKeyBytesUncompressed.getCompressedKeyHashAsBase58(keyUtility), is(equalTo(LegacyAddress.fromPubKeyHash(network, keyCompressed.getPubKeyHash()).toBase58())));
        assertThat(publicKeyBytesCompressed.getCompressedKeyHashAsBase58(keyUtility), is(equalTo(LegacyAddress.fromPubKeyHash(network, keyCompressed.getPubKeyHash()).toBase58())));
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
        assertThat(publicKeyBytesUncompressed.toString(), is(equalTo("PublicKeyBytes{secretKey=24250429618215260598957696001935175135959229619080974590971174872813112994997}")));
    }
    
    @Test
    public void maxPrivateKeyAsHexString_isEqualToConstant() throws IOException, InterruptedException {
        // arrange
        String maxPrivateKeyAsHexString = Hex.encodeHexString(byteBufferUtility.bigIntegerToBytes(PublicKeyBytes.MAX_PRIVATE_KEY));
        // act
        
        // assert
        assertThat(maxPrivateKeyAsHexString.toLowerCase(), is(equalTo("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141".toLowerCase())));
    }
    
    // <editor-fold defaultstate="collapsed" desc="Tests for runtimePublicKeyCalculationCheck">
    @Test
    public void runtimePublicKeyCalculationCheck_validKey_returnsTrue() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] uncompressed = ecKey.getPubKey();
        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secretKey, uncompressed); // compressed is derived

        Logger logger = mock(Logger.class);

        // act
        boolean result = publicKeyBytes.runtimePublicKeyCalculationCheck(logger);

        // assert
        assertThat(result, is(true));
        verify(logger, never()).error(anyString());
    }

    @Test
    public void runtimePublicKeyCalculationCheck_invalidCompressedHash_returnsFalse() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] uncompressed = ecKey.getPubKey();
        byte[] compressed = ECKey.fromPrivate(secretKey, true).getPubKey();
        byte[] wrongCompressedHash = new byte[PublicKeyBytes.HASH160_SIZE]; // all-zero hash (invalid)

        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secretKey, uncompressed, wrongCompressedHash);

        Logger logger = mock(Logger.class);

        // act
        boolean result = publicKeyBytes.runtimePublicKeyCalculationCheck(logger);

        // assert
        assertThat(result, is(false));
        verify(logger).error(contains("fromPrivateCompressed.getPubKeyHash()"));
    }

    @Test
    public void runtimePublicKeyCalculationCheck_invalidUncompressedHash_returnsFalse() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] uncompressed = ecKey.getPubKey();
        byte[] compressed = ECKey.fromPrivate(secretKey, true).getPubKey();
        byte[] wrongUncompressedHash = new byte[PublicKeyBytes.HASH160_SIZE]; // all-zero hash (invalid)

        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secretKey, wrongUncompressedHash, compressed);

        Logger logger = mock(Logger.class);

        // act
        boolean result = publicKeyBytes.runtimePublicKeyCalculationCheck(logger);

        // assert
        assertThat(result, is(false));
        verify(logger).error(contains("fromPrivateUncompressed.getPubKeyHash()"));
    }
    
    @Test
    public void runtimePublicKeyCalculationCheck_invalidCompressedAndUncompressedHash_returnsFalse() {
        // arrange
        BigInteger secretKey = new BigInteger("1337");
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        byte[] uncompressedWrong = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES]; // all-zero hash (invalid)
        byte[] compressedWrong = new byte[PublicKeyBytes.PUBLIC_KEY_COMPRESSED_BYTES]; // all-zero hash (invalid)

        PublicKeyBytes publicKeyBytes = new PublicKeyBytes(secretKey, uncompressedWrong, compressedWrong);

        Logger logger = mock(Logger.class);

        // act
        boolean result = publicKeyBytes.runtimePublicKeyCalculationCheck(logger);

        // assert
        assertThat(result, is(false));
        verify(logger).error(contains("fromPrivateUncompressed.getPubKeyHash()"));
        verify(logger).error(contains("fromPrivateCompressed.getPubKeyHash()"));
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="toString">
    @ToStringTest
    @Test
    public void toString_whenCalled_containsClassNameAndPrivateKey() throws IOException {
        // arrange
        ECKey keyUncompressed = new TestAddresses42(1, false).getECKeys().get(0);
        
        // act
        PublicKeyBytes publicKeyBytesUncompressed = new PublicKeyBytes(keyUncompressed.getPrivKey(), keyUncompressed.getPubKey());
        String toStringOutput = publicKeyBytesUncompressed.toString();

        assertThat(toStringOutput, not(emptyOrNullString()));
        assertThat(toStringOutput, matchesPattern("PublicKeyBytes\\{secretKey=\\d+}"));
    }
    // </editor-fold>
}
