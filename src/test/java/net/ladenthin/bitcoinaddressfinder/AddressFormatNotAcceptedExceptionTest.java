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
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import org.junit.Test;

/**
 * Unit tests for {@link AddressFormatNotAcceptedException}.
 */
public class AddressFormatNotAcceptedExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="constructor">
    @Test
    public void constructor_withReason_createsException() {
        // arrange
        String reason = "address is empty";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason);

        // assert
        assertThat(exception, is(instanceOf(Exception.class)));
    }

    @Test
    public void constructor_withReason_messageContainsReason() {
        // arrange
        String reason = "address is empty";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason);

        // assert
        assertThat(exception.getMessage(), containsString(reason));
    }

    @Test
    public void constructor_withReason_messageContainsPrefix() {
        // arrange
        String reason = "unsupported format";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason);

        // assert
        assertThat(exception.getMessage(), containsString("Address format not accepted:"));
    }

    @Test
    public void constructor_withReason_messageEqualsExpected() {
        // arrange
        String reason = "P2TR is not supported";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason);

        // assert
        assertThat(exception.getMessage(), is(equalTo("Address format not accepted: " + reason)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getReason">
    @Test
    public void getReason_withReason_returnsExpectedReason() {
        // arrange
        String reason = "address is null";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason);
        String actual = exception.getReason();

        // assert
        assertThat(actual, is(equalTo(reason)));
    }

    @Test
    public void getReason_withEmptyReason_returnsEmptyString() {
        // arrange
        String reason = "";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason);
        String actual = exception.getReason();

        // assert
        assertThat(actual, is(equalTo("")));
    }

    @Test
    public void getReason_withDifferentReasons_eachReasonIsPreserved() {
        // arrange
        String reason1 = "reason one";
        String reason2 = "reason two";

        // act
        AddressFormatNotAcceptedException exception1 = new AddressFormatNotAcceptedException(reason1);
        AddressFormatNotAcceptedException exception2 = new AddressFormatNotAcceptedException(reason2);

        // assert
        assertThat(exception1.getReason(), is(equalTo(reason1)));
        assertThat(exception2.getReason(), is(equalTo(reason2)));
    }
    // </editor-fold>
}
