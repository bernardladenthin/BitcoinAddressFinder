// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.constants;

import java.math.BigInteger;
import java.util.Locale;

/**
 * Pure secp256k1 specification constants used across the project.
 *
 * <p>Single source of truth for the curve invariants that both the
 * configuration layer (wire-format defaults) and the runtime layer
 * (validation, key derivation) need to reference. Living in the
 * {@link net.ladenthin.bitcoinaddressfinder.constants} leaf package keeps
 * the dependency graph one-way: {@code configuration → constants ← root},
 * with no cycles.
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
     *
     * <p>Derived from {@link #MIN_VALID_PRIVATE_KEY} rather than hard-coded so the
     * two stay in sync if the spec exclusion ever changes (e.g. excluding {@code 2}
     * as well in addition to {@code 1}).
     */
    public static final String MIN_VALID_PRIVATE_KEY_HEX =
            MIN_VALID_PRIVATE_KEY.toString(Radix.HEX).toUpperCase(Locale.ROOT);

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
    public static final String MAX_PRIVATE_KEY_HEX = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141";

    /**
     * The maximum valid private key according to the secp256k1
     * specification &mdash; the group order of the curve.
     *
     * <p>Parsed from {@link #MAX_PRIVATE_KEY_HEX} via {@link Radix#HEX} so
     * the "16" radix is named at the single source of truth in the same
     * leaf package, never inlined.
     */
    public static final BigInteger MAX_PRIVATE_KEY = new BigInteger(MAX_PRIVATE_KEY_HEX, Radix.HEX);

    /**
     * Replacement value substituted for private keys that fall outside the
     * valid range during batch sanitisation (see
     * {@code PrivateKeyValidator#coerceToValidPrivateKey}).
     *
     * <p>Defined as {@link #MIN_VALID_PRIVATE_KEY} so the replacement is
     * <em>valid by construction</em> &mdash; the substituted key is
     * guaranteed to fall inside the spec range without further checks.
     * Deriving the constant also avoids carrying two independent literals
     * that historically both happened to equal {@code 2}.
     */
    public static final BigInteger INVALID_PRIVATE_KEY_REPLACEMENT = MIN_VALID_PRIVATE_KEY;
}
