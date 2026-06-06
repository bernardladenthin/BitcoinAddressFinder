// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.io;

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

    // <editor-fold defaultstate="collapsed" desc="constructor(reason, detail)">
    @Test
    public void constructor_withReasonAndDetail_messageContainsBoth() {
        // arrange
        String reason = "P2TR is not supported";
        String detail = "bc1pXyZ123Taproot...";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason, detail);

        // assert
        assertThat(exception.getMessage(), containsString(reason));
        assertThat(exception.getMessage(), containsString(detail));
    }

    @Test
    public void constructor_withReasonAndDetail_messageEqualsExpected() {
        // arrange
        String reason = "address is empty";
        String detail = "   # commented out line";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason, detail);

        // assert
        assertThat(
                exception.getMessage(),
                is(equalTo("Address format not accepted: " + reason + " (input: " + detail + ")")));
    }

    @Test
    public void constructor_withReasonAndDetail_getReasonReturnsReasonOnly() {
        // arrange — getReason() must NOT include the detail; aggregation depends on it
        String reason = "address is empty";
        String detail = "   # commented out line";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason, detail);

        // assert
        assertThat(exception.getReason(), is(equalTo(reason)));
    }

    @Test
    public void constructor_withReasonAndDetail_noCause() {
        // arrange
        String reason = "unsupported witness version";
        String detail = "bc1qFoo... witnessVersion=2";

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason, detail);

        // assert
        assertThat(exception.getCause(), is(nullValue()));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="constructor(reason, detail, cause)">
    @Test
    public void constructor_withReasonDetailAndCause_messageContainsAll() {
        // arrange
        String reason = "Bitcoin Cash q-address not parsable";
        String detail = "qpfoo...invalid";
        Throwable cause = new RuntimeException("base32 decode failed");

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason, detail, cause);

        // assert
        assertThat(exception.getMessage(), containsString(reason));
        assertThat(exception.getMessage(), containsString(detail));
    }

    @Test
    public void constructor_withReasonDetailAndCause_chainsCause() {
        // arrange
        String reason = "invalid base58";
        String detail = "1NotABase58Address!!!";
        Throwable cause = new RuntimeException("checksum mismatch");

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason, detail, cause);

        // assert
        assertThat(exception.getCause(), is(equalTo(cause)));
    }

    @Test
    public void constructor_withReasonDetailAndCause_getReasonReturnsReasonOnly() {
        // arrange — getReason() aggregation invariant holds for the 3-arg form too
        String reason = "Bitcoin Cash q-address not parsable";
        String detail = "qpfoo...invalid";
        Throwable cause = new RuntimeException("base32 decode failed");

        // act
        AddressFormatNotAcceptedException exception = new AddressFormatNotAcceptedException(reason, detail, cause);

        // assert
        assertThat(exception.getReason(), is(equalTo(reason)));
    }
    // </editor-fold>
}
