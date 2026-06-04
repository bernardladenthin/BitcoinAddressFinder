// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.constants;

import java.math.BigInteger;

/**
 * Pure secp256k1 specification constants used across the project.
 *
 * <p>Single source of truth for the curve invariants that both the
 * configuration layer (wire-format defaults) and the runtime layer
 * (validation, key derivation) need to reference. Living in the
 * {@link net.ladenthin.bitcoinaddressfinder.constants} leaf package keeps
 * the dependency graph one-way: {@code configuration → constants ←
 * eckey / root}, with no cycles.
 *
 * <p>Values are taken from
 * <a href="https://www.secg.org/sec2-v2.pdf">SEC 2 v2</a>, &sect;2.4.1
 * ("Recommended Parameters secp256k1"), with one project-specific
 * exclusion (see {@link #MIN_VALID_PRIVATE_KEY}).
 */
public final class Secp256k1Constants {

    private Secp256k1Constants() {
        // utility constant holder; not instantiable.
    }

    /**
     * Bit length of a secp256k1 private key (256 bits). Pure spec value from
     * <a href="https://www.secg.org/sec2-v2.pdf">SEC 2 v2</a>, &sect;2.4.1
     * ("Recommended Parameters secp256k1"). Used as the natural upper bound
     * for any "batch size in bits" or per-key bit-width validation.
     */
    public static final int PRIVATE_KEY_MAX_NUM_BITS = 256;

    /**
     * The minimum valid private key that can be safely used in this
     * implementation.
     *
     * <p>While the secp256k1 specification allows private keys in the range
     * {@code [0x1, MAX_PRIVATE_KEY]} (i.e. including {@code 1}), this
     * implementation deliberately excludes {@code 1}. The exclusion avoids
     * edge cases in downstream libraries (notably
     * {@link org.bitcoinj.crypto.ECKey#fromPrivate(java.math.BigInteger, boolean)})
     * that have historically thrown or produced inconsistent results for
     * {@code 1}.
     *
     * @see #MAX_PRIVATE_KEY
     */
    public static final BigInteger MIN_VALID_PRIVATE_KEY = BigInteger.TWO;

    /**
     * Uppercase hexadecimal representation of {@link #MIN_VALID_PRIVATE_KEY},
     * for the configuration layer's wire-format defaults.
     */
    public static final String MIN_VALID_PRIVATE_KEY_HEX = "2";

    /**
     * Uppercase hexadecimal representation of the secp256k1 group order
     * (the maximum valid private key).
     *
     * <p>This is the public curve parameter {@code n} from
     * <a href="https://www.secg.org/sec2-v2.pdf">SEC 2 v2</a>, &sect;2.4.1
     * ("Recommended Parameters secp256k1"). It is the same value in every
     * secp256k1 implementation (bitcoinj, libsecp256k1, openssl, &hellip;)
     * and is used as the upper bound for valid-private-key range checks
     * &mdash; it is not a key, signing material, or secret.
     */
    public static final String MAX_PRIVATE_KEY_HEX =
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141";

    /**
     * The maximum valid private key according to the secp256k1
     * specification &mdash; the group order of the curve.
     *
     * <p>Parsed from {@link #MAX_PRIVATE_KEY_HEX} as a {@link BigInteger}
     * with the hex literal {@code 16} to keep this leaf package free of
     * any project-internal dependencies.
     */
    public static final BigInteger MAX_PRIVATE_KEY = new BigInteger(MAX_PRIVATE_KEY_HEX, 16);

    /**
     * Replacement value substituted for private keys that fall outside the
     * valid range during batch sanitisation (see
     * {@code PrivateKeyValidator#coerceToValidPrivateKey}).
     *
     * <p>The chosen value is {@link BigInteger#TWO}, which is also
     * {@link #MIN_VALID_PRIVATE_KEY}; the equality is incidental, not
     * structural. The substitution makes the batch deterministic and avoids
     * propagating an out-of-range key through the OpenCL pipeline.
     */
    public static final BigInteger INVALID_PRIVATE_KEY_REPLACEMENT = BigInteger.TWO;
}
