// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import org.junit.Test;

/**
 * Unit tests for {@link KeyProducerIdUnknownException}.
 */
public class KeyProducerIdUnknownExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="constructor">
    @Test
    public void constructor_withId_messageContainsId() {
        // arrange
        String id = "unknownProducer";

        // act
        KeyProducerIdUnknownException exception = new KeyProducerIdUnknownException(id);

        // assert
        assertThat(exception.getMessage(), containsString(id));
    }

    @Test
    public void constructor_withId_messageContainsExpectedPrefix() {
        // arrange
        String id = "anyId";

        // act
        KeyProducerIdUnknownException exception = new KeyProducerIdUnknownException(id);

        // assert
        assertThat(exception.getMessage(), containsString("unknown"));
    }

    @Test
    public void constructor_withId_messageEqualsExpected() {
        // arrange
        String id = "unknownProducer";

        // act
        KeyProducerIdUnknownException exception = new KeyProducerIdUnknownException(id);

        // assert
        assertThat(exception.getMessage(), is(equalTo("Key producer id is unknown: " + id)));
    }

    @Test
    public void constructor_withId_isInstanceOfRuntimeException() {
        // arrange
        String id = "anyId";

        // act
        KeyProducerIdUnknownException exception = new KeyProducerIdUnknownException(id);

        // assert
        assertThat(exception, is(instanceOf(RuntimeException.class)));
    }

    @Test
    public void constructor_withId_noCause() {
        // arrange
        String id = "anyId";

        // act
        KeyProducerIdUnknownException exception = new KeyProducerIdUnknownException(id);

        // assert
        assertThat(exception.getCause(), is(nullValue()));
    }

    @Test
    public void constructor_withNullId_messageContainsNull() {
        // act
        KeyProducerIdUnknownException exception = new KeyProducerIdUnknownException(null);

        // assert
        assertThat(exception.getMessage(), containsString("null"));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getId">
    @Test
    public void getId_withId_returnsId() {
        // arrange
        String id = "unknownProducer";

        // act
        KeyProducerIdUnknownException exception = new KeyProducerIdUnknownException(id);
        String actual = exception.getId();

        // assert
        assertThat(actual, is(equalTo(id)));
    }

    @Test
    public void getId_withNullId_returnsNull() {
        // act
        KeyProducerIdUnknownException exception = new KeyProducerIdUnknownException(null);
        String actual = exception.getId();

        // assert
        assertThat(actual, is(nullValue()));
    }
    // </editor-fold>
}
