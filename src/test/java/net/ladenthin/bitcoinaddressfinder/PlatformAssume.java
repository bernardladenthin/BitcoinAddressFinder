// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

/**
 * Marker interface for platform-specific test assumptions.
 * <p>
 * Classes implementing this interface group checks that determine whether
 * tests should be conditionally executed depending on the runtime environment
 * (e.g., required native libraries or hardware support).
 * </p>
 *
 * @see org.junit.jupiter.api.Assumptions
 */
public interface PlatformAssume {}
