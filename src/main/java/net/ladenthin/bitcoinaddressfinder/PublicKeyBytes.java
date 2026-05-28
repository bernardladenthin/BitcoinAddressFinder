// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
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
            BigInteger.valueOf(2).pow(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS).subtract(BigInteger.ONE);

    /** Minimum private key value defined by the secp256k1 specification ({@code 1}). */
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
    public static final BigInteger MIN_VALID_PRIVATE_KEY = BigInteger.TWO;
    /** Uppercase hexadecimal representation of {@link #MIN_VALID_PRIVATE_KEY}. */
    public static final String MIN_VALID_PRIVATE_KEY_HEX =
            MIN_VALID_PRIVATE_KEY.toString(BitHelper.RADIX_HEX).toUpperCase();

    /**
     * Uppercase hexadecimal representation of the secp256k1 group order
     * (the maximum valid private key).
     * <p>
     * This is the public curve parameter {@code n} from
     * <a href="https://www.secg.org/sec2-v2.pdf">SEC 2 v2</a>, &sect;2.4.1
     * ("Recommended Parameters secp256k1"). It is the same value in every
     * secp256k1 implementation (bitcoinj, libsecp256k1, openssl, &hellip;)
     * and is used as the upper bound for valid-private-key range checks
     * &mdash; it is not a key, signing material, or secret.
     */
    public static final String MAX_PRIVATE_KEY_HEX = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141";

    /**
     * The maximum valid private key according to the secp256k1 specification.
     * <p>
     * The valid range for secp256k1 private keys is technically defined as
     * {@code 0x1} to {@link #MAX_PRIVATE_KEY_HEX} (inclusive).
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
    public static final BigInteger MAX_PRIVATE_KEY = new BigInteger(MAX_PRIVATE_KEY_HEX, BitHelper.RADIX_HEX);

    /**
     * I choose a random value for a replacement.
     */
    public static final BigInteger INVALID_PRIVATE_KEY_REPLACEMENT = BigInteger.valueOf(2);

    // ==== BEGIN: SYNCHRONIZED WITH OpenCL CONSTANTS (Do not modify without updating OpenCL) ====
    /** Number of bits in a byte. */
    public static final int BITS_PER_BYTE = 8;
    /** Number of {@code u32} values per kernel word. */
    public static final int U32_PER_WORD = 1;
    /** Number of bytes per {@code u32} word. */
    public static final int U32_NUM_BYTES = 4;
    /** Shift in bits to move a byte to the MSB of a {@code u32}. */
    public static final int BYTE_SHIFT_TO_U32_MSB = 24;

    // === private key ===
    /** Maximum number of bits in a secp256k1 private key. */
    public static final int PRIVATE_KEY_MAX_NUM_BITS = 256;
    /** Maximum number of bytes in a secp256k1 private key. */
    public static final int PRIVATE_KEY_MAX_NUM_BYTES = PRIVATE_KEY_MAX_NUM_BITS / BITS_PER_BYTE; // 32
    /** Maximum number of {@code u32} words in a secp256k1 private key. */
    public static final int PRIVATE_KEY_MAX_NUM_WORDS = PRIVATE_KEY_MAX_NUM_BYTES / U32_NUM_BYTES; // 8

    // === SEC format prefixes ===
    /** Number of bits in the SEC format prefix. */
    public static final int SEC_PREFIX_NUM_BITS = BITS_PER_BYTE;
    /** Number of bytes in the SEC format prefix. */
    public static final int SEC_PREFIX_NUM_BYTES = 1;
    /** Number of {@code u32} words in the SEC format prefix. */
    public static final int SEC_PREFIX_NUM_WORDS = U32_PER_WORD;
    /** SEC prefix byte identifying an uncompressed ECDSA point ({@code 0x04}). */
    public static final int SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT = 0x04;
    /** SEC prefix byte identifying a compressed ECDSA point with even {@code y} ({@code 0x02}). */
    public static final int SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y = 0x02;
    /** SEC prefix byte identifying a compressed ECDSA point with odd {@code y} ({@code 0x03}). */
    public static final int SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y = 0x03;

    // ==== SEC format prefixes shifted versions (for use in u32[0] with MSB-first layout) ====
    /** Number of bytes per SEC prefix when written as the MSB of a {@code u32}. */
    public static final int SEC_PREFIX_SHIFTED_NUM_BYTES = U32_NUM_BYTES;
    /** {@link #SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT} shifted to the MSB of a {@code u32}. */
    public static final int SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT_SHIFTED =
            (SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT << BYTE_SHIFT_TO_U32_MSB);
    /** {@link #SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y} shifted to the MSB of a {@code u32}. */
    public static final int SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y_SHIFTED =
            (SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y << BYTE_SHIFT_TO_U32_MSB);
    /** {@link #SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y} shifted to the MSB of a {@code u32}. */
    public static final int SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y_SHIFTED =
            (SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y << BYTE_SHIFT_TO_U32_MSB);

    // ==== x, y coordinate length ====
    /** Number of bits per ECDSA coordinate. */
    public static final int ONE_COORDINATE_NUM_BITS = 256;
    /** Number of bytes per ECDSA coordinate. */
    public static final int ONE_COORDINATE_NUM_BYTES = ONE_COORDINATE_NUM_BITS / BITS_PER_BYTE; // 32
    /** Total number of bits in the two coordinates of an uncompressed point. */
    public static final int TWO_COORDINATES_NUM_BITS = ONE_COORDINATE_NUM_BITS * 2; // 512
    /** Total number of bytes in the two coordinates of an uncompressed point. */
    public static final int TWO_COORDINATES_NUM_BYTES = ONE_COORDINATE_NUM_BYTES * 2; // 64
    /** Number of {@code u32} words per coordinate. */
    public static final int ONE_COORDINATE_NUM_WORDS = ONE_COORDINATE_NUM_BYTES / U32_NUM_BYTES; // 8
    /** Number of {@code u32} words in the two coordinates of an uncompressed point. */
    public static final int TWO_COORDINATE_NUM_WORDS = ONE_COORDINATE_NUM_WORDS * 2; // 16

    // ==== public key length ====
    /** Number of bits in an uncompressed SEC public key. */
    public static final int SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BITS =
            SEC_PREFIX_NUM_BITS + TWO_COORDINATES_NUM_BITS; // 520
    /** Number of bytes in an uncompressed SEC public key. */
    public static final int SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BYTES =
            SEC_PREFIX_NUM_BYTES + TWO_COORDINATES_NUM_BYTES; // 65
    /** Number of {@code u32} words in an uncompressed SEC public key. */
    public static final int SEC_PUBLIC_KEY_UNCOMPRESSED_WORDS = SEC_PREFIX_NUM_WORDS + TWO_COORDINATE_NUM_WORDS; // 17
    /** Number of bits in a compressed SEC public key. */
    public static final int SEC_PUBLIC_KEY_COMPRESSED_NUM_BITS = SEC_PREFIX_NUM_BITS + ONE_COORDINATE_NUM_BITS; // 264
    /** Number of bytes in a compressed SEC public key. */
    public static final int SEC_PUBLIC_KEY_COMPRESSED_NUM_BYTES = SEC_PREFIX_NUM_BYTES + ONE_COORDINATE_NUM_BYTES; // 33
    /** Number of {@code u32} words in a compressed SEC public key. */
    public static final int SEC_PUBLIC_KEY_COMPRESSED_WORDS = SEC_PREFIX_NUM_WORDS + ONE_COORDINATE_NUM_WORDS; // 9

    // === Hash sizes in bytes ===
    /** SHA-256 input block size in bits. */
    public static final int SHA256_INPUT_BLOCK_SIZE_BITS = 512;
    /** SHA-256 input block size in bytes. */
    public static final int SHA256_INPUT_BLOCK_SIZE_BYTES = SHA256_INPUT_BLOCK_SIZE_BITS / BITS_PER_BYTE; // 64
    /** SHA-256 input block size in {@code u32} words. */
    public static final int SHA256_INPUT_BLOCK_SIZE_WORDS = SHA256_INPUT_BLOCK_SIZE_BYTES / U32_NUM_BYTES; // 16
    /** RIPEMD-160 input block size in bits. */
    public static final int RIPEMD160_INPUT_BLOCK_SIZE_BITS = 512;
    /** RIPEMD-160 input block size in bytes. */
    public static final int RIPEMD160_INPUT_BLOCK_SIZE_BYTES = RIPEMD160_INPUT_BLOCK_SIZE_BITS / BITS_PER_BYTE; // 64
    /** RIPEMD-160 input block size in {@code u32} words. */
    public static final int RIPEMD160_INPUT_BLOCK_SIZE_WORDS = RIPEMD160_INPUT_BLOCK_SIZE_BYTES / U32_NUM_BYTES; // 16
    /** SHA-256 output size in bits. */
    public static final int SHA256_HASH_NUM_BITS = 256;
    /** SHA-256 output size in bytes. */
    public static final int SHA256_HASH_NUM_BYTES = SHA256_HASH_NUM_BITS / BITS_PER_BYTE; // 32
    /** SHA-256 output size in {@code u32} words. */
    public static final int SHA256_HASH_NUM_WORDS = SHA256_HASH_NUM_BYTES / U32_NUM_BYTES; // 8
    /** RIPEMD-160 output size in bits. */
    public static final int RIPEMD160_HASH_NUM_BITS = 160;
    /** RIPEMD-160 output size in bytes. */
    public static final int RIPEMD160_HASH_NUM_BYTES = RIPEMD160_HASH_NUM_BITS / BITS_PER_BYTE; // 20
    /** RIPEMD-160 output size in {@code u32} words. */
    public static final int RIPEMD160_HASH_NUM_WORDS = RIPEMD160_HASH_NUM_BYTES / U32_NUM_BYTES; // 5

    /** Number of SHA-256 input blocks required for the uncompressed SEC public key. */
    public static final int SHA256_INPUT_BLOCKS_FOR_UNCOMPRESSED_SEC = 2;
    /** Total SHA-256 input bits for the uncompressed SEC public key. */
    public static final int SHA256_INPUT_TOTAL_BITS_UNCOMPRESSED =
            SHA256_INPUT_BLOCKS_FOR_UNCOMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_BITS; // 1024
    /** Total SHA-256 input bytes for the uncompressed SEC public key. */
    public static final int SHA256_INPUT_TOTAL_BYTES_UNCOMPRESSED =
            SHA256_INPUT_BLOCKS_FOR_UNCOMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_BYTES; // 128
    /** Total SHA-256 input words for the uncompressed SEC public key. */
    public static final int SHA256_INPUT_TOTAL_WORDS_UNCOMPRESSED =
            SHA256_INPUT_BLOCKS_FOR_UNCOMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_WORDS; // 32

    /** Number of SHA-256 input blocks required for the compressed SEC public key. */
    public static final int SHA256_INPUT_BLOCKS_FOR_COMPRESSED_SEC = 1;
    /** Total SHA-256 input bits for the compressed SEC public key. */
    public static final int SHA256_INPUT_TOTAL_BITS_COMPRESSED =
            SHA256_INPUT_BLOCKS_FOR_COMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_BITS; // 512
    /** Total SHA-256 input bytes for the compressed SEC public key. */
    public static final int SHA256_INPUT_TOTAL_BYTES_COMPRESSED =
            SHA256_INPUT_BLOCKS_FOR_COMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_BYTES; // 64
    /** Total SHA-256 input words for the compressed SEC public key. */
    public static final int SHA256_INPUT_TOTAL_WORDS_COMPRESSED =
            SHA256_INPUT_BLOCKS_FOR_COMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_WORDS; // 16

    // ==== Individual Chunk Sizes (Bytes in Java) ====
    /** Size of the X-coordinate slot inside an OpenCL result chunk (in bytes). */
    public static final int CHUNK_SIZE_00_NUM_BYTES_BIG_ENDIAN_X = ONE_COORDINATE_NUM_BYTES;
    /** Size of the Y-coordinate slot inside an OpenCL result chunk (in bytes). */
    public static final int CHUNK_SIZE_01_NUM_BYTES_BIG_ENDIAN_Y = ONE_COORDINATE_NUM_BYTES;
    /** Size of the uncompressed-key RIPEMD-160 slot inside an OpenCL result chunk (in bytes). */
    public static final int CHUNK_SIZE_10_NUM_BYTES_RIPEMD160_UNCOMPRESSED = RIPEMD160_HASH_NUM_BYTES;
    /** Size of the compressed-key RIPEMD-160 slot inside an OpenCL result chunk (in bytes). */
    public static final int CHUNK_SIZE_11_NUM_BYTES_RIPEMD160_COMPRESSED = RIPEMD160_HASH_NUM_BYTES;

    // ==== Offsets Within a Chunk ====
    /** Byte offset of the X coordinate inside a chunk. */
    public static final int CHUNK_OFFSET_00_NUM_BYTES_BIG_ENDIAN_X = 0;
    /** Byte offset of the Y coordinate inside a chunk. */
    public static final int CHUNK_OFFSET_01_NUM_BYTES_BIG_ENDIAN_Y =
            CHUNK_OFFSET_00_NUM_BYTES_BIG_ENDIAN_X + CHUNK_SIZE_00_NUM_BYTES_BIG_ENDIAN_X;
    /** Byte offset of the uncompressed-key RIPEMD-160 inside a chunk. */
    public static final int CHUNK_OFFSET_10_NUM_BYTES_RIPEMD160_UNCOMPRESSED =
            CHUNK_OFFSET_01_NUM_BYTES_BIG_ENDIAN_Y + CHUNK_SIZE_01_NUM_BYTES_BIG_ENDIAN_Y;
    /** Byte offset of the compressed-key RIPEMD-160 inside a chunk. */
    public static final int CHUNK_OFFSET_11_NUM_BYTES_RIPEMD160_COMPRESSED =
            CHUNK_OFFSET_10_NUM_BYTES_RIPEMD160_UNCOMPRESSED + CHUNK_SIZE_10_NUM_BYTES_RIPEMD160_UNCOMPRESSED;
    /** Byte offset just past the end of a chunk (equals the chunk size). */
    public static final int CHUNK_OFFSET_99_NUM_BYTES_END_OF_CHUNK =
            CHUNK_OFFSET_11_NUM_BYTES_RIPEMD160_COMPRESSED + CHUNK_SIZE_11_NUM_BYTES_RIPEMD160_COMPRESSED;

    // ==== Total Chunk Size ====
    /** Total size of one OpenCL result chunk in bytes. */
    public static final int CHUNK_SIZE_NUM_BYTES = CHUNK_OFFSET_99_NUM_BYTES_END_OF_CHUNK;

    // ==== END: SYNCHRONIZED WITH OpenCL CONSTANTS ====

    /**
     * Computes the maximum permissible length for an array intended to store pairs of coordinates within a 32-bit system.
     * This constant represents the upper limit on array length, factoring in the memory constraint imposed by the maximum
     * integer value addressable in Java ({@link Integer#MAX_VALUE}) and the storage requirement for two coordinates.
     * <p>
     * The calculation divides {@link Integer#MAX_VALUE} by the number of bytes needed to store a OpenCL chunk,
     * as defined by {@link PublicKeyBytes#CHUNK_SIZE_NUM_BYTES}, ensuring the array's indexing does not surpass
     * Java's maximum allowable array length.
     * </p>
     */
    public static final int MAXIMUM_CHUNK_ELEMENTS = Integer.MAX_VALUE / CHUNK_SIZE_NUM_BYTES;

    /**
     * Determines the minimum number of bits required to address the maximum array length for storing chunks.
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
    public static final int BIT_COUNT_FOR_MAX_CHUNKS_ARRAY =
            MAXIMUM_CHUNK_ELEMENTS == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(MAXIMUM_CHUNK_ELEMENTS - 1) - 1;

    /** Byte index of the last byte of the Y coordinate in an uncompressed SEC key. */
    public static final int LAST_Y_COORDINATE_BYTE_INDEX = SEC_PREFIX_NUM_BYTES + TWO_COORDINATES_NUM_BYTES - 1;

    /** Total size of an uncompressed SEC public key in bytes (prefix + X + Y). */
    public static final int PUBLIC_KEY_UNCOMPRESSED_BYTES = SEC_PREFIX_NUM_BYTES + TWO_COORDINATES_NUM_BYTES;
    /** Total size of a compressed SEC public key in bytes (prefix + X). */
    public static final int PUBLIC_KEY_COMPRESSED_BYTES = SEC_PREFIX_NUM_BYTES + ONE_COORDINATE_NUM_BYTES;

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
            compressed[0] = SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y;
        } else {
            compressed[0] = SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y;
        }
        // x
        System.arraycopy(
                uncompressed,
                SEC_PREFIX_NUM_BYTES,
                compressed,
                SEC_PREFIX_NUM_BYTES,
                PublicKeyBytes.ONE_COORDINATE_NUM_BYTES);
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
        uncompressed[0] = SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT;
        // x
        System.arraycopy(x, 0, uncompressed, SEC_PREFIX_NUM_BYTES, ONE_COORDINATE_NUM_BYTES);
        // y
        System.arraycopy(y, 0, uncompressed, SEC_PREFIX_NUM_BYTES + ONE_COORDINATE_NUM_BYTES, ONE_COORDINATE_NUM_BYTES);
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
        for (int i = SEC_PREFIX_NUM_BYTES; i < uncompressed.length; i++) {
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
        return 73 * 3 + Objects.hashCode(this.secretKey);
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
