// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.Test;

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
        assertThat(exception.getMessage(), is(equalTo("Unknown secret format: BIG_INTEGER")));
    }

    @Test
    public void constructor_withSha256_messageContainsFormatName() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.SHA256;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), is(equalTo("Unknown secret format: SHA256")));
    }

    @Test
    public void constructor_withStringDoSha256_messageContainsFormatName() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.STRING_DO_SHA256;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), is(equalTo("Unknown secret format: STRING_DO_SHA256")));
    }

    @Test
    public void constructor_withDumpedPrivateKey_messageContainsFormatName() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.DUMPED_RIVATE_KEY;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), is(equalTo("Unknown secret format: DUMPED_RIVATE_KEY")));
    }

    @Test
    public void constructor_anyFormat_messageContainsExpectedPrefix() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.BIG_INTEGER;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception.getMessage(), containsString("Unknown secret format:"));
    }

    @Test
    public void constructor_anyFormat_isInstanceOfIllegalArgumentException() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.SHA256;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception, is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void constructor_anyFormat_exceptionIsNotNull() {
        // arrange
        CSecretFormat secretFormat = CSecretFormat.BIG_INTEGER;

        // act
        UnknownSecretFormatException exception = new UnknownSecretFormatException(secretFormat);

        // assert
        assertThat(exception, is(notNullValue()));
    }
    // </editor-fold>
}
