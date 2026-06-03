// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

/**
 * Narrow capability marker for components whose background work cannot be safely
 * scheduled from their constructor — typically because doing so would publish a
 * partially-constructed instance to a worker thread (JEP&nbsp;410 {@code this}-escape).
 *
 * <p>Implementations move that background work out of the constructor and into
 * {@link #start()}, which the caller invokes after the constructor returns. Mirrors
 * the existing {@link Interruptable} pattern: a single-method interface that adds
 * one lifecycle capability without enlarging the broader component interface.</p>
 */
public interface Startable {
    /**
     * Begins any background work the implementation requires.
     *
     * <p>Must be invoked by the caller after construction. The contract is
     * idempotency-agnostic: implementations may either tolerate multiple calls or
     * throw {@link IllegalStateException} on the second call — see the concrete
     * class's documentation.</p>
     */
    void start();
}
