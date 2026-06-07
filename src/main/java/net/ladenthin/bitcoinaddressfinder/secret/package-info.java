// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0

/**
 * Foundation secret-generation primitives: secret suppliers, the BIP39
 * mnemonic wordlist, and the no-more-secrets signal. Depended on by both the
 * key utilities and the key-producer strategies.
 *
 * <p>JSpecify {@code @NullMarked}: everything is non-null unless annotated
 * {@code @Nullable}; NullAway enforces this at compile time.
 */
@NullMarked
package net.ladenthin.bitcoinaddressfinder.secret;

import org.jspecify.annotations.NullMarked;
