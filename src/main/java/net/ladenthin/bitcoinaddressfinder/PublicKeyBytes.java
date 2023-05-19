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

import com.google.common.hash.Hashing;
import java.math.BigInteger;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.codec.digest.DigestUtils;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;

@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PublicKeyBytes {
    
    /**
     * Use {@link com.​google.​common.​hash.Hashing} and
     * {@link org.bouncycastle.crypto.digests.RIPEMD160Digest} instead
     * {@link org.bitcoinj.core.Utils#sha256hash160(byte[])}.
     */
    public static final boolean USE_SHA256_RIPEMD160_FAST = true;

    public static final BigInteger MAX_TECHNICALLY_PRIVATE_KEY = BigInteger.valueOf(2).pow(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS).subtract(BigInteger.ONE);

    /**
     * Specifically, any 256-bit number between {@code 0x1} and {@code 0xFFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFE BAAE DCE6 AF48 A03B BFD2 5E8C D036 4141} is a valid
     * private key.
     */
    public static final BigInteger MAX_PRIVATE_KEY = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);

    public static final int PRIVATE_KEY_MAX_NUM_BITS = 256;
    public static final int BITS_PER_BYTE = 8;
    public static final int PRIVATE_KEY_MAX_NUM_BYTES = PRIVATE_KEY_MAX_NUM_BITS / BITS_PER_BYTE;

    public static final int ONE_COORDINATE_NUM_BYTES = 32;
    public static final int TWO_COORDINATES_NUM_BYTES = ONE_COORDINATE_NUM_BYTES * 2;
    public static final int PARITY_BYTES_LENGTH = 1;

    public static final int LAST_Y_COORDINATE_BYTE_INDEX = PublicKeyBytes.PARITY_BYTES_LENGTH + PublicKeyBytes.TWO_COORDINATES_NUM_BYTES - 1;

    /**
     * The first byte (parity) is 4 to indicate a public key with x and y
     * coordinate (uncompressed).
     */
    public static final int PARITY_UNCOMPRESSED = 4;
    public static final int PARITY_COMPRESSED_EVEN = 2;
    public static final int PARITY_COMPRESSED_ODD = 3;

    public static final int HASH160_SIZE = 20;
    
    public final static int PUBLIC_KEY_UNCOMPRESSED_BYTES = PARITY_BYTES_LENGTH + TWO_COORDINATES_NUM_BYTES;
    public final static int PUBLIC_KEY_COMPRESSED_BYTES = PARITY_BYTES_LENGTH + ONE_COORDINATE_NUM_BYTES;

    private final byte[] uncompressed;
    private final byte[] compressed;
    
    /**
     * Lazy initialization.
     */
    private byte[] uncompressedKeyHash;
    
    /**
     * Lazy initialization.
     */
    private byte[] compressedKeyHash;
    
    /**
     * Lazy initialization.
     */
    private String uncompressedKeyHashBase58;
    
    /**
     * Lazy initialization.
     */
    private String compressedKeyHashBase58;
    
    @EqualsAndHashCode.Include
    private final BigInteger secretKey;
    
    // [4, 121, -66, 102, 126, -7, -36, -69, -84, 85, -96, 98, -107, -50, -121, 11, 7, 2, -101, -4, -37, 45, -50, 40, -39, 89, -14, -127, 91, 22, -8, 23, -104, 72, 58, -38, 119, 38, -93, -60, 101, 93, -92, -5, -4, 14, 17, 8, -88, -3, 23, -76, 72, -90, -123, 84, 25, -100, 71, -48, -113, -5, 16, -44, -72]
    // Hex.decodeHex("0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8")
    public static final PublicKeyBytes INVALID_KEY_ONE = new PublicKeyBytes(BigInteger.ONE, new byte[] {4, 121, -66, 102, 126, -7, -36, -69, -84, 85, -96, 98, -107, -50, -121, 11, 7, 2, -101, -4, -37, 45, -50, 40, -39, 89, -14, -127, 91, 22, -8, 23, -104, 72, 58, -38, 119, 38, -93, -60, 101, 93, -92, -5, -4, 14, 17, 8, -88, -3, 23, -76, 72, -90, -123, 84, 25, -100, 71, -48, -113, -5, 16, -44, -72});
    
    public BigInteger getSecretKey() {
        return secretKey;
    }

    public byte[] getCompressed() {
        return compressed;
    }

    public byte[] getUncompressed() {
        return uncompressed;
    }
    
    public boolean isInvalid() {
        return isInvalid(secretKey);
    }

    /**
     * Prevent an IllegalArgumentException
     *
     * @param secret to be checked if invalid
     * @return <strong>true</strong> when secret equals 0000 or 0001
     */
    public static boolean isInvalid(BigInteger secret) {
        return BigInteger.ZERO.equals(secret) || BigInteger.ONE.equals(secret);
    }
    
    public PublicKeyBytes(BigInteger secretKey, byte[] uncompressed) {
        this(secretKey, uncompressed, createCompressedBytes(uncompressed));
    }
    
    public PublicKeyBytes(BigInteger secretKey, byte[] uncompressed, byte[] compressed) {
        this.secretKey = secretKey;
        this.uncompressed = uncompressed;
        this.compressed = compressed;
    }
    
    public static PublicKeyBytes fromPrivate(BigInteger secretKey) {
        ECKey ecKey = ECKey.fromPrivate(secretKey, false);
        return new PublicKeyBytes(ecKey.getPrivKey(), ecKey.getPubKey());
    }
    
    public static byte[] createCompressedBytes(byte[] uncompressed) {
        // add one byte for format sign
        byte[] compressed = new byte[PUBLIC_KEY_COMPRESSED_BYTES];
        
        // copy x
        System.arraycopy(uncompressed, PARITY_BYTES_LENGTH, compressed, PublicKeyBytes.PARITY_BYTES_LENGTH, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        
        boolean even = uncompressed[LAST_Y_COORDINATE_BYTE_INDEX] % 2 == 0;
        
        if (even) {
            compressed[0] = PARITY_COMPRESSED_EVEN;
        } else {
            compressed[0] = PARITY_COMPRESSED_ODD;
        }
        return compressed;
    }

    public byte[] getUncompressedKeyHash() {
        if (uncompressedKeyHash == null) {
            if (USE_SHA256_RIPEMD160_FAST) {
                uncompressedKeyHash = sha256hash160Fast(uncompressed);
            } else {
                uncompressedKeyHash = Utils.sha256hash160(uncompressed);
            }
        }
        return uncompressedKeyHash;
    }

    public byte[] getCompressedKeyHash() {
        if (compressedKeyHash == null) {
            if (USE_SHA256_RIPEMD160_FAST) {
                compressedKeyHash = Utils.sha256hash160(compressed);
            } else {
                compressedKeyHash = sha256hash160Fast(compressed);
            }
        }
        return compressedKeyHash;
    }

    /**
     * Calculates RIPEMD160(SHA256(input)). This is used in Address
     * calculations. Same as {@link Utils#sha256hash160(byte[])} but using
     * {@link DigestUtils}.
     */
    public static byte[] sha256hash160Fast(byte[] input) {
        byte[] sha256 = Hashing.sha256().hashBytes(input).asBytes();
        RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(sha256, 0, sha256.length);
        byte[] out = new byte[HASH160_SIZE];
        digest.doFinal(out, 0);
        return out;
    }

    public String getCompressedKeyHashAsBase58(KeyUtility keyUtility) {
        if (uncompressedKeyHashBase58 == null) {
            uncompressedKeyHashBase58 = keyUtility.toBase58(getCompressedKeyHash());
        }
        return uncompressedKeyHashBase58;
    }

    public String getUncompressedKeyHashAsBase58(KeyUtility keyUtility) {
        if (compressedKeyHashBase58 == null) {
            compressedKeyHashBase58 = keyUtility.toBase58(getUncompressedKeyHash());
        }
        return compressedKeyHashBase58;
    }
}
