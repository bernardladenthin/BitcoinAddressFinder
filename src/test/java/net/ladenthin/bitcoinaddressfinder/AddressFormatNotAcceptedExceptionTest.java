// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AddressFormatNotAcceptedException}.
 */
public class AddressFormatNotAcceptedExceptionTest {

    // <editor-fold defaultstate="collapsed" desc="constructor">
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
    public void constructor_withReason_messageContainsExpectedPrefix() {
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

    @Test
    public void constructor_withReason_isInstanceOfException() {
        // arrange
        String reason = "address is empty";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason);

        // assert
        assertThat(exception, is(instanceOf(Exception.class)));
    }

    @Test
    public void constructor_withReason_noCause() {
        // arrange
        String reason = "address is empty";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason);

        // assert
        assertThat(exception.getCause(), is(nullValue()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getReason">
    @Test
    public void getReason_withReason_returnsReason() {
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
    // </editor-fold>
}
