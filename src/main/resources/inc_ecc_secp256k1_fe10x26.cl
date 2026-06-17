// @formatter:off
// SPDX-FileCopyrightText: 2013-2014 Pieter Wuille (original field_10x26 implementation, libsecp256k1, MIT)
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com> (OpenCL port + compatibility layer)
//
// SPDX-License-Identifier: MIT
// @formatter:on

/*
 * Reduced-radix secp256k1 field arithmetic in base 2^26 (10 limbs of 26 bits),
 * an OpenCL C port of libsecp256k1's src/field_10x26_impl.h (Pieter Wuille, MIT).
 *
 * WHY THIS EXISTS
 * ---------------
 * The vendored copyfromhashcat/inc_ecc_secp256k1.cl field is radix-2^32 (8 fully
 * packed u32 limbs). Its schoolbook multiply is carry-bound: every 32x32->64
 * partial product immediately propagates a carry, so the field multiply is
 * dominated by long add-with-carry dependency chains rather than by the
 * multiplies themselves (this is why a dedicated sqr_mod measured *slower* on
 * this kernel - see docs/performance.md). The reduced-radix 2^26 form leaves
 * 6 spare bits in every 32-bit limb, so up to ~64 partial products can be
 * accumulated in a 64-bit register before a single carry pass is needed. That
 * defers carry propagation and is the representation libsecp256k1 itself uses
 * for the 32-bit field. It is the #1 field-arithmetic lever identified in
 * docs/performance.md.
 *
 * SCOPE / NON-GOALS
 * -----------------
 * This is a self-contained, drop-in field module. It deliberately does NOT
 * modify copyfromhashcat/inc_ecc_secp256k1.cl: that file stays byte-for-byte
 * hashcat-compatible. This file is written in the same hashcat dialect (DECLSPEC,
 * u32/u64, PRIVATE_AS) so it could equally be dropped into a hashcat tree.
 *
 * COMPATIBILITY LAYER
 * -------------------
 * fe10x26_from_u32x8 / fe10x26_to_u32x8 convert between this 2^26 form and the
 * radix-2^32 u32[8] form used everywhere else in the kernel (and by hashcat:
 * limb 0 = least-significant 32 bits). They make the two representations
 * interchangeable, so a caller can lift an existing u32[8] coordinate into 2^26
 * form, run a chain of mul/sqr/add/negate cheaply, and lower the result back.
 * This bridge is what the parity kernel (test_fe10x26) and FieldMulBenchmark
 * exercise; it is proven byte-identical to the radix-2^32 path before any
 * production hot path relies on it.
 *
 * REPRESENTATION
 * --------------
 * A field element is u32 n[10]; the value is sum(i=0..9, n[i] << (i*26)) mod p,
 * p = 2^256 - 2^32 - 977. "Normalized" means n[i] < 2^26 for i=0..8,
 * n[9] < 2^22, and the whole value is < p. Multiply/square inputs may carry a
 * small "magnitude" (limbs up to ~2^30); their outputs are magnitude-1 but only
 * weakly normalized (value may be >= p), so fe10x26_normalize must be called
 * before fe10x26_to_u32x8. See the libsecp256k1 source for the full magnitude
 * analysis; the VERIFY_BITS/VERIFY_CHECK assertions from the original are
 * intentionally dropped here (they were debug-only bounds proofs).
 */

#define SECP256K1_FE10X26_NUM_LIMBS 10

/*
 * Lift a radix-2^32 field element (u32 w[8], limb 0 = least significant) into
 * 2^26 form. Pure radix repack (no byte-endianness involved): both forms are
 * little-endian numeric. The result limbs are < 2^26 (n[9] < 2^22) whenever the
 * input value is < 2^256, i.e. the output is in normalized limb form.
 */
DECLSPEC void fe10x26_from_u32x8(PRIVATE_AS u32 *n, PRIVATE_AS const u32 *w)
{
  u64 acc = 0;
  int bits = 0;
  int wi = 0;

  #pragma unroll
  for (int i = 0; i < SECP256K1_FE10X26_NUM_LIMBS; i++)
  {
    // 32 > 26, so at most one source word is needed to top up each 26-bit limb.
    if (bits < 26 && wi < 8)
    {
      acc |= ((u64) w[wi]) << bits;
      bits += 32;
      wi++;
    }
    n[i] = (u32) (acc & 0x3FFFFFF);
    acc >>= 26;
    bits -= 26;
  }
}

/*
 * Lower a NORMALIZED 2^26 field element back to radix-2^32 (u32 w[8], limb 0 =
 * least significant). Requires each n[i] < 2^26 (i.e. the result of
 * fe10x26_normalize); a weakly-normalized multiply/square output must be
 * normalized first.
 */
DECLSPEC void fe10x26_to_u32x8(PRIVATE_AS u32 *w, PRIVATE_AS const u32 *n)
{
  u64 acc = 0;
  int bits = 0;
  int ni = 0;

  #pragma unroll
  for (int j = 0; j < 8; j++)
  {
    // 26 < 32, so up to two source limbs may be needed to fill a 32-bit word.
    while (bits < 32 && ni < SECP256K1_FE10X26_NUM_LIMBS)
    {
      acc |= ((u64) n[ni]) << bits;
      bits += 26;
      ni++;
    }
    w[j] = (u32) (acc & 0xFFFFFFFF);
    acc >>= 32;
    bits -= 32;
  }
}

/*
 * Fully normalize r in place: reduce to magnitude 1 and into [0, p). Constant-time
 * port of secp256k1_fe_impl_normalize (the final reduction is always applied).
 */
DECLSPEC void fe10x26_normalize(PRIVATE_AS u32 *r)
{
  u32 t0 = r[0], t1 = r[1], t2 = r[2], t3 = r[3], t4 = r[4],
      t5 = r[5], t6 = r[6], t7 = r[7], t8 = r[8], t9 = r[9];

  u32 m;
  u32 x = t9 >> 22; t9 &= 0x03FFFFF;

  t0 += x * 0x3D1; t1 += (x << 6);
  t1 += (t0 >> 26); t0 &= 0x3FFFFFF;
  t2 += (t1 >> 26); t1 &= 0x3FFFFFF;
  t3 += (t2 >> 26); t2 &= 0x3FFFFFF; m = t2;
  t4 += (t3 >> 26); t3 &= 0x3FFFFFF; m &= t3;
  t5 += (t4 >> 26); t4 &= 0x3FFFFFF; m &= t4;
  t6 += (t5 >> 26); t5 &= 0x3FFFFFF; m &= t5;
  t7 += (t6 >> 26); t6 &= 0x3FFFFFF; m &= t6;
  t8 += (t7 >> 26); t7 &= 0x3FFFFFF; m &= t7;
  t9 += (t8 >> 26); t8 &= 0x3FFFFFF; m &= t8;

  // At most a single final reduction is needed; check if value >= p.
  x = (t9 >> 22) | ((t9 == 0x03FFFFF) & (m == 0x3FFFFFF)
      & ((t1 + 0x40 + ((t0 + 0x3D1) >> 26)) > 0x3FFFFFF));

  t0 += x * 0x3D1; t1 += (x << 6);
  t1 += (t0 >> 26); t0 &= 0x3FFFFFF;
  t2 += (t1 >> 26); t1 &= 0x3FFFFFF;
  t3 += (t2 >> 26); t2 &= 0x3FFFFFF;
  t4 += (t3 >> 26); t3 &= 0x3FFFFFF;
  t5 += (t4 >> 26); t4 &= 0x3FFFFFF;
  t6 += (t5 >> 26); t5 &= 0x3FFFFFF;
  t7 += (t6 >> 26); t6 &= 0x3FFFFFF;
  t8 += (t7 >> 26); t7 &= 0x3FFFFFF;
  t9 += (t8 >> 26); t8 &= 0x3FFFFFF;

  // Mask off the possible multiple of 2^256 from the final reduction.
  t9 &= 0x03FFFFF;

  r[0] = t0; r[1] = t1; r[2] = t2; r[3] = t3; r[4] = t4;
  r[5] = t5; r[6] = t6; r[7] = t7; r[8] = t8; r[9] = t9;
}

/* r = a + b, limb-wise (no reduction). Magnitudes add; normalize before lowering. */
DECLSPEC void fe10x26_add(PRIVATE_AS u32 *r, PRIVATE_AS const u32 *a, PRIVATE_AS const u32 *b)
{
  r[0] = a[0] + b[0];
  r[1] = a[1] + b[1];
  r[2] = a[2] + b[2];
  r[3] = a[3] + b[3];
  r[4] = a[4] + b[4];
  r[5] = a[5] + b[5];
  r[6] = a[6] + b[6];
  r[7] = a[7] + b[7];
  r[8] = a[8] + b[8];
  r[9] = a[9] + b[9];
}

/*
 * r = -a, valid when a has magnitude <= m (limbs <= 2*m*(2^26-1), n[9] <= 2*m*(2^22-1)).
 * The result has magnitude m+1 and is congruent to -a mod p. Port of
 * secp256k1_fe_impl_negate_unchecked.
 */
DECLSPEC void fe10x26_negate(PRIVATE_AS u32 *r, PRIVATE_AS const u32 *a, const int m)
{
  r[0] = 0x3FFFC2F * 2 * (m + 1) - a[0];
  r[1] = 0x3FFFFBF * 2 * (m + 1) - a[1];
  r[2] = 0x3FFFFFF * 2 * (m + 1) - a[2];
  r[3] = 0x3FFFFFF * 2 * (m + 1) - a[3];
  r[4] = 0x3FFFFFF * 2 * (m + 1) - a[4];
  r[5] = 0x3FFFFFF * 2 * (m + 1) - a[5];
  r[6] = 0x3FFFFFF * 2 * (m + 1) - a[6];
  r[7] = 0x3FFFFFF * 2 * (m + 1) - a[7];
  r[8] = 0x3FFFFFF * 2 * (m + 1) - a[8];
  r[9] = 0x03FFFFF * 2 * (m + 1) - a[9];
}

/*
 * r = a * b mod p. Port of secp256k1_fe_mul_inner. Inputs must have limbs
 * a[0..8],b[0..8] < 2^30 and a[9],b[9] < 2^26 (magnitude <= 8). Output is
 * magnitude 1 but only weakly normalized.
 */
DECLSPEC void fe10x26_mul(PRIVATE_AS u32 *r, PRIVATE_AS const u32 *a, PRIVATE_AS const u32 *b)
{
  u64 c, d;
  u64 u0, u1, u2, u3, u4, u5, u6, u7, u8;
  u32 t9, t1, t0, t2, t3, t4, t5, t6, t7;
  const u32 M = 0x3FFFFFF, R0 = 0x3D10, R1 = 0x400;

  d  = (u64) a[0] * b[9]
     + (u64) a[1] * b[8]
     + (u64) a[2] * b[7]
     + (u64) a[3] * b[6]
     + (u64) a[4] * b[5]
     + (u64) a[5] * b[4]
     + (u64) a[6] * b[3]
     + (u64) a[7] * b[2]
     + (u64) a[8] * b[1]
     + (u64) a[9] * b[0];
  t9 = (u32) (d & M); d >>= 26;

  c  = (u64) a[0] * b[0];
  d += (u64) a[1] * b[9]
     + (u64) a[2] * b[8]
     + (u64) a[3] * b[7]
     + (u64) a[4] * b[6]
     + (u64) a[5] * b[5]
     + (u64) a[6] * b[4]
     + (u64) a[7] * b[3]
     + (u64) a[8] * b[2]
     + (u64) a[9] * b[1];
  u0 = d & M; d >>= 26; c += u0 * R0;
  t0 = (u32) (c & M); c >>= 26; c += u0 * R1;

  c += (u64) a[0] * b[1]
     + (u64) a[1] * b[0];
  d += (u64) a[2] * b[9]
     + (u64) a[3] * b[8]
     + (u64) a[4] * b[7]
     + (u64) a[5] * b[6]
     + (u64) a[6] * b[5]
     + (u64) a[7] * b[4]
     + (u64) a[8] * b[3]
     + (u64) a[9] * b[2];
  u1 = d & M; d >>= 26; c += u1 * R0;
  t1 = (u32) (c & M); c >>= 26; c += u1 * R1;

  c += (u64) a[0] * b[2]
     + (u64) a[1] * b[1]
     + (u64) a[2] * b[0];
  d += (u64) a[3] * b[9]
     + (u64) a[4] * b[8]
     + (u64) a[5] * b[7]
     + (u64) a[6] * b[6]
     + (u64) a[7] * b[5]
     + (u64) a[8] * b[4]
     + (u64) a[9] * b[3];
  u2 = d & M; d >>= 26; c += u2 * R0;
  t2 = (u32) (c & M); c >>= 26; c += u2 * R1;

  c += (u64) a[0] * b[3]
     + (u64) a[1] * b[2]
     + (u64) a[2] * b[1]
     + (u64) a[3] * b[0];
  d += (u64) a[4] * b[9]
     + (u64) a[5] * b[8]
     + (u64) a[6] * b[7]
     + (u64) a[7] * b[6]
     + (u64) a[8] * b[5]
     + (u64) a[9] * b[4];
  u3 = d & M; d >>= 26; c += u3 * R0;
  t3 = (u32) (c & M); c >>= 26; c += u3 * R1;

  c += (u64) a[0] * b[4]
     + (u64) a[1] * b[3]
     + (u64) a[2] * b[2]
     + (u64) a[3] * b[1]
     + (u64) a[4] * b[0];
  d += (u64) a[5] * b[9]
     + (u64) a[6] * b[8]
     + (u64) a[7] * b[7]
     + (u64) a[8] * b[6]
     + (u64) a[9] * b[5];
  u4 = d & M; d >>= 26; c += u4 * R0;
  t4 = (u32) (c & M); c >>= 26; c += u4 * R1;

  c += (u64) a[0] * b[5]
     + (u64) a[1] * b[4]
     + (u64) a[2] * b[3]
     + (u64) a[3] * b[2]
     + (u64) a[4] * b[1]
     + (u64) a[5] * b[0];
  d += (u64) a[6] * b[9]
     + (u64) a[7] * b[8]
     + (u64) a[8] * b[7]
     + (u64) a[9] * b[6];
  u5 = d & M; d >>= 26; c += u5 * R0;
  t5 = (u32) (c & M); c >>= 26; c += u5 * R1;

  c += (u64) a[0] * b[6]
     + (u64) a[1] * b[5]
     + (u64) a[2] * b[4]
     + (u64) a[3] * b[3]
     + (u64) a[4] * b[2]
     + (u64) a[5] * b[1]
     + (u64) a[6] * b[0];
  d += (u64) a[7] * b[9]
     + (u64) a[8] * b[8]
     + (u64) a[9] * b[7];
  u6 = d & M; d >>= 26; c += u6 * R0;
  t6 = (u32) (c & M); c >>= 26; c += u6 * R1;

  c += (u64) a[0] * b[7]
     + (u64) a[1] * b[6]
     + (u64) a[2] * b[5]
     + (u64) a[3] * b[4]
     + (u64) a[4] * b[3]
     + (u64) a[5] * b[2]
     + (u64) a[6] * b[1]
     + (u64) a[7] * b[0];
  d += (u64) a[8] * b[9]
     + (u64) a[9] * b[8];
  u7 = d & M; d >>= 26; c += u7 * R0;
  t7 = (u32) (c & M); c >>= 26; c += u7 * R1;

  c += (u64) a[0] * b[8]
     + (u64) a[1] * b[7]
     + (u64) a[2] * b[6]
     + (u64) a[3] * b[5]
     + (u64) a[4] * b[4]
     + (u64) a[5] * b[3]
     + (u64) a[6] * b[2]
     + (u64) a[7] * b[1]
     + (u64) a[8] * b[0];
  d += (u64) a[9] * b[9];
  u8 = d & M; d >>= 26; c += u8 * R0;

  r[3] = t3;
  r[4] = t4;
  r[5] = t5;
  r[6] = t6;
  r[7] = t7;

  r[8] = (u32) (c & M); c >>= 26; c += u8 * R1;
  c   += d * R0 + t9;
  r[9] = (u32) (c & (M >> 4)); c >>= 22; c += d * (R1 << 4);

  d    = c * (R0 >> 4) + t0;
  r[0] = (u32) (d & M); d >>= 26;
  d   += c * (R1 >> 4) + t1;
  r[1] = (u32) (d & M); d >>= 26;
  d   += t2;
  r[2] = (u32) d;
}

/*
 * r = a^2 mod p. Port of secp256k1_fe_sqr_inner (same input/output magnitude
 * contract as fe10x26_mul).
 */
DECLSPEC void fe10x26_sqr(PRIVATE_AS u32 *r, PRIVATE_AS const u32 *a)
{
  u64 c, d;
  u64 u0, u1, u2, u3, u4, u5, u6, u7, u8;
  u32 t9, t0, t1, t2, t3, t4, t5, t6, t7;
  const u32 M = 0x3FFFFFF, R0 = 0x3D10, R1 = 0x400;

  d  = (u64) (a[0] * 2) * a[9]
     + (u64) (a[1] * 2) * a[8]
     + (u64) (a[2] * 2) * a[7]
     + (u64) (a[3] * 2) * a[6]
     + (u64) (a[4] * 2) * a[5];
  t9 = (u32) (d & M); d >>= 26;

  c  = (u64) a[0] * a[0];
  d += (u64) (a[1] * 2) * a[9]
     + (u64) (a[2] * 2) * a[8]
     + (u64) (a[3] * 2) * a[7]
     + (u64) (a[4] * 2) * a[6]
     + (u64) a[5] * a[5];
  u0 = d & M; d >>= 26; c += u0 * R0;
  t0 = (u32) (c & M); c >>= 26; c += u0 * R1;

  c += (u64) (a[0] * 2) * a[1];
  d += (u64) (a[2] * 2) * a[9]
     + (u64) (a[3] * 2) * a[8]
     + (u64) (a[4] * 2) * a[7]
     + (u64) (a[5] * 2) * a[6];
  u1 = d & M; d >>= 26; c += u1 * R0;
  t1 = (u32) (c & M); c >>= 26; c += u1 * R1;

  c += (u64) (a[0] * 2) * a[2]
     + (u64) a[1] * a[1];
  d += (u64) (a[3] * 2) * a[9]
     + (u64) (a[4] * 2) * a[8]
     + (u64) (a[5] * 2) * a[7]
     + (u64) a[6] * a[6];
  u2 = d & M; d >>= 26; c += u2 * R0;
  t2 = (u32) (c & M); c >>= 26; c += u2 * R1;

  c += (u64) (a[0] * 2) * a[3]
     + (u64) (a[1] * 2) * a[2];
  d += (u64) (a[4] * 2) * a[9]
     + (u64) (a[5] * 2) * a[8]
     + (u64) (a[6] * 2) * a[7];
  u3 = d & M; d >>= 26; c += u3 * R0;
  t3 = (u32) (c & M); c >>= 26; c += u3 * R1;

  c += (u64) (a[0] * 2) * a[4]
     + (u64) (a[1] * 2) * a[3]
     + (u64) a[2] * a[2];
  d += (u64) (a[5] * 2) * a[9]
     + (u64) (a[6] * 2) * a[8]
     + (u64) a[7] * a[7];
  u4 = d & M; d >>= 26; c += u4 * R0;
  t4 = (u32) (c & M); c >>= 26; c += u4 * R1;

  c += (u64) (a[0] * 2) * a[5]
     + (u64) (a[1] * 2) * a[4]
     + (u64) (a[2] * 2) * a[3];
  d += (u64) (a[6] * 2) * a[9]
     + (u64) (a[7] * 2) * a[8];
  u5 = d & M; d >>= 26; c += u5 * R0;
  t5 = (u32) (c & M); c >>= 26; c += u5 * R1;

  c += (u64) (a[0] * 2) * a[6]
     + (u64) (a[1] * 2) * a[5]
     + (u64) (a[2] * 2) * a[4]
     + (u64) a[3] * a[3];
  d += (u64) (a[7] * 2) * a[9]
     + (u64) a[8] * a[8];
  u6 = d & M; d >>= 26; c += u6 * R0;
  t6 = (u32) (c & M); c >>= 26; c += u6 * R1;

  c += (u64) (a[0] * 2) * a[7]
     + (u64) (a[1] * 2) * a[6]
     + (u64) (a[2] * 2) * a[5]
     + (u64) (a[3] * 2) * a[4];
  d += (u64) (a[8] * 2) * a[9];
  u7 = d & M; d >>= 26; c += u7 * R0;
  t7 = (u32) (c & M); c >>= 26; c += u7 * R1;

  c += (u64) (a[0] * 2) * a[8]
     + (u64) (a[1] * 2) * a[7]
     + (u64) (a[2] * 2) * a[6]
     + (u64) (a[3] * 2) * a[5]
     + (u64) a[4] * a[4];
  d += (u64) a[9] * a[9];
  u8 = d & M; d >>= 26; c += u8 * R0;

  r[3] = t3;
  r[4] = t4;
  r[5] = t5;
  r[6] = t6;
  r[7] = t7;

  r[8] = (u32) (c & M); c >>= 26; c += u8 * R1;
  c   += d * R0 + t9;
  r[9] = (u32) (c & (M >> 4)); c >>= 22; c += d * (R1 << 4);

  d    = c * (R0 >> 4) + t0;
  r[0] = (u32) (d & M); d >>= 26;
  d   += c * (R1 >> 4) + t1;
  r[1] = (u32) (d & M); d >>= 26;
  d   += t2;
  r[2] = (u32) d;
}
