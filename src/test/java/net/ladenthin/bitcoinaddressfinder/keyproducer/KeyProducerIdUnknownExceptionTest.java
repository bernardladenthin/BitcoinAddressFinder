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
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import org.junit.Test;

public class KeyProducerIdUnknownExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="KeyProducerIdUnknownException">
    @Test
    public void keyProducerIdUnknownException_idGiven_messageContainsId() {
        // arrange
        String id = "unknownProducer";

        // act
        KeyProducerIdUnknownException ex = new KeyProducerIdUnknownException(id);

        // assert
        assertThat(ex.getMessage(), containsString(id));
    }

    @Test
    public void keyProducerIdUnknownException_idGiven_messageIndicatesUnknown() {
        // arrange, act
        KeyProducerIdUnknownException ex = new KeyProducerIdUnknownException("anyId");

        // assert
        assertThat(ex.getMessage(), containsString("unknown"));
    }

    @Test
    public void keyProducerIdUnknownException_isRuntimeException() {
        // arrange, act
        KeyProducerIdUnknownException ex = new KeyProducerIdUnknownException("anyId");

        // assert
        assertThat(ex, is(instanceOf(RuntimeException.class)));
    }

    @Test
    public void keyProducerIdUnknownException_nullId_messageContainsNull() {
        // arrange, act
        KeyProducerIdUnknownException ex = new KeyProducerIdUnknownException(null);

        // assert
        assertThat(ex.getMessage(), containsString("null"));
    }
    // </editor-fold>
}
