/**
 * Author......: Bernard Ladenthin, 2020
 * License.....: MIT
 */
/*
// example private key (in)
// hex: 68e23530deb6d5011ab56d8ad9f7b4a3b424f1112f08606357497495929f72dc
// decimal: 47440210799387980664936216788675555637818488436833759923669526136462528967388
// WiF
// to generate the public key (out)
// 025d99d81d9e731e0d7eebd1c858b1155da7981b1f0a16d322a361f8b589ad2e3b
// hex:
k_local[7] = 0x68e23530;
k_local[6] = 0xdeb6d501;
k_local[5] = 0x1ab56d8a;
k_local[4] = 0xd9f7b4a3;
k_local[3] = 0xb424f111;
k_local[2] = 0x2f086063;
k_local[1] = 0x57497495;
k_local[0] = 0x929f72dc;
*/

// ==== BEGIN: SYNCHRONIZED WITH JAVA CONSTANTS (Do not modify without updating Java) ====
// ==== Base Units ====
#define BITS_PER_BYTE                                      8
#define U32_PER_WORD                                       1
#define U32_NUM_BYTES                                      4
#define BYTE_SHIFT_TO_U32_MSB                              24

// ==== private key ====
#define PRIVATE_KEY_MAX_NUM_BITS                           256
#define PRIVATE_KEY_MAX_NUM_BYTES                          (PRIVATE_KEY_MAX_NUM_BITS / BITS_PER_BYTE) // 32
#define PRIVATE_KEY_MAX_NUM_WORDS                          (PRIVATE_KEY_MAX_NUM_BYTES / U32_NUM_BYTES) // 8

// ==== SEC format prefixes ====
#define SEC_PREFIX_NUM_BITS                                BITS_PER_BYTE
#define SEC_PREFIX_NUM_BYTES                               1
#define SEC_PREFIX_NUM_WORDS                               U32_PER_WORD
#define SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT                0x04
#define SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y           0x02
#define SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y            0x03

// ==== SEC format prefixes shifted versions (for use in u32[0] with MSB-first layout) ====
#define SEC_PREFIX_SHIFTED_NUM_BYTES                       U32_NUM_BYTES
#define SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT_SHIFTED        (SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT      << BYTE_SHIFT_TO_U32_MSB)
#define SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y_SHIFTED   (SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y << BYTE_SHIFT_TO_U32_MSB)
#define SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y_SHIFTED    (SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y  << BYTE_SHIFT_TO_U32_MSB)

// ==== x, y coordinate length ====
#define ONE_COORDINATE_NUM_BITS                            256
#define ONE_COORDINATE_NUM_BYTES                           (ONE_COORDINATE_NUM_BITS / BITS_PER_BYTE) // 32
#define TWO_COORDINATES_NUM_BITS                           (ONE_COORDINATE_NUM_BITS * 2) // 512
#define TWO_COORDINATES_NUM_BYTES                          (ONE_COORDINATE_NUM_BYTES * 2) // 64
#define ONE_COORDINATE_NUM_WORDS                           (ONE_COORDINATE_NUM_BYTES / U32_NUM_BYTES) // 8
#define TWO_COORDINATE_NUM_WORDS                           (ONE_COORDINATE_NUM_WORDS * 2) // 16

// ==== public key length ====
#define SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BITS               (SEC_PREFIX_NUM_BITS  + TWO_COORDINATES_NUM_BITS)  // 520
#define SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BYTES              (SEC_PREFIX_NUM_BYTES + TWO_COORDINATES_NUM_BYTES) // 65
#define SEC_PUBLIC_KEY_UNCOMPRESSED_WORDS                  (SEC_PREFIX_NUM_WORDS + TWO_COORDINATE_NUM_WORDS)  // 17
#define SEC_PUBLIC_KEY_COMPRESSED_NUM_BITS                 (SEC_PREFIX_NUM_BITS  + ONE_COORDINATE_NUM_BITS)   // 264
#define SEC_PUBLIC_KEY_COMPRESSED_NUM_BYTES                (SEC_PREFIX_NUM_BYTES + ONE_COORDINATE_NUM_BYTES)  // 33
#define SEC_PUBLIC_KEY_COMPRESSED_WORDS                    (SEC_PREFIX_NUM_WORDS + ONE_COORDINATE_NUM_WORDS)  // 9

// === Hash sizes ===
#define SHA256_INPUT_BLOCK_SIZE_BITS                       512
#define SHA256_INPUT_BLOCK_SIZE_BYTES                      (SHA256_INPUT_BLOCK_SIZE_BITS /  BITS_PER_BYTE) // 64
#define SHA256_INPUT_BLOCK_SIZE_WORDS                      (SHA256_INPUT_BLOCK_SIZE_BYTES / U32_NUM_BYTES) // 16
#define RIPEMD160_INPUT_BLOCK_SIZE_BITS                    512
#define RIPEMD160_INPUT_BLOCK_SIZE_BYTES                   (RIPEMD160_INPUT_BLOCK_SIZE_BITS /  BITS_PER_BYTE) // 64
#define RIPEMD160_INPUT_BLOCK_SIZE_WORDS                   (RIPEMD160_INPUT_BLOCK_SIZE_BYTES / U32_NUM_BYTES) // 16
#define SHA256_HASH_NUM_BITS                               256
#define SHA256_HASH_NUM_BYTES                              (SHA256_HASH_NUM_BITS /  BITS_PER_BYTE)    // 32
#define SHA256_HASH_NUM_WORDS                              (SHA256_HASH_NUM_BYTES / U32_NUM_BYTES)    // 8
#define RIPEMD160_HASH_NUM_BITS                            160
#define RIPEMD160_HASH_NUM_BYTES                           (RIPEMD160_HASH_NUM_BITS /  BITS_PER_BYTE) // 20
#define RIPEMD160_HASH_NUM_WORDS                           (RIPEMD160_HASH_NUM_BYTES / U32_NUM_BYTES) // 5

#define SHA256_INPUT_BLOCKS_FOR_UNCOMPRESSED_SEC 2
#define SHA256_INPUT_TOTAL_BITS_UNCOMPRESSED     (SHA256_INPUT_BLOCKS_FOR_UNCOMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_BITS)  // 1024
#define SHA256_INPUT_TOTAL_BYTES_UNCOMPRESSED    (SHA256_INPUT_BLOCKS_FOR_UNCOMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_BYTES) // 128
#define SHA256_INPUT_TOTAL_WORDS_UNCOMPRESSED    (SHA256_INPUT_BLOCKS_FOR_UNCOMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_WORDS) // 32

#define SHA256_INPUT_BLOCKS_FOR_COMPRESSED_SEC 1
#define SHA256_INPUT_TOTAL_BITS_COMPRESSED       (SHA256_INPUT_BLOCKS_FOR_COMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_BITS)  // 512
#define SHA256_INPUT_TOTAL_BYTES_COMPRESSED      (SHA256_INPUT_BLOCKS_FOR_COMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_BYTES) // 64
#define SHA256_INPUT_TOTAL_WORDS_COMPRESSED      (SHA256_INPUT_BLOCKS_FOR_COMPRESSED_SEC * SHA256_INPUT_BLOCK_SIZE_WORDS) // 16

// ==== Individual Chunk Sizes (Words in OpenCL) ====
#define CHUNK_SIZE_00_NUM_WORDS_BIG_ENDIAN_X                         ONE_COORDINATE_NUM_WORDS
#define CHUNK_SIZE_01_NUM_WORDS_BIG_ENDIAN_Y                         ONE_COORDINATE_NUM_WORDS
#define CHUNK_SIZE_10_NUM_WORDS_RIPEMD160_UNCOMPRESSED               RIPEMD160_HASH_NUM_WORDS
#define CHUNK_SIZE_11_NUM_WORDS_RIPEMD160_COMPRESSED                 RIPEMD160_HASH_NUM_WORDS

// ==== Offsets Within a Chunk ====
#define CHUNK_OFFSET_00_NUM_WORDS_BIG_ENDIAN_X                       0
#define CHUNK_OFFSET_01_NUM_WORDS_BIG_ENDIAN_Y                       (CHUNK_OFFSET_00_NUM_WORDS_BIG_ENDIAN_X           + CHUNK_SIZE_00_NUM_WORDS_BIG_ENDIAN_X)
#define CHUNK_OFFSET_10_NUM_WORDS_RIPEMD160_UNCOMPRESSED             (CHUNK_OFFSET_01_NUM_WORDS_BIG_ENDIAN_Y           + CHUNK_SIZE_01_NUM_WORDS_BIG_ENDIAN_Y)
#define CHUNK_OFFSET_11_NUM_WORDS_RIPEMD160_COMPRESSED               (CHUNK_OFFSET_10_NUM_WORDS_RIPEMD160_UNCOMPRESSED + CHUNK_SIZE_10_NUM_WORDS_RIPEMD160_UNCOMPRESSED)
#define CHUNK_OFFSET_99_NUM_WORDS_END_OF_CHUNK                       (CHUNK_OFFSET_11_NUM_WORDS_RIPEMD160_COMPRESSED   + CHUNK_SIZE_11_NUM_WORDS_RIPEMD160_COMPRESSED)

// ==== Total Chunk Size ====
#define CHUNK_SIZE_NUM_WORDS                                         CHUNK_OFFSET_99_NUM_WORDS_END_OF_CHUNK
// ==== END: SYNCHRONIZED WITH JAVA CONSTANTS ====

/**
 * @brief
 * Precomputed multiples of the base point G used in wNAF-based scalar multiplication.
 *
 * This table includes:
 * - ±1·G, ±3·G, ±5·G, ±7·G (total: 8 point pairs)
 * - Each point consists of (x, y) coordinates, 256 bits each
 * - For each y, the negative value is precomputed as well
 *
 * Memory layout:
 * - 4 window values (±1, ±3, ±5, ±7)
 * - 8 points × 2 coordinates (x, y) = 16 × 32 bytes = 512 bytes
 * - Each coordinate consists of 8 × u32 = 256 bits
 * - 8 (x) + 8 (y) + 8 (–y) = 24 coordinates per point (for sign flipping)
 * - Total: 96 × 4 bytes = **384 bytes** for each coordinate
 * - Overall memory usage: 96 × 4 = **384 bytes**
 *
 * This constant fits well into the GPU’s __constant memory space (64 KB on RTX 3090)
 * and benefits from fast broadcast access across all threads in a workgroup.
 *
 * Note:
 * Using __constant ensures:
 * - Reduced register pressure
 * - No redundant memory transfers per thread
 * - Better caching performance vs. global memory
 *
 * This allows thousands of threads to reuse the same precomputed data efficiently,
 * which is crucial in large scalar multiplication kernels on GPGPU.
 */
__constant secp256k1_t g_precomputed = {
    .xy = {
        // x1
        SECP256K1_G_PRE_COMPUTED_00,
        SECP256K1_G_PRE_COMPUTED_01,
        SECP256K1_G_PRE_COMPUTED_02,
        SECP256K1_G_PRE_COMPUTED_03,
        SECP256K1_G_PRE_COMPUTED_04,
        SECP256K1_G_PRE_COMPUTED_05,
        SECP256K1_G_PRE_COMPUTED_06,
        SECP256K1_G_PRE_COMPUTED_07,

        // y1
        SECP256K1_G_PRE_COMPUTED_08,
        SECP256K1_G_PRE_COMPUTED_09,
        SECP256K1_G_PRE_COMPUTED_10,
        SECP256K1_G_PRE_COMPUTED_11,
        SECP256K1_G_PRE_COMPUTED_12,
        SECP256K1_G_PRE_COMPUTED_13,
        SECP256K1_G_PRE_COMPUTED_14,
        SECP256K1_G_PRE_COMPUTED_15,

        // -y1
        SECP256K1_G_PRE_COMPUTED_16,
        SECP256K1_G_PRE_COMPUTED_17,
        SECP256K1_G_PRE_COMPUTED_18,
        SECP256K1_G_PRE_COMPUTED_19,
        SECP256K1_G_PRE_COMPUTED_20,
        SECP256K1_G_PRE_COMPUTED_21,
        SECP256K1_G_PRE_COMPUTED_22,
        SECP256K1_G_PRE_COMPUTED_23,

        // x3
        SECP256K1_G_PRE_COMPUTED_24,
        SECP256K1_G_PRE_COMPUTED_25,
        SECP256K1_G_PRE_COMPUTED_26,
        SECP256K1_G_PRE_COMPUTED_27,
        SECP256K1_G_PRE_COMPUTED_28,
        SECP256K1_G_PRE_COMPUTED_29,
        SECP256K1_G_PRE_COMPUTED_30,
        SECP256K1_G_PRE_COMPUTED_31,

        // y3
        SECP256K1_G_PRE_COMPUTED_32,
        SECP256K1_G_PRE_COMPUTED_33,
        SECP256K1_G_PRE_COMPUTED_34,
        SECP256K1_G_PRE_COMPUTED_35,
        SECP256K1_G_PRE_COMPUTED_36,
        SECP256K1_G_PRE_COMPUTED_37,
        SECP256K1_G_PRE_COMPUTED_38,
        SECP256K1_G_PRE_COMPUTED_39,

        // -y3
        SECP256K1_G_PRE_COMPUTED_40,
        SECP256K1_G_PRE_COMPUTED_41,
        SECP256K1_G_PRE_COMPUTED_42,
        SECP256K1_G_PRE_COMPUTED_43,
        SECP256K1_G_PRE_COMPUTED_44,
        SECP256K1_G_PRE_COMPUTED_45,
        SECP256K1_G_PRE_COMPUTED_46,
        SECP256K1_G_PRE_COMPUTED_47,

        // x5
        SECP256K1_G_PRE_COMPUTED_48,
        SECP256K1_G_PRE_COMPUTED_49,
        SECP256K1_G_PRE_COMPUTED_50,
        SECP256K1_G_PRE_COMPUTED_51,
        SECP256K1_G_PRE_COMPUTED_52,
        SECP256K1_G_PRE_COMPUTED_53,
        SECP256K1_G_PRE_COMPUTED_54,
        SECP256K1_G_PRE_COMPUTED_55,

        // y5
        SECP256K1_G_PRE_COMPUTED_56,
        SECP256K1_G_PRE_COMPUTED_57,
        SECP256K1_G_PRE_COMPUTED_58,
        SECP256K1_G_PRE_COMPUTED_59,
        SECP256K1_G_PRE_COMPUTED_60,
        SECP256K1_G_PRE_COMPUTED_61,
        SECP256K1_G_PRE_COMPUTED_62,
        SECP256K1_G_PRE_COMPUTED_63,

        // -y5
        SECP256K1_G_PRE_COMPUTED_64,
        SECP256K1_G_PRE_COMPUTED_65,
        SECP256K1_G_PRE_COMPUTED_66,
        SECP256K1_G_PRE_COMPUTED_67,
        SECP256K1_G_PRE_COMPUTED_68,
        SECP256K1_G_PRE_COMPUTED_69,
        SECP256K1_G_PRE_COMPUTED_70,
        SECP256K1_G_PRE_COMPUTED_71,

        // x7
        SECP256K1_G_PRE_COMPUTED_72,
        SECP256K1_G_PRE_COMPUTED_73,
        SECP256K1_G_PRE_COMPUTED_74,
        SECP256K1_G_PRE_COMPUTED_75,
        SECP256K1_G_PRE_COMPUTED_76,
        SECP256K1_G_PRE_COMPUTED_77,
        SECP256K1_G_PRE_COMPUTED_78,
        SECP256K1_G_PRE_COMPUTED_79,

        // y7
        SECP256K1_G_PRE_COMPUTED_80,
        SECP256K1_G_PRE_COMPUTED_81,
        SECP256K1_G_PRE_COMPUTED_82,
        SECP256K1_G_PRE_COMPUTED_83,
        SECP256K1_G_PRE_COMPUTED_84,
        SECP256K1_G_PRE_COMPUTED_85,
        SECP256K1_G_PRE_COMPUTED_86,
        SECP256K1_G_PRE_COMPUTED_87,

        // -y7
        SECP256K1_G_PRE_COMPUTED_88,
        SECP256K1_G_PRE_COMPUTED_89,
        SECP256K1_G_PRE_COMPUTED_90,
        SECP256K1_G_PRE_COMPUTED_91,
        SECP256K1_G_PRE_COMPUTED_92,
        SECP256K1_G_PRE_COMPUTED_93,
        SECP256K1_G_PRE_COMPUTED_94,
        SECP256K1_G_PRE_COMPUTED_95
    }
};

/**
 * @brief Copies a given number of u32 values from one u32 array to another.
 *
 * Performs a direct word-wise copy of 32-bit values from src to dst.
 *
 * @param dst Destination array of u32 values.
 * @param src Source array of u32 values.
 * @param word_count Number of u32 values to copy.
 */
inline void copy_global_u32_array_private_u32(u32 *dst, __global const u32 *src, const int word_count) {
    #pragma unroll
    for (int i = 0; i < word_count; i++) {
        dst[i] = src[i];
    }
}

/**
 * @brief Copies a given number of u32 values from one u32 array to another.
 *
 * Performs a direct word-wise copy of 32-bit values from src to dst.
 *
 * @param dst Destination array of u32 values.
 * @param src Source array of u32 values.
 * @param word_count Number of u32 values to copy.
 */
inline void copy_private_u32_array_global_u32(__global u32 *dst, const u32 *src, const int word_count) {
    #pragma unroll
    for (int i = 0; i < word_count; i++) {
        dst[i] = src[i];
    }
}

/**
 * @brief Copies the raw bytes of a u32 array into a uchar array.
 *
 * Copies word_count * 4 bytes from the source u32 array into the
 * destination byte array starting at the given offset.
 *
 * @param dst Destination byte array (uchar*).
 * @param dst_offset Byte offset in the destination array to begin writing.
 * @param src Source array of u32 values.
 * @param word_count Number of u32 words to copy (1 word = 4 bytes).
 */
inline void copy_u32_array_bytes(uchar *dst, int dst_offset, const u32 *src, const int word_count) {
    const uchar *src_bytes = (const uchar *)src;
    #pragma unroll
    for (int i = 0; i < word_count * 4; i++) {
        dst[dst_offset + i] = src_bytes[i];
    }
}

inline uchar get_lsb_of_little_endian_coordinate(const u32 *coord) {
    return as_uchar4(coord[0]).s0;
}

inline uchar get_lsb_of_big_endian_coordinate(const u32 *coord) {
    return as_uchar4(coord[ONE_COORDINATE_NUM_WORDS - 1]).s3;
}

// ======= Swap 32-bit value (safe across OpenCL versions) =======

inline u32 swap_u32(u32 v) {
#if defined(__builtin_bswap32)
    return __builtin_bswap32(v);
#else
    uchar4 bytes = as_uchar4(v);
    return as_uint((uchar4)(bytes.s3, bytes.s2, bytes.s1, bytes.s0));
#endif
}

/**
 * @brief Copies a u32 array to another while reversing word order and byte order (endianness).
 *
 * The source is interpreted as little-endian; the result will be big-endian, both in word and byte order.
 *
 * @param dst Destination u32 array.
 * @param dst_offset Starting index in the destination array.
 * @param src Source u32 array.
 * @param word_count Number of 32-bit words to copy and reverse.
 */
inline void copy_and_reverse_endianness_u32_array(u32 *dst, int dst_offset, const u32 *src, const int word_count) {
    #pragma unroll
    for (int i = 0; i < word_count; i++) {
        dst[dst_offset + i] = swap_u32(src[word_count - 1 - i]);
    }
}

/**
 * @brief Converts a byte array into an array of u32 words (big-endian).
 *
 * Packs 4 bytes at a time from the input byte array `src` into 32-bit words in `dst`,
 * assuming the byte order is big-endian (most significant byte first).
 *
 * For example, the bytes {0x12, 0x34, 0x56, 0x78} will become the u32 word 0x12345678.
 *
 * @param src Input byte array (length must be at least word_count * 4).
 * @param dst Output array of u32 words.
 * @param word_count Number of 32-bit words to produce (reads 4 * word_count bytes from src).
 */
inline void pack_bytes_to_u32_words(const uchar *src, u32 *dst, const int word_count)
{
    #pragma unroll
    for (int i = 0; i < word_count; i++) {
        dst[i] =
            ((u32)src[i * 4 + 0] << 24) |
            ((u32)src[i * 4 + 1] << 16) |
            ((u32)src[i * 4 + 2] << 8 ) |
            ((u32)src[i * 4 + 3]);
    }
}

inline void sha256_add_padding(u32 *dst, const int padding_start_index, const u32 final_byte, const int length_index, const u32 bit_len)
{
    // Write the final byte + 0x80 bit
    dst[padding_start_index] = ((u32)final_byte << 24) | 0x00800000;

    // Zero-fill from padding_start_index + 1 up to length_index - 1
    #pragma unroll
    for (int i = padding_start_index + 1; i < length_index; i++) {
        dst[i] = 0;
    }

    // Store bit length
    dst[length_index] = bit_len;
}

inline void get_sec_bytes_uncompressed(const u32 *x_bigEndian, const u32 *y_bigEndian, uchar *out_sec)
{
    out_sec[0] = SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT;
    copy_u32_array_bytes(out_sec,  1, x_bigEndian, ONE_COORDINATE_NUM_WORDS);
    copy_u32_array_bytes(out_sec, 33, y_bigEndian, ONE_COORDINATE_NUM_WORDS);
}

inline uchar get_compressed_prefix_from_lsb(uchar lsb)
{
    return (lsb & 1) == 0 ? SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y
                          : SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y;
}

inline void get_sec_bytes_compressed(const u32 *x_bigEndian, const u32 *y_bigEndian, uchar *out_sec)
{
    uchar lsb = get_lsb_of_big_endian_coordinate(y_bigEndian);
    out_sec[0] = get_compressed_prefix_from_lsb(lsb);
    copy_u32_array_bytes(out_sec, 1, x_bigEndian, ONE_COORDINATE_NUM_WORDS);
}

inline void transform_sec_prefix_from_uncompressed_to_compressed(uchar *out_sec)
{
    uchar lsb = out_sec[SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BYTES - 1];
    out_sec[0] = get_compressed_prefix_from_lsb(lsb);
}

inline void build_sha256_block_from_uncompressed_pubkey(const uchar *sec, u32 *sha256_input)
{
    // Pack 64 bytes from sec[0..63] into sha256_input[0..15]
    pack_bytes_to_u32_words(sec, sha256_input, TWO_COORDINATE_NUM_WORDS);

    // Apply SHA-256 padding: write 0x80 bit, zero-fill, append bit length (520 bits)
    sha256_add_padding(sha256_input, TWO_COORDINATE_NUM_WORDS, sec[TWO_COORDINATES_NUM_BYTES], SHA256_INPUT_TOTAL_WORDS_UNCOMPRESSED - 1, SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BITS);
}

inline void build_sha256_block_from_compressed_pubkey(const uchar *sec, u32 *sha256_input)
{
    // Pack 32 bytes from sec[0..31] into sha256_input[0..7]
    pack_bytes_to_u32_words(sec, sha256_input, ONE_COORDINATE_NUM_WORDS);

    // Apply SHA-256 padding: write 0x80 bit, zero-fill, append bit length (264 bits)
    sha256_add_padding(sha256_input, ONE_COORDINATE_NUM_WORDS, sec[ONE_COORDINATE_NUM_BYTES], SHA256_INPUT_TOTAL_WORDS_COMPRESSED - 1, SEC_PUBLIC_KEY_COMPRESSED_NUM_BITS);
}

inline void build_ripemd160_block_from_sha256(const u32 *sha256_hash, u32 *ripemd_input)
{
    // Copy 8 words (32 bytes) from SHA256
    #pragma unroll
    for (int i = 0; i < SHA256_HASH_NUM_WORDS; i++) {
        ripemd_input[i] = sha256_hash[i];
    }

    // Add RIPEMD160 padding
    // write "1" bit right after public key for SHA-256 padding
    ripemd_input[8] = 0x80000000;

    #pragma unroll
    for (int i = 9; i < 14; i++) {
        ripemd_input[i] = 0;
    }

    // begin of 64 length bits:
    // 0x0000000000010000  // 256 bits = 0x100 in hex
    // decimal 65536 is 00000000 00000001 00000000 00000000 in binary
    ripemd_input[14] = 0x00010000; // low word (bits 0–31)
    ripemd_input[15] = 0x00000000; // high word (bits 32–63)
}

/**
 * @define REUSE_UNCOMPRESSED_SEC_FOR_COMPRESSED
 *
 * When defined, enables optimization by reusing the buffer that stores the
 * uncompressed SEC (Standards for Efficient Cryptography) public key representation
 * to also hold the compressed form.
 *
 * Rationale:
 * - The compressed SEC format shares the same X coordinate as the uncompressed form.
 * - The only difference is the prefix byte (0x02 or 0x03 instead of 0x04).
 * - This allows us to avoid allocating a second array for the compressed key.
 *
 * Benefits:
 * - Reduces memory usage.
 * - Avoids redundant serialization logic.
 * - Helps GPU kernels minimize register and local memory pressure.
 *
 * Safety:
 * - This optimization is safe as long as the uncompressed SEC buffer is no longer needed
 *   after being overwritten with the compressed prefix.
 */
#define REUSE_FOR_COMPRESSED

/**
 * @brief
 * Generates multiple public key candidates from a single private key base by modifying the least significant bits.
 *
 * This kernel computes elliptic curve public keys and their corresponding Bitcoin address hashes for a grid
 * of private key candidates, where each candidate is derived by OR-ing the given base key with `global_id`.
 *
 * The output includes:
 * - X and Y coordinates of the resulting public key (in big-endian)
 * - RIPEMD-160 hash of the SHA-256 hash of the uncompressed public key
 * - RIPEMD-160 hash of the SHA-256 hash of the compressed public key
 *
 * Computation steps per work item:
 * 1. Take base private key from global memory and modify it by OR-ing with the thread's global ID.
 * 2. Multiply the private key with the curve's base point G using a precomputed wNAF table.
 * 3. Convert the resulting public key coordinates from little-endian to big-endian.
 * 4. Store X and Y coordinates in output buffer.
 * 5. Serialize uncompressed SEC format (0x04 + X + Y), hash it (SHA-256 then RIPEMD-160), and store the hash.
 * 6. Serialize compressed SEC format (0x02/0x03 + X), hash it (SHA-256 then RIPEMD-160), and store the hash.
 *
 * Optimization:
 * - If `REUSE_FOR_COMPRESSED` is defined, the buffer used for the uncompressed SEC format is reused for
 *   the compressed version to reduce memory footprint.
 *
 * @param r Output buffer (global u32*). Must be large enough to hold all chunks per thread.
 *          Each thread writes CHUNK_SIZE_NUM_WORDS u32 values.
 * @param k Input buffer (global const u32*) representing a single base private key (8 words, little-endian).
 */
__kernel void generateKeysKernel_grid(__global u32 *r, __global const u32 *k)
{
    // Little Endian format
    u32 k_littleEndian_local[PRIVATE_KEY_LENGTH];
    u32 x_littleEndian_local[ONE_COORDINATE_NUM_WORDS];
    u32 y_littleEndian_local[ONE_COORDINATE_NUM_WORDS];
    // Big Endian format
    u32 x_bigEndian_local[ONE_COORDINATE_NUM_WORDS];
    u32 y_bigEndian_local[ONE_COORDINATE_NUM_WORDS];
    
    u32             *sha256_input_uncompressed    ;
    u32             *ripemd160_input_uncompressed ;
    uchar           *sec_uncompressed             ;
    
    u32             *sha256_input_compressed      ;
    u32             *ripemd160_input_compressed   ;
    uchar           *sec_compressed               ;
    
    u32             sha256_input_uncompressed_alloc[SHA256_INPUT_TOTAL_WORDS_UNCOMPRESSED];
    u32             ripemd160_input_uncompressed_alloc[RIPEMD160_INPUT_BLOCK_SIZE_WORDS];
    uchar           sec_uncompressed_alloc[SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BYTES];
    
    sha256_input_uncompressed      = sha256_input_uncompressed_alloc;
    ripemd160_input_uncompressed   = ripemd160_input_uncompressed_alloc;
    sec_uncompressed               = sec_uncompressed_alloc;

    #ifdef REUSE_FOR_COMPRESSED
        // Shared context for both compressed and uncompressed operations
        sha256_ctx_t                             sha_ctx_shared;
        ripemd160_ctx_t                          ripemd_ctx_shared;

        // Aliases for readability — both use the shared context
        #define sha_ctx_uncompressed             sha_ctx_shared
        #define sha_ctx_compressed               sha_ctx_shared

        #define ripemd_ctx_uncompressed          ripemd_ctx_shared
        #define ripemd_ctx_compressed            ripemd_ctx_shared
        
        // arrays
        sha256_input_compressed    =             sha256_input_uncompressed_alloc;
        ripemd160_input_compressed =             ripemd160_input_uncompressed_alloc;
        sec_compressed             =             sec_uncompressed_alloc;
    #else
        // Separate contexts for uncompressed and compressed operations
        sha256_ctx_t                             sha_ctx_uncompressed;
        sha256_ctx_t                             sha_ctx_compressed;
        ripemd160_ctx_t                          ripemd_ctx_uncompressed;
        ripemd160_ctx_t                          ripemd_ctx_compressed;
        
        u32                                      sha256_input_compressed_alloc[SHA256_INPUT_TOTAL_WORDS_COMPRESSED];
        u32                                      ripemd160_input_compressed_alloc[RIPEMD160_INPUT_BLOCK_SIZE_WORDS];
        uchar                                    sec_compressed_alloc[SEC_PUBLIC_KEY_COMPRESSED_NUM_BYTES];
        
        sha256_input_compressed    =             sha256_input_compressed_alloc;
        ripemd160_input_compressed =             ripemd160_input_compressed_alloc;
        sec_compressed             =             sec_compressed_alloc;
    #endif

    // get_global_id(dim) where dim is the dimension index (0 for first, 1 for second dimension etc.)
    // The above call is equivalent to get_local_size(dim)*get_group_id(dim) + get_local_id(dim)
    // size_t global_id = get_global_id(0);
    u32 global_id = get_global_id(0);

    //int local_id = get_local_id(0);
    //int local_size = get_local_size(0);
    // Base offset for this work item
    const int r_offset = CHUNK_SIZE_NUM_WORDS * global_id;

    // Copy private key to local register
    copy_global_u32_array_private_u32(k_littleEndian_local, k, PRIVATE_KEY_MAX_NUM_WORDS);

    // Apply variation (global_id) to LSB part of key (k[0])
    k_littleEndian_local[0] |= global_id;

    point_mul_xy(x_littleEndian_local, y_littleEndian_local, k_littleEndian_local, &g_precomputed);

    // create big endian
    // x
    copy_and_reverse_endianness_u32_array(x_bigEndian_local, 0, x_littleEndian_local, ONE_COORDINATE_NUM_WORDS);
    copy_private_u32_array_global_u32(&r[r_offset + CHUNK_OFFSET_00_NUM_WORDS_BIG_ENDIAN_X],      x_bigEndian_local, CHUNK_SIZE_00_NUM_WORDS_BIG_ENDIAN_X);
    // y
    copy_and_reverse_endianness_u32_array(y_bigEndian_local, 0, y_littleEndian_local, ONE_COORDINATE_NUM_WORDS);
    copy_private_u32_array_global_u32(&r[r_offset + CHUNK_OFFSET_01_NUM_WORDS_BIG_ENDIAN_Y],      y_bigEndian_local, CHUNK_SIZE_01_NUM_WORDS_BIG_ENDIAN_Y);
    
    // === Hash uncompressed key ===
    get_sec_bytes_uncompressed(x_bigEndian_local, y_bigEndian_local, sec_uncompressed);
    build_sha256_block_from_uncompressed_pubkey(sec_uncompressed, sha256_input_uncompressed);
    sha256_init(&sha_ctx_uncompressed);
    sha256_update(&sha_ctx_uncompressed, sha256_input_uncompressed, SHA256_INPUT_TOTAL_BYTES_UNCOMPRESSED);

    build_ripemd160_block_from_sha256(sha_ctx_uncompressed.h, ripemd160_input_uncompressed);
    ripemd160_init(&ripemd_ctx_uncompressed);
    ripemd160_update_swap(&ripemd_ctx_uncompressed, ripemd160_input_uncompressed, RIPEMD160_INPUT_BLOCK_SIZE_BYTES);

    copy_private_u32_array_global_u32(&r[r_offset + CHUNK_OFFSET_10_NUM_WORDS_RIPEMD160_UNCOMPRESSED], ripemd_ctx_uncompressed.h, RIPEMD160_HASH_NUM_WORDS);

    // === Hash compressed key ===
    #ifdef REUSE_FOR_COMPRESSED
        transform_sec_prefix_from_uncompressed_to_compressed(sec_compressed);
    #else
        get_sec_bytes_compressed(x_bigEndian_local, y_bigEndian_local, sec_compressed);
    #endif
    build_sha256_block_from_compressed_pubkey(sec_compressed, sha256_input_compressed);
    sha256_init(&sha_ctx_compressed);
    sha256_update(&sha_ctx_compressed, sha256_input_compressed, SHA256_INPUT_TOTAL_BYTES_COMPRESSED);

    build_ripemd160_block_from_sha256(sha_ctx_compressed.h, ripemd160_input_compressed);
    ripemd160_init(&ripemd_ctx_compressed);
    ripemd160_update_swap(&ripemd_ctx_compressed, ripemd160_input_compressed, RIPEMD160_INPUT_BLOCK_SIZE_BYTES);

    copy_private_u32_array_global_u32(&r[r_offset + CHUNK_OFFSET_11_NUM_WORDS_RIPEMD160_COMPRESSED], ripemd_ctx_compressed.h, RIPEMD160_HASH_NUM_WORDS);
}
