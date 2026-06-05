// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InterruptedRuntimeException}.
 *
 * <p>Covers the three-arm constructor matrix mandated by the cross-repo
 * typed-exception unification audit (see
 * {@code workspace/crossrepostatus.md}): plain message, message + generic
 * cause, and message + typed {@link InterruptedException} cause. Also
 * verifies that the class itself does not touch the interrupt flag — that
 * remains the caller's responsibility per the class Javadoc contract.</p>
 */
public class InterruptedRuntimeExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="constructor(message)">
    @Test
    public void constructor_withMessage_messageEquals() {
        // arrange
        String message = "Interrupted while awaiting consumer termination";

        // act
        InterruptedRuntimeException exception = new InterruptedRuntimeException(message);

        // assert
        assertThat(exception.getMessage(), is(equalTo(message)));
    }

    @Test
    public void constructor_withMessage_causeIsNull() {
        // arrange
        String message = "Interrupted while awaiting consumer termination";

        // act
        InterruptedRuntimeException exception = new InterruptedRuntimeException(message);

        // assert
        assertThat(exception.getCause(), is(nullValue()));
    }

    @Test
    public void constructor_withMessage_isInstanceOfRuntimeException() {
        // arrange
        String message = "any";

        // act
        InterruptedRuntimeException exception = new InterruptedRuntimeException(message);

        // assert
        assertThat(exception, is(instanceOf(RuntimeException.class)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="constructor(message, Throwable)">
    @Test
    public void constructor_withMessageAndThrowableCause_messageAndCauseEqual() {
        // arrange
        String message = "Interrupted while waiting for x";
        Throwable cause = new RuntimeException("wrapper");

        // act
        InterruptedRuntimeException exception = new InterruptedRuntimeException(message, cause);

        // assert
        assertThat(exception.getMessage(), is(equalTo(message)));
        assertThat(exception.getCause(), is(sameInstance(cause)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="constructor(message, InterruptedException)">
    @Test
    public void constructor_withMessageAndInterruptedExceptionCause_chainsTypedCause() {
        // arrange
        String message = "Interrupted while awaiting runLatch";
        InterruptedException cause = new InterruptedException("inner");

        // act
        InterruptedRuntimeException exception = new InterruptedRuntimeException(message, cause);

        // assert
        assertThat(exception.getMessage(), is(equalTo(message)));
        assertThat(exception.getCause(), is(sameInstance(cause)));
        assertThat(exception.getCause(), is(instanceOf(InterruptedException.class)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="interrupt-flag contract">
    @Test
    public void constructor_doesNotTouchInterruptFlag_whenFlagIsClear() {
        // arrange — make sure the flag starts clear
        Thread.interrupted(); // clear any leftover state

        // act — construct the exception; per Javadoc this must NOT touch the flag
        InterruptedRuntimeException sideEffectProbe =
                new InterruptedRuntimeException("any", new InterruptedException("inner"));

        // assert — flag stays clear; sentinel reference keeps Error Prone's
        // DeadException happy without changing the test's intent.
        assertThat(sideEffectProbe.getMessage(), is(equalTo("any")));
        assertThat(Thread.currentThread().isInterrupted(), is(false));
    }

    @Test
    public void constructor_doesNotTouchInterruptFlag_whenFlagIsSet() {
        // arrange — set the flag, then construct
        Thread.currentThread().interrupt();

        // act — sentinel reference keeps Error Prone's DeadException happy
        InterruptedRuntimeException sideEffectProbe =
                new InterruptedRuntimeException("any", new InterruptedException("inner"));
        assertThat(sideEffectProbe.getMessage(), is(equalTo("any")));

        // assert — flag is still set; construction did NOT clear it.
        // Restore baseline before asserting, then check the captured state.
        boolean flagWasSetAfterConstruction = Thread.interrupted(); // also clears
        assertThat(flagWasSetAfterConstruction, is(true));
    }
    // </editor-fold>
}
