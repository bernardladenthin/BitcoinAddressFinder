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

// ==== Unified output buffer (ONE physical layout, two write modes) ====
// Mirrors Java OpenClKernelConstants OUTPUT_* constants. The buffer begins with a
// single-word count header, then fixed-stride entries that ALWAYS carry the work_item_index
// as their first word (redundant in full-transfer mode, where entry i sits at slot i, but it
// keeps the stride identical to compact mode). Word offsets here equal the Java byte offsets / 4.
#define OUTPUT_HEADER_NUM_WORDS                                      1   // 4-byte count word at r[0]
#define OUTPUT_COUNT_FULL_TRANSFER_SENTINEL                          0xFFFFFFFF
#define OUTPUT_ENTRY_INDEX_NUM_WORDS                                 1   // work_item_index (u32)
#define OUTPUT_ENTRY_OFFSET_INDEX                                    0
#define OUTPUT_ENTRY_OFFSET_X                                        (OUTPUT_ENTRY_OFFSET_INDEX + OUTPUT_ENTRY_INDEX_NUM_WORDS)
#define OUTPUT_ENTRY_OFFSET_Y                                        (OUTPUT_ENTRY_OFFSET_X + CHUNK_SIZE_00_NUM_WORDS_BIG_ENDIAN_X)
#define OUTPUT_ENTRY_OFFSET_RIPEMD160_UNCOMPRESSED                   (OUTPUT_ENTRY_OFFSET_Y + CHUNK_SIZE_01_NUM_WORDS_BIG_ENDIAN_Y)
#define OUTPUT_ENTRY_OFFSET_RIPEMD160_COMPRESSED                     (OUTPUT_ENTRY_OFFSET_RIPEMD160_UNCOMPRESSED + CHUNK_SIZE_10_NUM_WORDS_RIPEMD160_UNCOMPRESSED)
#define OUTPUT_ENTRY_NUM_WORDS                                       (OUTPUT_ENTRY_OFFSET_RIPEMD160_COMPRESSED + CHUNK_SIZE_11_NUM_WORDS_RIPEMD160_COMPRESSED)
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

// Coordinates per point
#define COORDS_PER_POINT            (ONE_COORDINATE_NUM_WORDS * 2)

// Precomputed base point offsets
#define G_OFFSET_X1                 (0)
#define G_OFFSET_Y1                 (G_OFFSET_X1 + ONE_COORDINATE_NUM_WORDS)
#define G_OFFSET_NEG_Y1             (G_OFFSET_Y1 + ONE_COORDINATE_NUM_WORDS)

#define G_OFFSET_X3                 (G_OFFSET_NEG_Y1 + ONE_COORDINATE_NUM_WORDS)
#define G_OFFSET_Y3                 (G_OFFSET_X3 + ONE_COORDINATE_NUM_WORDS)
#define G_OFFSET_NEG_Y3             (G_OFFSET_Y3 + ONE_COORDINATE_NUM_WORDS)

#define G_OFFSET_X5                 (G_OFFSET_NEG_Y3 + ONE_COORDINATE_NUM_WORDS)
#define G_OFFSET_Y5                 (G_OFFSET_X5 + ONE_COORDINATE_NUM_WORDS)
#define G_OFFSET_NEG_Y5             (G_OFFSET_Y5 + ONE_COORDINATE_NUM_WORDS)

#define G_OFFSET_X7                 (G_OFFSET_NEG_Y5 + ONE_COORDINATE_NUM_WORDS)
#define G_OFFSET_Y7                 (G_OFFSET_X7 + ONE_COORDINATE_NUM_WORDS)
#define G_OFFSET_NEG_Y7             (G_OFFSET_Y7 + ONE_COORDINATE_NUM_WORDS)

#define G_PRECOMPUTED_TOTAL_WORDS   (G_OFFSET_NEG_Y7 + ONE_COORDINATE_NUM_WORDS)

DECLSPEC void point_add_xy (PRIVATE_AS u32 *x1, PRIVATE_AS u32 *y1, PRIVATE_AS const u32 *x2, PRIVATE_AS const u32 *y2)
{
    u32 z1[8] = { 0 };
    z1[0] = 1;

    point_add(x1, y1, z1, x2, y2);

    // z1 now holds z of result -> need to transform back to affine
    inv_mod(z1);

    u32 z2[8];
    mul_mod(z2, z1, z1);     // z^2
    mul_mod(x1, x1, z2);     // x_affine = x / z^2

    mul_mod(z2, z2, z1);     // z^3
    mul_mod(y1, y1, z2);     // y_affine = y / z^3
}

/**
 * @brief Copies a given number of u32 values from __constant to __private memory.
 *
 * Performs a direct word-wise copy of 32-bit values from a statically allocated
 * constant memory region (e.g., precomputed lookup tables) into private memory
 * (thread-local registers or stack).
 *
 * This is useful when working with values stored in `__constant` space such as
 * secp256k1 precomputed base points that need to be accessed with full
 * read/write flexibility in local registers.
 *
 * @param dst Destination array of u32 values in __private address space.
 * @param src Source array of u32 values in __constant address space.
 * @param word_count Number of 32-bit words (u32) to copy.
 */
inline void copy_constant_u32_array_private_u32(u32 *dst, __constant const u32 *src, const int word_count) {
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

// ===========================================================================================
// Direct EC-coordinate -> SHA-256 input block construction (upstreamable to hashcat).
//
// Built from two small, general "append at byte offset" primitives instead of bespoke per-format
// splices: zero the block, then "put" the 1-byte SEC prefix at offset 0, "put" each big-endian
// coordinate word at successive offsets (X, then optionally Y), and "put" the trailing 0x80 pad bit;
// the message-length word finishes it. Compressed vs. uncompressed differ only by how many words are
// appended — same mechanism. No intermediate byte buffer, no per-word byte swap.
//
// Because the byte offsets are compile-time constants in the (unrolled) callers, sha_block_put_be32's
// shift/branch fold away to exactly the hand-written (msword<<24)|(lsword>>8) splice — i.e. this is
// the generality of a streaming append with the cost of a straight-line write.
//
// Input form: x[]/y[] are the secp256k1 coordinate limbs in DEVICE little-endian word order —
// x[0] = least-significant 32 bits, x[7] = most-significant 32 bits (the form the EC point math here
// produces, before any endianness reversal). Big-endian SEC word k is limb (7-k); the byte order
// WITHIN a limb is already SHA's big-endian, so no swap is needed. prefix = 0x02/0x03 from the parity
// of Y (its least-significant bit -> y[0] & 1), or 0x04 for the uncompressed form.
// ===========================================================================================

// Generic (NOT SHA-specific): OR a big-endian u32 `val` into a big-endian byte buffer `buf` (an array
// of u32 words, byte 0 = MSB of word 0) at byte offset `byte_off`. A non-word-aligned offset straddles
// two words. `buf` must be pre-zeroed over the written range (this ORs, it does not clear). When
// `byte_off` is a compile-time constant (as in the callers here), the shift/branch fold away to a
// straight-line write. Works for any big-endian word buffer — SHA-256 input blocks are just one user.
inline void be_buffer_put_u32(u32 *buf, const u32 byte_off, const u32 val)
{
    const u32 wi = byte_off >> 2;
    const u32 sh = (byte_off & 3u) * 8u;
    if (sh == 0u) {
        buf[wi] |= val;
    } else {
        buf[wi] |= (val >> sh);
        buf[wi + 1] |= (val << (32u - sh));
    }
}

// Generic: OR a single byte `byte_val` into a big-endian byte buffer `buf` at byte offset `byte_off`.
inline void be_buffer_put_byte(u32 *buf, const u32 byte_off, const u32 byte_val)
{
    const u32 wi = byte_off >> 2;
    const u32 sh = (3u - (byte_off & 3u)) * 8u;
    buf[wi] |= (byte_val & 0xffu) << sh;
}

// Appends the SEC prefix byte + the 8 big-endian X words (high limb -> low limb) starting at byte 1.
// Shared by both SEC forms; X always occupies bytes 1..32.
inline void be_buffer_put_sec_prefix_and_x(u32 *buf, const u32 prefix, const u32 *x)
{
    be_buffer_put_byte(buf, 0, prefix);
    #pragma unroll
    for (int i = 0; i < ONE_COORDINATE_NUM_WORDS; i++) {
        be_buffer_put_u32(buf, 1u + (u32)i * 4u, x[ONE_COORDINATE_NUM_WORDS - 1 - i]);
    }
}

// Uncompressed SEC pubkey -> 32-word (two-block) padded SHA-256 input. out_block must hold 32 u32.
inline void sec_uncompressed_pubkey_to_sha256_blocks(const u32 *x, const u32 *y, u32 *out_block)
{
    #pragma unroll
    for (int i = 0; i < SHA256_INPUT_TOTAL_WORDS_UNCOMPRESSED; i++) {
        out_block[i] = 0;
    }
    be_buffer_put_sec_prefix_and_x(out_block, (u32)SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT, x); // bytes 0..32
    #pragma unroll
    for (int i = 0; i < ONE_COORDINATE_NUM_WORDS; i++) {
        be_buffer_put_u32(out_block, 33u + (u32)i * 4u, y[ONE_COORDINATE_NUM_WORDS - 1 - i]); // Y bytes 33..64
    }
    be_buffer_put_byte(out_block, 65, 0x80); // pad bit right after the 65-byte message
    out_block[SHA256_INPUT_TOTAL_WORDS_UNCOMPRESSED - 1] = SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BITS; // 520
}

// Compressed SEC pubkey -> 16-word (one-block) padded SHA-256 input. out_block must hold 16 u32.
inline void sec_compressed_pubkey_to_sha256_block(const u32 *x, const u32 *y, u32 *out_block)
{
    const u32 prefix = ((y[0] & 1u) == 0u) ? (u32)SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y
                                           : (u32)SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y;
    #pragma unroll
    for (int i = 0; i < SHA256_INPUT_TOTAL_WORDS_COMPRESSED; i++) {
        out_block[i] = 0;
    }
    be_buffer_put_sec_prefix_and_x(out_block, prefix, x);  // bytes 0..32
    be_buffer_put_byte(out_block, 33, 0x80);               // pad bit right after the 33-byte message
    out_block[SHA256_INPUT_TOTAL_WORDS_COMPRESSED - 1] = SEC_PUBLIC_KEY_COMPRESSED_NUM_BITS; // 264
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

// SHA-256 over `numBlocks` pre-built, pre-padded 64-byte blocks (16 u32 words each, already in the
// order the transform consumes — exactly what build_sha256_block_* produces). Writes the 8-word
// big-endian digest. Calls sha256_transform directly instead of sha256_init + sha256_update: the
// message is already block-aligned and padded, so update's streaming bookkeeping (offset alignment
// via switch_buffer_by_offset, partial-block buffering, length tracking, and the init-time zeroing of
// the whole ctx) is pure overhead. Result is byte-identical (update ultimately calls this transform).
inline void sha256_hash_prebuilt_blocks(const u32 *blocks, u32 numBlocks, u32 *digest)
{
    digest[0] = SHA256M_A;
    digest[1] = SHA256M_B;
    digest[2] = SHA256M_C;
    digest[3] = SHA256M_D;
    digest[4] = SHA256M_E;
    digest[5] = SHA256M_F;
    digest[6] = SHA256M_G;
    digest[7] = SHA256M_H;

    for (u32 b = 0; b < numBlocks; b++) {
        const u32 *blk = blocks + b * SHA256_INPUT_BLOCK_SIZE_WORDS;
        u32 w0[4] = {blk[0], blk[1], blk[2], blk[3]};
        u32 w1[4] = {blk[4], blk[5], blk[6], blk[7]};
        u32 w2[4] = {blk[8], blk[9], blk[10], blk[11]};
        u32 w3[4] = {blk[12], blk[13], blk[14], blk[15]};
        sha256_transform(w0, w1, w2, w3, digest);
    }
}

// RIPEMD-160 of a single pre-built RIPEMD block (16 u32 words as build_ripemd160_block_from_sha256
// produces them, big-endian). Byte-swaps each word to little-endian (exactly what
// ripemd160_update_swap does) and calls ripemd160_transform directly, skipping the streaming
// bookkeeping and ctx zeroing. Writes the 5-word hash160. Byte-identical to the init+update_swap path.
inline void ripemd160_hash_prebuilt_block_swap(const u32 *block, u32 *out_h)
{
    u32 w0[4] = {hc_swap32_S(block[0]), hc_swap32_S(block[1]), hc_swap32_S(block[2]), hc_swap32_S(block[3])};
    u32 w1[4] = {hc_swap32_S(block[4]), hc_swap32_S(block[5]), hc_swap32_S(block[6]), hc_swap32_S(block[7])};
    u32 w2[4] = {hc_swap32_S(block[8]), hc_swap32_S(block[9]), hc_swap32_S(block[10]), hc_swap32_S(block[11])};
    u32 w3[4] = {hc_swap32_S(block[12]), hc_swap32_S(block[13]), hc_swap32_S(block[14]), hc_swap32_S(block[15])};

    out_h[0] = RIPEMD160M_A;
    out_h[1] = RIPEMD160M_B;
    out_h[2] = RIPEMD160M_C;
    out_h[3] = RIPEMD160M_D;
    out_h[4] = RIPEMD160M_E;

    ripemd160_transform(w0, w1, w2, w3, out_h);
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
 * Scope:
 * - Beyond the SEC byte buffer, this macro also shares the SHA-256 and RIPEMD-160 working
 *   contexts (sha_ctx_shared / ripemd_ctx_shared) and their input blocks between the
 *   uncompressed and compressed passes (see the #ifdef block in the kernel). That is where
 *   most of the saving comes from: ~349 bytes of private scratch per work-item (a second
 *   sha256_ctx_t = 100 B, a second ripemd160_ctx_t = 88 B, plus the compressed input/sec
 *   allocations). Keeping it materially lowers register pressure / raises occupancy.
 *
 * Safety:
 * - Safe as long as each shared buffer is no longer needed once the compressed pass
 *   overwrites it. The uncompressed pass is fully consumed into ripemd_ctx_shared.h before
 *   the compressed pass begins, so the only value that must survive is the uncompressed
 *   hash160 itself.
 * - IMPORTANT (compact output): because ripemd_ctx_uncompressed and ripemd_ctx_compressed
 *   are the SAME shared context, the compressed pass overwrites the uncompressed hash160.
 *   The kernel therefore snapshots both hash160 results into private register arrays
 *   (ripemd_uncompressed_h / ripemd_compressed_h, 20 B each) right after each is computed,
 *   so the deferred filter check + entry write read stable copies. Those 40 bytes are the
 *   minimal cost of holding both hashes until the compact-mode output slot is decided — they
 *   are required by the compact feature, NOT a side effect of this macro, and are far cheaper
 *   than the ~349 B that disabling REUSE would re-introduce. Do not remove them.
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
// ======================================================================================
// ==== GPU-side Binary Fuse 8 filter (mirrors BinaryFuse8AddressPresence exactly) ======
// ======================================================================================
// These primitives are a byte-exact port of the Java murmur64 / mix / fingerprint8 /
// hashPosition helpers of the real Binary Fuse Filter (fused overlapping segments). Any
// divergence produces silent false negatives (a stored address never flagged), so they are
// pinned against the Java implementation by Fuse8GpuHashParityTest.

inline ulong fuse8_murmur64(ulong h) {
    h ^= h >> 33;
    h *= 0xff51afd7ed558ccdUL;
    h ^= h >> 33;
    h *= 0xc4ceb9fe1a85ec53UL;
    h ^= h >> 33;
    return h;
}

// Per-key mix: murmur64(key + seed). A single mix drives all three positions and the fingerprint.
inline ulong fuse8_mix(ulong key, ulong seed) {
    return fuse8_murmur64(key + seed);
}

inline uchar fuse8_fingerprint(ulong h) {
    return (uchar)(h ^ (h >> 32));
}

// One fused fingerprint position: high bits of the hash pick a base in [0, seg_count_len), then
// index*seg selects one of three consecutive segments and a within-segment bit-window (masked by
// seg_mask) offsets inside it. mul_hi is the unsigned 64x64->high64 multiply (== Java
// Math.unsignedMultiplyHigh), matching the CPU base reduction exactly.
inline uint fuse8_position(int index, ulong hash, uint seg_count_len, uint seg, uint seg_mask) {
    ulong h = mul_hi(hash, (ulong)seg_count_len);
    h += (ulong)((uint)index * seg);
    // h0 is the bare base position; only h1 and h2 xor a within-segment window from distinct
    // hash bit-ranges that do not overlap the high bits used by the base.
    if (index == 1) {
        h ^= (hash >> 18) & (ulong)seg_mask;
    } else if (index == 2) {
        h ^= hash & (ulong)seg_mask;
    }
    return (uint)h;
}

// Key extraction: the first 8 bytes of the hash160 read as a big-endian uint64. The two
// little-endian RIPEMD-160 output words h[0], h[1] hold the canonical hash160 bytes in LE
// layout, so byte-swapping each word yields the big-endian halves that Java's
// ByteBuffer.getLong(0) produces.
inline ulong fuse8_key_from_ripemd(const u32 *h) {
    return ((ulong)swap_u32(h[0]) << 32) | (ulong)swap_u32(h[1]);
}

// Returns true when the key is POSSIBLY present (no false negatives; ~0.4% false positives).
inline bool fuse8_contains(
    __global const uchar *fp, ulong seed, uint seg, uint seg_mask, uint seg_count_len, ulong key) {
    if (seg_count_len == 0) {
        return false; // empty filter never matches
    }
    ulong hash = fuse8_mix(key, seed);
    uint h0 = fuse8_position(0, hash, seg_count_len, seg, seg_mask);
    uint h1 = fuse8_position(1, hash, seg_count_len, seg, seg_mask);
    uint h2 = fuse8_position(2, hash, seg_count_len, seg, seg_mask);
    uchar f8 = fuse8_fingerprint(hash);
    return (uchar)(fp[h0] ^ fp[h1] ^ fp[h2]) == f8;
}

// ---------------------------------------------------------------------------------------------
// Blocked Bloom filter probe (byte-exact port of BlockedBloomAddressPresence).
//
// Why it exists next to the fuse8 probe: a fuse lookup reads three *scattered* fingerprints, i.e.
// three uncoalesced global transactions per candidate. This layout confines all k probes of a key
// to one 512-bit block = 64 bytes = a single coalesced transaction. On the CPU that is worth
// 13-36% at the billion-entry tier on a 16 MB-L3 host; whether it pays off on a GPU is what the
// benchmark kernels below exist to answer.
//
// Any divergence from the Java implementation produces silent FALSE NEGATIVES (a stored address
// never flagged), which is the one failure mode this project cannot tolerate. The formula is
// pinned against Java by BlockedBloomAddressPresenceTest#gpuStyleLookup_agreesWithContainsAddress.
//
//   key   = hash160[0..7] big-endian            (same extraction as the fuse filter)
//   a     = murmur64(key)
//   b     = murmur64(key + GOLDEN)
//   block = mul_hi(a, num_blocks)               // Lemire fastrange: any block count, not just 2^n
//   x     = (uint)b
//   y     = (uint)(b >> 32) | 1                  <-- stride MUST be odd, see below
//   bit_i = (x + i*y) & 511                      for i in 0..k-1
//
// The "| 1" is load-bearing, not cosmetic: the probe walk has period 512/gcd(y,512), so an even
// stride collapses it. 1 key in 512 would otherwise place every probe on a single bit. Measured
// on the CPU this cost a factor ~5 in false-positive rate.
#define BLOCKEDBLOOM_GOLDEN 0x9E3779B97F4A7C15UL
#define BLOCKEDBLOOM_BLOCK_MASK 511u
#define BLOCKEDBLOOM_LONGS_PER_BLOCK 8u

// Returns true when the key is POSSIBLY present (no false negatives).
inline bool blockedbloom_contains(
    __global const ulong *words, uint num_blocks, uint k, ulong key) {
    if (num_blocks == 0u || k == 0u) {
        return false; // empty/degenerate filter never matches
    }
    ulong a = fuse8_murmur64(key);
    ulong b = fuse8_murmur64(key + BLOCKEDBLOOM_GOLDEN);
    // mul_hi is the unsigned 64x64->high64 multiply (== Java Math.unsignedMultiplyHigh), mapping the
    // hash uniformly onto [0, num_blocks) without requiring a power-of-two count -- the same
    // primitive fuse8_position uses for its base. Requiring 2^n wasted up to 2x the memory.
    // One coalesced 64-byte region per key: every probe below indexes inside [base, base+8).
    uint base = ((uint)mul_hi(a, (ulong)num_blocks)) * BLOCKEDBLOOM_LONGS_PER_BLOCK;
    uint x = (uint)b;
    uint y = ((uint)(b >> 32)) | 1u;
    for (uint i = 0u; i < k; i++) {
        uint bit = (x + i * y) & BLOCKEDBLOOM_BLOCK_MASK;
        if ((words[base + (bit >> 6)] & (1UL << (bit & 63u))) == 0UL) {
            return false;
        }
    }
    return true;
}

// ---------------------------------------------------------------------------------------------
// Filter micro-benchmark kernels.
//
// Deliberately separate from generateKeysKernel_grid: in the production kernel the filter probe is
// a small tail behind EC point generation and two hash160 chains (~57%/43% of kernel time per
// docs/performance.md), so an A/B there would be swamped by work both arms share. These kernels do
// nothing but probe, over a caller-supplied key array, so the measured difference *is* the filter.
//
// Both write a per-work-item 0/1 hit flag rather than using an atomic counter: atomics would
// serialise on contention and confound the very thing being measured.

__kernel void benchmarkFilterFuse8(
    __global const uchar *fp,
    __global const uint *meta,      // [seedLo, seedHi, segLen, segLenMask, segCountLen]
    __global const ulong *keys,
    __global uchar *hits,
    const uint key_count) {
    uint gid = get_global_id(0);
    if (gid >= key_count) {
        return;
    }
    ulong seed = ((ulong)meta[1] << 32) | (ulong)meta[0];
    hits[gid] = fuse8_contains(fp, seed, meta[2], meta[3], meta[4], keys[gid]) ? (uchar)1 : (uchar)0;
}

__kernel void benchmarkFilterBlockedBloom(
    __global const ulong *words,
    __global const uint *meta,      // [numBlocks, k]
    __global const ulong *keys,
    __global uchar *hits,
    const uint key_count) {
    uint gid = get_global_id(0);
    if (gid >= key_count) {
        return;
    }
    hits[gid] = blockedbloom_contains(words, meta[0], meta[1], keys[gid]) ? (uchar)1 : (uchar)0;
}

/**
 * Sub-batch size for Montgomery's simultaneous inversion in the scalar-walker loop.
 *
 * The keysPerWorkItem walk produces consecutive points P0, P0+G, P0+2G, ... Converting each
 * Jacobian point back to affine needs a modular inverse of its Z coordinate, the single most
 * expensive field operation. Instead of one inv_mod per key (the old per-key point_add_xy path),
 * KEYS_BATCH_INV points are inverted together: one inv_mod plus 3*(N-1) multiplies for N points.
 * Larger values amortise the inverse over more keys but cost more private scratch
 * (4 * KEYS_BATCH_INV * 8 u32 words) which lowers occupancy; tune per device.
 *
 * MUST be a compile-time constant: it sizes the per-work-item private arrays below
 * (batch_x/batch_y/batch_z/batch_prefix), and OpenCL C requires private-array dimensions to be
 * compile-time constants. It therefore cannot be a runtime kernel argument like keysPerWorkItem
 * (which is just a loop count); it is baked into the program at clBuildProgram time. To make it
 * host-configurable, prepend a "#define KEYS_BATCH_INV <n>" line to the program source before the
 * build (host side) rather than passing it as an argument.
 */
#define KEYS_BATCH_INV 16

/**
 * @brief Copies word_count u32 values from one __private array to another.
 *
 * @param dst Destination array in __private address space.
 * @param src Source array in __private address space.
 * @param word_count Number of 32-bit words to copy.
 */
inline void copy_private_u32_array_private_u32(u32 *dst, const u32 *src, const int word_count) {
    #pragma unroll
    for (int i = 0; i < word_count; i++) {
        dst[i] = src[i];
    }
}

/**
 * @brief Fixed-base SIGNED-digit comb scalar multiplication: (x1, y1) affine = k * G (Stage 2).
 *
 * Replaces the wNAF point_mul_xy for the one-time anchor P0. G is fixed, so k*G is evaluated from
 * a precomputed table with ~0 doublings instead of ~256. The scalar's 4-bit windows are recoded
 * on the fly into SIGNED digits b_pos in {-8..+7} (carry-propagated low->high):
 *     k = Sum_{pos} b_pos * 2^(4*pos),   k*G = Sum_{pos} b_pos * (2^(4*pos) * G).
 * Each term uses comb_table entry (pos, |b_pos|), negated (-P = (x, p - y)) when b_pos < 0. Storing
 * only magnitudes 1..8 (not the unsigned digits 0..15) HALVES the table. A signed recode of a
 * 256-bit scalar can carry out of the top window, so the comb runs to pos = 64 (one extra
 * position, which only ever uses magnitude 1 = 2^256 * G).
 *
 * comb_table holds, for pos=0..64 and mag=1..8, the affine point (mag * 2^(4*pos)) * G as
 * [x(8 words)][y(8 words)] in device word order; entry (pos, mag) is at word offset
 * (pos*8 + (mag-1))*16. There is no zero slot (digit 0 contributes nothing and is skipped).
 *
 * Accumulation uses the mixed Jacobian+affine point_add. Doubling/infinity edge cases (Q == ±P)
 * cannot occur for the running partial sum vs. the next window point except with astronomically
 * small probability (the partial sum's value is strictly below the next window's bit position),
 * the same practical assumption point_mul_xy and the affine walk already rely on; if it ever
 * happened the inv_mod zero-guard prevents a hang and the filter rejects the garbage result.
 *
 * @param x1          out: affine x of k*G (device word order)
 * @param y1          out: affine y of k*G (device word order)
 * @param k           in:  scalar, 8 little-endian u32 words
 * @param comb_table  in:  65*8 precomputed points (see layout above)
 */
inline void point_mul_xy_comb(
    PRIVATE_AS u32 *x1, PRIVATE_AS u32 *y1, PRIVATE_AS const u32 *k, __global const u32 *comb_table)
{
    u32 qx[ONE_COORDINATE_NUM_WORDS] = { 0 };
    u32 qy[ONE_COORDINATE_NUM_WORDS] = { 0 };
    u32 qz[ONE_COORDINATE_NUM_WORDS] = { 0 };
    u32 have = 0;
    u32 carry = 0;

    // secp256k1 field prime, for the free point negation -P = (x, p - y).
    const u32 fieldP[ONE_COORDINATE_NUM_WORDS] = {
        SECP256K1_P0, SECP256K1_P1, SECP256K1_P2, SECP256K1_P3,
        SECP256K1_P4, SECP256K1_P5, SECP256K1_P6, SECP256K1_P7 };

    for (u32 pos = 0; pos < 65; pos++) {
        // Window nibble (0 for the extra carry-out position 64), plus the incoming carry.
        u32 nib = 0;
        if (pos < 64) {
            u32 word = pos >> 3;          // each u32 holds 8 nibbles
            u32 shift = (pos & 7) << 2;   // nibble offset within the word
            nib = (k[word] >> shift) & 0x0fu;
        }
        u32 t = nib + carry; // 0..16

        // Signed recode: t >= 8 borrows from the next window (carry 1) and uses a negative digit.
        u32 mag;       // magnitude |b|, 0..8
        u32 negative;  // 1 if b < 0
        if (t >= 8) {
            mag = 16u - t; // t in 8..16 -> b = t-16 in -8..0 -> |b| = 16-t in 0..8
            negative = 1;
            carry = 1;
        } else {
            mag = t;       // t in 0..7 -> b = t in 0..7
            negative = 0;
            carry = 0;
        }
        if (mag == 0) {
            continue; // 0 * anything = point at infinity -> contributes nothing (carry already set)
        }

        // 8 magnitude slots per position; slot index = mag-1.
        u32 base = (pos * 8u + (mag - 1u)) * TWO_COORDINATE_NUM_WORDS;
        u32 x2[ONE_COORDINATE_NUM_WORDS];
        u32 y2[ONE_COORDINATE_NUM_WORDS];
        copy_global_u32_array_private_u32(x2, &comb_table[base], ONE_COORDINATE_NUM_WORDS);
        copy_global_u32_array_private_u32(y2, &comb_table[base + ONE_COORDINATE_NUM_WORDS], ONE_COORDINATE_NUM_WORDS);
        if (negative) {
            sub_mod(y2, fieldP, y2); // -P = (x, p - y)
        }

        if (have == 0) {
            copy_private_u32_array_private_u32(qx, x2, ONE_COORDINATE_NUM_WORDS);
            copy_private_u32_array_private_u32(qy, y2, ONE_COORDINATE_NUM_WORDS);
            qz[0] = 1; // affine seed: Z = 1 (qz[1..7] already 0)
            have = 1;
        } else {
            point_add(qx, qy, qz, x2, y2); // Q += comb point (mixed Jacobian+affine)
        }
    }

    // Convert Q (Jacobian) to affine: x = X / Z^2, y = Y / Z^3.
    inv_mod(qz);
    u32 z2[ONE_COORDINATE_NUM_WORDS];
    mul_mod(z2, qz, qz); // Z^2
    mul_mod(x1, qx, z2); // x affine
    mul_mod(qz, z2, qz); // Z^3
    mul_mod(y1, qy, qz); // y affine
}

__kernel void generateKeysKernel_grid(
    __global u32 *r,
    __global const u32 *k,
    const u32 keysPerWorkItem,
    __global const uchar *fuse8_fp,    // Binary Fuse 8 fingerprint slot array
    __global const uint *fuse8_meta,   // [seedLo, seedHi, segLen, segLenMask, segCountLen]
    const u32 transfer_all,            // 0 = compact (filter) mode, non-zero = full transfer
    __global const u32 *iG_table,      // (keysPerWorkItem-1) points: entry m-1 = [x_{mG}(8 words)][y_{mG}(8 words)], device word order
    __global const u32 *comb_table)    // fixed-base signed-digit comb table: 65 positions * 8 magnitudes, each [x(8)][y(8)], device word order
{
    // Little Endian format
    u32 k_littleEndian_local[PRIVATE_KEY_LENGTH];
    u32 x_littleEndian_local[ONE_COORDINATE_NUM_WORDS];
    u32 y_littleEndian_local[ONE_COORDINATE_NUM_WORDS];
    // Big Endian format
    u32 x_bigEndian_local[ONE_COORDINATE_NUM_WORDS];
    u32 y_bigEndian_local[ONE_COORDINATE_NUM_WORDS];

    // hash160 scratch. The SHA-256 input blocks are built directly from the coordinate limbs
    // (sec_*_pubkey_to_sha256_block*) and consumed by sha256_transform / ripemd160_transform
    // directly — no SEC uchar buffer and no sha256_ctx_t / ripemd160_ctx_t streaming contexts.
    u32             *sha256_input_uncompressed;
    u32             *ripemd160_input_uncompressed;
    u32             *sha256_input_compressed;
    u32             *ripemd160_input_compressed;

    u32             sha256_input_uncompressed_alloc[SHA256_INPUT_TOTAL_WORDS_UNCOMPRESSED];
    u32             ripemd160_input_uncompressed_alloc[RIPEMD160_INPUT_BLOCK_SIZE_WORDS];

    sha256_input_uncompressed    = sha256_input_uncompressed_alloc;
    ripemd160_input_uncompressed = ripemd160_input_uncompressed_alloc;

    #ifdef REUSE_FOR_COMPRESSED
        // Compressed reuses the uncompressed scratch (hashed sequentially, after the uncompressed
        // chain has been fully consumed).
        sha256_input_compressed    = sha256_input_uncompressed_alloc;
        ripemd160_input_compressed = ripemd160_input_uncompressed_alloc;
    #else
        // Separate scratch buffers for the uncompressed and compressed chains.
        u32                                      sha256_input_compressed_alloc[SHA256_INPUT_TOTAL_WORDS_COMPRESSED];
        u32                                      ripemd160_input_compressed_alloc[RIPEMD160_INPUT_BLOCK_SIZE_WORDS];

        sha256_input_compressed    = sha256_input_compressed_alloc;
        ripemd160_input_compressed = ripemd160_input_compressed_alloc;
    #endif

    // get_global_id(dim) where dim is the dimension index (0 for first, 1 for second dimension etc.)
    // The above call is equivalent to get_local_size(dim)*get_group_id(dim) + get_local_id(dim)
    // size_t global_id = get_global_id(0);
    u32 global_id = get_global_id(0);
    u32 base_offset = global_id * keysPerWorkItem;

    //int local_id = get_local_id(0);
    //int local_size = get_local_size(0);

    // Copy private key to local register
    copy_global_u32_array_private_u32(k_littleEndian_local, k, PRIVATE_KEY_MAX_NUM_WORDS);
    u32 baseK0 = k_littleEndian_local[0];

    // Load the Binary Fuse 8 filter metadata once (only consulted in compact mode).
    ulong fuse8_seed = ((ulong)fuse8_meta[1] << 32) | (ulong)fuse8_meta[0]; // [seedHi, seedLo]
    uint fuse8_seg = fuse8_meta[2];
    uint fuse8_seg_mask = fuse8_meta[3];
    uint fuse8_seg_count_len = fuse8_meta[4];

    // Full-transfer mode: work-item 0 stamps the count header with the sentinel. Every
    // work-item writes its own entry densely at its slot below, so the reader walks workSize
    // entries (the count is implicit). Compact mode instead leaves the count word at the
    // host-zeroed 0 and grows it via atomic_add as hits claim slots, so the sentinel must NOT
    // be written there.
    if (transfer_all != 0 && global_id == 0) {
        r[0] = OUTPUT_COUNT_FULL_TRANSFER_SENTINEL;
    }

    // ===================== Single-anchor affine batched-addition walk =====================
    // Every key is P_m = P0 + m·G. Instead of advancing a running point with a Jacobian mixed
    // addition (the old walk) and converting each result back to affine, we anchor ALL points at
    // the SAME affine P0 and add the fixed multiple m·G directly in affine. The m·G are read from
    // a host-uploaded table (iG_table; entry m-1 holds x_{mG} then y_{mG}, each in device word
    // order). Anchoring at one P0 makes the slope denominators dx_m = x_{mG} - x0 mutually
    // independent, so a single Montgomery simultaneous inversion covers a whole KEYS_BATCH_INV
    // sub-batch — no Jacobian state, no per-point Z conversion.
    //
    // Per walked key: ~5 mul_mod + ~5 sub_mod (slope formula + Montgomery overhead) plus one
    // inv_mod per sub-batch, vs the previous Jacobian add + affine-conversion machinery.
    //
    // Degenerate case (same practical assumption as the previous path): dx_m == 0 iff m·G == ±P0,
    // i.e. (baseK0 | base_offset) ≡ ∓m (mod n) — impossible for the supported aligned sequential /
    // random ranges. If it ever happened, acc becomes 0 in Pass A and the inv_mod zero-guard
    // returns without hanging; the recovered inverses are then garbage and the filter rejects the
    // resulting hashes — strictly no worse than the previous path.

    // Affine anchor P0 = (baseK0 | base_offset) * G, computed once with the fixed-base comb.
    u32 x0[ONE_COORDINATE_NUM_WORDS];
    u32 y0[ONE_COORDINATE_NUM_WORDS];
    k_littleEndian_local[0] = (baseK0 | base_offset);
    point_mul_xy_comb(x0, y0, k_littleEndian_local, comb_table);

#ifdef USE_REDUCED_RADIX_FIELD
    // Reduced-radix 2^26 walk (inc_ecc_secp256k1_fe10x26.cl): the comb anchor x0/y0 stays radix-2^32
    // (it is computed once), but the per-key slope arithmetic runs in 2^26. Convert the anchor once;
    // the increment-table reads, the single per-sub-batch inverse, and the coordinate outputs convert
    // at their boundaries. All field elements stay within libsecp's magnitude bounds (<= 8 for mul
    // inputs), so the only normalizes are immediately before lowering a coordinate to radix-2^32.
    u32 nx0[SECP256K1_FE10X26_NUM_LIMBS];
    u32 ny0[SECP256K1_FE10X26_NUM_LIMBS];
    fe10x26_from_u32x8(nx0, x0);
    fe10x26_from_u32x8(ny0, y0);

    // Per-sub-batch scratch: dx_m denominators and their Montgomery prefix products (2^26 limbs).
    u32 dx[KEYS_BATCH_INV][SECP256K1_FE10X26_NUM_LIMBS];
    u32 prefix[KEYS_BATCH_INV][SECP256K1_FE10X26_NUM_LIMBS];
#else
    // Per-sub-batch scratch: dx_m denominators and their Montgomery prefix products.
    u32 dx[KEYS_BATCH_INV][ONE_COORDINATE_NUM_WORDS];
    u32 prefix[KEYS_BATCH_INV][ONE_COORDINATE_NUM_WORDS];
#endif

    for (u32 done = 0; done < keysPerWorkItem; done += KEYS_BATCH_INV) {
        u32 count = keysPerWorkItem - done;
        if (count > (u32) KEYS_BATCH_INV) {
            count = (u32) KEYS_BATCH_INV;
        }

        // ---- Pass A: dx_m = x_{mG} - x0 and Montgomery prefix products of dx ----
#ifdef USE_REDUCED_RADIX_FIELD
        u32 acc[SECP256K1_FE10X26_NUM_LIMBS] = { 0 };
        acc[0] = 1; // field element 1, normalized
        for (u32 j = 0; j < count; j++) {
            u32 m = done + j;
            copy_private_u32_array_private_u32(prefix[j], acc, SECP256K1_FE10X26_NUM_LIMBS);
            if (m == 0) {
                // P0 itself has no slope; contribute the multiplicative identity to the product.
                dx[j][0] = 1;
                #pragma unroll
                for (int w = 1; w < SECP256K1_FE10X26_NUM_LIMBS; w++) {
                    dx[j][w] = 0;
                }
            } else {
                // i*G table is stored in 2^26 form (refinement (b), convert_ig_table_to_fe10x26):
                // read the x limbs straight in, with no per-key fe10x26_from_u32x8 conversion.
                u32 ig_base = (m - 1) * SECP256K1_FE10X26_TWO_COORD_WORDS; // 20 words per 2^26 entry
                u32 ngx[SECP256K1_FE10X26_NUM_LIMBS];
                copy_global_u32_array_private_u32(ngx, &iG_table[ig_base], SECP256K1_FE10X26_NUM_LIMBS);
                fe10x26_sub(dx[j], ngx, nx0, 1); // dx_m = x_{mG} - x0 (magnitude 3)
            }
            fe10x26_mul(acc, acc, dx[j]); // acc = dx_0 * ... * dx_j (magnitude 1)
        }

        // ---- one modular inverse for the whole sub-batch (done in radix-2^32 via the conversion
        //      layer; reuses the build-selected safegcd/legacy inv_mod) ----
        u32 accU[ONE_COORDINATE_NUM_WORDS];
        fe10x26_normalize(acc);
        fe10x26_to_u32x8(accU, acc);
        inv_mod(accU); // accU = 1 / (dx_0 * ... * dx_{count-1})
        fe10x26_from_u32x8(acc, accU);
#else
        u32 acc[ONE_COORDINATE_NUM_WORDS] = { 0 };
        acc[0] = 1;
        for (u32 j = 0; j < count; j++) {
            u32 m = done + j;
            copy_private_u32_array_private_u32(prefix[j], acc, ONE_COORDINATE_NUM_WORDS);
            if (m == 0) {
                // P0 itself has no slope; contribute the multiplicative identity to the product.
                dx[j][0] = 1;
                #pragma unroll
                for (int w = 1; w < ONE_COORDINATE_NUM_WORDS; w++) {
                    dx[j][w] = 0;
                }
            } else {
                u32 gx[ONE_COORDINATE_NUM_WORDS];
                u32 ig_base = (m - 1) * TWO_COORDINATE_NUM_WORDS; // 16 words per table entry
                copy_global_u32_array_private_u32(gx, &iG_table[ig_base], ONE_COORDINATE_NUM_WORDS);
                sub_mod(dx[j], gx, x0); // dx_m = x_{mG} - x0
            }
            mul_mod(acc, acc, dx[j]); // acc = dx_0 * ... * dx_j
        }

        // ---- one modular inverse for the whole sub-batch ----
        inv_mod(acc); // acc = 1 / (dx_0 * ... * dx_{count-1})
#endif

        // ---- Pass B (reverse): recover each 1/dx_m, apply the affine slope formula, emit ----
        for (int j = (int) count - 1; j >= 0; j--) {
            u32 m = done + (u32) j;

#ifdef USE_REDUCED_RADIX_FIELD
            u32 inv_dx[SECP256K1_FE10X26_NUM_LIMBS];
            fe10x26_mul(inv_dx, acc, prefix[j]); // 1/dx_m = (1/product) * (dx_0..dx_{j-1})
            fe10x26_mul(acc, acc, dx[j]);        // strip dx_m: acc = 1 / (dx_0..dx_{j-1})

            if (m == 0) {
                // Emit the anchor P0 directly (inv_dx unused for this single key); x0/y0 are still in
                // radix-2^32 from the comb, so no conversion is needed.
                copy_private_u32_array_private_u32(x_littleEndian_local, x0, ONE_COORDINATE_NUM_WORDS);
                copy_private_u32_array_private_u32(y_littleEndian_local, y0, ONE_COORDINATE_NUM_WORDS);
            } else {
                // 2^26 i*G table (refinement (b)): read x and y limbs straight in, no conversion.
                u32 ig_base = (m - 1) * SECP256K1_FE10X26_TWO_COORD_WORDS;
                u32 ngx[SECP256K1_FE10X26_NUM_LIMBS];
                u32 ngy[SECP256K1_FE10X26_NUM_LIMBS];
                copy_global_u32_array_private_u32(ngx, &iG_table[ig_base], SECP256K1_FE10X26_NUM_LIMBS);
                copy_global_u32_array_private_u32(ngy, &iG_table[ig_base + SECP256K1_FE10X26_NUM_LIMBS], SECP256K1_FE10X26_NUM_LIMBS);

                // Affine slope law in 2^26. Magnitudes (mul/sqr inputs must stay <= 8): num=3,
                // lambda/lam2=1, xr=3 then 5, t=7 then 1, ynew=3. Only the two emitted coordinates
                // are normalized + lowered to radix-2^32.
                u32 num[SECP256K1_FE10X26_NUM_LIMBS];
                u32 lambda[SECP256K1_FE10X26_NUM_LIMBS];
                u32 lam2[SECP256K1_FE10X26_NUM_LIMBS];
                u32 t[SECP256K1_FE10X26_NUM_LIMBS];
                u32 xr[SECP256K1_FE10X26_NUM_LIMBS];
                u32 ynew[SECP256K1_FE10X26_NUM_LIMBS];
                fe10x26_sub(num, ngy, ny0, 1);   // y_{mG} - y0           (mag 3)
                fe10x26_mul(lambda, num, inv_dx);// λ = (y_{mG} - y0)/dx_m (mag 1)
                fe10x26_sqr(lam2, lambda);       // λ^2                    (mag 1)
                fe10x26_sub(xr, lam2, ngx, 1);   // λ^2 - x_{mG}           (mag 3)
                fe10x26_sub(xr, xr, nx0, 1);     // x_m = λ^2 - x_{mG} - x0 (mag 5)
                fe10x26_sub(t, nx0, xr, 5);      // x0 - x_m               (mag 7)
                fe10x26_mul(t, lambda, t);       // λ(x0 - x_m)            (mag 1)
                fe10x26_sub(ynew, t, ny0, 1);    // y_m = λ(x0 - x_m) - y0 (mag 3)

                fe10x26_normalize(xr);
                fe10x26_to_u32x8(x_littleEndian_local, xr);
                fe10x26_normalize(ynew);
                fe10x26_to_u32x8(y_littleEndian_local, ynew);
            }
#else
            u32 inv_dx[ONE_COORDINATE_NUM_WORDS];
            mul_mod(inv_dx, acc, prefix[j]); // 1/dx_m = (1/product) * (dx_0..dx_{j-1})
            mul_mod(acc, acc, dx[j]);        // strip dx_m: acc = 1 / (dx_0..dx_{j-1})

            if (m == 0) {
                // Emit the anchor P0 directly (inv_dx unused for this single key).
                copy_private_u32_array_private_u32(x_littleEndian_local, x0, ONE_COORDINATE_NUM_WORDS);
                copy_private_u32_array_private_u32(y_littleEndian_local, y0, ONE_COORDINATE_NUM_WORDS);
            } else {
                u32 gx[ONE_COORDINATE_NUM_WORDS];
                u32 gy[ONE_COORDINATE_NUM_WORDS];
                u32 ig_base = (m - 1) * TWO_COORDINATE_NUM_WORDS;
                copy_global_u32_array_private_u32(gx, &iG_table[ig_base], ONE_COORDINATE_NUM_WORDS);
                copy_global_u32_array_private_u32(gy, &iG_table[ig_base + ONE_COORDINATE_NUM_WORDS], ONE_COORDINATE_NUM_WORDS);

                u32 num[ONE_COORDINATE_NUM_WORDS];
                u32 lambda[ONE_COORDINATE_NUM_WORDS];
                u32 lam2[ONE_COORDINATE_NUM_WORDS];
                u32 t[ONE_COORDINATE_NUM_WORDS];
                sub_mod(num, gy, y0);          // y_{mG} - y0
                mul_mod(lambda, num, inv_dx);  // λ = (y_{mG} - y0) / dx_m
                mul_mod(lam2, lambda, lambda); // λ^2
                sub_mod(x_littleEndian_local, lam2, gx);                 // λ^2 - x_{mG}
                sub_mod(x_littleEndian_local, x_littleEndian_local, x0); // x_m = λ^2 - x_{mG} - x0
                sub_mod(t, x0, x_littleEndian_local);                    // x0 - x_m
                mul_mod(t, lambda, t);                                   // λ(x0 - x_m)
                sub_mod(y_littleEndian_local, t, y0);                    // y_m = λ(x0 - x_m) - y0
            }
#endif

            u32 loop_index = base_offset + m;

            // create big endian (computed into private registers; written to global memory below
            // only for entries that are actually emitted)
            // x
            copy_and_reverse_endianness_u32_array(x_bigEndian_local, 0, x_littleEndian_local, ONE_COORDINATE_NUM_WORDS);
            // y
            copy_and_reverse_endianness_u32_array(y_bigEndian_local, 0, y_littleEndian_local, ONE_COORDINATE_NUM_WORDS);

            // === hash160 stage(s) ===
            // Profiling builds (docs/performance.md "Stage attribution") short-circuit the hashing to
            // attribute kernel time; the default build (neither define set) runs both real chains and
            // is byte-for-byte the production kernel. PROFILE_* modes produce INCORRECT hashes and are
            // for benchmarking only. ripemd_uncompressed_h / ripemd_compressed_h are defined in every
            // branch so the downstream filter/output path is identical.
            u32 ripemd_uncompressed_h[RIPEMD160_HASH_NUM_WORDS];
            u32 ripemd_compressed_h[RIPEMD160_HASH_NUM_WORDS];

            #ifdef PROFILE_SKIP_HASH160
            // EC-only: skip both chains. Fill both hash160 slots from the public-key X coordinate so
            // the EC result stays live (not dead-code-eliminated) and the filter/output path runs.
            #pragma unroll
            for (int w = 0; w < RIPEMD160_HASH_NUM_WORDS; w++) {
                ripemd_uncompressed_h[w] = x_bigEndian_local[w];
                ripemd_compressed_h[w] = x_bigEndian_local[w];
            }
            #else
            // === Hash uncompressed key ===
            // Direct block transforms on the pre-built padded blocks (see sha256_hash_prebuilt_blocks
            // / ripemd160_hash_prebuilt_block_swap) — no init/update streaming bookkeeping. The hash160
            // is written straight into the stable register array, so there is no shared-context
            // snapshot to worry about even under REUSE_FOR_COMPRESSED.
            sec_uncompressed_pubkey_to_sha256_blocks(
                x_littleEndian_local, y_littleEndian_local, sha256_input_uncompressed);
            u32 sha256_digest_uncompressed[SHA256_HASH_NUM_WORDS];
            sha256_hash_prebuilt_blocks(
                sha256_input_uncompressed, SHA256_INPUT_BLOCKS_FOR_UNCOMPRESSED_SEC, sha256_digest_uncompressed);
            build_ripemd160_block_from_sha256(sha256_digest_uncompressed, ripemd160_input_uncompressed);
            ripemd160_hash_prebuilt_block_swap(ripemd160_input_uncompressed, ripemd_uncompressed_h);

            // === Hash compressed key ===
            #ifdef PROFILE_SKIP_SECOND_HASH160
            // Single-chain profiling: skip the compressed chain, reuse the uncompressed hash160.
            #pragma unroll
            for (int w = 0; w < RIPEMD160_HASH_NUM_WORDS; w++) {
                ripemd_compressed_h[w] = ripemd_uncompressed_h[w];
            }
            #else
            sec_compressed_pubkey_to_sha256_block(
                x_littleEndian_local, y_littleEndian_local, sha256_input_compressed);
            u32 sha256_digest_compressed[SHA256_HASH_NUM_WORDS];
            sha256_hash_prebuilt_blocks(
                sha256_input_compressed, SHA256_INPUT_BLOCKS_FOR_COMPRESSED_SEC, sha256_digest_compressed);
            build_ripemd160_block_from_sha256(sha256_digest_compressed, ripemd160_input_compressed);
            ripemd160_hash_prebuilt_block_swap(ripemd160_input_compressed, ripemd_compressed_h);
            #endif // PROFILE_SKIP_SECOND_HASH160
            #endif // PROFILE_SKIP_HASH160

            // === Decide whether and where to emit this entry ===
            // Full-transfer mode: every work-item emits, densely at slot loop_index.
            // Compact mode: emit only if the uncompressed OR compressed hash160 passes the Binary
            //   Fuse 8 filter; the output slot is claimed atomically on the count word (r[0]).
            u32 slot;
            bool should_write;
            if (transfer_all != 0) {
                slot = loop_index;
                should_write = true;
            } else {
                ulong key_uncompressed = fuse8_key_from_ripemd(ripemd_uncompressed_h);
                ulong key_compressed   = fuse8_key_from_ripemd(ripemd_compressed_h);
                bool hit = fuse8_contains(
                                fuse8_fp, fuse8_seed, fuse8_seg, fuse8_seg_mask, fuse8_seg_count_len, key_uncompressed)
                        || fuse8_contains(
                                fuse8_fp, fuse8_seed, fuse8_seg, fuse8_seg_mask, fuse8_seg_count_len, key_compressed);
                should_write = hit;
                slot = 0;
                if (hit) {
                    // atomic_add returns the previous value -> the 0-based slot this hit claims.
                    slot = atomic_add((volatile __global uint *)&r[0], 1u);
                }
            }

            if (should_write) {
                u32 r_offset = OUTPUT_HEADER_NUM_WORDS + slot * OUTPUT_ENTRY_NUM_WORDS;
                r[r_offset + OUTPUT_ENTRY_OFFSET_INDEX] = loop_index;
                copy_private_u32_array_global_u32(&r[r_offset + OUTPUT_ENTRY_OFFSET_X],      x_bigEndian_local, CHUNK_SIZE_00_NUM_WORDS_BIG_ENDIAN_X);
                copy_private_u32_array_global_u32(&r[r_offset + OUTPUT_ENTRY_OFFSET_Y],      y_bigEndian_local, CHUNK_SIZE_01_NUM_WORDS_BIG_ENDIAN_Y);
                copy_private_u32_array_global_u32(&r[r_offset + OUTPUT_ENTRY_OFFSET_RIPEMD160_UNCOMPRESSED], ripemd_uncompressed_h, RIPEMD160_HASH_NUM_WORDS);
                copy_private_u32_array_global_u32(&r[r_offset + OUTPUT_ENTRY_OFFSET_RIPEMD160_COMPRESSED], ripemd_compressed_h, RIPEMD160_HASH_NUM_WORDS);
            }
        }
    }
}

/*
 * Validation kernel (test scaffolding, not used in production): cross-checks inv_mod_safegcd against
 * the legacy binary-GCD inv_mod AND against the defining identity x * x^-1 == 1 (mod p), for
 * deterministic pseudo-random inputs. Single work-item, loops over `count` so it reuses the existing
 * single-work-item precompute test harness. out[i] is a failure bitmask, 0 == pass:
 *   bit 0 (1): safegcd result disagrees with the legacy inv_mod
 *   bit 1 (2): x * safegcd(x) != 1 (mod p)   [x != 0]
 *   bit 2 (4): safegcd(0) != 0
 */
__kernel void test_inv_mod_safegcd(__global u32 *out, const u32 count)
{
    u32 one[ONE_COORDINATE_NUM_WORDS] = { 0 };
    one[0] = 1;

    for (u32 i = 0; i < count; i++) {
        // Deterministic xorshift-derived 256-bit candidate, then fully reduced into [0, p) via
        // mul_mod(x, x, 1). The seed is never zero, so x == 0 essentially never occurs (guarded anyway).
        u32 x[ONE_COORDINATE_NUM_WORDS];
        u32 s = i * 2654435761u + 0x9e3779b9u;
        for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) {
            s ^= s << 13;
            s ^= s >> 17;
            s ^= s << 5;
            x[j] = s;
        }
        mul_mod(x, x, one); // x = x mod p

        u32 r_sg[ONE_COORDINATE_NUM_WORDS];
        u32 r_bg[ONE_COORDINATE_NUM_WORDS];
        for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) {
            r_sg[j] = x[j];
            r_bg[j] = x[j];
        }
        inv_mod_safegcd(r_sg);
        inv_mod(r_bg);

        u32 status = 0;

        // (1) agreement with the legacy inverse
        u32 diff = 0;
        for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) diff |= (r_sg[j] ^ r_bg[j]);
        if (diff != 0) status |= 1u;

        // x == 0 ?
        u32 orx = 0;
        for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) orx |= x[j];

        if (orx != 0) {
            // (2) defining identity x * safegcd(x) == 1 (mod p)
            u32 prod[ONE_COORDINATE_NUM_WORDS];
            mul_mod(prod, x, r_sg);
            u32 not_one = (prod[0] ^ 1u);
            for (int j = 1; j < ONE_COORDINATE_NUM_WORDS; j++) not_one |= prod[j];
            if (not_one != 0) status |= 2u;
        } else {
            // (3) safegcd(0) must be 0
            u32 nz = 0;
            for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) nz |= r_sg[j];
            if (nz != 0) status |= 4u;
        }

        out[i] = status;
    }
}

/*
 * Microbenchmark kernel (test scaffolding, not used in production): each work-item performs
 * `iterations` modular inverses of freshly generated operands, XOR-accumulating a checksum so the
 * compiler cannot elide the work. It calls the build-selected inv_mod (safegcd by default, or the
 * binary GCD under -D USE_LEGACY_BINARY_GCD_INV_MOD), launched over a full grid so warp divergence is
 * realistic. `inputHighLimbsZero != 0` zeroes the top three u32 limbs of every operand (~160-bit
 * inputs); 0 uses full ~256-bit operands. This isolates the inverse cost (InvModBenchmark) and shows
 * how the binary GCD's input-dependent iteration count interacts with operand width, whereas safegcd
 * is fixed-cost. Timing only — correctness is gated by test_inv_mod_safegcd / ProbeAddressesOpenCLTest.
 */
__kernel void bench_inv_mod(__global u32 *out, const u32 iterations, const u32 inputHighLimbsZero)
{
    const u32 gid = get_global_id(0);
    u32 checksum = 0;
    u32 s = gid * 2654435761u + 0x9e3779b9u;

    for (u32 i = 0; i < iterations; i++) {
        u32 v[ONE_COORDINATE_NUM_WORDS];
        for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) {
            s ^= s << 13;
            s ^= s >> 17;
            s ^= s << 5;
            v[j] = s;
        }
        if (inputHighLimbsZero != 0) {
            v[5] = 0;
            v[6] = 0;
            v[7] = 0; // ~160-bit operand (low 5 limbs)
        }
        v[0] |= 1u; // ensure nonzero so the inv_mod(0) early-out never skews timing

        inv_mod(v);
        checksum ^= v[0];
    }

    out[gid] = checksum;
}

/*
 * Validation kernel (test scaffolding, not used in production): proves the reduced-radix 2^26 field
 * module (inc_ecc_secp256k1_fe10x26.cl) is byte-identical to the vendored radix-2^32 field, so it can
 * be trusted as a drop-in. Single work-item, loops over `count` deterministic pseudo-random operand
 * pairs (x, y), each fully reduced into [0, p) via mul_mod(., ., 1). out[i] is a failure bitmask,
 * 0 == pass:
 *   bit 0 (1):  roundtrip   to_u32x8(from_u32x8(x)) != x
 *   bit 1 (2):  multiply    fe10x26_mul(x, y) != mul_mod(x, y)
 *   bit 2 (4):  square      fe10x26_sqr(x)    != mul_mod(x, x)
 *   bit 3 (8):  add         fe10x26_add(x, y) != add_mod(x, y)
 *   bit 4 (16): subtract    fe10x26 (x + (-y)) != sub_mod(x, y)
 */
__kernel void test_fe10x26(__global u32 *out, const u32 count)
{
    u32 one[ONE_COORDINATE_NUM_WORDS] = { 0 };
    one[0] = 1;

    for (u32 i = 0; i < count; i++) {
        // Two independent xorshift streams -> x and y, then reduce each into [0, p).
        u32 x[ONE_COORDINATE_NUM_WORDS];
        u32 y[ONE_COORDINATE_NUM_WORDS];
        u32 sx = i * 2654435761u + 0x9e3779b9u;
        u32 sy = i * 40503u + 0x85ebca6bu;
        for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) {
            sx ^= sx << 13; sx ^= sx >> 17; sx ^= sx << 5; x[j] = sx;
            sy ^= sy << 13; sy ^= sy >> 17; sy ^= sy << 5; y[j] = sy;
        }
        mul_mod(x, x, one); // x = x mod p
        mul_mod(y, y, one); // y = y mod p

        u32 status = 0;

        u32 nx[10];
        u32 ny[10];
        fe10x26_from_u32x8(nx, x);
        fe10x26_from_u32x8(ny, y);

        // (1) roundtrip
        {
            u32 n[10];
            for (int j = 0; j < 10; j++) n[j] = nx[j];
            fe10x26_normalize(n);
            u32 w[ONE_COORDINATE_NUM_WORDS];
            fe10x26_to_u32x8(w, n);
            u32 diff = 0;
            for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) diff |= (w[j] ^ x[j]);
            if (diff != 0) status |= 1u;
        }

        // (2) multiply
        {
            u32 n[10];
            fe10x26_mul(n, nx, ny);
            fe10x26_normalize(n);
            u32 w[ONE_COORDINATE_NUM_WORDS];
            fe10x26_to_u32x8(w, n);
            u32 ref[ONE_COORDINATE_NUM_WORDS];
            mul_mod(ref, x, y);
            u32 diff = 0;
            for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) diff |= (w[j] ^ ref[j]);
            if (diff != 0) status |= 2u;
        }

        // (3) square
        {
            u32 n[10];
            fe10x26_sqr(n, nx);
            fe10x26_normalize(n);
            u32 w[ONE_COORDINATE_NUM_WORDS];
            fe10x26_to_u32x8(w, n);
            u32 ref[ONE_COORDINATE_NUM_WORDS];
            mul_mod(ref, x, x);
            u32 diff = 0;
            for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) diff |= (w[j] ^ ref[j]);
            if (diff != 0) status |= 4u;
        }

        // (4) add
        {
            u32 n[10];
            fe10x26_add(n, nx, ny);
            fe10x26_normalize(n);
            u32 w[ONE_COORDINATE_NUM_WORDS];
            fe10x26_to_u32x8(w, n);
            u32 ref[ONE_COORDINATE_NUM_WORDS];
            add_mod(ref, x, y);
            u32 diff = 0;
            for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) diff |= (w[j] ^ ref[j]);
            if (diff != 0) status |= 8u;
        }

        // (5) subtract via negate + add (ny is normalized, magnitude 1)
        {
            u32 neg[10];
            fe10x26_negate(neg, ny, 1);
            u32 n[10];
            fe10x26_add(n, nx, neg);
            fe10x26_normalize(n);
            u32 w[ONE_COORDINATE_NUM_WORDS];
            fe10x26_to_u32x8(w, n);
            u32 ref[ONE_COORDINATE_NUM_WORDS];
            sub_mod(ref, x, y);
            u32 diff = 0;
            for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) diff |= (w[j] ^ ref[j]);
            if (diff != 0) status |= 16u;
        }

        out[i] = status;
    }
}

/*
 * Microbenchmark kernel (test scaffolding, not used in production): each work-item performs
 * `iterations` chained field multiplies and XOR-accumulates a checksum so the compiler cannot elide
 * the work. `useReducedRadix != 0` runs the chain in the reduced-radix 2^26 field
 * (inc_ecc_secp256k1_fe10x26.cl), converting in/out only once (mirroring a real EC walk that keeps
 * coordinates in 2^26 form); 0 runs the chain in the vendored radix-2^32 mul_mod. Launched over a full
 * grid for realistic occupancy. Timing only - correctness is gated by test_fe10x26. See FieldMulBenchmark.
 */
__kernel void bench_fe_mul(__global u32 *out, const u32 iterations, const u32 useReducedRadix)
{
    const u32 gid = get_global_id(0);
    u32 one[ONE_COORDINATE_NUM_WORDS] = { 0 };
    one[0] = 1;

    u32 a[ONE_COORDINATE_NUM_WORDS];
    u32 b[ONE_COORDINATE_NUM_WORDS];
    u32 s = gid * 2654435761u + 0x9e3779b9u;
    for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) {
        s ^= s << 13; s ^= s >> 17; s ^= s << 5; a[j] = s;
    }
    for (int j = 0; j < ONE_COORDINATE_NUM_WORDS; j++) {
        s ^= s << 13; s ^= s >> 17; s ^= s << 5; b[j] = s;
    }
    a[0] |= 1u;
    b[0] |= 1u;
    mul_mod(a, a, one); // reduce into [0, p)
    mul_mod(b, b, one);

    u32 checksum = 0;

    if (useReducedRadix != 0) {
        u32 na[10];
        u32 nb[10];
        fe10x26_from_u32x8(na, a);
        fe10x26_from_u32x8(nb, b);
        for (u32 i = 0; i < iterations; i++) {
            fe10x26_mul(na, na, nb); // na = na * nb (alias-safe: all reads precede writes)
        }
        fe10x26_normalize(na);
        u32 w[ONE_COORDINATE_NUM_WORDS];
        fe10x26_to_u32x8(w, na);
        checksum = w[0];
    } else {
        for (u32 i = 0; i < iterations; i++) {
            mul_mod(a, a, b); // a = a * b mod p
        }
        checksum = a[0];
    }

    out[gid] = checksum;
}
