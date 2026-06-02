// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a discarded {@link java.util.concurrent.Future} return value as intentionally
 * fire-and-forget.
 *
 * <p>Apply at any call site where a {@code Future}-returning method (typically
 * {@code ExecutorService.submit} / {@code .execute} / {@code .scheduleAtFixedRate})
 * is invoked for its side effect only — the {@code Future} itself is discarded
 * because the submitted task's lifecycle is driven by an out-of-band mechanism
 * (executor shutdown, a sibling {@code interrupt()} method, etc.). This is the
 * canonical shape Error Prone's {@code FutureReturnValueIgnored} check warns
 * about; the pattern is correct here and the warning is intent-wise a false
 * positive.</p>
 *
 * <p><strong>Always pair with {@code @SuppressWarnings("FutureReturnValueIgnored")}.</strong>
 * Java does not let a custom annotation inherit
 * {@code @SuppressWarnings} semantics, so the JDK suppression annotation must
 * remain on every call site; this marker exists only to centralise the
 * documentation of the pattern and make every such site findable via
 * {@code grep -rn "@FireAndForget"} or IDE search.</p>
 *
 * <p>The optional {@link #value} field documents the site-specific shutdown
 * path (e.g. {@code "lifecycle via Producer.interrupt() and Finder.interrupt()"});
 * the shared "what fire-and-forget means" definition lives once on this
 * annotation's Javadoc rather than being repeated in a comment at every call
 * site.</p>
 *
 * <p>Canonical usage at a call site:</p>
 * <pre>{@code
 * @FireAndForget("lifecycle via Producer.interrupt() shutdown")
 * @SuppressWarnings("FutureReturnValueIgnored")
 * Object unused = producerExecutorService.submit(producer);
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
    ElementType.LOCAL_VARIABLE,
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.PARAMETER
})
public @interface FireAndForget {
    /**
     * Optional site-specific note describing the shutdown path that drives the
     * discarded {@code Future}'s task to completion. Empty when the call site is
     * self-explanatory from its surrounding context.
     *
     * @return site-specific shutdown-path description, or empty string
     */
    String value() default "";
}
