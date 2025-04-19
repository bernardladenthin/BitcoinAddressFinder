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
 * Copies 8 u32 values from source to destination.
 * Typically used for private key or X/Y coordinate data.
 *
 * @param dst A pointer to the destination array of at least 8 u32 elements.
 * @param src A pointer to the source array of at least 8 u32 elements.
 */
inline void copy_u32_array_8(u32 *dst, const u32 *src) {
    /*
    // Loop version (for reference):
    #pragma unroll
    for (int i = 0; i < 8; i++) {
        dst[i] = src[i];
    }
    */
    // Unrolled
    dst[0] = src[0];
    dst[1] = src[1];
    dst[2] = src[2];
    dst[3] = src[3];
    dst[4] = src[4];
    dst[5] = src[5];
    dst[6] = src[6];
    dst[7] = src[7];
}

/**
 * Copies 9 u32 values from source to destination.
 * Used when copying compressed public keys (X + parity).
 *
 * @param dst A pointer to the destination array of at least 9 u32 elements.
 * @param src A pointer to the source array of at least 9 u32 elements.
 */
inline void copy_u32_array_9(u32 *dst, const u32 *src) {
    /*
    // Loop version (for reference):
    #pragma unroll
    for (int i = 0; i < 9; i++) {
        dst[i] = src[i];
    }
    */
    // Unrolled
    dst[0] = src[0];
    dst[1] = src[1];
    dst[2] = src[2];
    dst[3] = src[3];
    dst[4] = src[4];
    dst[5] = src[5];
    dst[6] = src[6];
    dst[7] = src[7];
    dst[8] = src[8];
}

/**
 * Copies 8 u32 X coordinates and 8 u32 Y coordinates into the result buffer.
 * X is copied to r[offset + 0..7], Y to r[offset + 8..15].
 *
 * @param r       A pointer to the result array where the data will be written.
 * @param offset  The starting index in the result array for the X coordinate.
 * @param x       A pointer to the 8-element array containing the X coordinate.
 * @param y       A pointer to the 8-element array containing the Y coordinate.
 */
inline void copy_xy_to_r(u32 *r, int offset, u32 *x, u32 *y) {
    // x
    copy_u32_array_8(&r[offset], x);

    // y
    copy_u32_array_8(&r[offset + 8], y);
}

/*
 * Compute public key grid by varying private key's LSB using global_id.
 * Uses precomputed G and stores (X, Y) coordinates for each result.
 */
__kernel void generateKeysKernel_grid(__global u32 *r, __global const u32 *k)
{
    u32 x_local[PUBLIC_KEY_LENGTH_WITHOUT_PARITY];
    u32 y_local[PUBLIC_KEY_LENGTH_WITHOUT_PARITY];
    u32 k_local[PRIVATE_KEY_LENGTH];

    // get_global_id(dim) where dim is the dimension index (0 for first, 1 for second dimension etc.)
    // The above call is equivalent to get_local_size(dim)*get_group_id(dim) + get_local_id(dim)
    // size_t global_id = get_global_id(0);
    u32 global_id = get_global_id(0);

    //int local_id = get_local_id(0);
    //int local_size = get_local_size(0);

    // Copy private key to local register
    copy_u32_array_8(k_local, k);

    // Apply variation (global_id) to LSB part of key (k[0])
    k_local[0] |= global_id;

    point_mul_xy(x_local, y_local, k_local, &g_precomputed);

    // local to global
    int r_offset = PUBLIC_KEY_LENGTH_X_Y_WITHOUT_PARITY * global_id;

    copy_xy_to_r(r, r_offset, x_local, y_local);
}
