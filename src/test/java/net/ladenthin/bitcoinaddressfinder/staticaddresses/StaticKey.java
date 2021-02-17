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
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;

/**
 * Static strings from a random generated address https://www.bitaddress.org
 */
public class StaticKey {

    final public String privateKeyHex = "68e23530deb6d5011ab56d8ad9f7b4a3b424f1112f08606357497495929f72dc";
    final public BigInteger privateKeyBigInteger = new BigInteger("47440210799387980664936216788675555637818488436833759923669526136462528967388");
    final public byte[] privateKeyBytes = {104, -30, 53, 48, -34, -74, -43, 1, 26, -75, 109, -118, -39, -9, -76, -93, -76, 36, -15, 17, 47, 8, 96, 99, 87, 73, 116, -107, -110, -97, 114, -36};
    final public String privateKeyWiFUncompressed = "5JcUh9ET11ZZHnEhSvzEUCg3opTa9WCmsGuCFYGQGhBzKJpgJ39";
    final public String privateKeyWiFCompressed = "KzjbEBLMm3UhX4fTXTHcT4XMPeUHJXty2uBNfAzyiGPVynPeFMeV";

    final public String publicKeyUncompressedHex = "045d99d81d9e731e0d7eebd1c858b1155da7981b1f0a16d322a361f8b589ad2e3bde53dc614e3a84164dab3f5899abde3b09553dca10c9716fa623a5942b9ea420";
    final public byte[] publicKeyUncompressedBytes = {4, 93, -103, -40, 29, -98, 115, 30, 13, 126, -21, -47, -56, 88, -79, 21, 93, -89, -104, 27, 31, 10, 22, -45, 34, -93, 97, -8, -75, -119, -83, 46, 59, -34, 83, -36, 97, 78, 58, -124, 22, 77, -85, 63, 88, -103, -85, -34, 59, 9, 85, 61, -54, 16, -55, 113, 111, -90, 35, -91, -108, 43, -98, -92, 32};
    final public String publicKeyCompressedHex = "025d99d81d9e731e0d7eebd1c858b1155da7981b1f0a16d322a361f8b589ad2e3b";
    final public byte[] publicKeyCompressedBytes = {2, 93, -103, -40, 29, -98, 115, 30, 13, 126, -21, -47, -56, 88, -79, 21, 93, -89, -104, 27, 31, 10, 22, -45, 34, -93, 97, -8, -75, -119, -83, 46, 59};
    
    final public String publicKeyUncompressedHash160Hex = "024336956610316605d1051cb9b8e88f82b70b29";
    final public String publicKeyCompressedHash160Hex = "892852a28710e156b07fa7933edd5490cbbcfa4f";
    
    final public String publicKeyUncompressed = "1CxsSWgsWNxoqS1XB5QgchtMpWrzzPCES";
    final public String publicKeyCompressed = "1DWDsxY3mvzjPLHD67nRq15M8vs6VLZaqV";
    
    final public ByteBuffer byteBufferPublicKeyUncompressed = new ByteBufferUtility(false).getByteBufferFromHex(publicKeyUncompressedHash160Hex);
    final public ByteBuffer byteBufferPublicKeyCompressed = new ByteBufferUtility(false).getByteBufferFromHex(publicKeyCompressedHash160Hex);

}
