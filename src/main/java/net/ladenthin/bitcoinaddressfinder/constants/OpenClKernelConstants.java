// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.constants;

/**
 * OpenCL kernel byte-layout and wire-format constants.
 *
 * <p><b>SYNCHRONIZED WITH OpenCL CONSTANTS &mdash; Do not modify without
 * updating the matching OpenCL {@code .cl} source.</b>
 *
 * <p>Single source of truth for every numeric value the Java side needs to
 * interpret the GPU kernel output buffer (chunk layout, coordinate sizes,
 * SEC prefix bytes, SHA-256 / RIPEMD-160 block sizes, derived array-capacity
 * bound). Moved here from {@code PublicKeyBytes} so the configuration layer
 * (and any other leaf-only consumer) can reference these values without
 * pulling in the producer-side public-key DTO; and so the "must stay in
 * lockstep with the {@code .cl} kernel" contract is visible from the package
 * + class name.
 *
 * <p>Verified post-move byte-exact values (pinned by
 * {@code OpenClKernelConstantsTest}):
 * <ul>
 *   <li>{@link #CHUNK_SIZE_NUM_BYTES} = {@code 104}</li>
 *   <li>{@link #MAXIMUM_CHUNK_ELEMENTS} = {@code 20 648 881}</li>
 *   <li>{@link #BIT_COUNT_FOR_MAX_CHUNKS_ARRAY} = {@code 24}</li>
 * </ul>
 *
 * <p>The expressions below are reproduced byte-for-byte from the original
 * {@code PublicKeyBytes} declaration block &mdash; no operands were
 * substituted, no evaluation order was changed.
 */
public final class OpenClKernelConstants {

    private OpenClKernelConstants() {
        // utility constant holder; not instantiable.
    }

    /** Number of bits in a byte. */
    public static final int BITS_PER_BYTE = 8;
    /** Number of {@code u32} values per kernel word. */
    public static final int U32_PER_WORD = 1;
    /** Number of bytes per {@code u32} word. */
    public static final int U32_NUM_BYTES = 4;
    /** Shift in bits to move a byte to the MSB of a {@code u32}. */
    public static final int BYTE_SHIFT_TO_U32_MSB = 24;

    // === private key ===
    /** Maximum number of bytes in a secp256k1 private key. */
    public static final int PRIVATE_KEY_MAX_NUM_BYTES =
            Secp256k1Constants.PRIVATE_KEY_MAX_NUM_BITS / BITS_PER_BYTE; // 32
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

    // ==== Derived array-capacity bound (was in PublicKeyBytes outside the SYNCHRONIZED block) ====
    /**
     * Maximum number of OpenCL chunks that fit in a single Java array,
     * given {@link Integer#MAX_VALUE} as the addressable byte cap.
     *
     * <p>The calculation divides {@link Integer#MAX_VALUE} by the number of
     * bytes needed to store an OpenCL chunk ({@link #CHUNK_SIZE_NUM_BYTES}),
     * ensuring the array's indexing does not surpass Java's maximum allowable
     * array length.
     */
    public static final int MAXIMUM_CHUNK_ELEMENTS = Integer.MAX_VALUE / CHUNK_SIZE_NUM_BYTES;

    /**
     * Minimum bit count needed to address every entry in an array of
     * {@link #MAXIMUM_CHUNK_ELEMENTS} chunks.
     *
     * <p>Bit-twiddling: by decrementing the maximum array length by 1 and
     * calculating 32 minus the count of leading zeros in the decremented
     * value, we obtain the closest superior power of 2 that can address all
     * potential array indices without breaching the 32-bit address space
     * limitation. Source:
     * https://stackoverflow.com/questions/5242533/fast-way-to-find-exponent-of-nearest-superior-power-of-2.
     */
    public static final int BIT_COUNT_FOR_MAX_CHUNKS_ARRAY =
            MAXIMUM_CHUNK_ELEMENTS == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(MAXIMUM_CHUNK_ELEMENTS - 1) - 1;
}
