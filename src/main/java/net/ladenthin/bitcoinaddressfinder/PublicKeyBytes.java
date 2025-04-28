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
import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.internal.CryptoUtils;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.slf4j.Logger;

public class PublicKeyBytes {
    
    /**
     * Use {@link com.​google.​common.​hash.Hashing} and
     * {@link org.bouncycastle.crypto.digests.RIPEMD160Digest} instead
     * {@link org.bitcoinj.core.Utils#sha256hash160(byte[])}.
     */
    public static final boolean USE_SHA256_RIPEMD160_FAST = true;

    public static final BigInteger MAX_TECHNICALLY_PRIVATE_KEY = BigInteger.valueOf(2).pow(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS).subtract(BigInteger.ONE);

    public static final BigInteger MIN_PRIVATE_KEY = BigInteger.ONE;
    
    /**
     * The minimum valid private key that can be safely used in this implementation.
     * <p>
     * While the secp256k1 specification allows private keys in the range
     * {@code [0x1, MAX_PRIVATE_KEY]} (i.e., including {@code 1}), this implementation
     * deliberately excludes {@code 1} for practical safety and compatibility reasons.
     * The constant {@code MIN_VALID_PRIVATE_KEY} is therefore defined as {@code 2}.
     * </p>
     * <p>
     * This avoids edge cases or known issues in downstream libraries or certain 
     * ECKey handling implementations (e.g., {@link org.bitcoinj.crypto.ECKey#fromPrivate(BigInteger, boolean)})
     * that may throw exceptions or produce inconsistent results for {@code 1}.
     * </p>
     *
     * @see #MAX_PRIVATE_KEY
     * @see org.bitcoinj.crypto.ECKey#fromPrivate(BigInteger, boolean)
     */
    public static final BigInteger MIN_VALID_PRIVATE_KEY = BigInteger.valueOf(2);

    /**
     * The maximum valid private key according to the secp256k1 specification.
     * <p>
     * The valid range for secp256k1 private keys is technically defined as 
     * {@code 0x1} to {@code 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141} (inclusive).
     * This value represents the order of the secp256k1 curve (also called the group order).
     * </p>
     * <p>
     * However, this implementation deliberately defines {@link PublicKeyBytes#MIN_VALID_PRIVATE_KEY} as {@code 0x2},
     * excluding {@code 0x1} due to its potential to cause inconsistencies or exceptions in certain cryptographic
     * libraries such as {@link org.bitcoinj.crypto.ECKey#fromPrivate(BigInteger, boolean)}.
     * </p>
     *
     * @see #MIN_VALID_PRIVATE_KEY
     * @see org.bitcoinj.crypto.ECKey
     */
    public static final BigInteger MAX_PRIVATE_KEY = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);

    /**
     * I choose a random value for a replacement.
     */
    public static final BigInteger INVALID_PRIVATE_KEY_REPLACEMENT = BigInteger.valueOf(2);

    public static final int PRIVATE_KEY_MAX_NUM_BITS = 256;
    public static final int BITS_PER_BYTE = 8;
    public static final int PRIVATE_KEY_MAX_NUM_BYTES = PRIVATE_KEY_MAX_NUM_BITS / BITS_PER_BYTE;

    public static final int ONE_COORDINATE_NUM_BYTES = 32;
    public static final int TWO_COORDINATES_NUM_BYTES = ONE_COORDINATE_NUM_BYTES * 2;
    
    /**
     * Computes the maximum permissible length for an array intended to store pairs of coordinates within a 32-bit system.
     * This constant represents the upper limit on array length, factoring in the memory constraint imposed by the maximum
     * integer value addressable in Java ({@link Integer#MAX_VALUE}) and the storage requirement for two coordinates.
     * <p>
     * The calculation divides {@link Integer#MAX_VALUE} by the number of bytes needed to store two coordinates,
     * as defined by {@link PublicKeyBytes#TWO_COORDINATES_NUM_BYTES}, ensuring the array's indexing does not surpass
     * Java's maximum allowable array length.
     * </p>
     */
    public static final int MAXIMUM_TWO_COORDINATES_ARRAY_LENGTH = (int)(Integer.MAX_VALUE / PublicKeyBytes.TWO_COORDINATES_NUM_BYTES);

    /**
     * Determines the minimum number of bits required to address the maximum array length for storing coordinate pairs.
     * This value is crucial for efficiently allocating memory without exceeding the 32-bit system's limitations.
     * <p>
     * The calculation employs a bit manipulation strategy to find the exponent of the nearest superior power of 2
     * capable of accommodating the maximum array length. By decrementing the maximum array length by 1 and
     * calculating 32 minus the count of leading zeros in the decremented value, we obtain the closest superior power of 2.
     * This technique, derived from a common bit manipulation trick (source: https://stackoverflow.com/questions/5242533/fast-way-to-find-exponent-of-nearest-superior-power-of-2),
     * ensures the calculated bit count represents the smallest possible number that can address all potential array indices
     * without breaching the 32-bit address space limitation.
     * </p>
     */
    public static final int BIT_COUNT_FOR_MAX_COORDINATE_PAIRS_ARRAY = PublicKeyBytes.MAXIMUM_TWO_COORDINATES_ARRAY_LENGTH == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(PublicKeyBytes.MAXIMUM_TWO_COORDINATES_ARRAY_LENGTH - 1) - 1;

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
    
    public boolean isOutsidePrivateKeyRange() {
        return KeyUtility.isOutsidePrivateKeyRange(secretKey);
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
    
    /**
     * Creates a compressed public key from an uncompressed public key byte array in SEC format.
     * <p>
     * The method extracts the X coordinate and calculates the appropriate compression prefix
     * based on the parity (evenness) of the Y coordinate.
     * <p>
     * The resulting compressed key is structured as:
     * <ul>
     *   <li>1 byte prefix: {@code 0x02} if Y is even, {@code 0x03} if Y is odd</li>
     *   <li>32 bytes: X coordinate (Big-Endian) (MSB-first)</li>
     * </ul>
     * <p>
     * This format follows the Bitcoin and general ECC compressed public key convention,
     * where the full Y coordinate is not transmitted, but can later be recovered.
     *
     * @param uncompressed the full uncompressed public key byte array in SEC format ({@code 04 || X || Y})
     * @return the compressed public key byte array in SEC format
     */
    public static byte[] createCompressedBytes(byte[] uncompressed) {
        // add one byte for format sign
        byte[] compressed = new byte[PUBLIC_KEY_COMPRESSED_BYTES];
        // parity
        boolean even = uncompressed[LAST_Y_COORDINATE_BYTE_INDEX] % 2 == 0;
        if (even) {
            compressed[0] = PARITY_COMPRESSED_EVEN;
        } else {
            compressed[0] = PARITY_COMPRESSED_ODD;
        }
        // x
        System.arraycopy(uncompressed, PARITY_BYTES_LENGTH, compressed, PublicKeyBytes.PARITY_BYTES_LENGTH, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        return compressed;
    }
    
    /**
     * Assembles an uncompressed public key in SEC (Standards for Efficient Cryptography) format
     * from the X and Y coordinate byte arrays.
     * <p>
     * The method expects both the X and Y coordinates to be in Big-Endian (MSB-first) order,
     * which is the standard byte ordering for Bitcoin public keys.
     * <p>
     * The resulting byte array is structured as:
     * <ul>
     *   <li>1 byte prefix: {@code 0x04} indicating an uncompressed public key</li>
     *   <li>32 bytes: X coordinate (Big-Endian) (MSB-first)</li>
     *   <li>32 bytes: Y coordinate (Big-Endian) (MSB-first)</li>
     * </ul>
     * <p>
     * This format complies with Bitcoin, Ethereum, and general ECC usage where
     * uncompressed public keys are transmitted as {@code 04 || X || Y}.
     *
     * @param x the X coordinate in Big-Endian (MSB-first) order
     * @param y the Y coordinate in Big-Endian (MSB-first) order
     * @return the assembled uncompressed public key byte array in SEC format
     */
    public static byte[] assembleUncompressedPublicKey(byte[] x, byte[] y) {
        byte[] uncompressed = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES];
        // parity
        uncompressed[0] = PublicKeyBytes.PARITY_UNCOMPRESSED;
        // x
        System.arraycopy(x, 0, uncompressed, PublicKeyBytes.PARITY_BYTES_LENGTH, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        // y
        System.arraycopy(y, 0, uncompressed, PublicKeyBytes.PARITY_BYTES_LENGTH + PublicKeyBytes.ONE_COORDINATE_NUM_BYTES, PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
        return uncompressed;
    }

   /**
    * Checks whether all coordinate bytes (excluding the parity byte) are zero.
    * <p>
    * This is used to detect critical failures such as a broken OpenCL kernel execution or invalid GPU output.
    * </p>
    * 
    * @param uncompressed the full uncompressed public key byte array (parity + X + Y)
    * @return true if all coordinate bytes are zero, false otherwise
    */
   public static boolean isAllCoordinateBytesZero(byte[] uncompressed) {
       for (int i = PublicKeyBytes.PARITY_BYTES_LENGTH; i < uncompressed.length; i++) {
           if (uncompressed[i] != 0) {
               return false;
           }
       }
       return true;
   }
   
   private static byte[] calculateHash160(byte[] input) {
        if (USE_SHA256_RIPEMD160_FAST) {
            return sha256hash160Fast(input);
        } else {
            return CryptoUtils.sha256hash160(input);
        }
    }

    public byte[] getUncompressedKeyHash() {
        if (uncompressedKeyHash == null) {
            uncompressedKeyHash = calculateHash160(uncompressed);
        }
        return uncompressedKeyHash;
    }

    public byte[] getCompressedKeyHash() {
        if (compressedKeyHash == null) {
            compressedKeyHash = calculateHash160(compressed);
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
    
    public boolean runtimePublicKeyCalculationCheck(Logger logger) {
        byte[] hash160Uncompressed = getUncompressedKeyHash();
        byte[] hash160Compressed = getCompressedKeyHash();
        ECKey fromPrivateUncompressed = ECKey.fromPrivate(getSecretKey(), false);
        ECKey fromPrivateCompressed = ECKey.fromPrivate(getSecretKey(), true);
        
        final byte[] pubKeyUncompressedFromEcKey = fromPrivateUncompressed.getPubKey();
        final byte[] pubKeyCompressedFromEcKey = fromPrivateCompressed.getPubKey();
        
        final byte[] hash160UncompressedFromEcKey = fromPrivateUncompressed.getPubKeyHash();
        final byte[] hash160CompressedFromEcKey = fromPrivateCompressed.getPubKeyHash();
        
        boolean isValid = true;
        if (!Arrays.equals(hash160UncompressedFromEcKey, hash160Uncompressed)) {
            logger.error("fromPrivateUncompressed.getPubKeyHash() != hash160Uncompressed");
            logger.error("getSecretKey: " + getSecretKey());
            logger.error("pubKeyUncompressed: " + org.apache.commons.codec.binary.Hex.encodeHexString(getUncompressed()));
            logger.error("pubKeyUncompressedFromEcKey: " + org.apache.commons.codec.binary.Hex.encodeHexString(pubKeyUncompressedFromEcKey));
            logger.error("hash160Uncompressed: " + org.apache.commons.codec.binary.Hex.encodeHexString(hash160Uncompressed));
            logger.error("hash160UncompressedFromEcKey: " + org.apache.commons.codec.binary.Hex.encodeHexString(hash160UncompressedFromEcKey));
            isValid = false;
        }
        
        if (!Arrays.equals(hash160CompressedFromEcKey, hash160Compressed)) {
            logger.error("fromPrivateCompressed.getPubKeyHash() != hash160Compressed");
            logger.error("getSecretKey: " + getSecretKey());
            logger.error("pubKeyCompressed: " + org.apache.commons.codec.binary.Hex.encodeHexString(getCompressed()));
            logger.error("pubKeyCompressedFromEcKey: " + org.apache.commons.codec.binary.Hex.encodeHexString(pubKeyCompressedFromEcKey));
            logger.error("hash160Compressed: " + org.apache.commons.codec.binary.Hex.encodeHexString(hash160Compressed));
            logger.error("hash160CompressedFromEcKey: " + org.apache.commons.codec.binary.Hex.encodeHexString(hash160CompressedFromEcKey));
            isValid = false;
        }
        return isValid;
    }

    // <editor-fold defaultstate="collapsed" desc="Overrides: hashCode, equals, toString">
    /*
     * Overrides for {@code hashCode()}, {@code equals(Object)}, and {@code toString()}.
     * <p>
     * These methods are implemented based **only** on the {@code secretKey} field, 
     * which uniquely identifies the {@code PublicKeyBytes} instance.
     * <ul>
     *     <li>{@code hashCode()} – Generated using a prime multiplier and {@code secretKey} hash.</li>
     *     <li>{@code equals(Object)} – Considers two instances equal if their {@code secretKey} values are equal.</li>
     *     <li>{@code toString()} – Returns a string including the {@code secretKey} for debugging/logging.</li>
     * </ul>
     * <p>
     * This design ensures that objects with the same {@code secretKey} are treated as equal,
     * regardless of other internal state (e.g., precomputed hash representations or compressed keys).
     */
    
    // generated, based on secretKey only!
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 73 * hash + Objects.hashCode(this.secretKey);
        return hash;
    }

    // generated, based on secretKey only!
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PublicKeyBytes other = (PublicKeyBytes) obj;
        return Objects.equals(this.secretKey, other.secretKey);
    }

    // generated, based on secretKey only!
    @Override
    public String toString() {
        return "PublicKeyBytes{" + "secretKey=" + secretKey + '}';
    }
    // </editor-fold>
}
