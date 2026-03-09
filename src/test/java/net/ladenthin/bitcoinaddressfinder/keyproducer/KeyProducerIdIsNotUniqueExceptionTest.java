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

public class KeyProducerIdIsNotUniqueExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="KeyProducerIdIsNotUniqueException">
    @Test
    public void keyProducerIdIsNotUniqueException_idGiven_messageContainsId() {
        // arrange
        String id = "myProducerId";

        // act
        KeyProducerIdIsNotUniqueException ex = new KeyProducerIdIsNotUniqueException(id);

        // assert
        assertThat(ex.getMessage(), containsString(id));
    }

    @Test
    public void keyProducerIdIsNotUniqueException_idGiven_messageIndicatesUniquenessRequirement() {
        // arrange, act
        KeyProducerIdIsNotUniqueException ex = new KeyProducerIdIsNotUniqueException("anyId");

        // assert
        assertThat(ex.getMessage(), containsString("unique"));
    }

    @Test
    public void keyProducerIdIsNotUniqueException_isRuntimeException() {
        // arrange, act
        KeyProducerIdIsNotUniqueException ex = new KeyProducerIdIsNotUniqueException("anyId");

        // assert
        assertThat(ex, is(instanceOf(RuntimeException.class)));
    }

    @Test
    public void keyProducerIdIsNotUniqueException_differentIds_messagesContainRespectiveId() {
        // arrange
        String idA = "producerAlpha";
        String idB = "producerBeta";

        // act
        KeyProducerIdIsNotUniqueException exA = new KeyProducerIdIsNotUniqueException(idA);
        KeyProducerIdIsNotUniqueException exB = new KeyProducerIdIsNotUniqueException(idB);

        // assert
        assertThat(exA.getMessage(), containsString(idA));
        assertThat(exB.getMessage(), containsString(idB));
    }
    // </editor-fold>
}
