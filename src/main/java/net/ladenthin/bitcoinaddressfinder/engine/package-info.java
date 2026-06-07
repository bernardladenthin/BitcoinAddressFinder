// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0

/**
 * Top-level orchestration: wiring producers and the consumer together and driving graceful shutdown.
 *
 * <p>JSpecify {@code @NullMarked}: everything is non-null unless annotated
 * {@code @Nullable}; NullAway enforces this at compile time.
 */
@NullMarked
package net.ladenthin.bitcoinaddressfinder.engine;

import org.jspecify.annotations.NullMarked;
