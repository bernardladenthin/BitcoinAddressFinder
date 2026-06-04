// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import org.bitcoinj.crypto.ECKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container for the public-key bytes derived from a secp256k1 private key together with their
 * RIPEMD-160 hashes (compressed and uncompressed).
 */
public class PublicKeyBytes {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicKeyBytes.class);

    /** Maximum technically representable 256-bit private key value ({@code 2^256 - 1}). */
    public static final BigInteger MAX_TECHNICALLY_PRIVATE_KEY =
            BigInteger.valueOf(2).pow(Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS).subtract(BigInteger.ONE);

    /** Minimum private key value defined by the secp256k1 specification ({@code 1}). */
    public static final BigInteger MIN_PRIVATE_KEY = BigInteger.ONE;

    // The secp256k1 spec scalars (MIN_VALID_PRIVATE_KEY, MAX_PRIVATE_KEY, …) live in
    // net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants.
    // The OpenCL byte-layout block (BITS_PER_BYTE, SEC_PREFIX_*, ONE_COORDINATE_*,
    // SHA256_*, RIPEMD160_*, CHUNK_OFFSET_*, CHUNK_SIZE_NUM_BYTES) and the derived
    // array-capacity bound (MAXIMUM_CHUNK_ELEMENTS, BIT_COUNT_FOR_MAX_CHUNKS_ARRAY)
    // live in net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants, so
    // configuration POJOs and other leaf-only consumers don't need to depend on this
    // producer-side DTO. The values below stay here because they are public-key DTO
    // shape helpers — they qualify the moved constants by class name.

    /** Byte index of the last byte of the Y coordinate in an uncompressed SEC key. */
    public static final int LAST_Y_COORDINATE_BYTE_INDEX =
            OpenClKernelConstants.SEC_PREFIX_NUM_BYTES + OpenClKernelConstants.TWO_COORDINATES_NUM_BYTES - 1;

    /** Total size of an uncompressed SEC public key in bytes (prefix + X + Y). */
    public static final int PUBLIC_KEY_UNCOMPRESSED_BYTES =
            OpenClKernelConstants.SEC_PREFIX_NUM_BYTES + OpenClKernelConstants.TWO_COORDINATES_NUM_BYTES;
    /** Total size of a compressed SEC public key in bytes (prefix + X). */
    public static final int PUBLIC_KEY_COMPRESSED_BYTES =
            OpenClKernelConstants.SEC_PREFIX_NUM_BYTES + OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES;

    private final byte @NonNull [] uncompressed;
    private final byte @NonNull [] compressed;

    /**
     * Lazy initialization.
     */
    private byte @Nullable [] uncompressedKeyHash;

    /**
     * Lazy initialization.
     */
    private byte @Nullable [] compressedKeyHash;

    /**
     * Lazy initialization.
     */
    private @Nullable String uncompressedKeyHashBase58;

    /**
     * Lazy initialization.
     */
    private @Nullable String compressedKeyHashBase58;

    private final BigInteger secretKey;
    private final PrivateKeyValidator privateKeyValidator;
    private final Hash160 hash160 = new Hash160();

    // [4, 121, -66, 102, 126, -7, -36, -69, -84, 85, -96, 98, -107, -50, -121, 11, 7, 2, -101, -4, -37, 45, -50, 40,
    // -39, 89, -14, -127, 91, 22, -8, 23, -104, 72, 58, -38, 119, 38, -93, -60, 101, 93, -92, -5, -4, 14, 17, 8, -88,
    // -3, 23, -76, 72, -90, -123, 84, 25, -100, 71, -48, -113, -5, 16, -44, -72]
    // Hex.decodeHex("0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8")
    /** Replacement {@link PublicKeyBytes} used in place of the (technically valid but unsupported) secret {@code 1}. */
    public static final PublicKeyBytes INVALID_KEY_ONE = new PublicKeyBytes(BigInteger.ONE, new byte[] {
        4, 121, -66, 102, 126, -7, -36, -69, -84, 85, -96, 98, -107, -50, -121, 11, 7, 2, -101, -4, -37, 45, -50, 40,
        -39, 89, -14, -127, 91, 22, -8, 23, -104, 72, 58, -38, 119, 38, -93, -60, 101, 93, -92, -5, -4, 14, 17, 8, -88,
        -3, 23, -76, 72, -90, -123, 84, 25, -100, 71, -48, -113, -5, 16, -44, -72
    });

    /**
     * Returns the private-key value backing this instance.
     *
     * @return the private-key value backing this instance
     */
    public BigInteger getSecretKey() {
        return secretKey;
    }

    /**
     * Returns the compressed SEC public-key bytes.
     *
     * @return the compressed SEC public-key bytes
     */
    public byte[] getCompressed() {
        return compressed;
    }

    /**
     * Returns the uncompressed SEC public-key bytes.
     *
     * @return the uncompressed SEC public-key bytes
     */
    public byte[] getUncompressed() {
        return uncompressed;
    }

    /**
     * Indicates whether the underlying secret is outside the valid secp256k1 private-key range.
     *
     * @return {@code true} if the underlying secret is outside the valid secp256k1 private-key range
     */
    public boolean isOutsidePrivateKeyRange() {
        return privateKeyValidator.isOutsidePrivateKeyRange(secretKey);
    }

    /**
     * Convenience constructor that derives the compressed key from {@code uncompressed}.
     *
     * @param secretKey    the underlying secret
     * @param uncompressed the uncompressed SEC public-key bytes
     */
    public PublicKeyBytes(BigInteger secretKey, byte[] uncompressed) {
        this(secretKey, uncompressed, createCompressedBytes(uncompressed));
    }

    /**
     * Constructor accepting precomputed RIPEMD-160 hashes (typically from GPU output).
     *
     * @param secretKey            the underlying secret
     * @param uncompressed         the uncompressed SEC public-key bytes
     * @param uncompressedKeyHash  the RIPEMD-160 hash of the uncompressed key
     * @param compressedKeyHash    the RIPEMD-160 hash of the compressed key
     */
    public PublicKeyBytes(
            BigInteger secretKey, byte[] uncompressed, byte[] uncompressedKeyHash, byte[] compressedKeyHash) {
        this(secretKey, uncompressed, createCompressedBytes(uncompressed), uncompressedKeyHash, compressedKeyHash);
    }

    /**
     * Canonical constructor.
     *
     * @param secretKey    the underlying secret
     * @param uncompressed the uncompressed SEC public-key bytes
     * @param compressed   the compressed SEC public-key bytes
     */
    public PublicKeyBytes(BigInteger secretKey, byte @NonNull [] uncompressed, byte @NonNull [] compressed) {
        this.secretKey = secretKey;
        this.uncompressed = uncompressed;
        this.compressed = compressed;
        this.privateKeyValidator = new PrivateKeyValidator();
    }

    /**
     * Canonical constructor with precomputed RIPEMD-160 hashes.
     *
     * @param secretKey            the underlying secret
     * @param uncompressed         the uncompressed SEC public-key bytes
     * @param compressed           the compressed SEC public-key bytes
     * @param uncompressedKeyHash  the precomputed RIPEMD-160 hash of {@code uncompressed} (may be {@code null})
     * @param compressedKeyHash    the precomputed RIPEMD-160 hash of {@code compressed} (may be {@code null})
     */
    public PublicKeyBytes(
            BigInteger secretKey,
            byte @NonNull [] uncompressed,
            byte @NonNull [] compressed,
            byte @Nullable [] uncompressedKeyHash,
            byte @Nullable [] compressedKeyHash) {
        this.secretKey = secretKey;
        this.uncompressed = uncompressed;
        this.compressed = compressed;
        this.uncompressedKeyHash = uncompressedKeyHash;
        this.compressedKeyHash = compressedKeyHash;
        this.privateKeyValidator = new PrivateKeyValidator();
    }

    /**
     * Derives a {@link PublicKeyBytes} instance from a private-key {@link BigInteger} using bitcoinj.
     *
     * @param secretKey the private-key value
     * @return the derived {@link PublicKeyBytes}
     */
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
    public static byte @NonNull [] createCompressedBytes(byte @NonNull [] uncompressed) {
        // add one byte for format sign
        byte[] compressed = new byte[PUBLIC_KEY_COMPRESSED_BYTES];
        // parity
        boolean even = uncompressed[LAST_Y_COORDINATE_BYTE_INDEX] % 2 == 0;
        if (even) {
            compressed[0] = OpenClKernelConstants.SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y;
        } else {
            compressed[0] = OpenClKernelConstants.SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y;
        }
        // x
        System.arraycopy(
                uncompressed,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES,
                compressed,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES,
                OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES);
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
    public static byte @NonNull [] assembleUncompressedPublicKey(byte @NonNull [] x, byte @NonNull [] y) {
        byte[] uncompressed = new byte[PublicKeyBytes.PUBLIC_KEY_UNCOMPRESSED_BYTES];
        // prefix
        uncompressed[0] = OpenClKernelConstants.SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT;
        // x
        System.arraycopy(x, 0, uncompressed,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES,
                OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES);
        // y
        System.arraycopy(y, 0, uncompressed,
                OpenClKernelConstants.SEC_PREFIX_NUM_BYTES + OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES,
                OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES);
        return uncompressed;
    }

    /**
     * Checks whether all coordinate bytes (excluding the prefix byte) are zero.
     * <p>
     * This is used to detect critical failures such as a broken OpenCL kernel execution or invalid GPU output.
     * </p>
     *
     * @param uncompressed the full uncompressed public key byte array (prefix + X + Y)
     * @return true if all coordinate bytes are zero, false otherwise
     */
    public static boolean isAllCoordinateBytesZero(byte[] uncompressed) {
        for (int i = OpenClKernelConstants.SEC_PREFIX_NUM_BYTES; i < uncompressed.length; i++) {
            if (uncompressed[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the (lazily computed) RIPEMD-160 hash of the uncompressed key.
     *
     * @return the (lazily computed) RIPEMD-160 hash of the uncompressed key
     */
    public byte @NonNull [] getUncompressedKeyHash() {
        if (uncompressedKeyHash == null) {
            uncompressedKeyHash = hash160.hash(uncompressed);
        }
        return uncompressedKeyHash;
    }

    /**
     * Returns the (lazily computed) RIPEMD-160 hash of the compressed key.
     *
     * @return the (lazily computed) RIPEMD-160 hash of the compressed key
     */
    public byte @NonNull [] getCompressedKeyHash() {
        if (compressedKeyHash == null) {
            compressedKeyHash = hash160.hash(compressed);
        }
        return compressedKeyHash;
    }

    /**
     * Returns the Base58 representation of the compressed-key hash.
     *
     * @param keyUtility the key utility used for the Base58 encoding
     * @return the Base58 representation of the compressed-key hash
     */
    public @NonNull String getCompressedKeyHashAsBase58(@NonNull KeyUtility keyUtility) {
        if (uncompressedKeyHashBase58 == null) {
            uncompressedKeyHashBase58 = keyUtility.toBase58(getCompressedKeyHash());
        }
        return uncompressedKeyHashBase58;
    }

    /**
     * Returns the Base58 representation of the uncompressed-key hash.
     *
     * @param keyUtility the key utility used for the Base58 encoding
     * @return the Base58 representation of the uncompressed-key hash
     */
    public @NonNull String getUncompressedKeyHashAsBase58(@NonNull KeyUtility keyUtility) {
        if (compressedKeyHashBase58 == null) {
            compressedKeyHashBase58 = keyUtility.toBase58(getUncompressedKeyHash());
        }
        return compressedKeyHashBase58;
    }

    /**
     * Runs a self-consistency check by recomputing the public-key hashes via bitcoinj.
     * Mismatches are reported via the class-level SLF4J logger at ERROR level.
     *
     * @return {@code true} if the precomputed hashes match the freshly computed ones
     */
    public boolean runtimePublicKeyCalculationCheck() {
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
            LOGGER.error("fromPrivateUncompressed.getPubKeyHash() != hash160Uncompressed");
            LOGGER.error("getSecretKey: " + getSecretKey());
            LOGGER.error(
                    "pubKeyUncompressed: " + org.apache.commons.codec.binary.Hex.encodeHexString(getUncompressed()));
            LOGGER.error("pubKeyUncompressedFromEcKey: "
                    + org.apache.commons.codec.binary.Hex.encodeHexString(pubKeyUncompressedFromEcKey));
            LOGGER.error(
                    "hash160Uncompressed: " + org.apache.commons.codec.binary.Hex.encodeHexString(hash160Uncompressed));
            LOGGER.error("hash160UncompressedFromEcKey: "
                    + org.apache.commons.codec.binary.Hex.encodeHexString(hash160UncompressedFromEcKey));
            isValid = false;
        }

        if (!Arrays.equals(hash160CompressedFromEcKey, hash160Compressed)) {
            LOGGER.error("fromPrivateCompressed.getPubKeyHash() != hash160Compressed");
            LOGGER.error("getSecretKey: " + getSecretKey());
            LOGGER.error("pubKeyCompressed: " + org.apache.commons.codec.binary.Hex.encodeHexString(getCompressed()));
            LOGGER.error("pubKeyCompressedFromEcKey: "
                    + org.apache.commons.codec.binary.Hex.encodeHexString(pubKeyCompressedFromEcKey));
            LOGGER.error(
                    "hash160Compressed: " + org.apache.commons.codec.binary.Hex.encodeHexString(hash160Compressed));
            LOGGER.error("hash160CompressedFromEcKey: "
                    + org.apache.commons.codec.binary.Hex.encodeHexString(hash160CompressedFromEcKey));
            isValid = false;
        }
        return isValid;
    }

    // <editor-fold defaultstate="collapsed" desc="Overrides: hashCode, equals, toString">
    // Overrides for hashCode(), equals(Object), and toString() are implemented based ONLY
    // on the secretKey field, which uniquely identifies the PublicKeyBytes instance:
    //   - hashCode()       — generated using a prime multiplier and secretKey hash
    //   - equals(Object)   — considers two instances equal if their secretKey values are equal
    //   - toString()       — returns a string including the secretKey for debugging/logging
    // This design ensures that objects with the same secretKey are treated as equal,
    // regardless of other internal state (e.g., precomputed hash representations or
    // compressed keys).

    // generated, based on secretKey only!
    @Override
    public int hashCode() {
        return 73 * 3 + Objects.hashCode(this.secretKey);
    }

    // generated, based on secretKey only!
    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PublicKeyBytes other)) {
            return false;
        }
        return Objects.equals(this.secretKey, other.secretKey);
    }

    // generated, based on secretKey only!
    @Override
    public String toString() {
        return "PublicKeyBytes{" + "secretKey=" + secretKey + '}';
    }
    // </editor-fold>
}
