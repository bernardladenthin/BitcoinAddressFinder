// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import org.junit.Test;

public class NoMoreSecretsAvailableExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="NoMoreSecretsAvailableException">
    @Test
    public void noMoreSecretsAvailableException_noArgConstructor_isRuntimeException() {
        // arrange, act
        NoMoreSecretsAvailableException ex = new NoMoreSecretsAvailableException();

        // assert
        assertThat(ex, is(instanceOf(RuntimeException.class)));
    }

    @Test
    public void noMoreSecretsAvailableException_noArgConstructor_noCause() {
        // arrange, act
        NoMoreSecretsAvailableException ex = new NoMoreSecretsAvailableException();

        // assert
        assertThat(ex.getCause(), is(nullValue()));
    }

    @Test
    public void noMoreSecretsAvailableException_messageConstructor_messageIsPreserved() {
        // arrange
        String message = "no more secrets in the file";

        // act
        NoMoreSecretsAvailableException ex = new NoMoreSecretsAvailableException(message);

        // assert
        assertThat(ex.getMessage(), is(equalTo(message)));
    }

    @Test
    public void noMoreSecretsAvailableException_messageConstructor_noCause() {
        // arrange, act
        NoMoreSecretsAvailableException ex = new NoMoreSecretsAvailableException("a message");

        // assert
        assertThat(ex.getCause(), is(nullValue()));
    }

    @Test
    public void noMoreSecretsAvailableException_messageCauseConstructor_messageIsPreserved() {
        // arrange
        String message = "wrapped IO failure";
        Throwable cause = new RuntimeException("root cause");

        // act
        NoMoreSecretsAvailableException ex = new NoMoreSecretsAvailableException(message, cause);

        // assert
        assertThat(ex.getMessage(), containsString(message));
    }

    @Test
    public void noMoreSecretsAvailableException_messageCauseConstructor_causeIsPreserved() {
        // arrange
        Throwable cause = new RuntimeException("root cause");

        // act
        NoMoreSecretsAvailableException ex = new NoMoreSecretsAvailableException("msg", cause);

        // assert
        assertThat(ex.getCause(), is(equalTo(cause)));
    }

    @Test
    public void noMoreSecretsAvailableException_causeConstructor_causeIsPreserved() {
        // arrange
        Throwable cause = new IllegalStateException("underlying cause");

        // act
        NoMoreSecretsAvailableException ex = new NoMoreSecretsAvailableException(cause);

        // assert
        assertThat(ex.getCause(), is(equalTo(cause)));
    }

    @Test
    public void noMoreSecretsAvailableException_causeConstructor_messageContainsCauseMessage() {
        // arrange
        Throwable cause = new IllegalStateException("underlying cause");

        // act
        NoMoreSecretsAvailableException ex = new NoMoreSecretsAvailableException(cause);

        // assert
        assertThat(ex.getMessage(), containsString("underlying cause"));
    }
    // </editor-fold>
}
