// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.core;

/**
 * Unchecked wrapper around {@link InterruptedException} for callers that cannot
 * declare the checked exception on their method signature.
 *
 * <p>Java's {@link InterruptedException} carries two pieces of contract that
 * any wrapper has to preserve:</p>
 * <ol>
 *   <li>The thread's interrupt flag must be set when the InterruptedException
 *       is in transit (the standard pattern is
 *       {@code Thread.currentThread().interrupt();} immediately before
 *       wrapping or re-throwing).</li>
 *   <li>The wrapped cause must remain inspectable by callers who want to
 *       react to interruption specifically (e.g. unwind orderly rather
 *       than retry).</li>
 * </ol>
 *
 * <p>This class does <strong>NOT</strong> touch the interrupt flag itself —
 * setting it stays the responsibility of the catch site, so the standard
 * interrupt-handling protocol is unchanged. The
 * {@link #InterruptedRuntimeException(String, InterruptedException)} overload
 * exists to give the catch site a compile-time guarantee that the cause is
 * the canonical {@link InterruptedException} and not some unrelated
 * {@link Throwable}.</p>
 *
 * <p>This class follows the cross-repo typed-exception unification audit
 * (see {@code workspace/crossrepostatus.md}): constructor matrix
 * {@code (message)} / {@code (message, cause)}, plus the typed-cause
 * overload for the interrupt-specific intent.</p>
 *
 * <p>Equality semantics are inherited from {@link Throwable} (identity);
 * the BAF {@code spotbugs-exclude.xml} suppression of
 * {@code IMC_IMMATURE_CLASS_NO_EQUALS} on the project's exception classes
 * covers this one too.</p>
 *
 * <h2>Canonical usage pattern</h2>
 * <pre>{@code
 * try {
 *     runLatch.await(30, TimeUnit.SECONDS);
 * } catch (InterruptedException e) {
 *     Thread.currentThread().interrupt();
 *     throw new InterruptedRuntimeException(
 *             "Interrupted while awaiting runLatch during shutdown", e);
 * }
 * }</pre>
 */
public class InterruptedRuntimeException extends RuntimeException {

    /**
     * Creates a new exception with a message only.
     *
     * <p>Use this overload when there is no underlying
     * {@link InterruptedException} to chain (rare; usually you have one).</p>
     *
     * @param message a short description of what was being awaited when
     *                interruption was observed
     */
    public InterruptedRuntimeException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with a message and a chained cause.
     *
     * <p>Accepts {@link Throwable} for the rare cases where the cause was
     * already wrapped before reaching this site. For the common case where
     * the cause is the original {@link InterruptedException}, prefer
     * {@link #InterruptedRuntimeException(String, InterruptedException)}
     * for compile-time type-safety.</p>
     *
     * @param message a short description of what was being awaited
     * @param cause   the original cause (typically an {@link InterruptedException})
     */
    public InterruptedRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception with a message and a typed
     * {@link InterruptedException} cause.
     *
     * <p>This overload narrows the cause type so the compiler verifies the
     * catch site is wrapping an actual {@link InterruptedException} rather
     * than misusing the class for a generic Throwable.</p>
     *
     * @param message a short description of what was being awaited when
     *                interruption was observed
     * @param cause   the original {@link InterruptedException} caught at the
     *                throw site; the caller must already have restored the
     *                interrupt flag via
     *                {@code Thread.currentThread().interrupt()}
     */
    public InterruptedRuntimeException(String message, InterruptedException cause) {
        super(message, cause);
    }
}
