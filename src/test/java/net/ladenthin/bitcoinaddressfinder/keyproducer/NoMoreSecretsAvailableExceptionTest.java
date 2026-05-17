// @formatter:off

// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import org.junit.Test;

/**
 * Unit tests for {@link NoMoreSecretsAvailableException}.
 */
public class NoMoreSecretsAvailableExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="constructor">
    @Test
    public void constructor_noArg_isInstanceOfRuntimeException() {
        // act
        NoMoreSecretsAvailableException exception = new NoMoreSecretsAvailableException();

        // assert
        assertThat(exception, is(instanceOf(RuntimeException.class)));
    }

    @Test
    public void constructor_noArg_nullMessage() {
        // act
        NoMoreSecretsAvailableException exception = new NoMoreSecretsAvailableException();

        // assert
        assertThat(exception.getMessage(), is(nullValue()));
    }

    @Test
    public void constructor_noArg_noCause() {
        // act
        NoMoreSecretsAvailableException exception = new NoMoreSecretsAvailableException();

        // assert
        assertThat(exception.getCause(), is(nullValue()));
    }

    @Test
    public void constructor_withMessage_messageIsPreserved() {
        // arrange
        String message = "no more secrets in the file";

        // act
        NoMoreSecretsAvailableException exception = new NoMoreSecretsAvailableException(message);

        // assert
        assertThat(exception.getMessage(), is(equalTo(message)));
    }

    @Test
    public void constructor_withMessage_noCause() {
        // arrange
        String message = "a message";

        // act
        NoMoreSecretsAvailableException exception = new NoMoreSecretsAvailableException(message);

        // assert
        assertThat(exception.getCause(), is(nullValue()));
    }

    @Test
    public void constructor_withMessageAndCause_messageIsPreserved() {
        // arrange
        String message = "wrapped IO failure";
        Throwable cause = new RuntimeException("root cause");

        // act
        NoMoreSecretsAvailableException exception = new NoMoreSecretsAvailableException(message, cause);

        // assert
        assertThat(exception.getMessage(), containsString(message));
    }

    @Test
    public void constructor_withMessageAndCause_causeIsPreserved() {
        // arrange
        String message = "msg";
        Throwable cause = new RuntimeException("root cause");

        // act
        NoMoreSecretsAvailableException exception = new NoMoreSecretsAvailableException(message, cause);

        // assert
        assertThat(exception.getCause(), is(equalTo(cause)));
    }

    @Test
    public void constructor_withCause_causeIsPreserved() {
        // arrange
        Throwable cause = new IllegalStateException("underlying cause");

        // act
        NoMoreSecretsAvailableException exception = new NoMoreSecretsAvailableException(cause);

        // assert
        assertThat(exception.getCause(), is(equalTo(cause)));
    }

    @Test
    public void constructor_withCause_messageContainsCauseMessage() {
        // arrange
        Throwable cause = new IllegalStateException("underlying cause");

        // act
        NoMoreSecretsAvailableException exception = new NoMoreSecretsAvailableException(cause);

        // assert
        assertThat(exception.getMessage(), containsString("underlying cause"));
    }
    // </editor-fold>
}
