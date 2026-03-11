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

/**
 * Unit tests for {@link KeyProducerIdIsNotUniqueException}.
 */
public class KeyProducerIdIsNotUniqueExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="constructor">
    @Test
    public void constructor_withId_messageContainsId() {
        // arrange
        String id = "myProducerId";

        // act
        KeyProducerIdIsNotUniqueException exception = new KeyProducerIdIsNotUniqueException(id);

        // assert
        assertThat(exception.getMessage(), containsString(id));
    }

    @Test
    public void constructor_withId_messageContainsExpectedPrefix() {
        // arrange
        String id = "anyId";

        // act
        KeyProducerIdIsNotUniqueException exception = new KeyProducerIdIsNotUniqueException(id);

        // assert
        assertThat(exception.getMessage(), containsString("unique"));
    }

    @Test
    public void constructor_withId_messageEqualsExpected() {
        // arrange
        String id = "myProducerId";

        // act
        KeyProducerIdIsNotUniqueException exception = new KeyProducerIdIsNotUniqueException(id);

        // assert
        assertThat(exception.getMessage(), is(equalTo("Key producer id must be unique: " + id)));
    }

    @Test
    public void constructor_withId_isInstanceOfRuntimeException() {
        // arrange
        String id = "anyId";

        // act
        KeyProducerIdIsNotUniqueException exception = new KeyProducerIdIsNotUniqueException(id);

        // assert
        assertThat(exception, is(instanceOf(RuntimeException.class)));
    }

    @Test
    public void constructor_withId_noCause() {
        // arrange
        String id = "anyId";

        // act
        KeyProducerIdIsNotUniqueException exception = new KeyProducerIdIsNotUniqueException(id);

        // assert
        assertThat(exception.getCause(), is(nullValue()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getId">
    @Test
    public void getId_withId_returnsId() {
        // arrange
        String id = "producerAlpha";

        // act
        KeyProducerIdIsNotUniqueException exception = new KeyProducerIdIsNotUniqueException(id);
        String actual = exception.getId();

        // assert
        assertThat(actual, is(equalTo(id)));
    }
    // </editor-fold>
}
