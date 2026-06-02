// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UnknownSecretFormatException}.
 */
public class UnknownSecretFormatExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="constructor">
    @Test
    public void constructor_withBigInteger_messageContainsFormatName() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.BIG_INTEGER;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), containsString("BIG_INTEGER"));
    }

    @Test
    public void constructor_withSha256_messageContainsFormatName() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.SHA256;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), containsString("SHA256"));
    }

    @Test
    public void constructor_withStringDoSha256_messageContainsFormatName() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.STRING_DO_SHA256;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), containsString("STRING_DO_SHA256"));
    }

    @Test
    public void constructor_withDumpedPrivateKey_messageContainsFormatName() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.DUMPED_PRIVATE_KEY;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), containsString("DUMPED_PRIVATE_KEY"));
    }

    @Test
    public void constructor_withBigInteger_messageContainsExpectedPrefix() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.BIG_INTEGER;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), containsString("Unknown secret format:"));
    }

    @Test
    public void constructor_withBigInteger_messageEqualsExpected() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.BIG_INTEGER;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), is(equalTo("Unknown secret format: BIG_INTEGER")));
    }

    @Test
    public void constructor_withSha256_messageEqualsExpected() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.SHA256;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), is(equalTo("Unknown secret format: SHA256")));
    }

    @Test
    public void constructor_withBigInteger_isInstanceOfIllegalArgumentException() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.BIG_INTEGER;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception, is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void constructor_withBigInteger_noCause() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.BIG_INTEGER;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getCause(), is(nullValue()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getSecretFormat">
    @Test
    public void getSecretFormat_withBigInteger_returnsSecretFormat() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.BIG_INTEGER;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);
        CSecretFormat actual = exception.getSecretFormat();

        // assert
        assertThat(actual, is(equalTo(secretFormat)));
    }

    @Test
    public void getSecretFormat_withSha256_returnsSecretFormat() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.SHA256;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);
        CSecretFormat actual = exception.getSecretFormat();

        // assert
        assertThat(actual, is(equalTo(secretFormat)));
    }
    // </editor-fold>
}
