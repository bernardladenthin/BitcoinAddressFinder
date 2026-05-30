// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link KeyProducerIdNullException}.
 */
public class KeyProducerIdNullExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="constructor">
    @Test
    public void constructor_noArg_messageContainsNull() {
        // act
        KeyProducerIdNullException exception = new KeyProducerIdNullException();

        // assert
        assertThat(exception.getMessage(), containsString("null"));
    }

    @Test
    public void constructor_noArg_messageEqualsExpected() {
        // act
        KeyProducerIdNullException exception = new KeyProducerIdNullException();

        // assert
        assertThat(exception.getMessage(), is(equalTo("Key producer id must not be null.")));
    }

    @Test
    public void constructor_noArg_isInstanceOfRuntimeException() {
        // act
        KeyProducerIdNullException exception = new KeyProducerIdNullException();

        // assert
        assertThat(exception, is(instanceOf(RuntimeException.class)));
    }

    @Test
    public void constructor_noArg_noCause() {
        // act
        KeyProducerIdNullException exception = new KeyProducerIdNullException();

        // assert
        assertThat(exception.getCause(), is(nullValue()));
    }
    // </editor-fold>
}
