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
 * Loads the base point G in standard big-endian format (X coordinate only).
 *
 * @param g A pointer to an array of 8 u32 elements where the X coordinate of the base point G will be stored.
 */
inline void load_basepoint_x_only(u32 *g) {
    g[0] = SECP256K1_G0;
    g[1] = SECP256K1_G1;
    g[2] = SECP256K1_G2;
    g[3] = SECP256K1_G3;
    g[4] = SECP256K1_G4;
    g[5] = SECP256K1_G5;
    g[6] = SECP256K1_G6;
    g[7] = SECP256K1_G7;
}

/**
 * Loads the base point G with byte-reversed X coordinate and appended parity byte.
 * This format is used for compressed public key parsing.
 *
 * @param g A pointer to an array of 9 u32 elements where the byte-reversed X coordinate and parity of G will be stored.
 */
inline void load_basepoint_x_byte_reversed_with_parity(u32 *g) {
    g[0] = SECP256K1_G_STRING0;
    g[1] = SECP256K1_G_STRING1;
    g[2] = SECP256K1_G_STRING2;
    g[3] = SECP256K1_G_STRING3;
    g[4] = SECP256K1_G_STRING4;
    g[5] = SECP256K1_G_STRING5;
    g[6] = SECP256K1_G_STRING6;
    g[7] = SECP256K1_G_STRING7;
    g[8] = SECP256K1_G_STRING8;
}

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
 * Generate a public key from a private key.
 * @param r out: x coordinate with leading parity, a pointer to an u32 array with a size of 9.
 * @param k in: scalar to multiply the basepoint, a pointer to an u32 array with a size of 8.
 */
__kernel void generateKeysKernel_parse_public(__global u32 *r, __global const u32 *k)
{
    u32 g_local[PUBLIC_KEY_LENGTH_WITH_PARITY];
    u32 r_local[PUBLIC_KEY_LENGTH_WITH_PARITY];
    u32 k_local[PRIVATE_KEY_LENGTH];
    secp256k1_t g_xy_local;
    u32 return_value;

    load_basepoint_x_byte_reversed_with_parity(g_local);

    // global to local
    copy_u32_array_8(k_local, k);
    
    return_value = parse_public(&g_xy_local, g_local);
    if (return_value != 0) {
        return;
    }
    
    point_mul(r_local, k_local, &g_xy_local);

    // local to global
    copy_u32_array_9(r, r_local);
}

/*
 * Generate a secp256k1_t struct for the public point. pre-computed points: (x1,y1,-y1),(x3,y3,-y3),(x5,y5,-y5),(x7,y7,-y7).
 * @param r out: secp256k1_t structure, a pointer to an u32 array with a size of 96 (SECP256K1_PRE_COMPUTED_XY_SIZE).
 */
__kernel void get_precalculated_g(__global u32 *r)
{
    u32 g_local[PUBLIC_KEY_LENGTH_WITHOUT_PARITY];
    secp256k1_t g_xy_local;
    const u32 g_parity = SECP256K1_G_PARITY;
    u32 return_value;

    load_basepoint_x_only(g_local);
    
    return_value = transform_public(&g_xy_local, g_local, g_parity);
    
    if (return_value != 0) {
        return;
    }
    
    for(int i=0; i<SECP256K1_PRE_COMPUTED_XY_SIZE; i++) {
        r[i] = g_xy_local.xy[i];
    }
}

/*
 * Compute public key by multiplying private key with base point G.
 * Uses uncompressed G (X + parity) and stores compressed public key (X + parity).
 */
__kernel void generateKeysKernel_transform_public(__global u32 *r, __global const u32 *k)
{
    u32 g_local[PUBLIC_KEY_LENGTH_WITHOUT_PARITY];
    u32 r_local[PUBLIC_KEY_LENGTH_WITH_PARITY];
    u32 k_local[PRIVATE_KEY_LENGTH];
    secp256k1_t g_xy_local;
    const u32 g_parity = SECP256K1_G_PARITY;
    u32 return_value;

    load_basepoint_x_only(g_local);

    // global to local
    copy_u32_array_8(k_local, k);
    
    return_value = transform_public(&g_xy_local, g_local, g_parity);
    
    if (return_value != 0) {
        return;
    }
    
    point_mul(r_local, k_local, &g_xy_local);

    // local to global
    copy_u32_array_9(r, r_local);
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
    secp256k1_t g_xy_local;

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

    set_precomputed_basepoint_g(&g_xy_local);

    point_mul_xy(x_local, y_local, k_local, &g_xy_local);

    // local to global
    int r_offset = PUBLIC_KEY_LENGTH_X_Y_WITHOUT_PARITY * global_id;

    copy_xy_to_r(r, r_offset, x_local, y_local);
}

__kernel void test_kernel_do_nothing(__global u32 *r, __global const u32 *k)
{
}
